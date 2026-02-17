package com.fupfin.midiraja.dsp;

import java.util.Random;

/**
 * Simulates the sound of early 8-bit R-2R resistor ladder DACs like the Covox Speech Thing.
 * 
 * Key characteristics modeled:
 * 1. R-2R Resistor Variance: Simulated +/- 3% tolerance variance in the resistor ladder
 *    to produce authentic era-accurate harmonic distortion via a Look-Up Table (LUT).
 * 2. Limited Effective Sampling Rate: Covox was typically driven via the LPT port, 
 *    rarely exceeding 22kHz in practical use due to CPU overhead. This filter implements 
 *    a Zero-Order Hold (ZOH) at 22.05kHz.
 * 3. Analog Smoothing: A gentle low-pass filter simulates the physical capacitor usually 
 *    present in Covox circuits to tame the 8-bit steps.
 */
public class CovoxDacFilter implements AudioProcessor {
    private final boolean enabled;
    private final AudioProcessor next;

    private final float[] dacLut = new float[256];
    private static final float LPF_ALPHA = 0.6f; 
    private float lastL = 0, lastR = 0;

    // Zero-Order Hold state for 22.05kHz simulation (1/2 of 44.1kHz)
    private boolean holdNext = false;
    private float heldL = 0, heldR = 0;

    public CovoxDacFilter(boolean enabled, AudioProcessor next) {
        this.enabled = enabled;
        this.next = next;
        buildLut();
    }

    private void buildLut() {
        Random rand = new Random(1987); 
        float[] weights = new float[8];
        float total = 0;
        for (int i = 0; i < 8; i++) {
            float ideal = (float) Math.pow(2, i);
            float variance = 1.0f + ((rand.nextFloat() * 2.0f - 1.0f) * 0.03f);
            weights[i] = ideal * variance;
            total += weights[i];
        }
        for (int i = 0; i < 256; i++) {
            float sum = 0;
            for (int bit = 0; bit < 8; bit++) {
                if ((i & (1 << bit)) != 0) sum += weights[bit];
            }
            dacLut[i] = (sum / total) * 2.0f - 1.0f;
        }
    }

    @Override
    public void process(float[] left, float[] right, int frames) {
        if (!enabled) {
            next.process(left, right, frames);
            return;
        }
        for (int i = 0; i < frames; i++) {
            if (!holdNext) {
                // 1. Quantize and convert through non-linear R-2R LUT
                float inL = Math.max(-1.0f, Math.min(1.0f, left[i]));
                int idxL = Math.max(0, Math.min(255, Math.round((inL * 0.5f + 0.5f) * 255f)));
                heldL = dacLut[idxL];

                float inR = Math.max(-1.0f, Math.min(1.0f, right[i]));
                int idxR = Math.max(0, Math.min(255, Math.round((inR * 0.5f + 0.5f) * 255f)));
                heldR = dacLut[idxR];
                
                holdNext = true;
            } else {
                // 2. Zero-Order Hold (22.05kHz simulation)
                holdNext = false;
            }
            
            // 3. Apply smoothing LPF to the held 8-bit signal
            lastL += LPF_ALPHA * (heldL - lastL);
            lastR += LPF_ALPHA * (heldR - lastR);
            
            left[i] = lastL;
            right[i] = lastR;
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
            int leftIdx = i * channels;
            
            if (!holdNext) {
                float inL = interleavedPcm[leftIdx] / 32768.0f;
                int idxL = Math.max(0, Math.min(255, Math.round((inL * 0.5f + 0.5f) * 255f)));
                heldL = dacLut[idxL];

                if (channels > 1) {
                    float inR = interleavedPcm[leftIdx + 1] / 32768.0f;
                    int idxR = Math.max(0, Math.min(255, Math.round((inR * 0.5f + 0.5f) * 255f)));
                    heldR = dacLut[idxR];
                }
                holdNext = true;
            } else {
                holdNext = false;
            }

            lastL += LPF_ALPHA * (heldL - lastL);
            interleavedPcm[leftIdx] = (short) (lastL * 32767.0f);

            if (channels > 1) {
                lastR += LPF_ALPHA * (heldR - lastR);
                interleavedPcm[leftIdx + 1] = (short) (lastR * 32767.0f);
            }
        }
        next.processInterleaved(interleavedPcm, frames, channels);
    }

    @Override
    public void reset() {
        lastL = lastR = 0;
        heldL = heldR = 0;
        holdNext = false;
        next.reset();
    }
}
