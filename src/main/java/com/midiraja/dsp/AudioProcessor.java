package com.midiraja.dsp;

public interface AudioProcessor extends AudioSink {
    @Override
    default void push(float[] left, float[] right, int frames) {
        process(left, right, frames);
    }
    
    // Legacy method for backwards compatibility
    void process(float[] left, float[] right, int frames);
}
