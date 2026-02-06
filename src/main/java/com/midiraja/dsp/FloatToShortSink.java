package com.midiraja.dsp;

import com.midiraja.midi.AudioEngine;
import org.jspecify.annotations.Nullable;

/**
 * A terminal sink that converts float[] (-1.0 to 1.0) into interleaved short[] PCM
 * and pushes it to the native AudioEngine.
 */
public class FloatToShortSink implements AudioSink {
    private final @Nullable AudioEngine engine;
    private final int outputChannels;
    private short @Nullable [] pcmBuffer = null;

    public FloatToShortSink(@Nullable AudioEngine engine) {
        this(engine, 2); // Default to stereo
    }

    public FloatToShortSink(@Nullable AudioEngine engine, int outputChannels) {
        this.engine = engine;
        this.outputChannels = outputChannels;
    }

    @Override
    public void processInterleaved(short[] interleavedPcm, int frames, int channels) {
        if (engine != null) {
            engine.push(interleavedPcm);
        }
    }

    @Override
    public void process(float[] left, float[] right, int frames) {
        if (engine == null) return;
        
        int needed = frames * outputChannels;
        if (pcmBuffer == null || pcmBuffer.length < needed) {
            pcmBuffer = new short[needed];
        }

        if (outputChannels == 1) {
            for (int i = 0; i < frames; i++) {
                float avg = (left[i] + right[i]) * 0.5f;
                pcmBuffer[i] = (short) (Math.max(-1.0f, Math.min(1.0f, avg)) * 32767.0f);
            }
        } else {
            for (int i = 0; i < frames; i++) {
                float clampL = Math.max(-1.0f, Math.min(1.0f, left[i]));
                float clampR = Math.max(-1.0f, Math.min(1.0f, right[i]));
                pcmBuffer[i * 2] = (short) (clampL * 32767.0f);
                pcmBuffer[i * 2 + 1] = (short) (clampR * 32767.0f);
            }
        }

        engine.push(pcmBuffer);
    }

    @Override
    public void reset() {
        if (engine != null) {
            engine.flush();
        }
    }
}
