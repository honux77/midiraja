/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.midi.vgm;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Writes a VGM (Video Game Music) file.
 *
 * <p>Supports three chip modes:
 * <ul>
 *   <li><b>Dual AY-3-8910 / YM2149F</b> (default): VGM v1.61, 128-byte header.
 *       Primary chip uses command {@code 0xA0 rr dd}; secondary chip uses
 *       {@code 0xA0 (rr|0x80) dd}. Clock: 1,789,772 Hz (MSX NTSC).</li>
 *   <li><b>Dual SN76489</b>: VGM v1.50, 64-byte header.
 *       Primary chip uses {@code 0x50 dd}; secondary uses {@code 0x30 dd}.
 *       Clock: 3,579,545 Hz (NTSC).</li>
 *   <li><b>YM2413 (OPLL)</b>: VGM v1.50, 64-byte header.
 *       Uses command {@code 0x51 rr dd}. Clock: 3,579,545 Hz (MSX NTSC).</li>
 * </ul>
 *
 * <p>Timing reference: 44100 Hz (VGM standard sample rate).
 */
public class VgmFileWriter implements AutoCloseable
{
    // ── Chip clock constants ──────────────────────────────────────────────────

    /** AY-3-8910 / YM2149F clock for MSX NTSC (Hz). */
    public static final int AY8910_CLOCK = 1_789_772;

    /** AY-3-8910 clock with bit 31 set — signals dual-chip mode to VGM players. */
    public static final long AY8910_CLOCK_DUAL = AY8910_CLOCK | 0x80000000L;

    /** SN76489 NTSC clock frequency (Sega Master System standard). */
    public static final int SN76489_CLOCK = 3_579_545;

    /** SN76489 clock with bit 31 set — signals dual-chip mode. */
    public static final long SN76489_CLOCK_DUAL = SN76489_CLOCK | 0x80000000L;

    /** YM2413 (OPLL) clock for MSX NTSC (Hz). */
    public static final int YM2413_CLOCK = 3_579_545;

    /** VGM internal sample rate used for all timing. */
    public static final int VGM_SAMPLE_RATE = 44100;

    // ── Chip mode ─────────────────────────────────────────────────────────────

    /** Selects the target chip configuration for VGM output. */
    public enum ChipMode
    {
        /** Dual AY-3-8910 / YM2149F (VGM v1.61, 128-byte header). */
        AY8910,
        /** Dual SN76489 (VGM v1.50, 64-byte header). */
        SN76489,
        /** YM2413 OPLL — single chip (VGM v1.50, 64-byte header). */
        YM2413,
        /**
         * MSX combined: single AY-3-8910 PSG + YM2413 OPLL (VGM v1.61, 128-byte header).
         * Both AY8910 clock (0x74) and YM2413 clock (0x10) are written to the header.
         */
        MSX
    }

    // ── Header layout constants ───────────────────────────────────────────────

    /** Header size for VGM v1.61 (AY-3-8910): 128 bytes, data starts at 0x80. */
    private static final int HEADER_SIZE_AY = 0x80;

    /** Header size for VGM v1.50 (SN76489 / YM2413): 64 bytes, data starts at 0x40. */
    private static final int HEADER_SIZE_V150 = 0x40;

    // ── State ─────────────────────────────────────────────────────────────────

    private final RandomAccessFile file;
    private long totalSamples = 0;
    private final ChipMode chipMode;
    private final boolean dualChip;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Opens a dual AY-3-8910 VGM writer (VGM v1.61, 6 tone channels).
     * This is the default mode matching the project's AY-3-8910 emulation.
     */
    public VgmFileWriter(String path) throws IOException
    {
        this(path, true, true);
    }

    /**
     * Opens a VGM writer with explicit chip selection (AY-3-8910 or SN76489).
     *
     * @param path      output file path
     * @param useAy8910 {@code true} → AY-3-8910 (VGM v1.61);
     *                  {@code false} → SN76489 (VGM v1.50)
     * @param dualChip  {@code true} → two chips; {@code false} → single chip
     */
    public VgmFileWriter(String path, boolean useAy8910, boolean dualChip) throws IOException
    {
        this(path, useAy8910 ? ChipMode.AY8910 : ChipMode.SN76489, dualChip);
    }

    /**
     * Opens a VGM writer with explicit {@link ChipMode}.
     *
     * @param path     output file path
     * @param mode     target chip
     * @param dualChip {@code true} → two chips (ignored for {@link ChipMode#YM2413})
     */
    public VgmFileWriter(String path, ChipMode mode, boolean dualChip) throws IOException
    {
        this.chipMode = mode;
        this.dualChip = dualChip;
        file = new RandomAccessFile(path, "rw");
        file.setLength(0);
        int headerSize = (mode == ChipMode.AY8910) ? HEADER_SIZE_AY : HEADER_SIZE_V150;
        file.write(new byte[headerSize]); // placeholder
    }

