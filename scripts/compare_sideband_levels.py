#!/usr/bin/env python3
"""
레퍼런스 WAV와 시뮬레이션의 캐리어 사이드밴드 레벨을 직접 비교한다.

핵심 질문: 레퍼런스에서 캐리어 사이드밴드가 우리 시뮬레이션보다 얼마나 조용한가?
"""

import numpy as np
from numpy.fft import rfft, rfftfreq
import struct, os, sys

FS        = 44100
OVERSAMPLE = 4
IRATE     = FS * OVERSAMPLE  # 176400
CARRIER   = 15200.0
LEVELS    = 78.0
TAU_M     = 37.9e-6
TAU_E     = 10.0e-6
AM        = 1.0 - np.exp(-1.0 / (IRATE * TAU_M))
AE        = 1.0 - np.exp(-1.0 / (IRATE * TAU_E))
STEP      = CARRIER / IRATE
PRE       = 128
DRIVE     = 2.0
INVD      = 1.0 / DRIVE

def simulate(signal):
    n = len(signal); out = np.zeros(n)
    ph = pre = s1 = s2 = s3 = s4 = s5 = s6 = 0.0
    for i in range(n):
        x = max(-1., min(1., signal[i] * DRIVE))
        x = round(x * PRE) / PRE
        if abs(x) < 1e-4:
            for _ in range(OVERSAMPLE):
                pre += AE*(0.-pre); s1+=AM*(pre-s1); s2+=AM*(s1-s2)
                s3+=AM*(s2-s3); s4+=AM*(s3-s4); s5+=AM*(s4-s5); s6+=AM*(s5-s6)
                ph = (ph+STEP)%1.
            out[i] = s6*INVD; continue
        duty = round(max(0.,min(1.,(x+1.)*.5))*LEVELS)/LEVELS
        for _ in range(OVERSAMPLE):
            b = 1. if ph<duty else -1.
            pre+=AE*(b-pre); s1+=AM*(pre-s1); s2+=AM*(s1-s2)
            s3+=AM*(s2-s3); s4+=AM*(s3-s4); s5+=AM*(s4-s5); s6+=AM*(s5-s6)
            ph=(ph+STEP)%1.
        out[i]=s6*INVD
    return out

def load_wav(path):
    with open(path,'rb') as f:
        f.read(12)
        while True:
            h=f.read(8)
            if len(h)<8: break
            cid,csz=struct.unpack('<4sI',h); chunk=f.read(csz)
            if cid==b'fmt ': fmt=chunk
            elif cid==b'data': pcm=chunk
    ac,nc,sr,_,_,bits=struct.unpack('<HHIIHH',fmt[:16])
    s=np.frombuffer(pcm,'<i2').astype(np.float32)/32768.
    if nc>1: s=s[::nc]
    return s, sr

def high_res_spectrum(signal, fs, nfft=2**17):
    seg = signal[:nfft] * np.hanning(nfft)
    sp  = np.abs(rfft(seg, n=nfft))
    fr  = rfftfreq(nfft, 1./fs)
    # normalize to peak
    return fr, 20*np.log10(sp/sp.max()+1e-12)

def find_noise_between_harmonics(freqs, spec_db, fund, carrier, n_harm=80, bw=15):
    """
    하모닉이 없는 구간에서 캐리어 사이드밴드 위치의 레벨을 측정한다.
    캐리어 사이드밴드: carrier - k*fund (오디오 대역 안으로 내려온 것들)
    하모닉에서 bw Hz 이상 떨어진 것만 유효 측정으로 인정.
    """
    harmonics = [fund * k for k in range(1, n_harm+1) if fund*k < 22000]

    sidebands_in_band = []
    for k in range(25, 40):
        f_sb = carrier - k * fund
        if not (500 < f_sb < 5000): continue
        # 가장 가까운 하모닉과의 거리
        dists = [abs(f_sb - h) for h in harmonics]
        min_dist = min(dists)
        if min_dist < bw:
            continue  # 하모닉에 너무 가까움 → 측정 불가
        idx = np.argmin(np.abs(freqs - f_sb))
        level = np.max(spec_db[max(0,idx-3):idx+4])
        sidebands_in_band.append((f_sb, k, min_dist, level))
    return sidebands_in_band

