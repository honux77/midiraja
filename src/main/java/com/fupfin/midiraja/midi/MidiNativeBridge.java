/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.midi;

/**
 * Common interface for native synthesizers that support immediate MIDI event dispatch.
 */
public interface MidiNativeBridge extends AutoCloseable
{
    void init(int sampleRate) throws Exception;

    void reset();

    void panic();

    void generate(short[] buffer, int stereoFrames);

    void noteOn(int channel, int note, int velocity);

    void noteOff(int channel, int note);

    void controlChange(int channel, int type, int value);

    void patchChange(int channel, int patch);

    void pitchBend(int channel, int pitch);

    void systemExclusive(byte[] data);

    @Override
    void close();
}