    // ── Sample counter ────────────────────────────────────────────────────────

    /** Returns the total number of VGM samples accumulated so far. */
    public long getTotalSamples()
    {
        return totalSamples;
    }

    // ── AY-3-8910 chip commands ───────────────────────────────────────────────

    /**
     * Writes an AY-3-8910 register write for chip 0: {@code 0xA0 reg data}.
     *
     * @param reg  AY-3-8910 register address (0x00–0x0D)
     * @param data value to write
     */
    public void writeAy(int reg, int data) throws IOException
    {
        file.writeByte(0xA0);
        file.writeByte(reg & 0x0F);
        file.writeByte(data & 0xFF);
    }

    /**
     * Writes an AY-3-8910 register write for chip 1: {@code 0xA0 (reg|0x80) data}.
     *
     * @param reg  AY-3-8910 register address (0x00–0x0D)
     * @param data value to write
     */
    public void writeAy2(int reg, int data) throws IOException
    {
        file.writeByte(0xA0);
        file.writeByte((reg & 0x0F) | 0x80);
        file.writeByte(data & 0xFF);
    }

    // ── SN76489 chip commands ─────────────────────────────────────────────────

    /** Writes an SN76489 register write for chip 0: {@code 0x50 data}. */
    public void writePsg(int data) throws IOException
    {
        file.writeByte(0x50);
        file.writeByte(data & 0xFF);
    }

    /** Writes an SN76489 register write for chip 1: {@code 0x30 data}. */
    public void writePsg2(int data) throws IOException
    {
        file.writeByte(0x30);
        file.writeByte(data & 0xFF);
    }

    // ── YM2413 chip commands ──────────────────────────────────────────────────

    /**
     * Writes a YM2413 register write: {@code 0x51 reg data}.
     *
     * @param reg  YM2413 register address
     * @param data value to write
     */
    public void writeYm2413(int reg, int data) throws IOException
    {
        file.writeByte(0x51);
        file.writeByte(reg & 0xFF);
        file.writeByte(data & 0xFF);
    }

    // ── Timing ────────────────────────────────────────────────────────────────

