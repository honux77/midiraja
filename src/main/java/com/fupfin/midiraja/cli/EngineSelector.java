/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.cli;

import com.fupfin.midiraja.io.JLineTerminalIO;
import com.fupfin.midiraja.midi.MidiPort;
import com.fupfin.midiraja.ui.Logo;
import com.fupfin.midiraja.ui.ScreenBuffer;
import com.fupfin.midiraja.ui.Theme;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.NonBlockingReader;
import org.jspecify.annotations.Nullable;

/**
 * Interactive engine selection menu shown when {@code midra} is run without a subcommand.
 *
 * <p>
 * Combines built-in zero-setup synthesizers with available OS MIDI ports into a single list, then
 * lets the user pick one with arrow keys.
 */
public class EngineSelector
{
    public sealed interface Choice permits Choice.Builtin, Choice.Port
    {
        record Builtin(String engineName) implements Choice
        {
        }

        record Port(int portIndex) implements Choice
        {
        }
    }

    private record Entry(@Nullable Choice choice, String col1, String col2)
    {
        boolean isSeparator()
        {
            return choice == null;
        }

        static Entry builtin(String name, String desc)
        {
            return new Entry(new Choice.Builtin(name.strip()), name, desc);
        }

        static Entry port(MidiPort p)
        {
            return new Entry(new Choice.Port(p.index()), p.name(), "OS MIDI port");
        }

        static Entry separator(String label)
        {
            return new Entry(null, label, "");
        }
    }

    // Built-in engines ordered by audio quality (best first)
    private static final List<Entry> BUILTIN_ENTRIES = List.of(
            Entry.builtin("soundfont", "SoundFont            (FluidR3 GM SF3 bundled)"),
            Entry.builtin("patch    ", "GUS wavetable        (FreePats bundled)"),
            Entry.builtin("opn      ", "OPN FM synthesis     (Sega Genesis / PC-98)"),
            Entry.builtin("opl      ", "OPL FM synthesis     (DOS / AdLib / Sound Blaster)"),
            Entry.builtin("psg      ", "PSG chiptune         (MSX / ZX Spectrum / Atari ST)"),
            Entry.builtin("1bit     ", "1-Bit Beeper         (Apple II / PC Speaker)"));

    /**
     * Shows the engine selection menu and returns the user's choice, or {@code null} if the user
     * quit.
     */
    /**
     * Resets the terminal to canonical (cooked) mode by running {@code stty sane}.
     *
     * <p>
     * A previous run may have exited while the terminal was in raw mode (e.g. if the JLine terminal
     * was not closed cleanly). This call resets the OS-level tty state so that subsequent JLine
     * opens capture cooked mode as their "original" attributes and restore it correctly on close.
     */
    private static void resetTerminalToSane()
    {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try
        {
            new ProcessBuilder("stty", "sane")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(1, TimeUnit.SECONDS);
        }
        catch (Exception ignored)
        {
        }
    }

    @Nullable
    public static Choice select(List<MidiPort> ports, boolean preferFullMode, boolean preferMini,
            boolean preferClassic, PrintStream err) throws Exception
    {
        // Reset terminal to sane/cooked mode before opening any JLine terminal.
        // A previous run may have left the tty in raw mode; this ensures JLine captures
        // cooked mode as its "original" and restores it correctly on close.
        resetTerminalToSane();

        var entries = buildEntries(ports);

        var probe = new JLineTerminalIO();
        probe.init();
        boolean isInteractive = probe.isInteractive();
        probe.close();

        if (!isInteractive || preferClassic) return fallbackSelect(entries, err);

        if (preferMini) return dynamicSelect(entries);
        return preferFullMode ? fullScreenSelect(entries) : dynamicSelect(entries);
    }

    private static List<Entry> buildEntries(List<MidiPort> ports)
    {
        var list = new ArrayList<Entry>();
        if (!ports.isEmpty())
        {
            ports.forEach(p -> list.add(Entry.port(p)));
            list.add(Entry.separator("── Built-in Engines ───────────────────────────────────────"));
        }
        list.addAll(BUILTIN_ENTRIES);
        return list;
    }

    /** Advance {@code from} by {@code direction} (+1 or −1), skipping separator entries. */
    private static int nextSelectable(List<Entry> entries, int from, int direction)
    {
        int size = entries.size();
        int i = (from + direction + size) % size;
        while (entries.get(i).isSeparator())
        {
            i = (i + direction + size) % size;
        }
        return i;
    }