# ─────────────────────────────────────────────────────────────────
# 1) 시뮬레이션: 440 Hz 사인파
# ─────────────────────────────────────────────────────────────────
print("=== 시뮬레이션 (440 Hz @ -6 dBFS) ===")
t = np.arange(int(FS*8))/FS
sim_in = np.sin(2*np.pi*440*t)*0.5
sim_out = simulate(sim_in)
sf, sd = high_res_spectrum(sim_out, FS)

print(f"\n캐리어 사이드밴드 (하모닉에서 15 Hz 이상 떨어진 것만):")
print(f"{'주파수':>8}  {'k':>3}  {'하모닉과 거리':>12}  {'레벨(dB rel peak)':>18}")
sb_sim = find_noise_between_harmonics(sf, sd, 440., CARRIER, bw=20)
for f, k, dist, lv in sb_sim:
    print(f"  {f:6.0f} Hz  k={k:2d}  dist={dist:5.0f} Hz  {lv:+.1f} dB")

# 신호 대 사이드밴드 비
if sb_sim:
    # fundamental level
    fi = np.argmin(np.abs(sf-440)); fund_lv = np.max(sd[fi-3:fi+4])
    sb_levels = [lv for _,_,_,lv in sb_sim]
    avg_sb = np.mean(sb_levels)
    print(f"\n기본파 레벨   : {fund_lv:+.1f} dB (peak=0 dB로 정규화)")
    print(f"사이드밴드 평균: {avg_sb:+.1f} dB")
    print(f"기본파 vs 사이드밴드 차이: {fund_lv - avg_sb:.1f} dB")

# ─────────────────────────────────────────────────────────────────
# 2) 레퍼런스 WAV 분석
# ─────────────────────────────────────────────────────────────────
wav_path = os.path.join(os.path.dirname(__file__),'..','samples','realsound_sample.wav')
wav_path = os.path.normpath(wav_path)

if not os.path.exists(wav_path):
    print("\n레퍼런스 WAV 없음")
    sys.exit()

ref, ref_sr = load_wav(wav_path)
print(f"\n\n=== 레퍼런스 WAV ({ref_sr} Hz, {len(ref)/ref_sr:.1f}s) ===")

# 지배적인 주파수 탐색 (로우 프리퀀시)
NFFT = 2**17
seg = ref[:NFFT]
sp0 = np.abs(rfft(seg * np.hanning(NFFT), n=NFFT))
fr0 = rfftfreq(NFFT, 1./ref_sr)
# 50-500 Hz 범위에서 dominant
mask50 = (fr0>=50)&(fr0<=500)
dom_idx = np.argmax(sp0[mask50])
dom_f   = fr0[mask50][dom_idx]
print(f"지배 주파수: {dom_f:.1f} Hz")

rf, rd = high_res_spectrum(ref, ref_sr)

print(f"\n캐리어 사이드밴드 (하모닉에서 15 Hz 이상 떨어진 것만):")
print(f"{'주파수':>8}  {'k':>3}  {'하모닉과 거리':>12}  {'레벨(dB rel peak)':>18}")
sb_ref = find_noise_between_harmonics(rf, rd, dom_f, CARRIER, bw=20)
for f, k, dist, lv in sb_ref:
    print(f"  {f:6.0f} Hz  k={k:2d}  dist={dist:5.0f} Hz  {lv:+.1f} dB")

