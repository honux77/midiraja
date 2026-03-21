/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.midi.vgm;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.MetaMessage;

/**
 * Converts a MIDI file to a VGM file targeting dual AY-3-8910 / YM2149F PSG chips.
 *
 * <h2>Chip layout (dual AY-3-8910, VGM v1.61)</h2>
 * <pre>
 *   Chip 0 (0xA0 rr dd):  tone slots 0, 1, 2  —  channels A, B, C
 *   Chip 1 (0xA0 rr|80 dd): tone slots 3, 4, 5  —  channels A, B, C
 * </pre>
 * Total: 6 simultaneous melodic voices + 1 noise voice (chip 1 channel C).
 *
 * <h2>AY-3-8910 register map (per chip)</h2>
 * <pre>
 *   R0/R1  Channel A tone period (12-bit, fine/coarse)
 *   R2/R3  Channel B tone period
 *   R4/R5  Channel C tone period
 *   R6     Noise period (5-bit)
 *   R7     Mixer: tone-enable (bits 0-2) + noise-enable (bits 3-5), active-low
 *   R8     Channel A amplitude (4-bit; bit 4 = envelope mode, always 0 here)
 *   R9     Channel B amplitude
 *   R10    Channel C amplitude
 * </pre>
 *
 * <h2>Frequency mapping</h2>
 * AY tone period TP = round(clock / (16 × freq_hz)), clamped to [1, 4095] (12-bit).
 * Clock = 1,789,772 Hz (MSX NTSC).
 *
 * <h2>Volume mapping</h2>
 * MIDI velocity 0–127 → AY amplitude 0–15 (linear). 0 = silence, 15 = maximum.
 *
 * <h2>Melody-first voice allocation</h2>
 * MIDI channel 0 has the highest priority. When all 6 PSG slots are full, the slot
 * with the highest (least important) MIDI channel number is stolen.
 */
public final class MidiToVgmConverter
{
    /** Number of available tone slots (3 per AY chip × 2 chips). */
    private static final int TONE_SLOTS = 6;

    /**
     * Slot index used exclusively for percussion noise (chip 1, channel C).
     * Voice stealing will evict any melody from this slot when a drum hits.
     */
    private static final int NOISE_SLOT = 5;

    private MidiToVgmConverter()
    {
    }