    /** Non-interactive fallback: numbered prompt on stdout/stderr. */
    @Nullable
    private static Choice fallbackSelect(List<Entry> entries, PrintStream err)
    {
        err.println("Select an engine:");
        int n = 0;
        for (var e : entries)
        {
            if (e.isSeparator())
            {
                err.println("  " + e.col1());
            }
            else
            {
                err.println("  [" + (++n) + "] " + e.col1() + " — " + e.col2());
            }
        }
        err.print("Enter number: ");
        err.flush();
        var scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        if (!scanner.hasNextInt()) return null;
        int sel = scanner.nextInt();
        int idx = 0;
        for (var e : entries)
        {
            if (e.isSeparator()) continue;
            if (++idx == sel) return e.choice();
        }
        return null;
    }

    /** Arrow-key menu that redraws in-place (mini / small terminal). */
    @Nullable
    @SuppressWarnings("EmptyCatch")
    private static Choice dynamicSelect(List<Entry> entries) throws Exception
    {
        int numLines = entries.size() + 1; // +1 for the header line
        int selectedIdx = firstSelectable(entries);

        try (Terminal terminal = TerminalBuilder.builder().system(true).build())
        {
            terminal.enterRawMode();
            var km = buildNavKeyMap(terminal);
            var bindingReader = new BindingReader(terminal.reader());
            terminal.writer().print(Theme.TERM_HIDE_CURSOR);
            boolean firstDraw = true;

            while (true)
            {
                if (!firstDraw) terminal.writer().print("\033[" + numLines + "A");
                firstDraw = false;

                terminal.writer().println("Select an engine:");
                for (int i = 0; i < entries.size(); i++)
                {
                    Entry e = entries.get(i);
                    if (e.isSeparator())
                    {
                        terminal.writer().println(
                                "  " + Theme.COLOR_HIGHLIGHT + e.col1() + Theme.COLOR_RESET
                                        + Theme.TERM_CLEAR_TO_EOL);
                    }
                    else
                    {
                        String prefix = (i == selectedIdx) ? " > " : "   ";
                        terminal.writer().println(prefix + e.col1() + "  " + e.col2()
                                + Theme.TERM_CLEAR_TO_EOL);
                    }
                }
                terminal.writer().flush();

                if (terminal.reader().peek(100) == NonBlockingReader.READ_EXPIRED) continue;
                String action = bindingReader.readBinding(km, null, false);
                if (action == null) continue;

                switch (action)
                {
                    case "QUIT" -> {
                        clearLines(terminal, numLines);
                        terminal.writer().print(Theme.TERM_SHOW_CURSOR);
                        terminal.writer().flush();
                        return null;
                    }
                    case "SELECT" -> {
                        clearLines(terminal, numLines);
                        terminal.writer().print(Theme.TERM_SHOW_CURSOR);
                        terminal.writer().flush();
                        return entries.get(selectedIdx).choice();
                    }
                    case "UP"   -> selectedIdx = nextSelectable(entries, selectedIdx, -1);
                    case "DOWN" -> selectedIdx = nextSelectable(entries, selectedIdx, 1);
                }
            }
        }
    }

