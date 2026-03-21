#!/usr/bin/env python3
"""
Simulate the current OneBitHardwareFilter (--retro pc) and perform Welch PSD analysis.

Models the exact Java implementation:
  - 4x oversampling (INTERNAL_RATE = 176400 Hz)
  - Electrical pre-filter: τ_e = 10 µs, α_e ≈ 0.433
  - 6-pole cone IIR: τ_m = 37.9 µs, α_m ≈ 0.1395
  - 8-bit pre-quantization (preLevels = 128)
  - Drive gain = 2.0, invDriveGain = 0.5
  - PWM carrier: 15200 Hz, 78 levels

Also analyzes the reference WAV (realsound_sample.wav) for comparison.
"""

import numpy as np
from numpy.fft import rfft, rfftfreq
import struct
import os
import sys

FS          = 44100
OVERSAMPLE  = 4
INTERNAL_RATE = FS * OVERSAMPLE  # 176400

# --- PC mode parameters ---
CARRIER_HZ  = 15200.0
LEVELS      = 78.0
TAU_M_US    = 37.9          # mechanical: cone inertia
TAU_E_US    = 10.0          # electrical: voice-coil inductance
ALPHA_M     = 1.0 - np.exp(-1.0 / (INTERNAL_RATE * TAU_M_US * 1e-6))   # ≈ 0.1395
ALPHA_E     = 1.0 - np.exp(-1.0 / (INTERNAL_RATE * TAU_E_US * 1e-6))   # ≈ 0.433
SUB_STEP    = CARRIER_HZ / INTERNAL_RATE   # phase advance per sub-sample
PRE_LEVELS  = 128   # 8-bit: 1 << (8-1)
DRIVE_GAIN  = 2.0
INV_DRIVE   = 1.0 / DRIVE_GAIN


def simulate_pc(signal_mono, verbose=True):
    """Exact Python mirror of OneBitHardwareFilter.processOneSample() for PC mode."""
    n = len(signal_mono)
    out = np.zeros(n, dtype=np.float64)

    phase    = 0.0
    pre      = 0.0   # iirStatePre
    s1 = s2 = s3 = s4 = s5 = s6 = 0.0

    for i in range(n):
        mono = float(signal_mono[i])

        # Drive gain
        mono = max(-1.0, min(1.0, mono * DRIVE_GAIN))

        # 8-bit pre-quantization (no dither)
        mono = round(mono * PRE_LEVELS) / PRE_LEVELS

        # Silence fast-path
        if abs(mono) < 1e-4:
            for _ in range(OVERSAMPLE):
                pre += ALPHA_E * (0.0 - pre)
                s1  += ALPHA_M * (pre - s1)
                s2  += ALPHA_M * (s1  - s2)
                s3  += ALPHA_M * (s2  - s3)
                s4  += ALPHA_M * (s3  - s4)
                s5  += ALPHA_M * (s4  - s5)
                s6  += ALPHA_M * (s5  - s6)
                phase = (phase + SUB_STEP) % 1.0
            out[i] = s6 * INV_DRIVE
            continue

        raw_duty = max(0.0, min(1.0, (mono + 1.0) * 0.5))
        duty     = round(raw_duty * LEVELS) / LEVELS

        for _ in range(OVERSAMPLE):
            bit  = 1.0 if phase < duty else -1.0
            pre += ALPHA_E * (bit - pre)
            s1  += ALPHA_M * (pre - s1)
            s2  += ALPHA_M * (s1  - s2)
            s3  += ALPHA_M * (s2  - s3)
            s4  += ALPHA_M * (s3  - s4)
            s5  += ALPHA_M * (s4  - s5)
            s6  += ALPHA_M * (s5  - s6)
            phase = (phase + SUB_STEP) % 1.0

        out[i] = s6 * INV_DRIVE

    if verbose:
        rms = np.sqrt(np.mean(out**2))
        peak = np.max(np.abs(out))
        print(f"  Output RMS  : {rms:.4f}  ({20*np.log10(rms+1e-12):.1f} dBFS)")
        print(f"  Output Peak : {peak:.4f}  ({20*np.log10(peak+1e-12):.1f} dBFS)")
    return out


