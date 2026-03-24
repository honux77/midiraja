#!/usr/bin/env python3
"""
DSP Pipeline Audio Spectrum Analyzer

Single file (legacy):
  python3 scripts/analyze_audio.py path/to/file.raw

Compare all pipelines in a directory:
  python3 scripts/analyze_audio.py dsp_analysis/

Generate the input files first:
  ./gradlew testClasses && java -cp build/classes/java/test:build/classes/java/main \\
      com.fupfin.midiraja.dsp.DspAnalyzer [output-dir]

Requires: pip install numpy
"""
import sys, os, math

try:
    import numpy as np
except ImportError:
    print("Error: numpy not installed. Run: pip install numpy")
    sys.exit(1)

SR      = 44100
WARMUP  = SR // 2   # skip first 500 ms (reverb warmup)


# ── I/O ───────────────────────────────────────────────────────────────────────

def load_raw(path):
    """Load 16-bit LE stereo interleaved PCM; return left channel as float64 after warmup."""
    data = np.fromfile(path, dtype='<i2').astype(np.float64) / 32768.0
    left = data[0::2]
    return left[WARMUP:]


# ── metrics ───────────────────────────────────────────────────────────────────

def band_rms_db(spectrum, freqs, lo, hi):
    """RMS energy of FFT spectrum in [lo, hi) Hz, in dBFS."""
    mask  = (freqs >= lo) & (freqs < hi)
    power = float(np.mean(spectrum[mask] ** 2)) if mask.any() else 0.0
    return 20 * math.log10(math.sqrt(power) + 1e-12)


def thd_analysis(signal, fundamental=440.0, num_harmonics=5):
    """
    Compute THD using FFT + Hann window.

    Returns:
      thd_pct   – total harmonic distortion in %
      fund_db   – fundamental amplitude in dBFS
      harm_dbs  – list of harmonic amplitudes in dBFS (2f, 3f, …)
    """
    n       = len(signal)
    fft     = np.fft.rfft(signal * np.hanning(n))
    amp     = np.abs(fft) * 2 / n
    freqs   = np.fft.rfftfreq(n, 1.0 / SR)

    def amp_at(f, tol=5.0):
        mask = np.abs(freqs - f) < tol
        return float(np.max(amp[mask])) if mask.any() else 0.0

    fund     = amp_at(fundamental)
    harmonics = [amp_at(fundamental * h) for h in range(2, num_harmonics + 2)]
    thd_pct  = math.sqrt(sum(h ** 2 for h in harmonics)) / (fund + 1e-12) * 100
    to_db    = lambda a: 20 * math.log10(a + 1e-12)
    return thd_pct, to_db(fund), [to_db(h) for h in harmonics]


def noise_floor_db(signal, fundamental=440.0, num_harmonics=6, tol=20.0):
    """
    RMS of in-band noise: FFT energy after masking out fundamental and harmonics.
    Approximates the distortion + noise floor added by the pipeline.
    """
    n     = len(signal)
    fft   = np.fft.rfft(signal * np.hanning(n))
    amp   = np.abs(fft) * 2 / n
    freqs = np.fft.rfftfreq(n, 1.0 / SR)

    exclude = np.zeros(len(freqs), dtype=bool)
    for h in range(1, num_harmonics + 1):
        exclude |= np.abs(freqs - fundamental * h) < tol

    noise = amp[~exclude]
    rms   = math.sqrt(float(np.mean(noise ** 2))) if len(noise) > 0 else 0.0
    return 20 * math.log10(rms + 1e-12)


# ── single-file analysis (legacy) ─────────────────────────────────────────────

def analyze_single(path):
    data = np.fromfile(path, dtype='<i2').astype(np.float64) / 32768.0
    signal = data[0::2] if len(data) % 2 == 0 else data

    n         = len(signal)
    fft_vals  = np.abs(np.fft.rfft(signal))
    fft_freqs = np.fft.rfftfreq(n, 1.0 / SR)
    indices   = np.argsort(fft_vals)[-15:][::-1]

    print(f"\n=== Audio Spectrum Analysis: {path} ===")
    print(f"Sample Rate: {SR} Hz | Expected Fundamental: 440.0 Hz")
    print("-" * 55)
    for idx in indices:
        mag  = fft_vals[idx]
        if mag < 10:
            continue
        freq = fft_freqs[idx]
        if   abs(freq - 440.0) < 5:
            label = "[=== MELODY ==]"
        elif freq % 440.0 < 5 or (440.0 - freq % 440.0) < 5:
            label = "[- HARMONIC -]"
        elif abs(freq - 18600.0) < 100:
            label = "[~ CARRIER  ~]"
        else:
            label = "[! ALIASING !]"
        print(f"{label} {freq:10.1f} Hz | Magnitude: {mag:8.0f}")
    print("-" * 55)


