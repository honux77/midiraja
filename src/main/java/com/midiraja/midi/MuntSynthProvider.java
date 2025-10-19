/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.midi;

import java.util.List;

public class MuntSynthProvider implements SoftSynthProvider {

    private final MuntNativeBridge bridge;
    private final NativeAudioEngine audio;

    public MuntSynthProvider(MuntNativeBridge bridge, NativeAudioEngine audio) {
        this.bridge = bridge;
        this.audio = audio;
    }

    @Override
    public List<MidiPort> getOutputPorts() {
        return List.of(new MidiPort(0, "Munt MT-32 Emulator (Embedded)"));
    }

    @Override
    public void openPort(int portIndex) throws Exception {
        bridge.openSynth();
    }

    @Override
    public void loadSoundbank(String path) throws Exception {
        bridge.createSynth();
        bridge.loadRoms(path);
    }

    @Override
    public void sendMessage(byte[] data) throws Exception {
        if (data == null || data.length == 0) return;

        int status = data[0] & 0xFF;
        if (status >= 0xF0) {
            bridge.playSysex(data);
            return;
        }

        int command = status & 0xF0;
        int channel = status & 0x0F;

        if (data.length >= 2) {
            int data1 = data[1] & 0xFF;
            int data2 = (data.length >= 3) ? (data[2] & 0xFF) : 0;

            switch (command) {
                case 0x90:
                    bridge.playNoteOn(channel, data1, data2);
                    break;
                case 0x80:
                    bridge.playNoteOff(channel, data1);
                    break;
                case 0xB0:
                    bridge.playControlChange(channel, data1, data2);
                    break;
                case 0xC0:
                    bridge.playProgramChange(channel, data1);
                    break;
                case 0xE0:
                    int bend = (data2 << 7) | data1;
                    bridge.playPitchBend(channel, bend);
                    break;
            }
        }
    }

    @Override
    public void closePort() {
        bridge.close();
        if (audio != null) {
            audio.close();
        }
    }
}