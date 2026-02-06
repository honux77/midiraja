package com.midiraja.dsp;

/**
 * A base class for an audio processing node that modifies audio and passes it to the next processor in the chain.
 */
public abstract class AudioFilter implements AudioProcessor {
    protected final AudioProcessor next;

    public AudioFilter(AudioProcessor next) {
        this.next = next;
    }

    @Override
    public void reset() {
        if (next != null) {
            next.reset();
        }
    }
}
