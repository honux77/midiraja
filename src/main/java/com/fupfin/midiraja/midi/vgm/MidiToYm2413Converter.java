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
 * Converts a MIDI file to a VGM file targeting the YM2413 (OPLL) FM chip.
 *
 * <h2>YM2413 channel layout</h2>
 * <pre>
 *   Melodic mode  : 9 FM channels (channels 0–8)
 *   Rhythm mode   : 6 FM melodic channels (0–5) + 5 rhythm voices:
 *                   Bass Drum, Snare Drum, Tom-Tom, Cymbal, Hi-Hat
 * </pre>
 * Rhythm mode is activated automatically when MIDI channel 9 (drums) is used.
 *
 * <h2>YM2413 register map</h2>
 * <pre>
 *   R0x00–R0x07  Custom instrument patch (modulator/carrier)
 *   R0x0E        Rhythm key-on / mode enable (bit 5 = rhythm mode)
 *   R0x10–R0x18  F-Number low 8 bits (channels 0–8)
 *   R0x20–R0x28  Sustain | Key-On | Block[2:0] | F-Num bit 8  (channels 0–8)
 *   R0x30–R0x38  Instrument[3:0] (high nibble) | Volume[3:0] (low nibble)
 *   R0x36        Rhythm: BD volume [3:0]
 *   R0x37        Rhythm: HH volume [7:4] | SD volume [3:0]
 *   R0x38        Rhythm: TOM volume [7:4] | CYM volume [3:0]
 * </pre>
 *
 * <h2>Frequency mapping</h2>
 * freq_hz = Fnum * clock / (72 * 2^(19 - block)).
 * Clock = 3,579,545 Hz (MSX NTSC). Fnum is 9-bit [0, 511]; block is 3-bit [0, 7].
 *
 * <h2>Volume mapping</h2>
 * MIDI velocity 0–127 → YM2413 volume 15–0 (inverted: 0 = maximum, 15 = silent).
 *
 * <h2>Preset instruments</h2>
 * Instrument 0 = custom (user-defined patch); 1–15 = built-in OPLL presets.
 * MIDI program number is mapped to the closest YM2413 preset.
 *
 * <h2>Melody-first voice allocation</h2>
 * MIDI channel 0 has the highest priority. When all melodic slots are full,
 * the slot with the highest (least important) MIDI channel number is stolen.
 */
public final class MidiToYm2413Converter
{
    private MidiToYm2413Converter()
    {
    }

