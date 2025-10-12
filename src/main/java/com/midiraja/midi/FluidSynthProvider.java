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
        throw new Exception("libfluidsynth.dylib not found! Please install it.");
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
    }

    @Override
    public void closePort() {
    }
}