def welch_psd(signal, fs=44100, nperseg=4096):
    """Welch PSD using Hann window. Returns (freqs, psd_db)."""
    from numpy.lib.stride_tricks import sliding_window_view
    step = nperseg // 2
    n = len(signal)
    # number of complete windows
    nwin = (n - nperseg) // step + 1
    wins = sliding_window_view(signal[:step*(nwin-1)+nperseg], nperseg)[::step]
    window = np.hanning(nperseg)
    win_power = np.sum(window**2)
    specs = np.abs(np.fft.rfft(wins * window, n=nperseg))**2 / (win_power * fs)
    psd = np.mean(specs, axis=0)
    freqs = np.fft.rfftfreq(nperseg, 1.0/fs)
    return freqs, psd


def band_power(freqs, psd, f_lo, f_hi):
    """Integrate PSD over a frequency band (linear, then return dB)."""
    mask = (freqs >= f_lo) & (freqs <= f_hi)
    if not np.any(mask):
        return -np.inf
    df = freqs[1] - freqs[0]
    power = np.sum(psd[mask]) * df
    return 10 * np.log10(power + 1e-30)


def peak_db(freqs, psd, f_center, f_bw=200):
    """Peak magnitude in a narrow band around f_center."""
    mask = (freqs >= f_center - f_bw) & (freqs <= f_center + f_bw)
    if not np.any(mask):
        return -np.inf
    return 10 * np.log10(np.max(psd[mask]) + 1e-30)


def analyze_spectrum(label, signal, fs=44100, freq=440.0):
    print(f"\n{'='*60}")
    print(f" {label}")
    print(f"{'='*60}")

    freqs, psd = welch_psd(signal, fs)

    # Fundamental + first 5 harmonics
    signal_harmonics = [freq * k for k in range(1, 7)]
    sig_powers = []
    for f in signal_harmonics:
        if f < fs / 2:
            p = band_power(freqs, psd, f - 150, f + 150)
            sig_powers.append(p)
            print(f"  Harmonic {f:6.0f} Hz : {p:+.1f} dB")

    # Carrier region
    carrier_p = band_power(freqs, psd, CARRIER_HZ - 800, CARRIER_HZ + 800)
    print(f"\n  Carrier ~{CARRIER_HZ:.0f} Hz  : {carrier_p:+.1f} dB")

    # Noise floor bands (away from signal and carrier)
    noise_bands = [(100, 400), (1500, 2500), (3000, 6000), (7000, 12000)]
    noise_powers = []
    for lo, hi in noise_bands:
        p = band_power(freqs, psd, lo, hi)
        noise_powers.append(p)
        print(f"  Noise  {lo:4d}-{hi:5d} Hz : {p:+.1f} dB")

    # S/N estimate: strongest harmonic vs average noise band
    if sig_powers and noise_powers:
        sig_ref = max(sig_powers)
        noise_ref = np.mean(noise_powers)
        snr = sig_ref - noise_ref
        print(f"\n  --> Approx S/N  : {snr:.1f} dB  (strongest harmonic vs avg noise band)")
        carrier_rel = carrier_p - sig_ref
        print(f"  --> Carrier rel : {carrier_rel:.1f} dB  (carrier vs fundamental)")

    return freqs, psd


def load_wav(path):
    """Read a WAV file, return (samples_float, sample_rate)."""
    with open(path, 'rb') as f:
        # RIFF header
        riff, size, wave = struct.unpack('<4sI4s', f.read(12))
        assert riff == b'RIFF' and wave == b'WAVE'
        fmt_data = None
        pcm_data = None
        while True:
            hdr = f.read(8)
            if len(hdr) < 8: break
            chunk_id, chunk_size = struct.unpack('<4sI', hdr)
            chunk = f.read(chunk_size)
            if chunk_id == b'fmt ':
                fmt_data = chunk
            elif chunk_id == b'data':
                pcm_data = chunk
        audio_fmt, num_ch, sr, _, _, bits = struct.unpack('<HHIIHH', fmt_data[:16])
        assert audio_fmt == 1 and bits == 16
        samples = np.frombuffer(pcm_data, dtype='<i2').astype(np.float32) / 32768.0
        if num_ch > 1:
            samples = samples[::num_ch]  # take left channel
        return samples, sr


