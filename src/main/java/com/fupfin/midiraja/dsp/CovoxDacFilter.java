package com.fupfin.midiraja.dsp;

import java.util.Random;

/**
 * Simulates the sound of early 8-bit R-2R resistor ladder DACs like the Covox Speech Thing.
 * Uses a Look-Up Table (LUT) with simulated +/- 3% resistor tolerance variance to produce
 * authentic era-accurate harmonic distortion.
 */
public class CovoxDacFilter implements AudioProcessor {
    private final boolean enabled;
    private final AudioProcessor next;

    private final float[] dacLut = new float[256];
    private static final float LPF_ALPHA = 0.6f; 
    private float lastL = 0, lastR = 0;

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
            float inL = Math.max(-1.0f, Math.min(1.0f, left[i]));
            int idxL = Math.max(0, Math.min(255, Math.round((inL * 0.5f + 0.5f) * 255f)));
            float outL = dacLut[idxL];
            lastL += LPF_ALPHA * (outL - lastL);
            left[i] = lastL;

            float inR = Math.max(-1.0f, Math.min(1.0f, right[i]));
            int idxR = Math.max(0, Math.min(255, Math.round((inR * 0.5f + 0.5f) * 255f)));
            float outR = dacLut[idxR];
            lastR += LPF_ALPHA * (outR - lastR);
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
            float inL = interleavedPcm[leftIdx] / 32768.0f;
            int idxL = Math.max(0, Math.min(255, Math.round((inL * 0.5f + 0.5f) * 255f)));
            float outL = dacLut[idxL];
            lastL += LPF_ALPHA * (outL - lastL);
            interleavedPcm[leftIdx] = (short) (lastL * 32767.0f);

            if (channels > 1) {
                int rightIdx = leftIdx + 1;
                float inR = interleavedPcm[rightIdx] / 32768.0f;
                int idxR = Math.max(0, Math.min(255, Math.round((inR * 0.5f + 0.5f) * 255f)));
                float outR = dacLut[idxR];
                lastR += LPF_ALPHA * (outR - lastR);
                interleavedPcm[rightIdx] = (short) (lastR * 32767.0f);
            }
        }
        next.processInterleaved(interleavedPcm, frames, channels);
    }

    @Override
    public void reset() {
        lastL = lastR = 0;
        next.reset();
    }
}