    /** Full-screen alt-buffer menu with title box and section headers. */
    @Nullable
    @SuppressWarnings("EmptyCatch")
    private static Choice fullScreenSelect(List<Entry> entries) throws Exception
    {
        int selectedIdx = firstSelectable(entries);

        try (Terminal terminal = TerminalBuilder.builder().system(true).build())
        {
            terminal.enterRawMode();
            var km = buildNavKeyMap(terminal);
            var bindingReader = new BindingReader(terminal.reader());
            terminal.writer().print(Theme.TERM_ALT_SCREEN_ENABLE + Theme.TERM_HIDE_CURSOR);
            terminal.writer().flush();

            while (true)
            {
                int width = terminal.getWidth();
                int height = terminal.getHeight();
                int boxWidth = Math.max(64, Math.min(84, width - 4));
                boolean showLogo = width >= Logo.WIDTH + 4;
                int logoLines = showLogo ? Logo.LINES.length + 1 : 0; // +1 for subtitle
                int boxHeight = entries.size() + 4 + logoLines;
                int padLeft = Math.max(0, (width - boxWidth) / 2);
                int padTop = Math.max(0, (height - boxHeight) / 2);

                var buf = new ScreenBuffer(8192);
                buf.append(Theme.TERM_CURSOR_HOME).append(Theme.TERM_CLEAR_TO_END);
                buf.repeat("\n", padTop);

                if (showLogo)
                {
                    int logoPad = Math.max(0, (width - Logo.WIDTH) / 2);
                    for (int li = 0; li < Logo.LINES.length; li++)
                        buf.repeat(" ", logoPad).append(Logo.LINE_COLORS[li]).append(Logo.LINES[li])
                                .append(Theme.COLOR_RESET).appendLine();
                    int subtitlePad = Math.max(0, (width - Logo.SUBTITLE.length()) / 2);
                    buf.repeat(" ", subtitlePad)
                            .append(Theme.COLOR_DIM_FG).append(Logo.VU_BARS)
                            .append("  ").append(Logo.SUBTITLE_TEXT)
                            .append(Theme.COLOR_RESET).appendLine();
                }

                String title = " SELECT PLAYBACK ENGINE ";
                int titlePad = (boxWidth - title.length() - 2) / 2;
                buf.repeat(" ", padLeft).append(Theme.COLOR_HIGHLIGHT)
                        .repeat(Theme.DECORATOR_LINE, titlePad).append(Theme.COLOR_RESET)
                        .append(Theme.FORMAT_INVERT).append(title).append(Theme.COLOR_RESET)
                        .append(Theme.COLOR_HIGHLIGHT)
                        .repeat(Theme.DECORATOR_LINE, boxWidth - titlePad - title.length())
                        .append(Theme.COLOR_RESET).appendLine();

                for (int i = 0; i < entries.size(); i++)
                {
                    Entry e = entries.get(i);
                    buf.repeat(" ", padLeft);
                    if (e.isSeparator())
                    {
                        buf.append("  ").append(Theme.COLOR_HIGHLIGHT).append(e.col1())
                                .append(Theme.COLOR_RESET).appendLine();
                    }
                    else
                    {
                        String label = e.col1();
                        String desc = e.col2();
                        int maxDesc = boxWidth - label.length() - 8;
                        if (desc.length() > maxDesc) desc = desc.substring(0, maxDesc - 1) + "…";
                        if (i == selectedIdx)
                        {
                            buf.append("  ").append(Theme.COLOR_HIGHLIGHT)
                                    .append(Theme.CHAR_ARROW_RIGHT).append(" ").append(label)
                                    .append("  ").append(desc).append(Theme.COLOR_RESET)
                                    .appendLine();
                        }
                        else
                        {
                            buf.append("      ").append(label).append("  ").append(desc)
                                    .appendLine();
                        }
                    }
                }

                buf.repeat(" ", padLeft).append(Theme.COLOR_HIGHLIGHT)
                        .repeat(Theme.BORDER_HORIZONTAL, boxWidth).append(Theme.COLOR_RESET)
                        .appendLine();
                String footer = "[▲/▼] Move   [Enter] Select   [Q] Quit";
                int footerPad = (boxWidth - footer.length()) / 2;
                buf.repeat(" ", padLeft + footerPad).append(Theme.COLOR_HIGHLIGHT).append(footer)
                        .append(Theme.COLOR_RESET).appendLine();

                terminal.writer().print(buf.toString());
                terminal.writer().flush();

                if (terminal.reader().peek(50) == NonBlockingReader.READ_EXPIRED) continue;
                String action = bindingReader.readBinding(km, null, false);
                if (action == null) continue;

                switch (action)
                {
                    case "QUIT" -> {
                        terminal.writer()
                                .print(Theme.TERM_ALT_SCREEN_DISABLE + Theme.TERM_SHOW_CURSOR);
                        terminal.writer().flush();
                        return null;
                    }
                    case "SELECT" -> {
                        terminal.writer()
                                .print(Theme.TERM_ALT_SCREEN_DISABLE + Theme.TERM_SHOW_CURSOR);
                        terminal.writer().flush();
                        return entries.get(selectedIdx).choice();
                    }
                    case "UP"   -> selectedIdx = nextSelectable(entries, selectedIdx, -1);
                    case "DOWN" -> selectedIdx = nextSelectable(entries, selectedIdx, 1);
                }
            }
        }
    }

    private static KeyMap<String> buildNavKeyMap(Terminal terminal)
    {
        var km = new KeyMap<String>();
        km.setAmbiguousTimeout(100);
        String upSeq = KeyMap.key(terminal, InfoCmp.Capability.key_up);
        if (upSeq != null && !upSeq.isEmpty()) km.bind("UP", upSeq);
        km.bind("UP", "\033[A", "\033OA");
        String downSeq = KeyMap.key(terminal, InfoCmp.Capability.key_down);
        if (downSeq != null && !downSeq.isEmpty()) km.bind("DOWN", downSeq);
        km.bind("DOWN", "\033[B", "\033OB");
        km.bind("SELECT", "\r", "\n");
        km.bind("QUIT", "q", "Q", "\033");
        return km;
    }

    private static int firstSelectable(List<Entry> entries)
    {
        for (int i = 0; i < entries.size(); i++)
        {
            if (!entries.get(i).isSeparator()) return i;
        }
        return 0;
    }

    private static void clearLines(Terminal terminal, int count)
    {
        terminal.writer().print("\033[" + count + "A");
        for (int i = 0; i < count; i++)
        {
            terminal.writer().println(Theme.TERM_CLEAR_TO_EOL);
        }
        terminal.writer().print("\033[" + count + "A");
    }
}
