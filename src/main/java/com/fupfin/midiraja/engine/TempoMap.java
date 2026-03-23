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

    /**
     * Returns the tick position corresponding to {@code targetMicroseconds} of absolute playback
     * time, accounting for tempo-change meta-events in the sequence.
     *
     * @param events       all MIDI events in the sequence, sorted by tick (ascending)
     * @param resolution   ticks per quarter note (from {@code Sequence.getResolution()})
     * @param tickLength   total tick length of the sequence (from {@code Sequence.getTickLength()})
     * @param targetMicroseconds absolute time to seek to; returns -1 when ≤ 0
     */
    static long getTickForTime(List<MidiEvent> events, int resolution, long tickLength,
            long targetMicroseconds)
    {
        if (targetMicroseconds <= 0) return -1;
        long targetNanos = targetMicroseconds * 1000;
        long currentNanos = 0;
        long lastTick = 0;
        // 120 BPM = 500_000 µs/beat → nanoseconds per tick at initial tempo
        double ticksToNanos = 500_000_000.0 / resolution; // Absolute time ignores speed multiplier

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
                    ticksToNanos = (double) mspqn * 1000.0 / resolution;
                }
            }
        }
        return tickLength;
    }
}