# ── IMD: intermodulation distortion ──────────────────────────────────────────

def imd_products(signal, fundamentals, sr=SR, tol=8.0):
    """
    For a multi-tone input, measure energy at:
      - each input fundamental
      - sum/difference tones (f1±f2, 2f1±f2, etc.)
    Returns a list of (freq, amp_db, label) tuples sorted by amplitude.
    """
    n     = len(signal)
    fft   = np.fft.rfft(signal * np.hanning(n))
    amp   = np.abs(fft) * 2 / n
    freqs = np.fft.rfftfreq(n, 1.0 / sr)

    def amp_at(f):
        mask = np.abs(freqs - f) < tol
        return float(np.max(amp[mask])) if mask.any() and f > 0 else 0.0

    results = []
    # fundamentals
    for f in fundamentals:
        a = amp_at(f)
        results.append((f, 20 * math.log10(a + 1e-12), f"fund {f:.0f}Hz"))

    # 2nd-order: f1±f2
    fs = sorted(fundamentals)
    for i in range(len(fs)):
        for j in range(i + 1, len(fs)):
            for f in [fs[j] - fs[i], fs[i] + fs[j]]:
                if 20 < f < sr / 2:
                    a = amp_at(f)
                    results.append((f, 20 * math.log10(a + 1e-12),
                                    f"IMD2 {fs[i]:.0f}±{fs[j]:.0f}"))

    # 3rd-order: 2f1±f2, 2f2±f1
    for i in range(len(fs)):
        for j in range(len(fs)):
            if i == j: continue
            for f in [2 * fs[i] - fs[j], 2 * fs[i] + fs[j]]:
                if 20 < f < sr / 2:
                    a = amp_at(f)
                    results.append((f, 20 * math.log10(a + 1e-12),
                                    f"IMD3 2×{fs[i]:.0f}±{fs[j]:.0f}"))

    results.sort(key=lambda x: -x[1])
    return results


# ── frequency sweep: THD at each test frequency ───────────────────────────────

def sweep_thd_table(dir_path, files):
    sweep_freqs = [100, 200, 500, 1000, 1500, 2000, 3000]
    print("\n=== [4] Frequency Sweep: THD% at each frequency (AmigaOnly) ===")
    print(f"  {'Freq':>6}  {'Dry THD':>9}  {'AmigaOnly THD':>14}  "
          f"{'3rd harm dB':>12}  {'5th harm dB':>12}  {'NoiseFl dB':>11}")
    print("  " + "-" * 72)
    for freq in sweep_freqs:
        dry_key   = f"sweep_dry_{freq}"
        amiga_key = f"sweep_amiga_{freq}"
        if dry_key not in files or amiga_key not in files:
            continue
        d_sig = load_raw(files[dry_key])
        a_sig = load_raw(files[amiga_key])
        d_pct, _, _           = thd_analysis(d_sig, fundamental=freq)
        a_pct, _, a_harms     = thd_analysis(a_sig, fundamental=freq)
        nf                    = noise_floor_db(a_sig, fundamental=freq)
        h3 = a_harms[1] if len(a_harms) > 1 else -120
        h5 = a_harms[3] if len(a_harms) > 3 else -120
        print(f"  {freq:>6}  {d_pct:>8.3f}%  {a_pct:>13.3f}%  "
              f"{h3:>+12.1f}  {h5:>+12.1f}  {nf:>+10.1f}")


# ── directory comparison mode ─────────────────────────────────────────────────

PIPELINE_ORDER = [
    "sine_dry", "sine_amiga", "sine_reverb", "sine_new", "sine_old",
    "noise_dry", "noise_amiga", "noise_new", "noise_old",
]

LABELS = {
    "sine_dry":    "Dry (sine 440 Hz)",
    "sine_amiga":  "AmigaOnly (A500)",
    "sine_reverb": "ReverbOnly (ROOM 50%)",
    "sine_new":    "NEW: Amiga→Reverb",
    "sine_old":    "OLD: Reverb→Amiga",
    "noise_dry":   "Dry (white noise)",
    "noise_amiga": "Amiga (noise)",
    "noise_new":   "NEW: Amiga→Rev (noise)",
    "noise_old":   "OLD: Rev→Amiga (noise)",
}


