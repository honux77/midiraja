package com.fupfin.midiraja.dsp;

/**
 * A marker interface indicating that this node is the terminal end of an audio processing pipeline.
 * It consumes audio frames but does not pass them on to any further processors.
 */
public interface AudioSink extends AudioProcessor
{
}
