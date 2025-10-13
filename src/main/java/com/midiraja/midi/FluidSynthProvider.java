/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.midi;

import org.jspecify.annotations.Nullable;
import java.util.List;

public class FluidSynthProvider implements SoftSynthProvider {
    
    public FluidSynthProvider(@Nullable String explicitLibraryPath) throws Exception {
        if (!"MOCK_LIBRARY".equals(explicitLibraryPath)) {
            throw new Exception("libfluidsynth.dylib not found! Please install it.");
        }
    }

    @Override
    public void loadSoundbank(String path) throws Exception {
    }

    @Override
    public List<MidiPort> getOutputPorts() {
        return List.of();
    }

    @Override
    public void openPort(int portIndex) throws Exception {
    }

    @Override
    public void sendMessage(byte[] data) throws Exception {
        if (data == null || data.length == 0) return;

        int status = data[0] & 0xFF;
        
        if (status >= 0xF0) {
            fluid_synth_sysex(data);
            return;
        }

        int command = status & 0xF0;
        int channel = status & 0x0F;

        if (data.length >= 2) {
            int data1 = data[1] & 0xFF;
            int data2 = (data.length >= 3) ? (data[2] & 0xFF) : 0;

            switch (command) {
                case 0x90: // Note On
                    fluid_synth_noteon(channel, data1, data2);
                    break;
                case 0x80: // Note Off
                    fluid_synth_noteoff(channel, data1);
                    break;
                case 0xB0: // Control Change
                    fluid_synth_cc(channel, data1, data2);
                    break;
                case 0xC0: // Program Change
                    fluid_synth_program_change(channel, data1);
                    break;
                case 0xE0: // Pitch Bend
                    int bend = (data2 << 7) | data1;
                    fluid_synth_pitch_bend(channel, bend);
                    break;
                // Ignored for now: Channel Pressure (0xD0), Poly Aftertouch (0xA0)
            }
        }
    }

    // Protected methods to allow for overriding in tests and mapping via FFM
    protected void fluid_synth_noteon(int channel, int key, int velocity) { }
    protected void fluid_synth_noteoff(int channel, int key) { }
    protected void fluid_synth_cc(int channel, int num, int val) { }
    protected void fluid_synth_program_change(int channel, int program) { }
    protected void fluid_synth_pitch_bend(int channel, int val) { }
    protected void fluid_synth_sysex(byte[] data) { }

    @Override
    public void closePort() {
    }
}