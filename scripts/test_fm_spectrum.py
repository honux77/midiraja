import numpy as np
import os
import struct
import math

# We are analyzing the New "Pure PM" + "DSD" Engine without any tanh clipping.
# Let's run the FFT to mathematically prove the 5kHz squeak is dead.

sampleRate = 44100
oversample = 32
oversampledRate = sampleRate * oversample

note_freq = 261.63 # C4
fmRatio = 1.0
fmIndex = 1.1

phase = 0.0
modPhase = 0.0

sigmaDeltaError = 0.0
lpfState = 0.0
lpfState2 = 0.0

frames = 44100
buffer = np.zeros(frames)

# Sine LUT
SINE_LUT_SIZE = 4096
SINE_LUT = np.sin(np.linspace(0, 2 * np.pi, SINE_LUT_SIZE, endpoint=False))

def fastSin(p):
    idx = int(p * SINE_LUT_SIZE)
    if idx < 0: idx = 0
    if idx >= SINE_LUT_SIZE: idx = SINE_LUT_SIZE - 1
    return SINE_LUT[idx]

np.random.seed(42)

for i in range(frames):
    sumPwm = 0.0
    time = i / sampleRate
    decay = max(0.0, 1.0 - (time / 0.5))
    
    # Key Scale
    keyScale = 1.0
    if note_freq > 261.63:
        keyScale = 261.63 / note_freq
    scaledFmIndex = fmIndex * keyScale
    envIndex = (scaledFmIndex * 0.1) + (scaledFmIndex * decay)
    
    for o in range(oversample):
        # Synthesis: Pure PM Mode (NO TANH)
        modFreq = note_freq * fmRatio
        modPhase += modFreq / oversampledRate
        modPhase -= math.floor(modPhase)
        modulator = fastSin(modPhase)
        
        phase += note_freq / oversampledRate
        phase -= math.floor(phase)
        
        finalPhase = phase + (modulator * (envIndex / (2.0 * math.pi)))
        finalPhase -= math.floor(finalPhase)
        
        # PURE SINE
        rawSine = fastSin(finalPhase)
        out = rawSine * decay
        
        analogMix = out 
        
        # Mux: 1st-Order DSD Mode with TPDF Dither
        dither1 = np.random.random() * 2.0 - 1.0
        dither2 = np.random.random() * 2.0 - 1.0
        tpdfDither = (dither1 + dither2) * 0.03
        
        sigmaDeltaError += (analogMix + tpdfDither)
        outBit = 1.0 if sigmaDeltaError > 0.0 else -1.0
        sigmaDeltaError -= outBit
        
        sumPwm += outBit
        
    rawPwm = sumPwm / oversample
    cleanSignal = rawPwm
    
    # Master LPF
    filterCutoff = 0.18
    lpfState += filterCutoff * (cleanSignal - lpfState)
    lpfState2 += filterCutoff * (lpfState - lpfState2)
    buffer[i] = max(-1.0, min(1.0, lpfState2))

# Perform FFT
fft_result = np.fft.rfft(buffer)
freqs = np.fft.rfftfreq(frames, 1.0/44100.0)
mags = np.abs(fft_result)

print("FFT Analysis of PURE PM Output (No Tanh Distortion) + DSD:")
print("-" * 50)

# Check the fundamental and its immediate FM harmonics (Good)
print("--- Good Harmonics (Fundamental & FM Sidebands) ---")
good_mask = freqs < 3000.0
good_freqs = freqs[good_mask]
good_mags = mags[good_mask]
top_good = np.argsort(good_mags)[-5:][::-1]
for idx in top_good:
    if good_mags[idx] > 10.0:
        print(f"Freq: {good_freqs[idx]:8.1f} Hz | Magnitude: {good_mags[idx]:10.1f}")

print("\\n--- Dangerous High-Frequency Artifacts (> 5kHz) ---")
# Find high-frequency artifacts (if any remain) above 5kHz
high_freq_mask = freqs > 5000.0
high_freqs = freqs[high_freq_mask]
high_mags = mags[high_freq_mask]

top_high_indices = np.argsort(high_mags)[-5:][::-1]

for idx in top_high_indices:
    print(f"Freq: {high_freqs[idx]:8.1f} Hz | Magnitude: {high_mags[idx]:10.1f}")
        
