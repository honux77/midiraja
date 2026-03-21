/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.cli;

import com.fupfin.midiraja.MidirajaCommand;
import com.fupfin.midiraja.midi.vgm.MidiToYm2413Converter;
import java.io.File;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code midra opll} — converts a MIDI file to a VGM file (YM2413 OPLL FM chip).
 *
 * <p>No audio is produced during conversion. The output VGM can be played back with any
 * VGM-compatible player (e.g., VGMPlay, foobar2000 + vgmstream, or hardware players).
 *
 * <p>Example:
 * <pre>
 *   midra opll song.mid              # writes song.vgm
 *   midra opll song.mid output.vgm  # writes output.vgm
 * </pre>
 */
@Command(name = "opll", mixinStandardHelpOptions = true,
        description = "Convert a MIDI file to VGM format (YM2413 OPLL FM chip, offline, no audio).")
public class Ym2413Command implements Callable<Integer>
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
        err.println("Converting: " + input.getName() + " → " + new File(outputPath).getName());

        try
        {
            MidiToYm2413Converter.convert(input, outputPath, err);
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
