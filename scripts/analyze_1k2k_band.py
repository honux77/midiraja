#!/usr/bin/env python3
"""
1.5-2.5 kHz 대역의 실제 스펙트럼 성분 분석.
이 대역의 에너지가 '하모닉인가, 노이즈인가'를 판별한다.
"""

import numpy as np
from numpy.fft import rfft, rfftfreq

FS          = 44100
OVERSAMPLE  = 4
INTERNAL_RATE = FS * OVERSAMPLE  # 176400
CARRIER_HZ  = 15200.0
LEVELS      = 78.0
TAU_M_US    = 37.9
TAU_E_US    = 10.0
ALPHA_M     = 1.0 - np.exp(-1.0 / (INTERNAL_RATE * TAU_M_US * 1e-6))
ALPHA_E     = 1.0 - np.exp(-1.0 / (INTERNAL_RATE * TAU_E_US * 1e-6))
SUB_STEP    = CARRIER_HZ / INTERNAL_RATE
PRE_LEVELS  = 128
DRIVE_GAIN  = 2.0
INV_DRIVE   = 1.0 / DRIVE_GAIN

def simulate_pc(signal_mono):
    n = len(signal_mono)
    out = np.zeros(n, dtype=np.float64)
    phase = 0.0
    pre = s1 = s2 = s3 = s4 = s5 = s6 = 0.0

    for i in range(n):
        mono = max(-1.0, min(1.0, float(signal_mono[i]) * DRIVE_GAIN))
        mono = round(mono * PRE_LEVELS) / PRE_LEVELS
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
        duty = round(raw_duty * LEVELS) / LEVELS
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
    return out


DURATION = 8.0
n = int(FS * DURATION)
t = np.arange(n) / FS
FREQ = 440.0
sine = np.sin(2.0 * np.pi * FREQ * t) * 0.5

print("Simulating...")
filtered = simulate_pc(sine)

# High-resolution FFT
NFFT = 2**17  # ~131072 points → freq resolution: 44100/131072 ≈ 0.34 Hz
segment = filtered[:NFFT]
window  = np.hanning(NFFT)
spec    = np.abs(rfft(segment * window))
freqs   = rfftfreq(NFFT, 1.0/FS)
spec_db = 20 * np.log10(spec / np.max(spec) + 1e-12)  # normalized to peak

print(f"\n=== 스펙트럼 성분 상세 분석 (440 Hz 사인파, 해상도 {FS/NFFT:.2f} Hz/bin) ===")

# 1. 기본파 + 하모닉 목록
print("\n[하모닉 목록]")
harmonics_in_band = []
for k in range(1, 20):
    fh = FREQ * k
    if fh > 20000: break
    idx = np.argmin(np.abs(freqs - fh))
    # local peak in ±10 bins
    lo, hi = max(0, idx-10), min(len(spec), idx+11)
    peak_idx = lo + np.argmax(spec[lo:hi])
    peak_f   = freqs[peak_idx]
    peak_db  = spec_db[peak_idx]
    marker = " <-- 1.5~2.5 kHz" if 1500 <= fh <= 2500 else ""
    print(f"  {k:2d}차 하모닉  {fh:6.0f} Hz → peak at {peak_f:.1f} Hz  {peak_db:+.1f} dB{marker}")
    if 1500 <= peak_f <= 2500:
        harmonics_in_band.append((peak_f, peak_db))

# 2. 1.5-2.5 kHz 대역 전체를 50 Hz bin으로 스캔
print(f"\n[1.5-2.5 kHz 대역 50 Hz 단위 스캔]")
print(f"{'Freq':>8}  {'dB (rel peak)':>14}  {'성분 추정'}")
print("-" * 55)
band_mask = (freqs >= 1500) & (freqs <= 2500)
band_freqs = freqs[band_mask]
band_db    = spec_db[band_mask]

# 50 Hz bins
for f_center in range(1550, 2501, 50):
    bm = (band_freqs >= f_center - 25) & (band_freqs < f_center + 25)
    if not np.any(bm): continue
    max_db = np.max(band_db[bm])
    # classify: is it near a harmonic of 440?
    nearest_harm_k = round(f_center / FREQ)
    nearest_harm_f = nearest_harm_k * FREQ
    dist = abs(f_center - nearest_harm_f)
    if dist < 30:
        kind = f"▲ {nearest_harm_k}차 하모닉 ({nearest_harm_f:.0f} Hz)"
    else:
        kind = "  broadband noise"
    print(f"  {f_center:5.0f} Hz  {max_db:+7.1f} dB  {kind}")

# 3. 대역 내 하모닉 vs 비하모닉 에너지 비율
print(f"\n[1.5-2.5 kHz 대역 에너지 분류]")
harm_power = 0.0
noise_power = 0.0
for idx, (f, p) in enumerate(zip(band_freqs, band_db)):
    power_lin = 10 ** (p / 10)
    nearest_k = round(f / FREQ)
    if nearest_k > 0 and abs(f - nearest_k * FREQ) < 50:
        harm_power += power_lin
    else:
        noise_power += power_lin

total = harm_power + noise_power + 1e-30
print(f"  하모닉 에너지 비율  : {100*harm_power/total:.1f}%  ({10*np.log10(harm_power+1e-30):+.1f} dB)")
print(f"  비하모닉 노이즈 비율: {100*noise_power/total:.1f}%  ({10*np.log10(noise_power+1e-30):+.1f} dB)")

# 4. 실제 브로드밴드 노이즈 플로어 측정: 하모닉에서 멀리 떨어진 구간만
print(f"\n[실제 노이즈 플로어 (하모닉 없는 구간)]")
# 하모닉 목록: 440k Hz들 — 1.5~2.5 kHz 사이 하모닉: 1760, 2200 Hz
# 하모닉에서 ±100 Hz 이상 떨어진 구간: 1600~1660, 1900~2100 Hz
noise_windows = [(1600, 1660), (1850, 1950), (2050, 2100)]
for lo, hi in noise_windows:
    m = (freqs >= lo) & (freqs <= hi)
    if np.any(m):
        median_db = np.median(spec_db[m])
        print(f"  {lo}-{hi} Hz (no harmonics): median {median_db:+.1f} dB")
