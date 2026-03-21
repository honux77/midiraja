/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.cli;

import com.fupfin.midiraja.MidirajaCommand;
import com.fupfin.midiraja.midi.vgm.MidiToMsxConverter;
import java.io.File;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code midra msx} — converts a MIDI file to a VGM file using both MSX sound chips:
 * AY-3-8910 PSG (3 channels) + YM2413 OPLL FM (9 channels / 6 FM + 5 rhythm).
 *
 * <p>Total polyphony: up to 12 simultaneous voices (3 PSG + 9 FM), or
 * 9 voices + 5 rhythm instruments when percussion is active.
 *
 * <p>Example:
 * <pre>
 *   midra msx song.mid              # writes song.vgm
 *   midra msx song.mid output.vgm  # writes output.vgm
 * </pre>
 */
@Command(name = "msxvgm", mixinStandardHelpOptions = true,
        description = "Convert a MIDI file to VGM format (MSX: AY-3-8910 PSG + YM2413 OPLL FM, offline, no audio).")
public class MsxCommand implements Callable<Integer>
{
    @ParentCommand
    @Nullable
    private MidirajaCommand parent;

    @Parameters(index = "0", description = "Source MIDI file.")
    private File input = new File("");

    @Parameters(index = "1", arity = "0..1",
            description = "Output VGM file (default: replaces .mid extension with .vgm).")
    @Nullable
    private File output;

    @Override
    public Integer call() throws Exception
    {
        PrintStream err = parent != null ? parent.getErr() : System.err;

        if (!input.exists())
        {
            err.println("Error: Input file not found: " + input);
            return 1;
        }

        String outputPath = resolveOutputPath();
        err.println("Converting: " + input.getName() + " \u2192 " + new File(outputPath).getName());

        try
        {
            MidiToMsxConverter.convert(input, outputPath, err);
        }
        catch (Exception e)
        {
            err.println("Error: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    private String resolveOutputPath()
    {
        if (output != null) return output.getPath();
        String name = input.getName();
        int dot = name.lastIndexOf('.');
        String baseName = dot > 0 ? name.substring(0, dot) : name;
        File parentDir = input.getParentFile();
        String vgmName = baseName + ".vgm";
        return parentDir != null ? new File(parentDir, vgmName).getPath() : vgmName;
    }
}
