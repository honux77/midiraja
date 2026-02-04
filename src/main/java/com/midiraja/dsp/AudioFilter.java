package com.midiraja.dsp;

/**
 * A base class for an audio processing node that modifies audio and passes it to the next node.
 */
public abstract class AudioFilter implements AudioSink {
    protected final AudioSink next;

    public AudioFilter(AudioSink next) {
        this.next = next;
    }

    @Override
    public void reset() {
        if (next != null) {
            next.reset();
        }
    }
}