    /**
     * Converts {@code midiFile} to a YM2413 VGM file at {@code outputPath}.
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

        try (VgmFileWriter vgm = new VgmFileWriter(outputPath, VgmFileWriter.ChipMode.YM2413, false))
        {
            Ym2413State state = new Ym2413State(vgm);
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

    // ── YM2413 state machine ──────────────────────────────────────────────────

    private static final class Ym2413State
    {
        // ── YM2413 preset instrument map ──────────────────────────────────────
        //
        // Preset 1=Violin  2=Guitar   3=Piano    4=Flute    5=Clarinet
        //        6=Oboe    7=Trumpet  8=Organ    9=Horn    10=Synthesizer
        //       11=Harpsichord  12=Vibraphone  13=Synth Bass  14=Acoustic Bass
        //       15=Electric Guitar
        //
        // MIDI GM program groups → YM2413 preset:
        private static final int[] GM_TO_YM2413 = new int[128];

        static
        {
            // Piano (0–7) → Piano
            Arrays.fill(GM_TO_YM2413,   0,   8,  3);
            // Chromatic percussion (8–15) → Vibraphone
            Arrays.fill(GM_TO_YM2413,   8,  16, 12);
            // Organ (16–23) → Organ
            Arrays.fill(GM_TO_YM2413,  16,  24,  8);
            // Guitar (24–31) → Guitar/Electric Guitar
            Arrays.fill(GM_TO_YM2413,  24,  28,  2);
            Arrays.fill(GM_TO_YM2413,  28,  32, 15);
            // Bass (32–39) → Acoustic Bass / Synth Bass
            Arrays.fill(GM_TO_YM2413,  32,  36, 14);
            Arrays.fill(GM_TO_YM2413,  36,  40, 13);
            // Strings (40–47) → Violin
            Arrays.fill(GM_TO_YM2413,  40,  48,  1);
            // Ensemble (48–55) → Violin
            Arrays.fill(GM_TO_YM2413,  48,  56,  1);
            // Brass (56–63) → Trumpet / Horn
            Arrays.fill(GM_TO_YM2413,  56,  60,  7);
            Arrays.fill(GM_TO_YM2413,  60,  64,  9);
            // Reed (64–71) → Clarinet
            Arrays.fill(GM_TO_YM2413,  64,  72,  5);
            // Pipe (72–79) → Flute
            Arrays.fill(GM_TO_YM2413,  72,  80,  4);
            // Synth Lead (80–87) → Synthesizer
            Arrays.fill(GM_TO_YM2413,  80,  88, 10);
            // Synth Pad (88–95) → Violin (sustained)
            Arrays.fill(GM_TO_YM2413,  88,  96,  1);
            // Synth FX (96–103) → Synthesizer
            Arrays.fill(GM_TO_YM2413,  96, 104, 10);
            // Ethnic (104–111) → Oboe
            Arrays.fill(GM_TO_YM2413, 104, 112,  6);
            // Percussive (112–119) → Vibraphone
            Arrays.fill(GM_TO_YM2413, 112, 120, 12);
            // Sound effects (120–127) → Synthesizer
            Arrays.fill(GM_TO_YM2413, 120, 128, 10);
        }

        // ── Rhythm instrument bits in R0x0E ───────────────────────────────────
        // bit 4 = BD, 3 = SD, 2 = TOM, 1 = CYM, 0 = HH
        private static final int RHY_BD  = 1 << 4;
        private static final int RHY_SD  = 1 << 3;
        private static final int RHY_TOM = 1 << 2;
        private static final int RHY_CYM = 1 << 1;
        private static final int RHY_HH  = 1 << 0;

        private final VgmFileWriter vgm;

        // Melodic slot state (slots 0–8; reduced to 0–5 when rhythm mode active)
        private static final int MAX_MELODIC_SLOTS = 9;
        private final int[] slotMidiCh   = new int[MAX_MELODIC_SLOTS];
        private final int[] slotNote     = new int[MAX_MELODIC_SLOTS];
        private final int[] slotVolume   = new int[MAX_MELODIC_SLOTS]; // 0=loud, 15=silent
        private final int[] slotInst     = new int[MAX_MELODIC_SLOTS]; // 1-15 YM2413 preset
        private final boolean[] slotActive = new boolean[MAX_MELODIC_SLOTS];

        // Per-MIDI-channel program (0–127)
        private final int[] channelProgram = new int[16];

        // Rhythm state
        private boolean rhythmMode = false;
        private int rhythmKeyOn = 0;  // active bits in R0x0E (excluding bit5)
        // Per-rhythm-instrument volume (0=loud, 15=silent)
        private int volBD  = 15;
        private int volSD  = 15;
        private int volTOM = 15;
        private int volCYM = 15;
        private int volHH  = 15;

        Ym2413State(VgmFileWriter vgm)
        {
            this.vgm = vgm;
            Arrays.fill(slotMidiCh, -1);
            Arrays.fill(slotNote, -1);
            Arrays.fill(slotVolume, 15);
            Arrays.fill(slotInst, 1);
        }

        /** Mutes all channels and initialises to a known-silent state. */
        void initSilence() throws IOException
        {
            for (int i = 0; i < MAX_MELODIC_SLOTS; i++)
            {
                vgm.writeYm2413(0x30 + i, 0x0F);  // instrument 0, volume 15 (silent)
                vgm.writeYm2413(0x10 + i, 0);     // F-Num low
                vgm.writeYm2413(0x20 + i, 0);     // block/fnum-high/key-off
            }
            vgm.writeYm2413(0x0E, 0x00);           // rhythm mode off, all keys off
        }

        // ── MIDI dispatch ─────────────────────────────────────────────────────

