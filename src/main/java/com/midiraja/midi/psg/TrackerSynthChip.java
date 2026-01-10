/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE
 * file in the root directory of this source tree.
 */

package com.midiraja.midi.psg;

/**
 * Common interface for 8-bit tracker-driven sound chips (e.g. PSG, SCC).
 */
public interface TrackerSynthChip {
    void reset();
    double render();
    boolean updateNote(int ch, int note, int velocity);
    boolean tryAllocateFree(int ch, int note, int velocity);
    void forceArpeggioFallback(int ch, int note, int velocity);
    void handleNoteOff(int ch, int note);
    void setProgram(int ch, int program);
}
