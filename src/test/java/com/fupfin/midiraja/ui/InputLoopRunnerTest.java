/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.fupfin.midiraja.engine.PlaybackCommands;
import com.fupfin.midiraja.engine.PlaybackEngine.PlaybackStatus;
import com.fupfin.midiraja.engine.PlaylistContext;
import com.fupfin.midiraja.io.MockTerminalIO;
import com.fupfin.midiraja.io.TerminalIO;
import com.fupfin.midiraja.io.TerminalIO.TerminalKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InputLoopRunnerTest
{
    /** Records every call made so tests can verify routing. */
    static class RecordingCommands implements PlaybackCommands
    {
        final List<String>   calls      = new ArrayList<>();
        private final int    stopAfter; // isPlaying() returns false after this many calls
        private final AtomicInteger callCount = new AtomicInteger(0);

        RecordingCommands(int stopAfter) { this.stopAfter = stopAfter; }

        @Override public boolean isPlaying()
        {
            return callCount.incrementAndGet() <= stopAfter;
        }

        @Override public void requestStop(PlaybackStatus s) { calls.add("requestStop:" + s); }

        @Override public void adjustVolume(double d)      { calls.add("adjustVolume:" + d); }

        @Override public void adjustSpeed(double d)       { calls.add("adjustSpeed:" + d); }

        @Override public void adjustTranspose(int d)      { calls.add("adjustTranspose:" + d); }

        @Override public void seekRelative(long us)       { calls.add("seekRelative:" + us); }

        @Override public void togglePause()               { calls.add("togglePause"); }

        @Override public void toggleLoop()                { calls.add("toggleLoop"); }

        @Override public void toggleShuffle()             { calls.add("toggleShuffle"); }

        @Override public void fireBookmark()              { calls.add("fireBookmark"); }

        @Override public void firePlayOrderChanged(PlaylistContext ctx)
        {
            calls.add("firePlayOrderChanged");
        }
    }

    /** MockTerminalIO that throws IOException on readKey(). */
    static class ThrowingTerminalIO extends MockTerminalIO
    {
        @Override public TerminalKey readKey() throws IOException
        {
            throw new IOException("simulated error");
        }
    }

    // -----------------------------------------------------------------------
    // C1. run_callsHandlerForEachKey_untilNotPlaying
    // -----------------------------------------------------------------------
    @Test void run_callsHandlerForEachKey_untilNotPlaying() throws Exception
    {
        // isPlaying() returns true for the first 3 checks, then false
        RecordingCommands engine = new RecordingCommands(3);

        MockTerminalIO mockIO = new MockTerminalIO();
        mockIO.injectKey(TerminalKey.VOLUME_UP);
        mockIO.injectKey(TerminalKey.PAUSE);
        mockIO.injectKey(TerminalKey.QUIT);

        ScopedValue.where(TerminalIO.CONTEXT, mockIO).call(() -> {
            InputLoopRunner.run(engine, InputHandler::handleCommonInput);
            return null;
        });

        assertTrue(engine.calls.contains("adjustVolume:0.05"), "VOLUME_UP should have been processed");
        assertTrue(engine.calls.contains("togglePause"),        "PAUSE should have been processed");
        assertTrue(engine.calls.contains("requestStop:QUIT_ALL"), "QUIT should have been processed");
    }

    // -----------------------------------------------------------------------
    // C2. run_stopsWhenNotPlaying_withoutProcessingMoreKeys
    // -----------------------------------------------------------------------
    @Test void run_stopsWhenNotPlaying_withoutProcessingMoreKeys() throws Exception
    {
        // isPlaying() returns false on the very first call
        RecordingCommands engine = new RecordingCommands(0);

        MockTerminalIO mockIO = new MockTerminalIO();
        mockIO.injectKey(TerminalKey.VOLUME_UP);
        mockIO.injectKey(TerminalKey.PAUSE);
        mockIO.injectKey(TerminalKey.QUIT);
        mockIO.injectKey(TerminalKey.NEXT_TRACK);
        mockIO.injectKey(TerminalKey.PREV_TRACK);

        ScopedValue.where(TerminalIO.CONTEXT, mockIO).call(() -> {
            InputLoopRunner.run(engine, InputHandler::handleCommonInput);
            return null;
        });

        assertTrue(engine.calls.isEmpty(), "No keys should be processed when isPlaying() is false from the start");
    }

    // -----------------------------------------------------------------------
    // C3. run_onIOException_callsRequestStopQuitAll
    // -----------------------------------------------------------------------
    @Test void run_onIOException_callsRequestStopQuitAll() throws Exception
    {
        // Engine always reports playing so the loop doesn't exit on its own
        RecordingCommands engine = new RecordingCommands(Integer.MAX_VALUE);

        ThrowingTerminalIO throwingIO = new ThrowingTerminalIO();

        ScopedValue.where(TerminalIO.CONTEXT, throwingIO).call(() -> {
            InputLoopRunner.run(engine, InputHandler::handleCommonInput);
            return null;
        });

        assertTrue(engine.calls.contains("requestStop:QUIT_ALL"),
                "IOException should trigger requestStop(QUIT_ALL)");
    }
}
