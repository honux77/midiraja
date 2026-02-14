#!/usr/bin/env python3
import sys
import numpy as np
import matplotlib.pyplot as plt
import wave

def plot_wav(filepath):
    try:
        wf = wave.open(filepath, 'rb')
    except Exception as e:
        print(f"Error opening {filepath}: {e}")
        sys.exit(1)

    sample_rate = wf.getframerate()
    channels = wf.getnchannels()
    n_frames = wf.getnframes()
    
    # Read raw bytes and convert to numpy array
    raw_data = wf.readframes(n_frames)
    wf.close()
    
    # Convert to 16-bit integers
    data = np.frombuffer(raw_data, dtype=np.int16)
    
    # If stereo, take the left channel for analysis
    if channels == 2:
        data = data[0::2]
        
    # Convert to float [-1.0, 1.0] for processing
    signal = data / 32768.0

    print(f"Loaded '{filepath}'")
    print(f"Sample Rate: {sample_rate} Hz, Channels: {channels}, Duration: {n_frames/sample_rate:.2f} s")

    # Limit to first 5 seconds to avoid blowing up memory/plot time if the file is huge
    max_seconds = 5.0
    if len(signal) > sample_rate * max_seconds:
        signal = signal[:int(sample_rate * max_seconds)]
        print(f"Plotting first {max_seconds} seconds...")

    # Create figure with two subplots (Time Domain and Spectrogram)
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 8))

    # Plot 1: Time Domain (first 0.05 seconds for detail)
    t_short = np.linspace(0, 0.05, int(sample_rate * 0.05), endpoint=False)
    sig_short = signal[:len(t_short)]
    ax1.plot(t_short, sig_short, color='blue', linewidth=1)
    ax1.set_title(f"Time Domain (First 50ms) - {filepath}")
    ax1.set_xlabel("Time (s)")
    ax1.set_ylabel("Amplitude")
    ax1.grid(True, alpha=0.3)

    # Plot 2: Spectrogram
    NFFT = 2048 
    Pxx, freqs, bins, im = ax2.specgram(signal, NFFT=NFFT, Fs=sample_rate, noverlap=NFFT//2, cmap='inferno')
    ax2.set_title("Spectrogram (Frequency over Time)")
    ax2.set_xlabel("Time (s)")
    ax2.set_ylabel("Frequency (Hz)")
    
    # Add a colorbar
    cbar = fig.colorbar(im, ax=ax2)
    cbar.set_label('Intensity (dB)')

    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python plot_wav.py <file.wav>")
        sys.exit(1)
    plot_wav(sys.argv[1])