def main():
    DURATION = 4.0
    FREQ     = 440.0
    n = int(FS * DURATION)
    t = np.arange(n) / FS
    sine = np.sin(2.0 * np.pi * FREQ * t) * 0.5   # -6 dBFS, not full scale

    # --- Simulated output ---
    print(f"\nSimulating PC filter on {FREQ} Hz sine (amplitude 0.5) ...")
    print(f"  α_e (electrical, τ={TAU_E_US} µs) = {ALPHA_E:.4f}")
    print(f"  α_m (mechanical, τ={TAU_M_US} µs) = {ALPHA_M:.4f}")
    print(f"  Drive gain = {DRIVE_GAIN}x  (pre-quant levels effective = {int(PRE_LEVELS * DRIVE_GAIN)})")
    filtered = simulate_pc(sine)

    analyze_spectrum(f"Simulated --retro pc  (440 Hz sine @ -6 dBFS)", filtered, FS, FREQ)

    # Also try at lower amplitude to see if noise floor is proportional or fixed
    sine_quiet = np.sin(2.0 * np.pi * FREQ * t) * 0.1  # -20 dBFS
    print(f"\nSimulating PC filter on {FREQ} Hz sine (amplitude 0.1, quiet) ...")
    filtered_quiet = simulate_pc(sine_quiet, verbose=False)
    analyze_spectrum(f"Simulated --retro pc  (440 Hz sine @ -20 dBFS)", filtered_quiet, FS, FREQ)

    # --- Reference WAV ---
    wav_path = os.path.join(os.path.dirname(__file__), '..', 'samples', 'realsound_sample.wav')
    wav_path = os.path.normpath(wav_path)
    if os.path.exists(wav_path):
        print(f"\nLoading reference WAV: {wav_path}")
        ref_signal, ref_sr = load_wav(wav_path)
        print(f"  Duration: {len(ref_signal)/ref_sr:.1f}s @ {ref_sr} Hz")
        # Find dominant frequency in reference
        fft_ref = np.abs(rfft(ref_signal[:ref_sr]))
        ref_freqs = rfftfreq(ref_sr, 1.0/ref_sr)
        dom_freq = ref_freqs[np.argmax(fft_ref[1:]) + 1]
        print(f"  Dominant frequency: {dom_freq:.0f} Hz")
        analyze_spectrum("Reference realsound_sample.wav", ref_signal, ref_sr, dom_freq)
    else:
        print(f"\nReference WAV not found at {wav_path}, skipping.")

    # --- Noise component breakdown ---
    print(f"\n{'='*60}")
    print(" Noise component breakdown (simulated @ -6 dBFS)")
    print(f"{'='*60}")
    freqs, psd = welch_psd(filtered, FS)
    # Remove signal bins (±200 Hz around each harmonic)
    noise_mask = np.ones(len(freqs), dtype=bool)
    for k in range(1, 30):
        fh = FREQ * k
        if fh >= FS / 2: break
        bw = 200 + fh * 0.02  # wider mask for higher harmonics
        noise_mask &= ~((freqs >= fh - bw) & (freqs <= fh + bw))
    # Also mask carrier
    noise_mask &= ~((freqs >= CARRIER_HZ - 1000) & (freqs <= CARRIER_HZ + 1000))

    noise_only = psd.copy()
    noise_only[~noise_mask] = 0.0
    df = freqs[1] - freqs[0]

    # Noise in sub-bands
    sub_bands = [
        ("Quantization noise  20Hz–1kHz  ", 20,    1000),
        ("Quantization noise  1kHz–8kHz  ", 1000,  8000),
        ("Noise               8kHz–14kHz ", 8000,  14000),
        ("Carrier residual   14kHz–16.5kHz",14000, 16500),
        ("Above carrier      16.5kHz–20kHz",16500, 20000),
    ]
    total_noise_power = np.sum(noise_only) * df
    fund_idx = np.argmin(np.abs(freqs - FREQ))
    fund_power = np.sum(psd[max(0,fund_idx-3):fund_idx+4]) * df
    print(f"  Fundamental power : {10*np.log10(fund_power+1e-30):+.1f} dB")
    for label, lo, hi in sub_bands:
        mask = (freqs >= lo) & (freqs <= hi)
        p = np.sum(noise_only[mask]) * df
        rel = 10*np.log10(p+1e-30) - 10*np.log10(fund_power+1e-30)
        print(f"  {label}: {10*np.log10(p+1e-30):+.1f} dB  ({rel:+.1f} dB rel fund)")
    total_noise_db = 10*np.log10(total_noise_power+1e-30)
    snr_total = 10*np.log10(fund_power+1e-30) - total_noise_db
    print(f"\n  Total noise power  : {total_noise_db:+.1f} dB")
    print(f"  Wideband SNR       : {snr_total:.1f} dB")


if __name__ == "__main__":
    main()
