#!/usr/bin/env python3
#
# Audio Spectrum Analyzer
# Reads a 16-bit LE PCM raw file (e.g., exported by DspAnalyzer.java)
# and performs a Fast Fourier Transform (FFT) to detect harmonics,
# aliasing noise, and overall signal-to-noise health.
#
# Requires: pip install numpy

import sys
try:
    import numpy as np
except ImportError:
    print("Error: 'numpy' library is not installed.")
    print("Please install it using: pip install numpy")
    sys.exit(1)

def analyze(file_path):
    fs = 44100
    try:
        data = np.fromfile(file_path, dtype=np.int16)
    except FileNotFoundError:
        print(f"Error: Could not find file '{file_path}'")
        sys.exit(1)

    # Take only the left channel if interleaved stereo
    if len(data) % 2 == 0:
        signal = data[0::2] / 32768.0
    else:
        signal = data / 32768.0
    
    n = len(signal)
    fft_vals = np.abs(np.fft.rfft(signal))
    fft_freqs = np.fft.rfftfreq(n, 1.0/fs)
    
    indices = np.argsort(fft_vals)[-15:][::-1]
    
    print(f"\n=== Audio Spectrum Analysis: {file_path} ===")
    print(f"Sample Rate: {fs} Hz | Expected Fundamental: 440.0 Hz")
    print("-" * 55)
    
    for idx in indices:
        magnitude = fft_vals[idx]
        if magnitude < 10: continue
        freq = fft_freqs[idx]
        
        type_label = "[??? UNKNOWN ]"
        # 440Hz +/- 5Hz tolerance
        if abs(freq - 440.0) < 5:
            type_label = "[=== MELODY ==]"
        # Harmonic multiples of 440Hz (e.g., 880, 1320, 1760...)
        elif freq % 440.0 < 5 or (440.0 - (freq % 440.0)) < 5:
            type_label = "[- HARMONIC -]"
        # PWM Carrier leakage (around 18.6kHz)
        elif abs(freq - 18600.0) < 100:
            type_label = "[~ CARRIER  ~]"
        else:
            type_label = "[! ALIASING !]"
            
        print(f"{type_label} {freq:10.1f} Hz | Magnitude: {magnitude:8.0f}")
    
    print("-" * 55)

if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "realsound_test.raw"
    analyze(target)
