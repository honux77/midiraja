/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.midi;

/**
 * Dependency Inversion interface for Munt's native C API (`libmt32emu`).
 * This isolates the FFM MethodHandles, allowing the MuntSynthProvider's 
 * core logic to be tested purely in Java using a mock implementation.
 */
public interface MuntNativeBridge extends AutoCloseable {

    /** Initializes the Munt emulation context. */
    void createSynth() throws Exception;

    /** Loads ROM files (MT32_CONTROL.ROM, MT32_PCM.ROM) from the specified directory. */
    void loadRoms(String romDirectory) throws Exception;

    /** Opens the synthesizer engine, preparing it for playback and rendering. */
    void openSynth() throws Exception;

    // --- MIDI Event Routing ---
    void playNoteOn(int channel, int key, int velocity);
    void playNoteOff(int channel, int key);
    void playControlChange(int channel, int number, int value);
    void playProgramChange(int channel, int program);
    void playPitchBend(int channel, int value);
    void playSysex(byte[] sysexData);

    /** 
     * Renders PCM data into the provided short array (interleaved stereo).
     * @param buffer The array to fill with 16-bit PCM samples.
     * @param frames The number of sample frames (1 frame = 2 shorts for stereo).
     */
    void renderAudio(short[] buffer, int frames);

    /** Closes and frees all native resources. */
    @Override
    void close();
}