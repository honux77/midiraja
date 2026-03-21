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
 * Converts a MIDI file to a VGM file targeting the YMF262 (OPL3) FM chip.
 *
 * <h2>Channel layout</h2>
 * <pre>
 *   Slots 0–13  : 14 melodic FM channels (2-op mode)
 *   Slots 14–17 : 4 percussion channels (round-robin, 2-op mode)
 *   Bank 0: slots  0–8  (OPL3 channels 0–8,  commands 0x5E)
 *   Bank 1: slots  9–17 (OPL3 channels 0–8 in bank 1, commands 0x5F)
 * </pre>
 *
 * <h2>OPL3 register layout (per bank)</h2>
 * <pre>
 *   0x20+op  AM/VIB/EGT/KSR/MULT (modulator/carrier)
 *   0x40+op  KSL/TL   — total level (volume); carrier TL is velocity-scaled
 *   0x60+op  AR/DR
 *   0x80+op  SL/RR
 *   0xA0+ch  F-Number low 8 bits
 *   0xB0+ch  Key-On | Block[2:0] | F-Number[9:8]
 *   0xC0+ch  0x30 (stereo) | (FB[2:0] << 1) | CNT
 *   0xE0+op  Wave select
 * </pre>
 *
 * <h2>OPL3 initialisation</h2>
 * Bank-1 register 0x05 must be set to 0x01 to enable OPL3 mode (18 channels, stereo).
 *
 * <h2>Frequency</h2>
 * f_note = F_Num × 49716 / 2^(20−block).  F_Num is 10-bit [0,1023], block is 3-bit [0,7].
 * 49716 ≈ YMF262_CLOCK / 288.
 *
 * <h2>Volume</h2>
 * Carrier TL register: 0 = maximum, 63 = silence.
 * Effective TL = clamp(patch_carrier_TL + (127 − velocity) × 63 / 127, 0, 63).
 *
 * <h2>Patches</h2>
 * All 128 General MIDI instruments are mapped to built-in OPL2-compatible patches.
 * Percussion uses a separate set of drum patches per note type.
 */
public final class MidiToOpl3Converter
{
    private MidiToOpl3Converter()
    {
    }