if sb_ref:
    fi = np.argmin(np.abs(rf-dom_f)); fund_lv_r = np.max(rd[fi-3:fi+4])
    sb_levels_r = [lv for _,_,_,lv in sb_ref]
    avg_sb_r = np.mean(sb_levels_r)
    print(f"\n기본파 레벨   : {fund_lv_r:+.1f} dB (peak=0 dB로 정규화)")
    print(f"사이드밴드 평균: {avg_sb_r:+.1f} dB")
    print(f"기본파 vs 사이드밴드 차이: {fund_lv_r - avg_sb_r:.1f} dB")

# ─────────────────────────────────────────────────────────────────
# 3) 핵심 비교
# ─────────────────────────────────────────────────────────────────
print("\n\n" + "="*55)
print(" 핵심 비교: 사이드밴드/기본파 비율")
print("="*55)
if sb_sim and sb_ref:
    sim_ratio  = fund_lv - avg_sb   # 클수록 사이드밴드가 작음
    ref_ratio  = fund_lv_r - avg_sb_r
    print(f"  시뮬레이션: {sim_ratio:.1f} dB (기본파가 사이드밴드보다 이만큼 큼)")
    print(f"  레퍼런스  : {ref_ratio:.1f} dB")
    print(f"  차이      : {ref_ratio - sim_ratio:.1f} dB (+ = 레퍼런스가 더 조용함)")

# ─────────────────────────────────────────────────────────────────
# 4) τ 다르게 해서 사이드밴드 변화 확인
# ─────────────────────────────────────────────────────────────────
print("\n\n" + "="*55)
print(" τ 변화에 따른 사이드밴드 1560/2000/2440 Hz 레벨 변화")
print("="*55)
print(f"{'τ_m (µs)':>10}  {'1560 Hz':>10}  {'2000 Hz':>10}  {'2440 Hz':>10}  {'기본파':>10}")

for tau_us in [10.0, 15.0, 20.0, 25.0, 30.0, 37.9, 50.0]:
    am_t = 1.0 - np.exp(-1.0/(IRATE * tau_us * 1e-6))
    # quick simulate (first 2s only for speed)
    n2 = int(FS*2); t2=np.arange(n2)/FS
    sig2 = np.sin(2*np.pi*440*t2)*0.5
    # inline simulate with this τ
    out2=np.zeros(n2); ph=pre=s1=s2=s3=s4=s5=s6=0.
    for i in range(n2):
        x=max(-1.,min(1.,sig2[i]*DRIVE)); x=round(x*PRE)/PRE
        if abs(x)<1e-4:
            for _ in range(OVERSAMPLE):
                pre+=AE*(0.-pre); s1+=am_t*(pre-s1); s2+=am_t*(s1-s2)
                s3+=am_t*(s2-s3); s4+=am_t*(s3-s4); s5+=am_t*(s4-s5); s6+=am_t*(s5-s6)
                ph=(ph+STEP)%1.
            out2[i]=s6*INVD; continue
        duty=round(max(0.,min(1.,(x+1.)*.5))*LEVELS)/LEVELS
        for _ in range(OVERSAMPLE):
            b=1. if ph<duty else -1.
            pre+=AE*(b-pre); s1+=am_t*(pre-s1); s2+=am_t*(s1-s2)
            s3+=am_t*(s2-s3); s4+=am_t*(s3-s4); s5+=am_t*(s4-s5); s6+=am_t*(s5-s6)
            ph=(ph+STEP)%1.
        out2[i]=s6*INVD
    NFFT2=2**15
    sp2=np.abs(rfft(out2[:NFFT2]*np.hanning(NFFT2),n=NFFT2))
    fr2=rfftfreq(NFFT2,1./FS)
    ref_peak = sp2.max()
    def lv(f):
        i=np.argmin(np.abs(fr2-f))
        return 20*np.log10(np.max(sp2[max(0,i-2):i+3])/ref_peak+1e-12)
    print(f"  {tau_us:8.1f}    {lv(1560):+8.1f}    {lv(2000):+8.1f}    {lv(2440):+8.1f}    {lv(440):+8.1f}")