        void handleMessage(ShortMessage sm) throws IOException
        {
            int cmd = sm.getCommand();
            int ch  = sm.getChannel();
            int d1  = sm.getData1();
            int d2  = sm.getData2();

            switch (cmd)
            {
                case ShortMessage.NOTE_ON ->
                {
                    if (d2 > 0) handleNoteOn(ch, d1, d2);
                    else        handleNoteOff(ch, d1);
                }
                case ShortMessage.NOTE_OFF -> handleNoteOff(ch, d1);
                case ShortMessage.PROGRAM_CHANGE -> channelProgram[ch] = d1;
                case ShortMessage.CONTROL_CHANGE ->
                {
                    if (d1 == 123 || d1 == 120) allNotesOff();
                }
            }
        }

        // ── Note-on ───────────────────────────────────────────────────────────

        private void handleNoteOn(int ch, int note, int velocity) throws IOException
        {
            if (ch == 9)
            {
                handleDrum(note, velocity);
                return;
            }

            int vol  = velocityToVolume(velocity);
            int inst = GM_TO_YM2413[channelProgram[ch] & 0x7F];
            int slots = melodicSlots();

            // 1. Same note already on this MIDI channel → update volume
            for (int i = 0; i < slots; i++)
            {
                if (slotMidiCh[i] == ch && slotNote[i] == note && slotActive[i])
                {
                    slotVolume[i] = vol;
                    vgm.writeYm2413(0x30 + i, ((inst & 0x0F) << 4) | (vol & 0x0F));
                    return;
                }
            }

            // 2. Free slot
            for (int i = 0; i < slots; i++)
            {
                if (!slotActive[i])
                {
                    allocate(i, ch, note, vol, inst);
                    return;
                }
            }

            // 3. Voice steal
            int worst = leastImportantSlot(slots);
            if (ch <= slotMidiCh[worst])
            {
                keyOff(worst);
                allocate(worst, ch, note, vol, inst);
            }
        }

        private void allocate(int slot, int ch, int note, int vol, int inst) throws IOException
        {
            slotMidiCh[slot]  = ch;
            slotNote[slot]    = note;
            slotVolume[slot]  = vol;
            slotInst[slot]    = inst;
            slotActive[slot]  = true;

            int[] fb = fnumBlock(note);
            int fnum  = fb[0];
            int block = fb[1];

            vgm.writeYm2413(0x30 + slot, ((inst & 0x0F) << 4) | (vol & 0x0F));
            vgm.writeYm2413(0x10 + slot, fnum & 0xFF);
            // bit4=keyon, bits[3:1]=block, bit0=fnum bit8
            vgm.writeYm2413(0x20 + slot, (1 << 4) | ((block & 0x07) << 1) | ((fnum >> 8) & 0x01));
        }

        private void keyOff(int slot) throws IOException
        {
            int[] fb = fnumBlock(slotNote[slot]);
            // bit4=0 (key off), preserve block/fnum-high for release phase
            vgm.writeYm2413(0x20 + slot,
                    ((fb[1] & 0x07) << 1) | ((fb[0] >> 8) & 0x01));
            slotActive[slot]  = false;
            slotMidiCh[slot]  = -1;
            slotNote[slot]    = -1;
        }

        // ── Note-off ──────────────────────────────────────────────────────────

        private void handleNoteOff(int ch, int note) throws IOException
        {
            if (ch == 9)
            {
                drumKeyOff(note);
                return;
            }

            for (int i = 0; i < melodicSlots(); i++)
            {
                if (slotMidiCh[i] == ch && slotNote[i] == note && slotActive[i])
                {
                    keyOff(i);
                    return;
                }
            }
        }

        private void allNotesOff() throws IOException
        {
            for (int i = 0; i < MAX_MELODIC_SLOTS; i++)
            {
                if (slotActive[i]) keyOff(i);
            }
            rhythmKeyOn = 0;
            writeRhythmReg();
        }

        // ── Voice stealing ────────────────────────────────────────────────────

        private int leastImportantSlot(int slots)
        {
            int idx = 0;
            for (int i = 1; i < slots; i++)
            {
                int cur  = slotMidiCh[i];
                int best = slotMidiCh[idx];
                if (cur > best || (cur == best && slotVolume[i] > slotVolume[idx]))
                    idx = i;
            }
            return idx;
        }

        // ── Percussion / rhythm mode ──────────────────────────────────────────

