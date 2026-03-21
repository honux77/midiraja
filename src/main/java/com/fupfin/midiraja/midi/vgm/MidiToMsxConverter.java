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
 * Converts a MIDI file to a VGM file targeting the MSX2 combined sound hardware:
 * AY-3-8910 PSG (3 tone channels) + YM2413 OPLL (9 FM channels / 6 FM + 5 rhythm).
 *
 * <h2>Voice allocation</h2>
 * <pre>
 *   FM slots 0–8  : YM2413 melodic channels (reduced to 0–5 when rhythm mode active)
 *   PSG slots 0–2 : AY-3-8910 tone channels (overflow / additional polyphony)
 * </pre>
 * FM channels are filled first (richer timbre). PSG channels provide additional
 * polyphony as overflow. Voice stealing evicts the least important PSG note first,
 * then falls back to FM if all PSG slots are empty.
 *
 * <h2>Rhythm</h2>
 * YM2413 rhythm mode is activated automatically when MIDI channel 9 (drums) is used.
 * Drum notes are mapped to Bass Drum, Snare, Hi-Hat, Tom-Tom, and Cymbal.
 *
 * <h2>VGM format</h2>
 * VGM v1.61 (128-byte header) with both AY8910 clock (0x74) and YM2413 clock (0x10).
 * AY-3-8910 uses commands {@code 0xA0 rr dd}; YM2413 uses {@code 0x51 rr dd}.
 */
public final class MidiToMsxConverter
{
    private MidiToMsxConverter()
    {
    }

