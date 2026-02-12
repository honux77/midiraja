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

    // The Macintosh horizontal sync frequency is approx 22,254.5 Hz
    private static final double MAC_SAMPLE_RATE = 22254.5;
    
    private double phaseAcc = 0.0;
    
    // For linear interpolation between the Mac 22.25kHz samples
    private int currentMacSampleL = 0;
    private int currentMacSampleR = 0;
    private int nextMacSampleL = 0;
    private int nextMacSampleR = 0;

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

        int globalSampleRate = 44100;
        double phaseStep = MAC_SAMPLE_RATE / globalSampleRate;

        for (int i = 0; i < frames; i++) {
            phaseAcc += phaseStep;
            
            if (phaseAcc >= 1.0) {
                phaseAcc -= 1.0;
                
                currentMacSampleL = nextMacSampleL;
                currentMacSampleR = nextMacSampleR;
                
                // 8-bit Quantize
                nextMacSampleL = Math.max(-128, Math.min(127, (int)Math.round(left[i] * 127.0)));
                nextMacSampleR = Math.max(-128, Math.min(127, (int)Math.round(right[i] * 127.0)));
            }
            
            // Linear interpolation 
            double interpL = currentMacSampleL + (nextMacSampleL - currentMacSampleL) * phaseAcc;
            double interpR = currentMacSampleR + (nextMacSampleR - currentMacSampleR) * phaseAcc;
            
            left[i] = (float) (interpL / 127.0);
            right[i] = (float) (interpR / 127.0);
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
        phaseAcc = 0;
        currentMacSampleL = 0; currentMacSampleR = 0;
        nextMacSampleL = 0; nextMacSampleR = 0;
    }
}