package com.midiraja.dsp;

/**
 * A DSP filter that applies simple Master Gain (Volume) scaling to the audio stream.
 */
public class MasterGainFilter extends AudioFilter {
    private volatile float gain = 1.0f;

    public MasterGainFilter(AudioProcessor next) {
        super(next);
    }
    
    public MasterGainFilter(AudioSink next, float initialGain) {
        super(next);
        this.gain = Math.max(0.0f, initialGain);
    }

    public void setGain(float gain) {
        this.gain = Math.max(0.0f, gain);
    }

    @Override
    public void process(float[] left, float[] right, int frames) {
        if (gain != 1.0f) {
            for (int i = 0; i < frames; i++) {
                left[i] *= gain;
                right[i] *= gain;
            }
        }
        next.process(left, right, frames);
    }
}