    /**
     * Emits wait commands totalling {@code samples} VGM samples (at 44100 Hz).
     * Uses the {@code 0x61 nn nn} two-byte wait command, chunked to 65535 at a time.
     */
    public void waitSamples(long samples) throws IOException
    {
        if (samples <= 0) return;
        totalSamples += samples;
        while (samples > 0)
        {
            long chunk = Math.min(samples, 65535L);
            file.writeByte(0x61);
            file.writeByte((int) (chunk & 0xFF));
            file.writeByte((int) ((chunk >> 8) & 0xFF));
            samples -= chunk;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Closes the writer: emits {@code 0x66} (end of data), then patches the header with
     * the final EOF offset and total sample count.
     */
    @Override
    public void close() throws IOException
    {
        file.writeByte(0x66);
        long fileSize = file.getFilePointer();
        file.seek(0);
        switch (chipMode)
        {
            case AY8910  -> writeHeaderAy(fileSize, false);
            case MSX     -> writeHeaderAy(fileSize, true);
            case SN76489 -> writeHeaderSn(fileSize);
            case YM2413  -> writeHeaderYm2413(fileSize);
        }
        file.close();
    }

    // ── Header writers ────────────────────────────────────────────────────────

    /**
     * Writes the VGM v1.61 header (128 bytes) for AY-3-8910 (optionally with YM2413).
     *
     * @param withYm2413 {@code true} → also write YM2413 clock at 0x10 (MSX combined mode)
     */
    private void writeHeaderAy(long fileSize, boolean withYm2413) throws IOException
    {
        long clockVal = dualChip ? AY8910_CLOCK_DUAL : AY8910_CLOCK;
        // Data starts at 0x80; VGM data offset field at 0x34 is relative: 0x80 - 0x34 = 0x4C
        final int dataOffset = 0x4C;

        writeAscii("Vgm ");                // 0x00
        writeLe32(fileSize - 4);           // 0x04 EOF offset
        writeLe32(0x00000161L);            // 0x08 Version 1.61
        writeLe32(0);                      // 0x0C SN76489 clock (unused)
        writeLe32(withYm2413 ? YM2413_CLOCK : 0); // 0x10 YM2413 clock
        writeLe32(0);                      // 0x14 GD3 offset (none)
        writeLe32(totalSamples);           // 0x18 Total samples
        writeLe32(0);                      // 0x1C Loop offset (none)
        writeLe32(0);                      // 0x20 Loop samples
        writeLe32(60);                     // 0x24 Rate (NTSC 60)
        writeLe16(0);                      // 0x28 SN76489 feedback (unused)
        file.writeByte(0);                 // 0x2A SN76489 shift reg width
        file.writeByte(0);                 // 0x2B SN76489 flags
        writeLe32(0);                      // 0x2C YM2612 clock
        writeLe32(0);                      // 0x30 YM2151 clock
        writeLe32(dataOffset);             // 0x34 VGM data offset
        writeLe32(0);                      // 0x38 Sega PCM clock
        writeLe32(0);                      // 0x3C SPCM interface
        writeLe32(0);                      // 0x40 RF5C68 clock
        writeLe32(0);                      // 0x44 YM2203 clock
        writeLe32(0);                      // 0x48 YM2608 clock
        writeLe32(0);                      // 0x4C YM2610/B clock
        writeLe32(0);                      // 0x50 YM3812 clock
        writeLe32(0);                      // 0x54 YM3526 clock
        writeLe32(0);                      // 0x58 Y8950 clock
        writeLe32(0);                      // 0x5C YMF262 clock
        writeLe32(0);                      // 0x60 YMF278B clock
        writeLe32(0);                      // 0x64 YMF271 clock
        writeLe32(0);                      // 0x68 YMZ280B clock
        writeLe32(0);                      // 0x6C RF5C164 clock
        writeLe32(0);                      // 0x70 PWM clock
        writeLe32(clockVal);               // 0x74 AY8910 clock (bit31=dual)
        file.writeByte(0x00);              // 0x78 AY8910 chip type: 0=AY8910
        file.writeByte(0x00);             // 0x79 AY8910 flags
        file.writeByte(0x00);             // 0x7A YM2203/AY8910 flags
        file.writeByte(0x00);             // 0x7B YM2608/AY8910 flags
        // 0x7C–0x7F: padding
        writeLe32(0);
    }

    /** Writes the VGM v1.50 header (64 bytes) for dual SN76489. */
    private void writeHeaderSn(long fileSize) throws IOException
    {
        long clockVal = dualChip ? SN76489_CLOCK_DUAL : SN76489_CLOCK;

        writeAscii("Vgm ");                // 0x00
        writeLe32(fileSize - 4);           // 0x04 EOF offset
        writeLe32(0x00000150L);            // 0x08 Version 1.50
        writeLe32(clockVal);               // 0x0C SN76489 clock
        writeLe32(0);                      // 0x10 YM2413 clock
        writeLe32(0);                      // 0x14 GD3 offset
        writeLe32(totalSamples);           // 0x18 Total samples
        writeLe32(0);                      // 0x1C Loop offset
        writeLe32(0);                      // 0x20 Loop samples
        writeLe32(60);                     // 0x24 Rate
        writeLe16(0x0009);                 // 0x28 SN76489 feedback
        file.writeByte(16);                // 0x2A shift register width
        file.writeByte(0);                 // 0x2B flags
        writeLe32(0);                      // 0x2C YM2612 clock
        writeLe32(0);                      // 0x30 YM2151 clock
        writeLe32(0x0C);                   // 0x34 VGM data offset (data at 0x40)
        writeLe32(0);                      // 0x38 Sega PCM clock
        writeLe32(0);                      // 0x3C SPCM interface
    }

    /** Writes the VGM v1.50 header (64 bytes) for YM2413 (OPLL). */
    private void writeHeaderYm2413(long fileSize) throws IOException
    {
        writeAscii("Vgm ");                // 0x00
        writeLe32(fileSize - 4);           // 0x04 EOF offset
        writeLe32(0x00000150L);            // 0x08 Version 1.50
        writeLe32(0);                      // 0x0C SN76489 clock (unused)
        writeLe32(YM2413_CLOCK);           // 0x10 YM2413 clock
        writeLe32(0);                      // 0x14 GD3 offset
        writeLe32(totalSamples);           // 0x18 Total samples
        writeLe32(0);                      // 0x1C Loop offset
        writeLe32(0);                      // 0x20 Loop samples
        writeLe32(60);                     // 0x24 Rate
        writeLe16(0);                      // 0x28 SN76489 feedback (unused)
        file.writeByte(0);                 // 0x2A shift register width
        file.writeByte(0);                 // 0x2B flags
        writeLe32(0);                      // 0x2C YM2612 clock
        writeLe32(0);                      // 0x30 YM2151 clock
        writeLe32(0x0C);                   // 0x34 VGM data offset (data at 0x40)
        writeLe32(0);                      // 0x38 Sega PCM clock
        writeLe32(0);                      // 0x3C SPCM interface
    }

    // ── Low-level helpers ─────────────────────────────────────────────────────

    private void writeAscii(String s) throws IOException
    {
        for (char c : s.toCharArray()) file.writeByte(c);
    }

    private void writeLe32(long value) throws IOException
    {
        file.writeByte((int) (value & 0xFF));
        file.writeByte((int) ((value >> 8) & 0xFF));
        file.writeByte((int) ((value >> 16) & 0xFF));
        file.writeByte((int) ((value >> 24) & 0xFF));
    }

    private void writeLe16(int value) throws IOException
    {
        file.writeByte(value & 0xFF);
        file.writeByte((value >> 8) & 0xFF);
    }
}