    /**
     * Converts {@code midiFile} to a dual AY-3-8910 VGM file at {@code outputPath}.
     *
     * @param midiFile   source MIDI file
     * @param outputPath destination VGM file path
     * @param err        stream for progress/warning messages
     * @throws Exception if the MIDI file cannot be read or the VGM file cannot be written
     */
    public static void convert(File midiFile, String outputPath, PrintStream err) throws Exception
    {
        Sequence seq = MidiSystem.getSequence(midiFile);
        if (seq.getDivisionType() != Sequence.PPQ)
        {
            throw new IllegalArgumentException(
                    "SMPTE-timed MIDI files are not supported (only PPQ).");
        }

        int resolution = seq.getResolution();
        List<MidiEvent> events = mergeAndSort(seq);

        try (VgmFileWriter vgm = new VgmFileWriter(outputPath, true, true))
        {
            Ay8910State state = new Ay8910State(vgm);
            state.initSilence();

            long currentTick = 0;
            long tempoUs = 500_000L; // 120 BPM default
            double sampleAccum = 0.0;

            for (MidiEvent event : events)
            {
                long tick = event.getTick();
                long deltaTick = tick - currentTick;

                if (deltaTick > 0)
                {
                    double deltaSamples = deltaTick * tempoUs
                            * (double) VgmFileWriter.VGM_SAMPLE_RATE / (resolution * 1_000_000.0);
                    sampleAccum += deltaSamples;
                    long wholeSamples = (long) sampleAccum;
                    sampleAccum -= wholeSamples;
                    if (wholeSamples > 0)
                    {
                        vgm.waitSamples(wholeSamples);
                    }
                    currentTick = tick;
                }

                MidiMessage msg = event.getMessage();
                if (msg instanceof MetaMessage meta)
                {
                    if (meta.getType() == 0x51 && meta.getData().length >= 3)
                    {
                        byte[] d = meta.getData();
                        tempoUs = ((d[0] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[2] & 0xFF);
                    }
                }
                else if (msg instanceof ShortMessage sm)
                {
                    state.handleMessage(sm);
                }
            }

            long remaining = Math.round(sampleAccum);
            if (remaining > 0) vgm.waitSamples(remaining);

            err.println("VGM written: " + outputPath);
            err.printf("  Duration: %.1f s  (%,d samples)%n",
                    vgm.getTotalSamples() / (double) VgmFileWriter.VGM_SAMPLE_RATE,
                    vgm.getTotalSamples());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static List<MidiEvent> mergeAndSort(Sequence seq)
    {
        List<MidiEvent> all = new ArrayList<>();
        Arrays.stream(seq.getTracks())
                .flatMap(track -> IntStream.range(0, track.size()).mapToObj(track::get))
                .forEach(all::add);
        all.sort(Comparator.comparingLong(MidiEvent::getTick));
        return all;
    }

    // ── AY-3-8910 state machine ───────────────────────────────────────────────

    private static final class Ay8910State
    {
        private final VgmFileWriter vgm;

        // Per-slot state (slots 0-2 = chip 0 ch A/B/C; slots 3-5 = chip 1 ch A/B/C)
        private final int[] slotMidiCh = new int[TONE_SLOTS];
        private final int[] slotNote = new int[TONE_SLOTS];
        private final int[] slotAmplitude = new int[TONE_SLOTS]; // 0=silent, 15=max

        // Per-chip mixer register R7 (active-low: 0=enabled, 1=disabled)
        // Default 0x38 = all 3 tones enabled, all noise disabled
        private final int[] mixer = {0x38, 0x38};

        Ay8910State(VgmFileWriter vgm)
        {
            this.vgm = vgm;
            Arrays.fill(slotMidiCh, -1);
            Arrays.fill(slotNote, -1);
            Arrays.fill(slotAmplitude, 0);
        }

        /** Mutes all channels on both chips and sets default mixer state. */
        void initSilence() throws IOException
        {
            // Chip 0: all amplitudes = 0, mixer = all tones, no noise
            for (int ch = 0; ch < 3; ch++) vgm.writeAy(8 + ch, 0);
            vgm.writeAy(7, 0x38);
            // Chip 1: same
            for (int ch = 0; ch < 3; ch++) vgm.writeAy2(8 + ch, 0);
            vgm.writeAy2(7, 0x38);
        }

        // ── MIDI dispatch ─────────────────────────────────────────────────────

        void handleMessage(ShortMessage sm) throws IOException
        {
            int cmd = sm.getCommand();
            int ch = sm.getChannel();
            int d1 = sm.getData1();
            int d2 = sm.getData2();

            if (cmd == ShortMessage.NOTE_ON && d2 > 0)
                handleNoteOn(ch, d1, d2);
            else if (cmd == ShortMessage.NOTE_OFF || (cmd == ShortMessage.NOTE_ON && d2 == 0))
                handleNoteOff(ch, d1);
            else if (cmd == ShortMessage.CONTROL_CHANGE && (d1 == 123 || d1 == 120))
                allNotesOff();
        }

        // ── Note-on ───────────────────────────────────────────────────────────

        private void handleNoteOn(int ch, int note, int velocity) throws IOException
        {
            int amp = velocityToAmplitude(velocity);

            if (ch == 9)
            {
                handleDrum(note, amp);
                return;
            }

            // 1. Same note already on this MIDI channel → update amplitude only
            for (int i = 0; i < TONE_SLOTS; i++)
            {
                if (slotMidiCh[i] == ch && slotNote[i] == note)
                {
                    slotAmplitude[i] = amp;
                    writeAmplitude(i, amp);
                    return;
                }
            }

            // 2. Free slot available (amplitude == 0 → effectively free)
            for (int i = 0; i < TONE_SLOTS; i++)
            {
                if (slotMidiCh[i] == -1)
                {
                    allocate(i, ch, note, amp);
                    return;
                }
            }

            // 3. Voice steal: evict least important slot (highest MIDI ch, then quietest)
            int worstIdx = leastImportantSlot();
            if (ch <= slotMidiCh[worstIdx])
            {
                allocate(worstIdx, ch, note, amp);
            }
            // else: new note less important than all → silently drop
        }

        private void allocate(int slot, int ch, int note, int amp) throws IOException
        {
            // If this slot was the noise slot and noise was active, restore tone mode first
            if (slot == NOISE_SLOT && !isToneMode(NOISE_SLOT))
            {
                restoreNoiseSlotToTone();
            }
            slotMidiCh[slot] = ch;
            slotNote[slot] = note;
            slotAmplitude[slot] = amp;
            writeToneFreq(slot, note);
            writeAmplitude(slot, amp);
        }

        /** Returns index of the slot with the highest (least important) MIDI channel. */
        private int leastImportantSlot()
        {
            int idx = 0;
            for (int i = 1; i < TONE_SLOTS; i++)
            {
                int cur = slotMidiCh[i];
                int best = slotMidiCh[idx];
                if (cur > best || (cur == best && slotAmplitude[i] < slotAmplitude[idx]))
                    idx = i;
            }
            return idx;
        }

        // ── Note-off ──────────────────────────────────────────────────────────

        private void handleNoteOff(int ch, int note) throws IOException
        {
            if (ch == 9)
            {
                silenceNoiseSlot();
                return;
            }

            for (int i = 0; i < TONE_SLOTS; i++)
            {
                if (slotMidiCh[i] == ch && slotNote[i] == note)
                {
                    slotMidiCh[i] = -1;
                    slotNote[i] = -1;
                    slotAmplitude[i] = 0;
                    writeAmplitude(i, 0);
                    return;
                }
            }
        }

        private void allNotesOff() throws IOException
        {
            for (int i = 0; i < TONE_SLOTS; i++)
            {
                slotMidiCh[i] = -1;
                slotNote[i] = -1;
                slotAmplitude[i] = 0;
                writeAmplitude(i, 0);
            }
            restoreNoiseSlotToTone();
        }

        // ── Percussion (noise via chip 1, channel C = slot NOISE_SLOT) ────────

        private void handleDrum(int note, int amp) throws IOException
        {
            int noisePeriod = drumNoisePeriod(note);
            int chipIdx = chipOf(NOISE_SLOT);

            // Switch slot 5 from tone to noise mode
            int newMixer = mixer[chipIdx];
            int chInChip = NOISE_SLOT % 3; // = 2 (channel C)
            newMixer |= (1 << chInChip);       // disable tone on ch C (bit 2)
            newMixer &= ~(1 << (chInChip + 3)); // enable  noise on ch C (bit 5 → 0)
            setMixer(chipIdx, newMixer);

            writeReg(NOISE_SLOT, 6, noisePeriod & 0x1F); // R6 noise period
            slotMidiCh[NOISE_SLOT] = 9;
            slotNote[NOISE_SLOT] = note;
            slotAmplitude[NOISE_SLOT] = amp;
            writeAmplitude(NOISE_SLOT, amp);
        }

        private void silenceNoiseSlot() throws IOException
        {
            writeAmplitude(NOISE_SLOT, 0);
            slotAmplitude[NOISE_SLOT] = 0;
            slotMidiCh[NOISE_SLOT] = -1;
            slotNote[NOISE_SLOT] = -1;
            restoreNoiseSlotToTone();
        }

        /** Restores chip 1 channel C (slot 5) from noise mode back to tone mode. */
        private void restoreNoiseSlotToTone() throws IOException
        {
            int chipIdx = chipOf(NOISE_SLOT);
            int chInChip = NOISE_SLOT % 3; // 2
            int newMixer = mixer[chipIdx];
            newMixer &= ~(1 << chInChip);      // re-enable tone on ch C
            newMixer |= (1 << (chInChip + 3)); // disable noise on ch C
            setMixer(chipIdx, newMixer);
        }

        private boolean isToneMode(int slot)
        {
            int chipIdx = chipOf(slot);
            int chInChip = slot % 3;
            return (mixer[chipIdx] & (1 << chInChip)) == 0; // bit = 0 → tone enabled
        }

        private void setMixer(int chipIdx, int value) throws IOException
        {
            mixer[chipIdx] = value & 0xFF;
            if (chipIdx == 0) vgm.writeAy(7, mixer[0]);
            else vgm.writeAy2(7, mixer[1]);
        }

        // ── Drum note → noise period mapping ─────────────────────────────────

        private static int drumNoisePeriod(int note)
        {
            if (note == 35 || note == 36) return 31; // kick → slowest (low freq)
            if (note == 42 || note == 44 || note == 46
                    || note == 49 || note == 51) return 4;  // hi-hat → fast (high freq)
            return 14; // snare / everything else → mid freq
        }

        // ── AY-3-8910 register write helpers ─────────────────────────────────

        /**
         * Sets the 12-bit tone period for the given slot.
         * R(base) = fine bits [7:0], R(base+1) = coarse bits [11:8].
         */
        private void writeToneFreq(int slot, int note) throws IOException
        {
            double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
            int tp = (int) Math.round(VgmFileWriter.AY8910_CLOCK / (16.0 * freq));
            tp = Math.max(1, Math.min(4095, tp));

            int chInChip = slot % 3; // 0=A, 1=B, 2=C
            int regBase = chInChip * 2; // R0/R1, R2/R3, R4/R5
            writeReg(slot, regBase,     tp & 0xFF);        // fine
            writeReg(slot, regBase + 1, (tp >> 8) & 0x0F); // coarse
        }

        /** Writes the amplitude (volume) register for the given slot. */
        private void writeAmplitude(int slot, int amp) throws IOException
        {
            int chInChip = slot % 3;
            writeReg(slot, 8 + chInChip, amp & 0x0F); // R8/R9/R10, no envelope bit
        }

        /** Routes a register write to chip 0 or chip 1 based on slot index. */
        private void writeReg(int slot, int reg, int value) throws IOException
        {
            if (slot < 3) vgm.writeAy(reg, value);
            else vgm.writeAy2(reg, value);
        }

        private static int chipOf(int slot) { return slot < 3 ? 0 : 1; }

        private static int velocityToAmplitude(int velocity)
        {
            return Math.round(velocity * 15.0f / 127.0f);
        }
    }
}
