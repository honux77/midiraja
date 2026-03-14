/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.midi;

import com.fupfin.midiraja.dsp.AudioProcessor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * SoftSynthProvider backed by TinySoundFont (SF2/SF3 SoundFont synthesis).
 */
public class TsfSynthProvider extends AbstractSoftSynthProvider<TsfNativeBridge>
{
    public TsfSynthProvider(TsfNativeBridge bridge, @Nullable AudioProcessor audioOut)
    {
        super(bridge, audioOut);
    }

    @Override
    public long getAudioLatencyNanos()
    {
        return 4096L * 1_000_000_000L / SAMPLE_RATE;
    }

    @Override
    public List<MidiPort> getOutputPorts()
    {
        return List.of(new MidiPort(0, "TinySoundFont"));
    }

    @Override
    public void openPort(int portIndex)
    {
        // openPort is a no-op for TSF; device creation happens in loadSoundbank
    }

    @Override
    public void loadSoundbank(String path) throws Exception
    {
        bridge.loadSoundfontFile(path, SAMPLE_RATE);

        if (audioOut != null)
        {
            startRenderThread("TsfRenderThread");
        }
    }
}
