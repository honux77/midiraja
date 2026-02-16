package com.fupfin.midiraja.dsp;

/**
 * Simulates the Apple II 1-bit speaker toggle logic.
 * In hardware, this was a simple flip-flop toggled by accessing memory address $C030.
 * In a signal chain, this maps to a zero-crossing 1-bit quantization.
 */
public class Apple2DacFilter implements AudioProcessor {
    private final boolean enabled;
    private final AudioProcessor next;

    public Apple2DacFilter(boolean enabled, AudioProcessor next) {
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
            left[i] = left[i] >= 0.0f ? 1.0f : -1.0f;
            right[i] = right[i] >= 0.0f ? 1.0f : -1.0f;
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
            int idx = i * channels;
            interleavedPcm[idx] = (short) (interleavedPcm[idx] >= 0 ? 32767 : -32768);
            if (channels > 1) {
                interleavedPcm[idx + 1] = (short) (interleavedPcm[idx + 1] >= 0 ? 32767 : -32768);
            }
        }
        next.processInterleaved(interleavedPcm, frames, channels);
    }

    @Override
    public void reset() {
        next.reset();
    }
}
