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
    
    private double carrierPhase = -1.0;
    private final double carrierStep;
    private final int oversampleFactor = 32;
    
    private double dsdErrL = 0.0, dsdErrR = 0.0;
    private final Random rand = new Random();

    public IbmPcDacFilter(boolean enabled, String mode, AudioProcessor next) {
        this.enabled = enabled;
        this.next = next;
        this.mode = mode != null ? mode.toLowerCase(java.util.Locale.ROOT) : "pwm";
        this.carrierStep = (18600.0 / 44100.0) * 2.0;
    }

    @Override
    public void process(float[] left, float[] right, int frames) {
        if (!enabled) {
            next.process(left, right, frames);
            return;
        }
        for (int i = 0; i < frames; i++) {
            double l = left[i], r = right[i];
            double sumL = 0, sumR = 0;
            for (int over = 0; over < oversampleFactor; over++) {
                double outL, outR;
                if ("dsd".equals(mode)) {
                    dsdErrL += l + (rand.nextDouble() - 0.5) * 0.1;
                    dsdErrR += r + (rand.nextDouble() - 0.5) * 0.1;
                    outL = dsdErrL > 0.0 ? 1.0 : -1.0;
                    outR = dsdErrR > 0.0 ? 1.0 : -1.0;
                    dsdErrL -= outL; dsdErrR -= outR;
                } else {
                    carrierPhase += carrierStep / oversampleFactor;
                    if (carrierPhase > 1.0) carrierPhase -= 2.0;
                    outL = l > carrierPhase ? 1.0 : -1.0;
                    outR = r > carrierPhase ? 1.0 : -1.0;
                }
                sumL += outL; sumR += outR;
            }
            left[i] = (float) (sumL / oversampleFactor);
            right[i] = (float) (sumR / oversampleFactor);
        }
        next.process(left, right, frames);
    }

    @Override
    public void processInterleaved(short[] interleavedPcm, int frames, int channels) {
        if (!enabled) {
            next.processInterleaved(interleavedPcm, frames, channels);
            return;
        }
        for (int i = 0; i < frames; i++) {
            int lIdx = i * channels;
            float l = interleavedPcm[lIdx] / 32768.0f;
            float r = channels > 1 ? interleavedPcm[lIdx + 1] / 32768.0f : l;
            double sumL = 0, sumR = 0;
            for (int over = 0; over < oversampleFactor; over++) {
                double outL, outR;
                if ("dsd".equals(mode)) {
                    dsdErrL += l + (rand.nextDouble() - 0.5) * 0.1;
                    dsdErrR += r + (rand.nextDouble() - 0.5) * 0.1;
                    outL = dsdErrL > 0.0 ? 1.0 : -1.0;
                    outR = dsdErrR > 0.0 ? 1.0 : -1.0;
                    dsdErrL -= outL; dsdErrR -= outR;
                } else {
                    carrierPhase += carrierStep / oversampleFactor;
                    if (carrierPhase > 1.0) carrierPhase -= 2.0;
                    outL = l > carrierPhase ? 1.0 : -1.0;
                    outR = r > carrierPhase ? 1.0 : -1.0;
                }
                sumL += outL; sumR += outR;
            }
            interleavedPcm[lIdx] = (short) (sumL / oversampleFactor * 32767.0f);
            if (channels > 1) interleavedPcm[lIdx + 1] = (short) (sumR / oversampleFactor * 32767.0f);
        }
        next.processInterleaved(interleavedPcm, frames, channels);
    }

    @Override
    public void reset() {
        carrierPhase = -1.0;
        dsdErrL = dsdErrR = 0;
        next.reset();
    }
}