        private void handleDrum(int note, int velocity) throws IOException
        {
            if (!rhythmMode)
            {
                enableRhythmMode();
            }

            int vol = velocityToVolume(velocity);
            int bit = drumBit(note);
            if (bit == 0) return;

            setRhythmVolume(bit, vol);
            rhythmKeyOn |= bit;
            writeRhythmReg();
        }

        private void drumKeyOff(int note) throws IOException
        {
            int bit = drumBit(note);
            if (bit == 0) return;
            rhythmKeyOn &= ~bit;
            writeRhythmReg();
        }

        private void enableRhythmMode() throws IOException
        {
            rhythmMode = true;
            // Silence melodic channels 6–8 (they become rhythm channels)
            for (int i = 6; i < MAX_MELODIC_SLOTS; i++)
            {
                if (slotActive[i]) keyOff(i);
            }
            // Initialise rhythm volumes to silent
            vgm.writeYm2413(0x36, 0x0F);  // BD volume = 15
            vgm.writeYm2413(0x37, 0xFF);  // HH=15, SD=15
            vgm.writeYm2413(0x38, 0xFF);  // TOM=15, CYM=15
            // Enable rhythm mode (bit5=1), keys off
            vgm.writeYm2413(0x0E, 0x20);
        }

        private int melodicSlots()
        {
            return rhythmMode ? 6 : MAX_MELODIC_SLOTS;
        }

        private static int drumBit(int note)
        {
            // Bass Drum
            if (note == 35 || note == 36) return RHY_BD;
            // Snare Drum
            if (note == 38 || note == 40) return RHY_SD;
            // Hi-Hat (closed, pedal, open)
            if (note == 42 || note == 44 || note == 46) return RHY_HH;
            // Tom-Tom
            if (note == 41 || note == 43 || note == 45 || note == 47
                    || note == 48 || note == 50) return RHY_TOM;
            // Cymbal / ride
            if (note == 49 || note == 51 || note == 52
                    || note == 55 || note == 57 || note == 59) return RHY_CYM;
            return 0; // unmapped → ignore
        }

        private void setRhythmVolume(int bit, int vol) throws IOException
        {
            switch (bit)
            {
                case RHY_BD ->
                {
                    volBD = vol;
                    vgm.writeYm2413(0x36, vol & 0x0F);
                }
                case RHY_SD ->
                {
                    volSD = vol;
                    vgm.writeYm2413(0x37, ((volHH & 0x0F) << 4) | (vol & 0x0F));
                }
                case RHY_HH ->
                {
                    volHH = vol;
                    vgm.writeYm2413(0x37, ((vol & 0x0F) << 4) | (volSD & 0x0F));
                }
                case RHY_TOM ->
                {
                    volTOM = vol;
                    vgm.writeYm2413(0x38, ((vol & 0x0F) << 4) | (volCYM & 0x0F));
                }
                case RHY_CYM ->
                {
                    volCYM = vol;
                    vgm.writeYm2413(0x38, ((volTOM & 0x0F) << 4) | (vol & 0x0F));
                }
            }
        }

        private void writeRhythmReg() throws IOException
        {
            // bit5=1 (rhythm mode enable) | active key-on bits
            vgm.writeYm2413(0x0E, 0x20 | (rhythmKeyOn & 0x1F));
        }

        // ── Frequency calculation ─────────────────────────────────────────────

        /**
         * Returns {Fnum, block} for the given MIDI note.
         * Formula: freq = Fnum * clock / (72 * 2^(19 - block))
         * Finds the smallest block [0..7] where Fnum fits in [0..511].
         */
        private static int[] fnumBlock(int note)
        {
            double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
            for (int block = 0; block <= 7; block++)
            {
                double fnum = freq * 72.0 * (1L << (19 - block)) / VgmFileWriter.YM2413_CLOCK;
                if (fnum <= 511.5)
                {
                    return new int[]{Math.max(0, (int) Math.round(fnum)), block};
                }
            }
            return new int[]{511, 7};
        }

        // ── Volume conversion ─────────────────────────────────────────────────

        /** MIDI velocity 0–127 → YM2413 volume 15–0 (inverted: 0=max, 15=silent). */
        private static int velocityToVolume(int velocity)
        {
            return 15 - Math.round(velocity * 15.0f / 127.0f);
        }
    }
}
