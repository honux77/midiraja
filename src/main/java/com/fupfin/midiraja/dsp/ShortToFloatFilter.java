package com.fupfin.midiraja.dsp;

import static com.fupfin.midiraja.dsp.DspConstants.INTERNAL_LEVEL;

import org.jspecify.annotations.Nullable;

/**
 * A utility class that converts interleaved short[] PCM to non-interleaved float[] arrays and feeds
 * them to an AudioProcessor pipeline.
 */
public class ShortToFloatFilter implements AudioProcessor
{
    private final AudioProcessor next;
    private float @Nullable [] leftBuffer = null;
    private float @Nullable [] rightBuffer = null;

    public ShortToFloatFilter(AudioProcessor next)
    {
        this.next = next;
    }

    @Override
    public void process(float[] left, float[] right, int frames)
    {
        next.process(left, right, frames);
    }

    @Override
    public void processInterleaved(short[] pcm, int frames, int channels)
    {
        if (leftBuffer == null || leftBuffer.length < frames)
        {
            leftBuffer = new float[frames];
            rightBuffer = new float[frames];
        }

        final float[] l = leftBuffer;
        final float[] r = rightBuffer;

        if (l != null && r != null)
        {
            if (channels == 1)
            {
                for (int i = 0; i < frames; i++)
                {
                    float val = pcm[i] / 32768.0f * INTERNAL_LEVEL;
                    l[i] = val;
                    r[i] = val;
                }
            }
            else
            {
                for (int i = 0; i < frames; i++)
                {
                    l[i] = pcm[i * 2] / 32768.0f * INTERNAL_LEVEL;
                    r[i] = pcm[i * 2 + 1] / 32768.0f * INTERNAL_LEVEL;
                }
            }
            next.process(l, r, frames);
        }
    }
}
