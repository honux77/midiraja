package com.midiraja.dsp;

import com.midiraja.midi.AudioEngine;

/**
 * A terminal sink that converts float[] (-1.0 to 1.0) into interleaved short[] PCM
 * and pushes it to the native AudioEngine.
 */
public class FloatToShortSink implements AudioSink {
    private final @org.jspecify.annotations.Nullable AudioEngine engine;
    private short @org.jspecify.annotations.Nullable [] pcmBuffer = null;

    public FloatToShortSink(@org.jspecify.annotations.Nullable AudioEngine engine) {
        this.engine = engine;
    }

    @Override
    public void push(float[] left, float[] right, int frames) {
        if (engine == null) return;
        
        int needed = frames * 2;
        if (pcmBuffer == null || pcmBuffer.length < needed) {
            pcmBuffer = new short[needed];
        }

        for (int i = 0; i < frames; i++) {
            float clampL = Math.max(-1.0f, Math.min(1.0f, left[i]));
            float clampR = Math.max(-1.0f, Math.min(1.0f, right[i]));
            pcmBuffer[i * 2] = (short) (clampL * 32767.0f);
            pcmBuffer[i * 2 + 1] = (short) (clampR * 32767.0f);
        }

        if (pcmBuffer != null) engine.push(pcmBuffer);
    }

    @Override
    public void reset() {
        if (engine != null) {
            engine.flush();
        }
    }
}
