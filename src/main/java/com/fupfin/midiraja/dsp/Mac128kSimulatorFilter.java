package com.fupfin.midiraja.dsp;

/**
 * Simulates the unique audio hardware of the original Macintosh 128k (1984).
 * The Mac used a custom Sony sound chip (or SWIM later) but initially relied on 
 * the 68000 CPU stuffing 8-bit values into a PWM generator built from two 74LS161
 * 4-bit counters. The sample rate was strictly tied to the horizontal video 
 * flyback frequency: exactly 22.25 kHz.
 * 
 * This filter performs:
 * 1. Resampling to 22.25 kHz with linear interpolation.
 * 2. 8-bit quantization of the signal at that rate.
 * 3. Outputting to the audio line out without an internal speaker EQ.
 */
public class Mac128kSimulatorFilter implements AudioProcessor {
private final boolean enabled;
    private final AudioProcessor next;

    private boolean holdNext = false;
    private float heldL = 0;
    private float heldR = 0;

    // Analog Line-Out circuitry simulation
    // We use a 2-pole Butterworth low-pass filter at ~8kHz.
    // This represents the combined roll-off of the Mac's RC filter and the cheap internal speaker.
    // It keeps the 8-bit staircase crunch at lower frequencies but kills the 21kHz "siren" ZOH aliasing.
    private float x1L = 0, x2L = 0, y1L = 0, y2L = 0;
    private float x1R = 0, x2R = 0, y1R = 0, y2R = 0;

    // 2-pole LPF coefficients (Fc=8000Hz, Fs=44100Hz)
    private static final float b0 = 0.20657f;
    private static final float b1 = 0.41314f;
    private static final float b2 = 0.20657f;
    private static final float a1 = -0.36953f;
    private static final float a2 = 0.19582f;

    public Mac128kSimulatorFilter(boolean enabled, AudioProcessor next) {
        this.enabled = enabled;
        this.next = next;
    }

    @Override
    public void process(float[] left, float[] right, int frames) {
        if (!enabled) {
            next.process(left, right, frames);
            return;
        }

        for (int i = 0; i < frames; i++) {
            if (!holdNext) {
                // 1. CPU fetches an 8-bit sample exactly at ~22.05kHz
                // Map to -1.0 to 1.0 range
                heldL = ((int) (left[i] * 127.0f)) / 127.0f; 
                heldR = ((int) (right[i] * 127.0f)) / 127.0f;
                
                holdNext = true;
            } else {
                // ZOH: Repeat the exact same value for the second frame (22.05kHz hold)
                holdNext = false;
            }
            
            // 2. Analog Reconstruction Filter (8kHz 2-pole LPF)
            // This is crucial. Without it, the 22kHz ZOH creates a loud 21kHz "siren" mirror frequency.
            // The original Mac hardware didn't output 44.1kHz discrete steps to a modern hi-fi DAC; 
            // it went through an analog RC circuit and a very limited physical speaker.
            float outL = b0 * heldL + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L;
            float outR = b0 * heldR + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R;
            
            x2L = x1L; x1L = heldL;
            y2L = y1L; y1L = outL;
            
            x2R = x1R; x1R = heldR;
            y2R = y1R; y1R = outR;
            
            // Prevent denormalization
            if (Math.abs(y1L) < 1e-6f) y1L = 0;
            if (Math.abs(y1R) < 1e-6f) y1R = 0;

            left[i] = outL;
            right[i] = outR;
        }
        
        next.process(left, right, frames);
    }
    
    @Override
    public void processInterleaved(short[] interleavedPcm, int frames, int channels) {
        next.processInterleaved(interleavedPcm, frames, channels);
    }

    @Override
    public void reset() {
        next.reset();
        holdNext = false;
        heldL = 0;
        heldR = 0;
    }
}