def compare_directory(dir_path):
    files = {
        os.path.splitext(f)[0]: os.path.join(dir_path, f)
        for f in sorted(os.listdir(dir_path))
        if f.endswith(".raw")
    }

    # ── [1] Band energy comparison ────────────────────────────────────────────
    bands = [
        (0,     200,   "0-200 Hz"),
        (200,   500,   "200-500"),
        (500,   1500,  "500-1.5k"),
        (1500,  3000,  "1.5-3k"),
        (3000,  6000,  "3-6k"),
        (6000,  12000, "6-12k"),
        (12000, 22050, "12-22k"),
    ]
    col_w = 10
    print("\n=== [1] Band Energy Comparison (dBFS, post-warmup) ===")
    header = f"{'Config':<26}" + "".join(f"{b[2]:>{col_w}}" for b in bands)
    print(header)
    print("-" * len(header))
    for key in PIPELINE_ORDER:
        if key not in files:
            continue
        sig   = load_raw(files[key])
        n     = len(sig)
        fft   = np.fft.rfft(sig * np.hanning(n))
        amp   = np.abs(fft) * 2 / n
        freqs = np.fft.rfftfreq(n, 1.0 / SR)
        row   = f"{LABELS.get(key, key):<26}" + "".join(
            f"{band_rms_db(amp, freqs, lo, hi):>{col_w}.1f}" for lo, hi, _ in bands)
        print(row)

    # ── [2] THD analysis (sine files) ────────────────────────────────────────
    print("\n=== [2] THD Analysis (440 Hz sine input, ROOM reverb 50%) ===")
    print(f"{'Config':<26} {'THD%':>7}  {'Fund dB':>8}  "
          f"{'2f':>7}  {'3f':>7}  {'4f':>7}  {'5f':>7}  {'NoiseFloor':>11}")
    print("-" * 90)
    for key in ["sine_dry", "sine_amiga", "sine_reverb", "sine_new", "sine_old"]:
        if key not in files:
            continue
        sig              = load_raw(files[key])
        pct, fund, harms = thd_analysis(sig)
        nf               = noise_floor_db(sig)
        harm_str         = "  ".join(f"{h:+7.1f}" for h in harms[:4])
        print(f"{LABELS.get(key, key):<26} {pct:>7.3f}%  {fund:>+8.1f}  {harm_str}  {nf:>+10.1f}")

    # ── [3] Noise floor — white noise input ───────────────────────────────────
    print("\n=== [3] Spectral Shape with White Noise Input ===")
    print(f"{'Config':<26} {'RMS dBFS':>10}  "
          f"{'0-200 Hz':>10}  {'200-1.5k':>10}  {'1.5-6k':>8}  {'6-22k':>8}")
    print("-" * 78)
    for key in ["noise_dry", "noise_amiga", "noise_new", "noise_old"]:
        if key not in files:
            continue
        sig = load_raw(files[key])
        rms = 20 * math.log10(float(np.sqrt(np.mean(sig ** 2))) + 1e-12)
        n   = len(sig)
        fft = np.fft.rfft(sig * np.hanning(n))
        amp = np.abs(fft) * 2 / n
        frq = np.fft.rfftfreq(n, 1.0 / SR)
        b   = [band_rms_db(amp, frq, lo, hi) for lo, hi in
               [(0, 200), (200, 1500), (1500, 6000), (6000, 22050)]]
        print(f"{LABELS.get(key, key):<26} {rms:>+9.1f}  "
              f"{b[0]:>+10.1f}  {b[1]:>+10.1f}  {b[2]:>+8.1f}  {b[3]:>+8.1f}")

    # ── [4] Frequency sweep: THD at each frequency ────────────────────────────
    sweep_thd_table(dir_path, files)

    # ── [5] Multi-tone IMD: chord (440+880+1320 Hz) through AmigaOnly ─────────
    if "chord_amiga" in files:
        print("\n=== [5] Multi-tone IMD: 440+880+1320 Hz chord through AmigaOnly ===")
        print("  Measures intermodulation distortion products added by 8-bit DAC LUT.")
        print(f"  {'Freq':>8}  {'Amp (dBFS)':>12}  Label")
        print("  " + "-" * 50)
        chord_dry   = load_raw(files["chord_dry"])   if "chord_dry"   in files else None
        chord_amiga = load_raw(files["chord_amiga"])
        fundamentals = [440.0, 880.0, 1320.0]
        products = imd_products(chord_amiga, fundamentals)
        dry_products = {r[0]: r[1] for r in imd_products(chord_dry, fundamentals)} \
            if chord_dry is not None else {}
        for freq, db, label in products[:20]:
            delta = f"  Δ{db - dry_products[freq]:+.1f}dB" \
                if freq in dry_products else ""
            marker = " ◀ IMD" if "IMD" in label else ""
            print(f"  {freq:>8.0f}  {db:>+11.1f}  {label}{delta}{marker}")


# ── entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "dsp_analysis"
    if os.path.isdir(target):
        compare_directory(target)
    else:
        analyze_single(target)
