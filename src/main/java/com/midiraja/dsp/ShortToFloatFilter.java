package com.midiraja.dsp;

/**
 * A utility class that converts interleaved short[] PCM to non-interleaved float[] arrays
 * and feeds them to an AudioSink pipeline.
 */
public class ShortToFloatFilter {
    private final AudioSink next;
    private float @org.jspecify.annotations.Nullable [] leftBuffer = null;
    private float @org.jspecify.annotations.Nullable [] rightBuffer = null;

    public ShortToFloatFilter(AudioSink next) {
        this.next = next;
    }

    public void processInterleaved(short[] pcm, int frames) {
        if (leftBuffer == null || leftBuffer.length < frames) {
            leftBuffer = new float[frames];
            rightBuffer = new float[frames];
        }
        
        for (int i = 0; i < frames; i++) {
            if (leftBuffer != null && rightBuffer != null) {
                leftBuffer[i] = pcm[i * 2] / 32768.0f;
                rightBuffer[i] = pcm[i * 2 + 1] / 32768.0f;
            }
        }
        
        if (leftBuffer != null && rightBuffer != null) next.push(leftBuffer, rightBuffer, frames);
    }
}