    /**
     * Converts {@code midiFile} to a YMF262 OPL3 VGM file at {@code outputPath}.
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

        try (VgmFileWriter vgm = new VgmFileWriter(outputPath, VgmFileWriter.ChipMode.OPL3, false))
        {
            Opl3State state = new Opl3State(vgm);
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
                    if (wholeSamples > 0) vgm.waitSamples(wholeSamples);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<MidiEvent> mergeAndSort(Sequence seq)
    {
        List<MidiEvent> all = new ArrayList<>();
        Arrays.stream(seq.getTracks())
                .flatMap(track -> IntStream.range(0, track.size()).mapToObj(track::get))
                .forEach(all::add);
        all.sort(Comparator.comparingLong(MidiEvent::getTick));
        return all;
    }

    // ── OPL3 state machine ────────────────────────────────────────────────────

    private static final class Opl3State
    {
        // ── Patch table ───────────────────────────────────────────────────────
        //
        // Format per patch (11 ints):
        //   [0] modulator AVEKM  [1] modulator KSL|TL  [2] modulator AR|DR
        //   [3] modulator SL|RR  [4] modulator WS
        //   [5] carrier   AVEKM  [6] carrier   KSL|TL  [7] carrier   AR|DR
        //   [8] carrier   SL|RR  [9] carrier   WS
        //  [10] (FB[2:0] << 1) | CNT   (stereo bit 0x30 added at write time)
        //
        // KSL|TL: upper 2 bits = KSL, lower 6 bits = TL (0=max, 63=silent)
        // Carrier TL is scaled by velocity; modulator TL is written as-is.

        // GM melodic patches (0–127)
        private static final int[][] GM_PATCHES = {
            // ── Piano ────────────────────────────────────────────────────────
            /* 000 Acoustic Grand   */ {0x21,0x4F,0xF3,0x86,0, 0xA1,0x00,0xF3,0x88,1, 0x0E},
            /* 001 Bright Acoustic  */ {0x31,0x4F,0xF3,0x76,0, 0xA1,0x00,0xF3,0x78,1, 0x0E},
            /* 002 Electric Grand   */ {0x11,0x40,0xA3,0x47,0, 0xA1,0x00,0xA3,0x56,0, 0x08},
            /* 003 Honky-tonk       */ {0x31,0x4F,0xF3,0x76,0, 0xA1,0x00,0xF3,0x78,1, 0x0C},
            /* 004 Electric Piano 1 */ {0x01,0x4A,0xA3,0x47,0, 0x01,0x00,0x93,0x56,0, 0x04},
            /* 005 Electric Piano 2 */ {0x11,0x46,0xA3,0x57,0, 0x11,0x00,0xA3,0x56,0, 0x08},
            /* 006 Harpsichord      */ {0x61,0x4D,0xF3,0x06,0, 0x71,0x00,0xF3,0x08,0, 0x0E},
            /* 007 Clavinet         */ {0x21,0x4C,0xF3,0x86,0, 0x21,0x04,0xD3,0x88,0, 0x0C},
            // ── Chromatic Perc ───────────────────────────────────────────────
            /* 008 Celesta          */ {0x01,0x4F,0xF3,0x76,0, 0x81,0x00,0xF3,0x77,0, 0x06},
            /* 009 Glockenspiel     */ {0x01,0x4F,0xF3,0x85,0, 0x81,0x00,0xF3,0x87,0, 0x04},
            /* 010 Music Box        */ {0x01,0x4A,0xF3,0x75,0, 0x01,0x00,0xF3,0x77,0, 0x02},
            /* 011 Vibraphone       */ {0x01,0x4A,0xF3,0x74,0, 0x01,0x00,0xF3,0x76,0, 0x02},
            /* 012 Marimba          */ {0x01,0x49,0xF3,0x85,0, 0x01,0x00,0xF3,0x87,0, 0x02},
            /* 013 Xylophone        */ {0x01,0x49,0xF3,0x95,0, 0x01,0x00,0xF3,0x97,0, 0x02},
            /* 014 Tubular Bell     */ {0x01,0x4F,0xF3,0x76,0, 0x01,0x00,0xF3,0x77,0, 0x04},
            /* 015 Dulcimer         */ {0x21,0x4A,0xF3,0x85,0, 0x21,0x00,0xF3,0x87,0, 0x04},
            // ── Organ ────────────────────────────────────────────────────────
            /* 016 Drawbar Organ    */ {0xB1,0x0A,0xF2,0x00,1, 0xB1,0x00,0xF2,0x00,1, 0x06},
            /* 017 Percussive Organ */ {0xB1,0x0A,0xF4,0x00,1, 0xB1,0x00,0xF4,0x00,1, 0x04},
            /* 018 Rock Organ       */ {0xB1,0x0A,0xF2,0x0A,1, 0xB1,0x00,0xF2,0x00,1, 0x06},
            /* 019 Church Organ     */ {0xA1,0x16,0xF3,0x00,2, 0x21,0x00,0xF3,0x00,2, 0x06},
            /* 020 Reed Organ       */ {0x71,0x20,0xF3,0x00,0, 0x21,0x00,0xF3,0x00,0, 0x04},
            /* 021 Accordion        */ {0x21,0x22,0xD2,0x05,0, 0xA2,0x00,0xD2,0x00,0, 0x06},
            /* 022 Harmonica        */ {0x31,0x1C,0xF3,0x00,0, 0x21,0x00,0xF3,0x00,0, 0x06},
            /* 023 Bandoneon        */ {0x21,0x22,0xD2,0x05,0, 0xA2,0x00,0xD2,0x00,0, 0x04},
            // ── Guitar ───────────────────────────────────────────────────────
            /* 024 Nylon Guitar     */ {0xA1,0x4C,0xF3,0x86,0, 0x21,0x05,0xF3,0x88,0, 0x04},
            /* 025 Steel Guitar     */ {0x21,0x48,0xF3,0x96,0, 0x21,0x00,0xF3,0x98,0, 0x04},
            /* 026 Jazz Guitar      */ {0x31,0x48,0xD3,0x96,0, 0x21,0x00,0xD3,0x98,0, 0x08},
            /* 027 Clean Guitar     */ {0x21,0x48,0xF3,0x86,0, 0x21,0x00,0xF3,0x88,0, 0x06},
            /* 028 Muted Guitar     */ {0x31,0x4C,0xF3,0x98,0, 0x21,0x00,0xF3,0x99,0, 0x04},
            /* 029 Overdriven Gtr   */ {0x31,0x49,0xF3,0x86,0, 0xA1,0x00,0xF3,0x88,0, 0x08},
            /* 030 Distortion Gtr   */ {0x31,0x4A,0xF3,0x97,0, 0xA1,0x00,0xF3,0x98,0, 0x0A},
            /* 031 Guitar Harmonics */ {0x21,0x4C,0xF3,0x77,1, 0x21,0x04,0xF3,0x78,1, 0x04},
            // ── Bass ─────────────────────────────────────────────────────────
            /* 032 Acoustic Bass    */ {0x21,0x19,0xF4,0x87,0, 0x21,0x00,0xF3,0x86,0, 0x04},
            /* 033 Finger Bass      */ {0x21,0x1A,0xF4,0x97,0, 0x21,0x00,0xF3,0x96,0, 0x04},
            /* 034 Pick Bass        */ {0x31,0x1A,0xF4,0x97,0, 0x21,0x00,0xF3,0x96,0, 0x06},
            /* 035 Fretless Bass    */ {0x21,0x19,0xF3,0x87,0, 0x21,0x00,0xF3,0x86,0, 0x02},
            /* 036 Slap Bass 1      */ {0x21,0x1A,0xF4,0x87,0, 0xA1,0x00,0xF3,0x88,0, 0x04},
            /* 037 Slap Bass 2      */ {0x31,0x1C,0xF4,0x97,0, 0xA1,0x00,0xF3,0x98,0, 0x06},
            /* 038 Synth Bass 1     */ {0x21,0x15,0xF4,0x87,0, 0x21,0x00,0xF3,0x86,0, 0x06},
            /* 039 Synth Bass 2     */ {0x31,0x17,0xF4,0x87,0, 0x31,0x00,0xF3,0x86,0, 0x06},
            // ── Strings ──────────────────────────────────────────────────────
            /* 040 Violin           */ {0x71,0x52,0xA3,0x77,0, 0x21,0x17,0xA3,0x78,0, 0x04},
            /* 041 Viola            */ {0x71,0x54,0xA3,0x77,0, 0x21,0x1A,0xA3,0x78,0, 0x04},
            /* 042 Cello            */ {0x61,0x56,0x83,0x77,0, 0x21,0x1A,0x83,0x78,0, 0x04},
            /* 043 Contrabass       */ {0x61,0x58,0x83,0x77,0, 0x21,0x1C,0x83,0x78,0, 0x04},
            /* 044 Tremolo Strings  */ {0x71,0x52,0x73,0x77,0, 0x21,0x17,0x73,0x78,0, 0x04},
            /* 045 Pizzicato Str    */ {0x01,0x4A,0xF3,0x76,0, 0x01,0x00,0xF3,0x77,0, 0x04},
            /* 046 Orchestral Harp  */ {0x21,0x4C,0xF3,0x85,0, 0x21,0x00,0xF3,0x88,0, 0x04},
            /* 047 Timpani          */ {0x01,0x4F,0xF3,0xA6,0, 0x01,0x00,0xF3,0xA8,0, 0x04},
            // ── Ensemble ─────────────────────────────────────────────────────
            /* 048 String Ensemble1 */ {0x61,0x51,0x73,0x56,0, 0x21,0x17,0x73,0x57,0, 0x04},
            /* 049 String Ensemble2 */ {0x61,0x53,0x63,0x56,0, 0x21,0x17,0x63,0x57,0, 0x04},
            /* 050 Synth Strings 1  */ {0x61,0x51,0x73,0x56,2, 0x21,0x17,0x73,0x57,2, 0x04},
            /* 051 Synth Strings 2  */ {0x61,0x53,0x63,0x56,2, 0x21,0x17,0x63,0x57,2, 0x04},
            /* 052 Choir Aahs       */ {0x61,0x51,0x63,0x44,0, 0x21,0x17,0x63,0x45,0, 0x04},
            /* 053 Voice Oohs       */ {0x61,0x53,0x73,0x44,0, 0x21,0x17,0x73,0x45,0, 0x04},
            /* 054 Synth Voice      */ {0x61,0x51,0x73,0x34,0, 0x21,0x10,0x73,0x45,0, 0x04},
            /* 055 Orchestra Hit    */ {0x01,0x4F,0xF4,0xA5,0, 0x01,0x00,0xF3,0xA6,0, 0x06},
            // ── Brass ────────────────────────────────────────────────────────
            /* 056 Trumpet          */ {0x21,0x1D,0xF4,0x97,0, 0x21,0x00,0xF3,0x67,0, 0x06},
            /* 057 Trombone         */ {0x21,0x1E,0xF4,0x87,0, 0x21,0x00,0xF3,0x67,0, 0x04},
            /* 058 Tuba             */ {0x21,0x1F,0xF4,0x87,0, 0x21,0x00,0xF3,0x77,0, 0x04},
            /* 059 Muted Trumpet    */ {0x31,0x1D,0xF4,0x97,0, 0x21,0x00,0xF3,0x67,0, 0x06},
            /* 060 French Horn      */ {0x21,0x1B,0xF3,0x77,0, 0x21,0x00,0xF3,0x67,0, 0x06},
            /* 061 Brass Section    */ {0x21,0x1D,0xF4,0x97,0, 0x21,0x00,0xF3,0x57,0, 0x06},
            /* 062 Synth Brass 1    */ {0x21,0x1D,0xF4,0x97,2, 0x21,0x00,0xF3,0x67,2, 0x06},
            /* 063 Synth Brass 2    */ {0x31,0x1D,0xF4,0x97,2, 0x21,0x00,0xF3,0x67,2, 0x06},
            // ── Reed ─────────────────────────────────────────────────────────
            /* 064 Soprano Sax      */ {0x31,0x47,0xD3,0x55,0, 0xA1,0x04,0xD3,0x37,0, 0x06},
            /* 065 Alto Sax         */ {0x31,0x47,0xD3,0x65,0, 0xA1,0x04,0xD3,0x47,0, 0x06},
            /* 066 Tenor Sax        */ {0x31,0x47,0xC3,0x65,0, 0xA1,0x04,0xC3,0x47,0, 0x06},
            /* 067 Baritone Sax     */ {0x31,0x47,0xC3,0x75,0, 0xA1,0x04,0xC3,0x57,0, 0x06},
            /* 068 Oboe             */ {0x21,0x56,0xE3,0x04,0, 0xA1,0x04,0xD3,0x05,0, 0x08},
            /* 069 English Horn     */ {0x21,0x56,0xD3,0x04,0, 0xA1,0x04,0xD3,0x05,0, 0x06},
            /* 070 Bassoon          */ {0x71,0x58,0x83,0x74,0, 0x21,0x1A,0x83,0x75,0, 0x04},
            /* 071 Clarinet         */ {0x21,0x50,0xC3,0x15,0, 0xA1,0x04,0xD3,0x15,0, 0x08},
            // ── Pipe ─────────────────────────────────────────────────────────
            /* 072 Piccolo          */ {0x21,0x40,0xE3,0x04,0, 0xA1,0x00,0xD3,0x05,0, 0x08},
            /* 073 Flute            */ {0x21,0x40,0xD3,0x04,0, 0xA1,0x00,0xD3,0x05,0, 0x06},
            /* 074 Recorder         */ {0x21,0x40,0xC3,0x14,0, 0xA1,0x00,0xC3,0x15,0, 0x06},
            /* 075 Pan Flute        */ {0x21,0x42,0xD3,0x04,0, 0xA1,0x00,0xD3,0x05,0, 0x06},
            /* 076 Blown Bottle     */ {0x21,0x44,0xD3,0x04,0, 0xA1,0x00,0xD3,0x05,0, 0x04},
            /* 077 Shakuhachi       */ {0x21,0x44,0xD3,0x14,0, 0xA1,0x00,0xD3,0x15,0, 0x04},
            /* 078 Whistle          */ {0x21,0x42,0xC3,0x14,0, 0xA1,0x00,0xC3,0x15,0, 0x06},
            /* 079 Ocarina          */ {0x21,0x42,0xC3,0x14,0, 0xA1,0x00,0xC3,0x15,0, 0x04},
            // ── Synth Lead ───────────────────────────────────────────────────
            /* 080 Square Lead      */ {0x71,0x40,0xF3,0x00,0, 0x71,0x00,0xF3,0x00,0, 0x06},
            /* 081 Sawtooth Lead    */ {0x21,0x43,0xF3,0x07,0, 0xA1,0x00,0xF3,0x07,0, 0x0A},
            /* 082 Calliope Lead    */ {0x21,0x40,0xF3,0x14,0, 0xA1,0x00,0xF3,0x15,0, 0x06},
            /* 083 Chiff Lead       */ {0x21,0x43,0xF3,0x07,0, 0xA1,0x00,0xF3,0x07,0, 0x08},
            /* 084 Charang Lead     */ {0x21,0x43,0xF3,0x17,0, 0xA1,0x00,0xF3,0x17,0, 0x0A},
            /* 085 Voice Lead       */ {0x61,0x51,0x73,0x34,0, 0x21,0x10,0x73,0x35,0, 0x04},
            /* 086 Fifth Lead       */ {0x21,0x44,0xF3,0x07,0, 0x21,0x00,0xF3,0x07,0, 0x08},
            /* 087 Bass+Lead        */ {0x21,0x43,0xF3,0x07,0, 0xA1,0x00,0xF3,0x07,0, 0x0C},
            // ── Synth Pad ────────────────────────────────────────────────────
            /* 088 New Age Pad      */ {0x61,0x51,0x73,0x56,2, 0x21,0x17,0x73,0x47,2, 0x04},
            /* 089 Warm Pad         */ {0x61,0x51,0x73,0x66,0, 0x21,0x17,0x73,0x57,0, 0x04},
            /* 090 Polysynth Pad    */ {0x61,0x51,0x73,0x56,0, 0x21,0x17,0x73,0x57,0, 0x04},
            /* 091 Choir Pad        */ {0x61,0x51,0x63,0x56,0, 0x21,0x17,0x63,0x57,0, 0x04},
            /* 092 Bowed Pad        */ {0x61,0x51,0x73,0x46,2, 0x21,0x17,0x73,0x47,2, 0x04},
            /* 093 Metallic Pad     */ {0x21,0x44,0xF3,0x57,1, 0xA1,0x00,0xF3,0x57,1, 0x06},
            /* 094 Halo Pad         */ {0x61,0x51,0x63,0x56,2, 0x21,0x17,0x63,0x57,2, 0x04},
            /* 095 Sweep Pad        */ {0x61,0x53,0x73,0x56,2, 0x21,0x17,0x73,0x57,2, 0x04},
            // ── Synth FX ─────────────────────────────────────────────────────
            /* 096 Rain FX          */ {0x21,0x44,0xD3,0x07,0, 0xA1,0x00,0xD3,0x07,0, 0x06},
            /* 097 Soundtrack FX    */ {0x61,0x51,0x73,0x56,0, 0x21,0x17,0x73,0x57,0, 0x04},
            /* 098 Crystal FX       */ {0x01,0x4F,0xF3,0x75,0, 0x01,0x00,0xF3,0x77,0, 0x02},
            /* 099 Atmosphere FX    */ {0x61,0x51,0x73,0x56,2, 0x21,0x17,0x73,0x47,2, 0x04},
            /* 100 Brightness FX    */ {0x21,0x40,0xD3,0x04,0, 0xA1,0x00,0xD3,0x05,0, 0x06},
            /* 101 Goblin FX        */ {0x21,0x43,0xF3,0x07,0, 0xA1,0x00,0xF3,0x07,0, 0x08},
            /* 102 Echoes FX        */ {0x21,0x44,0xD3,0x07,0, 0xA1,0x00,0xD3,0x07,0, 0x04},
            /* 103 Sci-fi FX        */ {0x21,0x43,0xF3,0x07,0, 0xA1,0x00,0xF3,0x07,0, 0x0A},
            // ── Ethnic ───────────────────────────────────────────────────────
            /* 104 Sitar            */ {0xA1,0x4C,0xF3,0x86,0, 0x21,0x05,0xF3,0x88,0, 0x04},
            /* 105 Banjo            */ {0x21,0x4A,0xF3,0x95,0, 0x21,0x00,0xF3,0x97,0, 0x06},
            /* 106 Shamisen         */ {0x21,0x4C,0xF3,0x86,0, 0x21,0x04,0xF3,0x88,0, 0x04},
            /* 107 Koto             */ {0x21,0x4B,0xF3,0x86,0, 0x21,0x04,0xF3,0x87,0, 0x04},
            /* 108 Kalimba          */ {0x01,0x4A,0xF3,0x75,0, 0x01,0x00,0xF3,0x77,0, 0x02},
            /* 109 Bagpipe          */ {0x21,0x22,0xD2,0x05,0, 0xA2,0x00,0xD2,0x00,0, 0x06},
            /* 110 Fiddle           */ {0x71,0x52,0xA3,0x77,0, 0x21,0x17,0xA3,0x78,0, 0x04},
            /* 111 Shanai           */ {0x21,0x50,0xD3,0x05,0, 0xA1,0x04,0xD3,0x05,0, 0x08},
            // ── Percussive ───────────────────────────────────────────────────
            /* 112 Tinkle Bell      */ {0x01,0x4F,0xF3,0x85,0, 0x01,0x00,0xF3,0x87,0, 0x02},
            /* 113 Agogo            */ {0x01,0x4E,0xF3,0x95,0, 0x01,0x00,0xF3,0x97,0, 0x02},
            /* 114 Steel Drums      */ {0x21,0x48,0xF3,0x86,0, 0x21,0x00,0xF3,0x88,0, 0x04},
            /* 115 Woodblock        */ {0x01,0x4C,0xF3,0x96,0, 0x01,0x00,0xF3,0x98,0, 0x02},
            /* 116 Taiko Drum       */ {0x01,0x4F,0xF3,0xA6,0, 0x01,0x00,0xF3,0xA8,0, 0x04},
            /* 117 Melodic Tom      */ {0x01,0x4F,0xF3,0xA5,0, 0x01,0x00,0xF3,0xA7,0, 0x04},
            /* 118 Synth Drum       */ {0x01,0x4F,0xF4,0xA6,0, 0x01,0x00,0xF4,0xA8,0, 0x04},
            /* 119 Reverse Cymbal   */ {0x01,0x4F,0x30,0xA6,0, 0x01,0x00,0x30,0xA8,0, 0x02},
            // ── Sound Effects ────────────────────────────────────────────────
            /* 120 Guitar Fret Noise*/ {0x21,0x4C,0xF3,0x86,0, 0x21,0x00,0xF3,0x87,0, 0x04},
            /* 121 Breath Noise     */ {0x21,0x40,0xC3,0x14,0, 0xA1,0x00,0xC3,0x15,0, 0x04},
            /* 122 Seashore         */ {0x21,0x44,0xD3,0x07,0, 0xA1,0x00,0xD3,0x07,0, 0x04},
            /* 123 Bird Tweet       */ {0x21,0x42,0xC3,0x14,0, 0xA1,0x00,0xC3,0x15,0, 0x06},
            /* 124 Telephone Ring   */ {0x21,0x4F,0xF3,0x76,0, 0x21,0x00,0xF3,0x77,0, 0x04},
            /* 125 Helicopter       */ {0x21,0x43,0xF3,0x07,0, 0xA1,0x00,0xF3,0x07,0, 0x08},
            /* 126 Applause         */ {0x21,0x44,0xD3,0x07,0, 0xA1,0x00,0xD3,0x07,0, 0x04},
            /* 127 Gunshot          */ {0x01,0x4F,0xF4,0xA6,0, 0x01,0x00,0xF4,0xA8,0, 0x04},
        };

        // Drum patches {m_avekm, m_ksl_tl, m_ar_dr, m_sl_rr, m_ws,
        //               c_avekm, c_ksl_tl, c_ar_dr, c_sl_rr, c_ws, fb_cnt}
        private static final int[][] DRUM_PATCHES = {
            /* 0 Kick    */ {0x07,0x4F,0xFF,0x97,0, 0x05,0x00,0xFF,0xA7,0, 0x04},
            /* 1 Snare   */ {0x21,0x43,0xF4,0x97,0, 0x01,0x00,0xF4,0x98,0, 0x04},
            /* 2 Hi-Hat  */ {0x01,0x4F,0xF4,0xB5,0, 0x01,0x00,0xF4,0xB6,0, 0x02},
            /* 3 Tom     */ {0x01,0x4F,0xF4,0xA5,0, 0x01,0x00,0xF4,0xA7,0, 0x04},
            /* 4 Cymbal  */ {0x01,0x4F,0xD4,0xB5,0, 0x01,0x00,0xD4,0xB6,0, 0x02},
        };

        // MIDI note → {drum_patch_index, midi_note_for_pitch}
        // Notes outside [0..127] → unmapped (index -1)
        private static final int[] DRUM_PATCH_IDX  = new int[128];
        private static final int[] DRUM_PITCH_NOTE = new int[128];

        static
        {
            Arrays.fill(DRUM_PATCH_IDX, -1);
            Arrays.fill(DRUM_PITCH_NOTE, 60);

            // Bass Drum
            for (int n : new int[]{35, 36})  { DRUM_PATCH_IDX[n] = 0; DRUM_PITCH_NOTE[n] = 36; }
            // Snare
            for (int n : new int[]{38, 40})  { DRUM_PATCH_IDX[n] = 1; DRUM_PITCH_NOTE[n] = 60; }
            // Hand Clap / Rim shot
            for (int n : new int[]{37, 39})  { DRUM_PATCH_IDX[n] = 1; DRUM_PITCH_NOTE[n] = 64; }
            // Hi-Hat closed / pedal
            for (int n : new int[]{42, 44})  { DRUM_PATCH_IDX[n] = 2; DRUM_PITCH_NOTE[n] = 80; }
            // Hi-Hat open
            DRUM_PATCH_IDX[46] = 2; DRUM_PITCH_NOTE[46] = 76;
            // Toms
            int[] tomNotes = {41, 43, 45, 47, 48, 50};
            int[] tomPitch = {41, 45, 48, 52, 55, 60};
            for (int i = 0; i < tomNotes.length; i++)
            {
                DRUM_PATCH_IDX[tomNotes[i]] = 3;
                DRUM_PITCH_NOTE[tomNotes[i]] = tomPitch[i];
            }
            // Cymbal / Ride
            for (int n : new int[]{49, 51, 52, 55, 57, 59})
            {
                DRUM_PATCH_IDX[n] = 4; DRUM_PITCH_NOTE[n] = 84;
            }
            // Cowbell / misc percussion → snare-like
            for (int n : new int[]{54, 56, 58, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70,
                                   71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81})
            {
                DRUM_PATCH_IDX[n] = 3; DRUM_PITCH_NOTE[n] = 60;
            }
        }

        // ── OPL3 operator offset table ────────────────────────────────────────
        // Channel index within a bank (0–8) → operator register offsets
        private static final int[] CH_TO_MOD_OFF = {0x00, 0x01, 0x02, 0x08, 0x09, 0x0A, 0x10, 0x11, 0x12};
        private static final int[] CH_TO_CAR_OFF = {0x03, 0x04, 0x05, 0x0B, 0x0C, 0x0D, 0x13, 0x14, 0x15};

        // ── Slot layout ───────────────────────────────────────────────────────
        private static final int MELODIC_SLOTS = 14;
        private static final int DRUM_SLOTS    = 4;
        private static final int TOTAL_SLOTS   = MELODIC_SLOTS + DRUM_SLOTS; // 18

        private final VgmFileWriter vgm;

        // Melodic slot state
        private final int[] slotMidiCh   = new int[MELODIC_SLOTS];
        private final int[] slotNote     = new int[MELODIC_SLOTS];
        private final int[] slotVelocity = new int[MELODIC_SLOTS];
        private final int[] slotPatch    = new int[MELODIC_SLOTS]; // index into GM_PATCHES
        private final boolean[] slotActive = new boolean[MELODIC_SLOTS];

        // Per-MIDI-channel program
        private final int[] channelProgram = new int[16];

        // Drum slots: round-robin
        private int drumRoundRobin = 0;

        Opl3State(VgmFileWriter vgm)
        {
            this.vgm = vgm;
            Arrays.fill(slotMidiCh, -1);
            Arrays.fill(slotNote, -1);
        }

        void initSilence() throws IOException
        {
            // Enable OPL3 mode (NEW2 bit in bank-1 register 0x05)
            vgm.writeOpl3Bank1(0x05, 0x01);

            // Key-off all channels
            for (int slot = 0; slot < TOTAL_SLOTS; slot++)
            {
                int ch = slot % 9;
                // Set C0: stereo + no feedback
                writeChReg(slot, 0xC0, 0x30);
                // Key off
                writeChReg(slot, 0xB0, 0x00);
            }
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
                case ShortMessage.NOTE_OFF    -> handleNoteOff(ch, d1);
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

            int patchIdx = channelProgram[ch] & 0x7F;

            // 1. Same note already playing → retrigger with new velocity
            for (int i = 0; i < MELODIC_SLOTS; i++)
            {
                if (slotMidiCh[i] == ch && slotNote[i] == note && slotActive[i])
                {
                    keyOff(i);
                    keyOn(i, patchIdx, note, velocity);
                    return;
                }
            }

            // 2. Free slot
            for (int i = 0; i < MELODIC_SLOTS; i++)
            {
                if (!slotActive[i])
                {
                    slotMidiCh[i] = ch;
                    keyOn(i, patchIdx, note, velocity);
                    return;
                }
            }

            // 3. Voice steal: highest MIDI channel, then lowest velocity
            int worst = worstSlot();
            if (ch <= slotMidiCh[worst])
            {
                keyOff(worst);
                slotMidiCh[worst] = ch;
                keyOn(worst, patchIdx, note, velocity);
            }
        }

        private void keyOn(int slot, int patchIdx, int note, int velocity) throws IOException
        {
            int[] p = GM_PATCHES[patchIdx];
            slotNote[slot]     = note;
            slotVelocity[slot] = velocity;
            slotPatch[slot]    = patchIdx;
            slotActive[slot]   = true;

            writePatch(slot, p, velocity);

            int[] fb = fnumBlock(note);
            int fnum = fb[0], block = fb[1];
            writeChReg(slot, 0xA0, fnum & 0xFF);
            writeChReg(slot, 0xB0, 0x20 | ((block & 7) << 2) | ((fnum >> 8) & 3)); // key-on + block + fnum[9:8]
        }

        private void keyOff(int slot) throws IOException
        {
            int[] fb = fnumBlock(slotNote[slot]);
            writeChReg(slot, 0xB0, ((fb[1] & 7) << 2) | ((fb[0] >> 8) & 3)); // key-off
            slotActive[slot]  = false;
            slotMidiCh[slot]  = -1;
            slotNote[slot]    = -1;
        }

        private int worstSlot()
        {
            int idx = 0;
            for (int i = 1; i < MELODIC_SLOTS; i++)
            {
                if (slotMidiCh[i] > slotMidiCh[idx]
                        || (slotMidiCh[i] == slotMidiCh[idx]
                            && slotVelocity[i] < slotVelocity[idx]))
                    idx = i;
            }
            return idx;
        }

        // ── Note-off ──────────────────────────────────────────────────────────

        private void handleNoteOff(int ch, int note) throws IOException
        {
            if (ch == 9) return; // drum key-offs handled by round-robin expiry

            for (int i = 0; i < MELODIC_SLOTS; i++)
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
            for (int i = 0; i < MELODIC_SLOTS; i++) if (slotActive[i]) keyOff(i);
            // Silence drum slots
            for (int i = MELODIC_SLOTS; i < TOTAL_SLOTS; i++) writeChReg(i, 0xB0, 0x00);
        }

        // ── Percussion ────────────────────────────────────────────────────────

        private void handleDrum(int note, int velocity) throws IOException
        {
            if (note < 0 || note > 127) return;
            int patchIdx = DRUM_PATCH_IDX[note];
            if (patchIdx < 0) return;

            int pitchNote = DRUM_PITCH_NOTE[note];
            int slot      = MELODIC_SLOTS + (drumRoundRobin % DRUM_SLOTS);
            drumRoundRobin++;

            int[] p = DRUM_PATCHES[patchIdx];
            writePatch(slot, p, velocity);

            int[] fb  = fnumBlock(pitchNote);
            int fnum  = fb[0], block = fb[1];
            writeChReg(slot, 0xA0, fnum & 0xFF);
            writeChReg(slot, 0xB0, 0x20 | ((block & 7) << 2) | ((fnum >> 8) & 3));
        }

        // ── Patch write ───────────────────────────────────────────────────────

        private void writePatch(int slot, int[] p, int velocity) throws IOException
        {
            int modOff = CH_TO_MOD_OFF[slot % 9];
            int carOff = CH_TO_CAR_OFF[slot % 9];

            // Carrier TL: velocity controls volume directly (TL=0 max, TL=63 silent).
            // Patch carrier TL is ignored for loudness — only KSL bits are preserved.
            // Max attenuation capped at 40 so even velocity=0 notes are audible.
            int carTl    = (127 - velocity) * 40 / 127;
            int carKslTl = (p[6] & 0xC0) | carTl;

            // Modulator
            writeOpReg(slot, 0x20 + modOff, p[0]);
            writeOpReg(slot, 0x40 + modOff, p[1]);   // mod TL as-is
            writeOpReg(slot, 0x60 + modOff, p[2]);
            writeOpReg(slot, 0x80 + modOff, p[3]);
            writeOpReg(slot, 0xE0 + modOff, p[4]);
            // Carrier
            writeOpReg(slot, 0x20 + carOff, p[5]);
            writeOpReg(slot, 0x40 + carOff, carKslTl); // velocity-scaled TL
            writeOpReg(slot, 0x60 + carOff, p[7]);
            writeOpReg(slot, 0x80 + carOff, p[8]);
            writeOpReg(slot, 0xE0 + carOff, p[9]);
            // Channel C0: 0x30 = stereo, plus feedback & connection
            writeChReg(slot, 0xC0, 0x30 | (p[10] & 0x0F));
        }

        // ── OPL3 register routing ─────────────────────────────────────────────

        /** Writes an operator register (bank-routed by slot). */
        private void writeOpReg(int slot, int reg, int value) throws IOException
        {
            if (slot < 9) vgm.writeOpl3(reg, value);
            else          vgm.writeOpl3Bank1(reg, value);
        }

        /** Writes a channel register (A0/B0/C0 + channel offset, bank-routed by slot). */
        private void writeChReg(int slot, int regBase, int value) throws IOException
        {
            int reg = regBase + (slot % 9);
            if (slot < 9) vgm.writeOpl3(reg, value);
            else          vgm.writeOpl3Bank1(reg, value);
        }

        // ── Frequency ────────────────────────────────────────────────────────

        /**
         * Returns {F_Num, block} for a MIDI note.
         * f_note = F_Num × 49716 / 2^(20-block).  F_Num in [0,1023], block in [0,7].
         */
        private static int[] fnumBlock(int note)
        {
            double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
            for (int block = 0; block <= 7; block++)
            {
                double fnum = freq * (1L << (20 - block)) / 49716.0;
                if (fnum <= 1023.5)
                    return new int[]{Math.max(0, (int) Math.round(fnum)), block};
            }
            return new int[]{1023, 7};
        }
    }
}
