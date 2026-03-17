/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.ui;

import java.io.PrintStream;

/** ASCII-art logo for Midiraja. */
public final class Logo
{
    private Logo()
    {}

    // "MIDIRAJA" in figlet Standard font — 65 chars wide, 5 lines
    public static final String[] LINES = {
        " __  __  ___ ____   ___ ____      _           _       _   ",
        "|  \\/  | |_ _| |  _ \\  |_ _| |  _ \\    / \\       | |   / \\  ",
        "| |\\/| |  | |  | | | |  | |  | |_) |  / _ \\    _  | |  / _ \\ ",
        "| |  | |  | |  | |_| |  | |  |  _ <  / ___ \\   | |_| | / ___ \\",
        "|_|  |_| |___| |____/  |___| |_| \\_\\/_/   \\_\\  \\___/ /_/   \\_\\"
    };

    public static final String SUBTITLE = "Cross-platform CLI MIDI Player";
    public static final int WIDTH = 65;

    /** Prints the logo and subtitle to {@code out}, with amber color if the terminal supports it. */
    public static void print(PrintStream out)
    {
        for (String line : LINES)
            out.println(Theme.COLOR_HIGHLIGHT + line + Theme.COLOR_RESET);
        int pad = (WIDTH - SUBTITLE.length()) / 2;
        out.println(" ".repeat(pad) + Theme.COLOR_DIM + SUBTITLE + Theme.COLOR_RESET);
        out.println();
    }
}
