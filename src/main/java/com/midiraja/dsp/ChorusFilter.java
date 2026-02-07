package com.midiraja.dsp;

/**
 * A classic Stereo Chorus effect.
 * It uses a modulated delay line (driven by a Low Frequency Oscillator - LFO) 
 * to create a detuned copy of the signal. When mixed with the original dry signal,
 * it creates a thick, swirling, "multiple voices" effect.
 */
public class ChorusFilter extends AudioFilter {

    private final float sampleRate = 44100.0f;
    private final float[] delayBufferL;
    private final float[] delayBufferR;
    private int writeIndex = 0;

    // Chorus Parameters (Tuned for a classic 80s synth chorus)
    private final float baseDelayMs = 20.0f; // 20 milliseconds base delay
    private final float depthMs = 5.0f;      // +/- 5 milliseconds of LFO sweep
    private final float rateHz = 0.8f;       // LFO speed (0.8 wobbles per second)
    private final float mixWet = 0.5f;       // 50% Dry / 50% Wet

    private float lfoPhase = 0.0f;
    private final float lfoStep;

    private volatile boolean enabled = true;

    public ChorusFilter(AudioProcessor next) {
        super(next);
        // Allocate 100ms of delay buffer (plenty for chorus)
        int bufferSize = (int) (sampleRate * 0.1f);
        delayBufferL = new float[bufferSize];
        delayBufferR = new float[bufferSize];

        lfoStep = (float) ((2.0 * Math.PI * rateHz) / sampleRate);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void process(float[] left, float[] right, int frames) {
        if (!enabled) {
            if (next != null) next.process(left, right, frames);
            return;
        }

        final int bufLen = delayBufferL.length;
        
        // Convert milliseconds to samples
        final float baseDelaySamples = (baseDelayMs / 1000.0f) * sampleRate;
        final float depthSamples = (depthMs / 1000.0f) * sampleRate;

        for (int i = 0; i < frames; i++) {
            float inL = left[i];
            float inR = right[i];

            // 1. Write current input to delay buffers
            delayBufferL[writeIndex] = inL;
            delayBufferR[writeIndex] = inR;

            // 2. Calculate LFO values (Sine wave)
            // Left and Right channels get slightly out-of-phase LFOs to create wide stereo.
            // Right is 90 degrees (PI/2) ahead of Left.
            float lfoValL = (float) Math.sin(lfoPhase);
            float lfoValR = (float) Math.sin(lfoPhase + (Math.PI / 2.0));

            // Advance LFO phase
            lfoPhase += lfoStep;
            if (lfoPhase > 2.0 * Math.PI) lfoPhase -= (float)(2.0 * Math.PI);

            // 3. Calculate dynamic read positions
            float delaySamplesL = baseDelaySamples + (lfoValL * depthSamples);
            float delaySamplesR = baseDelaySamples + (lfoValR * depthSamples);

            // 4. Read from delay buffer with linear interpolation
            float outL = readInterpolated(delayBufferL, writeIndex, delaySamplesL, bufLen);
            float outR = readInterpolated(delayBufferR, writeIndex, delaySamplesR, bufLen);

            // 5. Mix Dry and Wet
            left[i] = (inL * (1.0f - mixWet)) + (outL * mixWet);
            right[i] = (inR * (1.0f - mixWet)) + (outR * mixWet);

            // Advance write index and wrap around
            writeIndex++;
            if (writeIndex >= bufLen) writeIndex = 0;
        }

        if (next != null) {
            next.process(left, right, frames);
        }
    }

    /**
     * Reads a fractional index from the circular delay buffer using linear interpolation.
     */
    private float readInterpolated(float[] buffer, int writeIdx, float delaySamples, int len) {
        float readPos = writeIdx - delaySamples;
        while (readPos < 0) readPos += len;

        int index1 = (int) readPos;
        int index2 = index1 + 1;
        if (index2 >= len) index2 -= len;

        float frac = readPos - index1;

        // Linear interpolation: y = y1 + (y2 - y1) * frac
        float y1 = buffer[index1];
        float y2 = buffer[index2];
        return y1 + (y2 - y1) * frac;
    }
}