    /**
     * Converts {@code midiFile} to a MSX PSG+FM VGM file at {@code outputPath}.
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

        try (VgmFileWriter vgm = new VgmFileWriter(outputPath, VgmFileWriter.ChipMode.MSX, false))
        {
            MsxState state = new MsxState(vgm);
            state.initSilence();

            long currentTick = 0;
            long tempoUs = 500_000L;
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

    // ── Combined MSX state machine ────────────────────────────────────────────

    private static final class MsxState
    {
        // ── YM2413 GM instrument map ──────────────────────────────────────────
        private static final int[] GM_TO_YM2413 = new int[128];

        static
        {
            Arrays.fill(GM_TO_YM2413,   0,   8,  3);  // Piano
            Arrays.fill(GM_TO_YM2413,   8,  16, 12);  // Chromatic perc → Vibraphone
            Arrays.fill(GM_TO_YM2413,  16,  24,  8);  // Organ
            Arrays.fill(GM_TO_YM2413,  24,  28,  2);  // Guitar
            Arrays.fill(GM_TO_YM2413,  28,  32, 15);  // Electric guitar
            Arrays.fill(GM_TO_YM2413,  32,  36, 14);  // Acoustic bass
            Arrays.fill(GM_TO_YM2413,  36,  40, 13);  // Synth bass
            Arrays.fill(GM_TO_YM2413,  40,  56,  1);  // Strings / ensemble → Violin
            Arrays.fill(GM_TO_YM2413,  56,  60,  7);  // Brass → Trumpet
            Arrays.fill(GM_TO_YM2413,  60,  64,  9);  // French horn
            Arrays.fill(GM_TO_YM2413,  64,  72,  5);  // Reed → Clarinet
            Arrays.fill(GM_TO_YM2413,  72,  80,  4);  // Pipe → Flute
            Arrays.fill(GM_TO_YM2413,  80,  88, 10);  // Synth lead → Synthesizer
            Arrays.fill(GM_TO_YM2413,  88,  96,  1);  // Synth pad → Violin (sustained)
            Arrays.fill(GM_TO_YM2413,  96, 104, 10);  // Synth FX
            Arrays.fill(GM_TO_YM2413, 104, 112,  6);  // Ethnic → Oboe
            Arrays.fill(GM_TO_YM2413, 112, 120, 12);  // Percussive → Vibraphone
            Arrays.fill(GM_TO_YM2413, 120, 128, 10);  // Sound effects
        }

        // ── Rhythm bits (R0x0E) ───────────────────────────────────────────────
        private static final int RHY_BD  = 1 << 4;
        private static final int RHY_SD  = 1 << 3;
        private static final int RHY_TOM = 1 << 2;
        private static final int RHY_CYM = 1 << 1;
        private static final int RHY_HH  = 1 << 0;

        private final VgmFileWriter vgm;

        // ── YM2413 FM slots (0–8) ─────────────────────────────────────────────
        private static final int MAX_FM_SLOTS = 9;
        private final int[] fmMidiCh  = new int[MAX_FM_SLOTS];
        private final int[] fmNote    = new int[MAX_FM_SLOTS];
        private final int[] fmVolume  = new int[MAX_FM_SLOTS]; // 0=loud, 15=silent
        private final int[] fmInst    = new int[MAX_FM_SLOTS];
        private final boolean[] fmActive = new boolean[MAX_FM_SLOTS];

        // ── PSG (AY-3-8910) tone slots (0–2) ─────────────────────────────────
        private static final int PSG_SLOTS = 3;
        private final int[] psgMidiCh  = new int[PSG_SLOTS];
        private final int[] psgNote    = new int[PSG_SLOTS];
        private final int[] psgAmp     = new int[PSG_SLOTS]; // 0=silent, 15=max
        private final boolean[] psgActive = new boolean[PSG_SLOTS];

        // PSG mixer R7: default 0x38 = all 3 tones enabled, no noise
        private int psgMixer = 0x38;

        // ── Per-MIDI-channel program ──────────────────────────────────────────
        private final int[] channelProgram = new int[16];

        // ── YM2413 rhythm state ───────────────────────────────────────────────
        private boolean rhythmMode = false;
        private int rhythmKeyOn = 0;
        private int volBD  = 15;
        private int volSD  = 15;
        private int volTOM = 15;
        private int volCYM = 15;
        private int volHH  = 15;

        MsxState(VgmFileWriter vgm)
        {
            this.vgm = vgm;
            Arrays.fill(fmMidiCh, -1);
            Arrays.fill(fmNote, -1);
            Arrays.fill(fmVolume, 15);
            Arrays.fill(fmInst, 1);
            Arrays.fill(psgMidiCh, -1);
            Arrays.fill(psgNote, -1);
            Arrays.fill(psgAmp, 0);
        }

        void initSilence() throws IOException
        {
            // YM2413: silence all channels
            for (int i = 0; i < MAX_FM_SLOTS; i++)
            {
                vgm.writeYm2413(0x30 + i, 0x0F);
                vgm.writeYm2413(0x10 + i, 0);
                vgm.writeYm2413(0x20 + i, 0);
            }
            vgm.writeYm2413(0x0E, 0x00);

            // PSG: silence all channels, set mixer
            for (int ch = 0; ch < 3; ch++) vgm.writeAy(8 + ch, 0);
            vgm.writeAy(7, 0x38);
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

            int fmVol = fmVelocityToVolume(velocity);
            int amp   = psgVelocityToAmplitude(velocity);
            int inst  = GM_TO_YM2413[channelProgram[ch] & 0x7F];
            int fmSlots = fmMelodicSlots();

            // 1. Same note already on this MIDI channel → update volume
            for (int i = 0; i < fmSlots; i++)
            {
                if (fmMidiCh[i] == ch && fmNote[i] == note && fmActive[i])
                {
                    fmVolume[i] = fmVol;
                    vgm.writeYm2413(0x30 + i, ((inst & 0x0F) << 4) | (fmVol & 0x0F));
                    return;
                }
            }
            for (int i = 0; i < PSG_SLOTS; i++)
            {
                if (psgMidiCh[i] == ch && psgNote[i] == note && psgActive[i])
                {
                    psgAmp[i] = amp;
                    writePsgAmplitude(i, amp);
                    return;
                }
            }

            // 2. Free FM slot (preferred — richer timbre)
            for (int i = 0; i < fmSlots; i++)
            {
                if (!fmActive[i])
                {
                    fmAllocate(i, ch, note, fmVol, inst);
                    return;
                }
            }

            // 3. Free PSG slot (overflow)
            for (int i = 0; i < PSG_SLOTS; i++)
            {
                if (!psgActive[i])
                {
                    psgAllocate(i, ch, note, amp);
                    return;
                }
            }

            // 4. Voice steal: evict worst PSG note first (keep FM for quality)
            int worstPsg = worstPsgSlot();
            int worstFm  = worstFmSlot(fmSlots);

            boolean psgCandidateExists = psgActive[worstPsg];
            boolean fmCandidateExists  = fmActive[worstFm];

            if (psgCandidateExists && ch <= psgMidiCh[worstPsg])
            {
                psgKeyOff(worstPsg);
                psgAllocate(worstPsg, ch, note, amp);
            }
            else if (fmCandidateExists && ch <= fmMidiCh[worstFm])
            {
                fmKeyOff(worstFm);
                fmAllocate(worstFm, ch, note, fmVol, inst);
            }
            // else: new note less important than all → drop
        }

        // ── Note-off ──────────────────────────────────────────────────────────

        private void handleNoteOff(int ch, int note) throws IOException
        {
            if (ch == 9)
            {
                drumKeyOff(note);
                return;
            }

            for (int i = 0; i < fmMelodicSlots(); i++)
            {
                if (fmMidiCh[i] == ch && fmNote[i] == note && fmActive[i])
                {
                    fmKeyOff(i);
                    return;
                }
            }
            for (int i = 0; i < PSG_SLOTS; i++)
            {
                if (psgMidiCh[i] == ch && psgNote[i] == note && psgActive[i])
                {
                    psgKeyOff(i);
                    return;
                }
            }
        }

        private void allNotesOff() throws IOException
        {
            for (int i = 0; i < MAX_FM_SLOTS; i++) if (fmActive[i]) fmKeyOff(i);
            for (int i = 0; i < PSG_SLOTS; i++)  if (psgActive[i]) psgKeyOff(i);
            rhythmKeyOn = 0;
            if (rhythmMode) writeRhythmReg();
        }

        // ── YM2413 FM helpers ─────────────────────────────────────────────────

        private void fmAllocate(int slot, int ch, int note, int vol, int inst) throws IOException
        {
            fmMidiCh[slot]  = ch;
            fmNote[slot]    = note;
            fmVolume[slot]  = vol;
            fmInst[slot]    = inst;
            fmActive[slot]  = true;

            int[] fb    = fnumBlock(note);
            int fnum    = fb[0];
            int block   = fb[1];

            vgm.writeYm2413(0x30 + slot, ((inst & 0x0F) << 4) | (vol & 0x0F));
            vgm.writeYm2413(0x10 + slot, fnum & 0xFF);
            vgm.writeYm2413(0x20 + slot,
                    (1 << 4) | ((block & 0x07) << 1) | ((fnum >> 8) & 0x01));
        }

        private void fmKeyOff(int slot) throws IOException
        {
            int[] fb = fnumBlock(fmNote[slot]);
            vgm.writeYm2413(0x20 + slot,
                    ((fb[1] & 0x07) << 1) | ((fb[0] >> 8) & 0x01));
            fmActive[slot]  = false;
            fmMidiCh[slot]  = -1;
            fmNote[slot]    = -1;
        }

        private int worstFmSlot(int slots)
        {
            int idx = 0;
            for (int i = 1; i < slots; i++)
            {
                if (fmMidiCh[i] > fmMidiCh[idx]
                        || (fmMidiCh[i] == fmMidiCh[idx] && fmVolume[i] > fmVolume[idx]))
                    idx = i;
            }
            return idx;
        }

        private int fmMelodicSlots()
        {
            return rhythmMode ? 6 : MAX_FM_SLOTS;
        }

        // ── PSG helpers ───────────────────────────────────────────────────────

        private void psgAllocate(int slot, int ch, int note, int amp) throws IOException
        {
            psgMidiCh[slot]  = ch;
            psgNote[slot]    = note;
            psgAmp[slot]     = amp;
            psgActive[slot]  = true;

            writePsgToneFreq(slot, note);
            writePsgAmplitude(slot, amp);
        }

        private void psgKeyOff(int slot) throws IOException
        {
            psgActive[slot]  = false;
            psgMidiCh[slot]  = -1;
            psgNote[slot]    = -1;
            psgAmp[slot]     = 0;
            writePsgAmplitude(slot, 0);
        }

        private int worstPsgSlot()
        {
            int idx = 0;
            for (int i = 1; i < PSG_SLOTS; i++)
            {
                if (psgMidiCh[i] > psgMidiCh[idx]
                        || (psgMidiCh[i] == psgMidiCh[idx] && psgAmp[i] < psgAmp[idx]))
                    idx = i;
            }
            return idx;
        }

        private void writePsgToneFreq(int slot, int note) throws IOException
        {
            double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
            int tp = (int) Math.round(VgmFileWriter.AY8910_CLOCK / (16.0 * freq));
            tp = Math.max(1, Math.min(4095, tp));
            int regBase = slot * 2; // R0/R1, R2/R3, R4/R5
            vgm.writeAy(regBase,     tp & 0xFF);
            vgm.writeAy(regBase + 1, (tp >> 8) & 0x0F);
        }

        private void writePsgAmplitude(int slot, int amp) throws IOException
        {
            vgm.writeAy(8 + slot, amp & 0x0F);
        }

        // ── Percussion / rhythm mode ──────────────────────────────────────────

        private void handleDrum(int note, int velocity) throws IOException
        {
            if (!rhythmMode) enableRhythmMode();

            int vol = fmVelocityToVolume(velocity);
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
            for (int i = 6; i < MAX_FM_SLOTS; i++) if (fmActive[i]) fmKeyOff(i);
            vgm.writeYm2413(0x36, 0x0F);
            vgm.writeYm2413(0x37, 0xFF);
            vgm.writeYm2413(0x38, 0xFF);
            vgm.writeYm2413(0x0E, 0x20);
        }

        private static int drumBit(int note)
        {
            if (note == 35 || note == 36) return RHY_BD;
            if (note == 38 || note == 40) return RHY_SD;
            if (note == 42 || note == 44 || note == 46) return RHY_HH;
            if (note == 41 || note == 43 || note == 45 || note == 47
                    || note == 48 || note == 50) return RHY_TOM;
            if (note == 49 || note == 51 || note == 52
                    || note == 55 || note == 57 || note == 59) return RHY_CYM;
            return 0;
        }

        private void setRhythmVolume(int bit, int vol) throws IOException
        {
            switch (bit)
            {
                case RHY_BD  -> { volBD  = vol; vgm.writeYm2413(0x36, vol & 0x0F); }
                case RHY_SD  -> { volSD  = vol; vgm.writeYm2413(0x37, ((volHH  & 0x0F) << 4) | (vol & 0x0F)); }
                case RHY_HH  -> { volHH  = vol; vgm.writeYm2413(0x37, ((vol    & 0x0F) << 4) | (volSD  & 0x0F)); }
                case RHY_TOM -> { volTOM = vol; vgm.writeYm2413(0x38, ((vol    & 0x0F) << 4) | (volCYM & 0x0F)); }
                case RHY_CYM -> { volCYM = vol; vgm.writeYm2413(0x38, ((volTOM & 0x0F) << 4) | (vol    & 0x0F)); }
            }
        }

        private void writeRhythmReg() throws IOException
        {
            vgm.writeYm2413(0x0E, 0x20 | (rhythmKeyOn & 0x1F));
        }

        // ── Frequency helpers ─────────────────────────────────────────────────

        private static int[] fnumBlock(int note)
        {
            double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
            for (int block = 0; block <= 7; block++)
            {
                double fnum = freq * 72.0 * (1L << (19 - block)) / VgmFileWriter.YM2413_CLOCK;
                if (fnum <= 511.5)
                    return new int[]{Math.max(0, (int) Math.round(fnum)), block};
            }
            return new int[]{511, 7};
        }

        // ── Volume conversion ─────────────────────────────────────────────────

        /** YM2413: velocity 0–127 → volume 15–0 (0=max, 15=silent). */
        private static int fmVelocityToVolume(int velocity)
        {
            return 15 - Math.round(velocity * 15.0f / 127.0f);
        }

        /** PSG: velocity 0–127 → amplitude 0–15 (0=silent, 15=max). */
        private static int psgVelocityToAmplitude(int velocity)
        {
            return Math.round(velocity * 15.0f / 127.0f);
        }
    }
}
