/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.midi;

import java.nio.charset.StandardCharsets;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import org.jspecify.annotations.Nullable;

public class MidiUtils
{
    private MidiUtils()
    {}

    public static @Nullable String extractSequenceTitle(Sequence sequence)
    {
        for (Track track : sequence.getTracks())
        {
            for (int i = 0; i < track.size(); i++)
            {
                MidiMessage msg = track.get(i).getMessage();
                if (msg instanceof MetaMessage meta && meta.getType() == 0x03)
                {
                    byte[] data = meta.getData();
                    if (data != null && data.length > 0)
                    {
                        String text = new String(data, StandardCharsets.UTF_8).trim();
                        if (!text.isEmpty() && !text.matches("^[\\s\\p{C}]+$")) return text;
                    }
                }
            }
        }
        return null;
    }
}
