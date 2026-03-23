/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.engine;

import java.util.List;
import javax.sound.midi.MidiEvent;

/** Pure utility for MIDI time-to-tick conversion. */
final class TempoMap
{
    private TempoMap() {}

    static long parseTimeToMicroseconds(String timeStr)
    {
        try
        {
            String[] parts = timeStr.trim().split(":", -1);
            long seconds = 0;
            for (String part : parts)
            {
                seconds = seconds * 60 + Long.parseLong(part);
            }
            return seconds * 1000000L;
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    static long getTickForTime(List<MidiEvent> events, int resolution, long tickLength,
            long targetMicroseconds)
    {
        if (targetMicroseconds <= 0) return -1;
        long targetNanos = targetMicroseconds * 1000;
        long currentNanos = 0;
        long lastTick = 0;
        float bpm = 120.0f;
        double ticksToNanos = (60000000000.0 / (bpm * resolution)); // Absolute time logic ignores
                                                                     // speed multiplier

        for (MidiEvent ev : events)
        {
            long t = ev.getTick();
            long nextNanos = currentNanos + (long) ((t - lastTick) * ticksToNanos);
            if (nextNanos >= targetNanos)
            {
                long remainingNanos = targetNanos - currentNanos;
                return lastTick + (long) (remainingNanos / ticksToNanos);
            }

            currentNanos = nextNanos;
            lastTick = t;

            var msg = ev.getMessage().getMessage();
            int status = msg[0] & 0xFF;

            if (status == 0xFF && msg.length >= 6 && (msg[1] & 0xFF) == 0x51)
            {
                int mspqn = ((msg[3] & 0xFF) << 16) | ((msg[4] & 0xFF) << 8) | (msg[5] & 0xFF);
                if (mspqn > 0)
                {
                    bpm = 60000000.0f / mspqn;
                    ticksToNanos = (60000000000.0 / (bpm * resolution));
                }
            }
        }
        return tickLength;
    }
}
