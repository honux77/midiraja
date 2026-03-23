/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.ShortMessage;
import org.junit.jupiter.api.Test;

class TempoMapTest
{
    // --- parseTimeToMicroseconds ---

    @Test
    void parseSeconds_returnsCorrectMicroseconds()
    {
        assertEquals(30_000_000L, TempoMap.parseTimeToMicroseconds("30"));
    }

    @Test
    void parseMinutesAndSeconds_returnsCorrectMicroseconds()
    {
        assertEquals(90_000_000L, TempoMap.parseTimeToMicroseconds("1:30"));
    }

    @Test
    void parseHoursMinutesSeconds_returnsCorrectMicroseconds()
    {
        assertEquals(3_600_000_000L, TempoMap.parseTimeToMicroseconds("1:00:00"));
    }

    @Test
    void parseZero_returnsZero()
    {
        assertEquals(0L, TempoMap.parseTimeToMicroseconds("0"));
    }

    @Test
    void parseBlankString_returnsZero()
    {
        assertEquals(0L, TempoMap.parseTimeToMicroseconds("   "));
    }

    @Test
    void parseNonNumeric_returnsZero()
    {
        assertEquals(0L, TempoMap.parseTimeToMicroseconds("abc"));
    }

    @Test
    void parseWithLeadingTrailingWhitespace_ignored()
    {
        assertEquals(30_000_000L, TempoMap.parseTimeToMicroseconds("  30  "));
    }

    // --- getTickForTime ---

    @Test
    void getTickForTime_negativeTarget_returnsMinusOne()
    {
        assertEquals(-1L, TempoMap.getTickForTime(List.of(), 480, 1000L, -1L));
    }

    @Test
    void getTickForTime_zeroTarget_returnsMinusOne()
    {
        assertEquals(-1L, TempoMap.getTickForTime(List.of(), 480, 1000L, 0L));
    }

    @Test
    void getTickForTime_constantBpm_tickProportionalToTime() throws InvalidMidiDataException
    {
        // At 120 BPM, resolution=480 ticks/beat:
        // 1 beat = 0.5s = 500_000 µs → 480 ticks per 500_000 µs
        // 1_000_000 µs = 2 beats = 960 ticks
        // A sentinel event at tick 10000 ensures the loop runs past the target time.
        int resolution = 480;
        long tickLength = 10000L;
        ShortMessage note = new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 64);
        MidiEvent sentinel = new MidiEvent(note, tickLength);
        long tick = TempoMap.getTickForTime(List.of(sentinel), resolution, tickLength, 1_000_000L);
        assertEquals(960L, tick);
    }

    @Test
    void getTickForTime_beyondSequence_returnsTickLength()
    {
        int resolution = 480;
        long tickLength = 100L;
        // Target is 1 hour — way beyond any reasonable sequence
        long tick = TempoMap.getTickForTime(List.of(), resolution, tickLength, 3_600_000_000L);
        assertEquals(tickLength, tick);
    }

    @Test
    void getTickForTime_tempoChangeAffectsTickCalculation() throws InvalidMidiDataException
    {
        // Sequence: 480 ticks/beat, starts at 120 BPM.
        // Tempo change to 60 BPM at tick 480 (= 0.5s in).
        // Query: 1_500_000 µs (1.5 s total).
        //   First 0.5s (500_000 µs) @ 120 BPM = 480 ticks.
        //   Remaining 1_000_000 µs @ 60 BPM: 1 beat/s × 480 ticks/beat = 480 ticks.
        //   Total expected tick = 480 + 480 = 960.
        int resolution = 480;
        long tickLength = 10000L;

        int mspqn = 1_000_000; // 60 BPM: 1_000_000 µs per quarter note
        byte[] tempoData = {
            (byte) ((mspqn >> 16) & 0xFF),
            (byte) ((mspqn >> 8) & 0xFF),
            (byte) (mspqn & 0xFF)
        };
        MetaMessage tempoMsg = new MetaMessage(0x51, tempoData, 3);
        MidiEvent tempoEvent = new MidiEvent(tempoMsg, 480L);
        // Sentinel event beyond the target tick ensures the loop runs past 1_500_000 µs.
        ShortMessage note = new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 64);
        MidiEvent sentinel = new MidiEvent(note, tickLength);

        List<MidiEvent> events = List.of(tempoEvent, sentinel);
        long tick = TempoMap.getTickForTime(events, resolution, tickLength, 1_500_000L);
        assertEquals(960L, tick);
    }

    @Test
    void getTickForTime_targetLandsBeforeTempoEvent_usesPreChangeBpm()
            throws InvalidMidiDataException
    {
        // Tempo changes to 60 BPM at tick 480 (= 0.5s @ 120 BPM).
        // Query: 250_000 µs — falls before the tempo event.
        // At 120 BPM, resolution=480: 250_000 µs = 0.5 beats = 240 ticks.
        // The tempo change must NOT affect the result.
        int resolution = 480;
        long tickLength = 10000L;

        int mspqn = 1_000_000; // 60 BPM
        byte[] tempoData = {
            (byte) ((mspqn >> 16) & 0xFF),
            (byte) ((mspqn >> 8) & 0xFF),
            (byte) (mspqn & 0xFF)
        };
        MetaMessage tempoMsg = new MetaMessage(0x51, tempoData, 3);
        MidiEvent tempoEvent = new MidiEvent(tempoMsg, 480L);

        long tick = TempoMap.getTickForTime(List.of(tempoEvent), resolution, tickLength, 250_000L);
        assertEquals(240L, tick);
    }
}
