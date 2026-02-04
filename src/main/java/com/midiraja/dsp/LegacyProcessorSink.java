package com.midiraja.dsp;

/**
 * An adapter sink that iterates over a legacy list of AudioProcessors.
 */
public class LegacyProcessorSink extends AudioFilter {
    private final java.util.List<AudioProcessor> processors;

    public LegacyProcessorSink(AudioSink next, java.util.List<AudioProcessor> processors) {
        super(next);
        this.processors = processors;
    }

    @Override
    public void push(float[] left, float[] right, int frames) {
        for (AudioProcessor proc : processors) {
            proc.process(left, right, frames);
        }
        next.push(left, right, frames);
    }

    @Override
    public void reset() {
        for (AudioProcessor proc : processors) {
            proc.reset();
        }
        super.reset();
    }
}
