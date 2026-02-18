package com.fupfin.midiraja.dsp;

import java.util.Random;

/**
 * Simulates the IBM PC 1-bit DAC (PC Speaker) conversion logic.
 * Primarily uses Pulse Width Modulation (PWM) driven by the PIT timer.
 */
public class IbmPcDacFilter implements AudioProcessor {
private final boolean enabled;
    private final AudioProcessor next;
    private final String mode; 
    
    // PWM State (Analytical Integration)
    // Carrier runs at 18.6kHz. Fs is 44.1kHz.
    private double carrierPhaseL = 0.0;
    private double carrierPhaseR = 0.0;
    private final double carrierStep;
    
    // DSD State
    private double dsdErrL = 0.0, dsdErrR = 0.0;
    private final Random rand = new Random();

    public IbmPcDacFilter(boolean enabled, String mode, AudioProcessor next) {
        this.enabled = enabled;
        this.next = next;
        this.mode = mode != null ? mode.toLowerCase(java.util.Locale.ROOT) : "pwm";
        // How much the carrier phase advances per 44.1kHz frame
        this.carrierStep = 18600.0 / 44100.0; 
    }

    @Override
    public void process(float[] left, float[] right, int frames) {
        if (!enabled) {
            next.process(left, right, frames);
            return;
        }
        
        // Pull architecture: fill buffers from upstream source first
        next.process(left, right, frames);

        for (int i = 0; i < frames; i++) {
            double l = left[i], r = right[i];
            
            if ("dsd".equals(mode)) {
                // For DSD (Delta-Sigma), we can't easily analytical integrate because it's chaotic.
                // We keep a lightweight 8x oversampling loop for DSD only, or just run it at 1x 
                // and let the speaker filter smooth it. Let's do 1x for true raw 1-bit PDM constraint.
                dsdErrL += l + (rand.nextDouble() - 0.5) * 0.1;
                dsdErrR += r + (rand.nextDouble() - 0.5) * 0.1;
                double outL = dsdErrL > 0.0 ? 1.0 : -1.0;
                double outR = dsdErrR > 0.0 ? 1.0 : -1.0;
                dsdErrL -= outL; dsdErrR -= outR;
                left[i] = (float) outL;
                right[i] = (float) outR;
            } else {
                // Analytical PWM Integration (Area under the curve)
                // We calculate the exact time ratio the 18.6kHz pulse is HIGH within this 44.1kHz window.
                
                // Map audio [-1.0, 1.0] to duty cycle [0.0, 1.0]
                double dutyL = Math.max(0.0, Math.min(1.0, (l + 1.0) * 0.5));
                double dutyR = Math.max(0.0, Math.min(1.0, (r + 1.0) * 0.5));
                
                double outL = integratePwm(carrierPhaseL, carrierStep, dutyL);
                double outR = integratePwm(carrierPhaseR, carrierStep, dutyR);
                
                carrierPhaseL = (carrierPhaseL + carrierStep) % 1.0;
                carrierPhaseR = (carrierPhaseR + carrierStep) % 1.0;
                
                left[i] = (float) outL;
                right[i] = (float) outR;
            }
        }
    }

    @Override
    public void processInterleaved(short[] interleavedPcm, int frames, int channels) {
        if (!enabled) {
            next.processInterleaved(interleavedPcm, frames, channels);
            return;
        }
        
        // Pull architecture
        next.processInterleaved(interleavedPcm, frames, channels);
        
        for (int i = 0; i < frames; i++) {
            int lIdx = i * channels;
            float l = interleavedPcm[lIdx] / 32768.0f;
            float r = channels > 1 ? interleavedPcm[lIdx + 1] / 32768.0f : l;
            
            if ("dsd".equals(mode)) {
                dsdErrL += l + (rand.nextDouble() - 0.5) * 0.1;
                dsdErrR += r + (rand.nextDouble() - 0.5) * 0.1;
                double outL = dsdErrL > 0.0 ? 1.0 : -1.0;
                double outR = dsdErrR > 0.0 ? 1.0 : -1.0;
                dsdErrL -= outL; dsdErrR -= outR;
                interleavedPcm[lIdx] = (short) (outL * 32767.0f);
                if (channels > 1) interleavedPcm[lIdx + 1] = (short) (outR * 32767.0f);
            } else {
                double dutyL = Math.max(0.0, Math.min(1.0, (l + 1.0) * 0.5));
                double dutyR = Math.max(0.0, Math.min(1.0, (r + 1.0) * 0.5));
                
                double outL = integratePwm(carrierPhaseL, carrierStep, dutyL);
                double outR = integratePwm(carrierPhaseR, carrierStep, dutyR);
                
                carrierPhaseL = (carrierPhaseL + carrierStep) % 1.0;
                carrierPhaseR = (carrierPhaseR + carrierStep) % 1.0;
                
                interleavedPcm[lIdx] = (short) (outL * 32767.0f);
                if (channels > 1) interleavedPcm[lIdx + 1] = (short) (outR * 32767.0f);
            }
        }
    }

    /**
     * Calculates the exact average voltage of a PWM signal over a discrete time step.
     * This physically models the speaker integrating the high-frequency pulse train.
     */
    private double integratePwm(double startPhase, double step, double duty) {
        double endPhase = startPhase + step;
        double highTime = 0.0;
        
        // If the step crosses the wrap-around boundary (1.0)
        if (endPhase > 1.0) {
            // Portion before the wrap
            if (startPhase < duty) highTime += (duty - startPhase);
            // Portion after the wrap
            double remainder = endPhase - 1.0;
            if (remainder > duty) highTime += duty;
            else highTime += remainder;
        } else {
            // No wrap
            if (endPhase <= duty) {
                highTime = step; // Entirely high
            } else if (startPhase >= duty) {
                highTime = 0.0; // Entirely low
            } else {
                highTime = duty - startPhase; // Partially high
            }
        }
        
        // Ratio of HIGH time vs total step time
        double highRatio = highTime / step;
        
        // Map 0.0..1.0 ratio back to -1.0..1.0 audio range
        return (highRatio * 2.0) - 1.0;
    }

    @Override
    public void reset() {
        carrierPhaseL = 0.0;
        carrierPhaseR = 0.0;
        dsdErrL = dsdErrR = 0;
        next.reset();
    }
}
