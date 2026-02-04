package com.midiraja.dsp;

/**
 * A destination or processor for raw audio PCM streams.
 */
public interface AudioSink {
    /**
     * Processes or consumes a block of audio frames.
     * @param left Array of left channel samples (-1.0 to 1.0)
     * @param right Array of right channel samples (-1.0 to 1.0)
     * @param frames The number of valid frames in the arrays to process
     */
    void push(float[] left, float[] right, int frames);

    /**
     * Resets the internal state of the sink (e.g. clearing delay buffers).
     */
    void reset();
}
