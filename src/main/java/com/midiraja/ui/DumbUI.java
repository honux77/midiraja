/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import com.midiraja.engine.PlaybackEngine;
import com.midiraja.io.TerminalIO;

/**
 * A non-interactive UI implementation. No input polling and minimal, static output.
 * Used for headless CI or simple batch playback.
 */
public class DumbUI implements PlaybackUI
{
    @Override
    public void runRenderLoop(PlaybackEngine engine)
    {
        var term = TerminalIO.CONTEXT.get();
        term.println("Playing (Non-interactive)...");
        try
        {
            while (engine.isPlaying())
            {
                Thread.sleep(1000); // Sleep and wait for engine to finish
            }
        }
        catch (InterruptedException _)
        {
        }
    }

    @Override
    public void runInputLoop(PlaybackEngine engine)
    {
        // No input handling in dumb mode
        try
        {
            while (engine.isPlaying())
            {
                Thread.sleep(1000);
            }
        }
        catch (InterruptedException _)
        {
        }
    }
}