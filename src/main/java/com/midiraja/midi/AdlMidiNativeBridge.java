/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.midi;

/**
 * Dependency Inversion interface for libADLMIDI's native C API.
 * This isolates the FFM MethodHandles, allowing AdlMidiSynthProvider's
 * core logic to be tested purely in Java using a mock implementation.
 *
 * <p>libADLMIDI is NOT thread-safe. All methods must be called from the
 * render thread, except MIDI routing methods which are called from the
 * playback thread but queued in AdlMidiSynthProvider before dispatching.
 */
public interface AdlMidiNativeBridge extends AutoCloseable {

    /**
     * Initializes the OPL synthesizer device at the given sample rate.
     * Must be called before any other method.
     *
     * @param sampleRate Audio output sample rate (e.g. 44100).
     */
    void init(int sampleRate) throws Exception;

    /**
     * Selects one of the 76 built-in OPL instrument banks by index.
     * Call after {@link #init}.
     */
    void setBank(int bankNumber);

    /**
     * Loads an external WOPL bank file.
     * Call after {@link #init}.
     */
    void loadBankFile(String path) throws Exception;

    /**
     * Sets the number of OPL chips to emulate (affects polyphony).
     * Default is 4 chips = 72 simultaneous channels.
     */
    void setNumChips(int numChips);

    /**
     * Switches the OPL emulator backend.
     * 0 = Nuked OPL3 (highest quality), 2 = YMFM (good quality, lower CPU).
     */
    void switchEmulator(int emulatorId);

    /**
     * Resets the synthesizer state (clears all notes and patch settings).
     * Safe to call only from the render thread (not thread-safe).
     */
    void reset();

    // --- MIDI Event Routing (dispatched by render thread from event queue) ---

    void noteOn(int channel, int note, int velocity);
    void noteOff(int channel, int note);
    void controlChange(int channel, int type, int value);
    void patchChange(int channel, int patch);

    /**
     * Sends a pitch-bend event on the given channel.
     *
     * @param channel MIDI channel (0–15).
     * @param pitch   14-bit signed pitch bend value (-8192 to +8191).
     */
    void pitchBend(int channel, int pitch);
    void systemExclusive(byte[] data);

    /**
     * Immediately cuts all active notes. Must be called from the render thread
     * while it is paused (i.e., not in {@link #generate}).
     */
    void panic();

    /**
     * Renders {@code stereoFrames} stereo PCM frames into {@code buffer}.
     * Buffer must have at least {@code stereoFrames * 2} elements.
     *
     * @param buffer      Output buffer (interleaved stereo 16-bit PCM).
     * @param stereoFrames Number of stereo frames to render.
     */
    void generate(short[] buffer, int stereoFrames);

    /** Returns the number of built-in banks available. */
    default int getBanksCount() { return 0; }

    /** Closes and frees all native resources. */
    @Override
    void close();
}
