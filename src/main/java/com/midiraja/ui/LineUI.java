/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import com.midiraja.engine.PlaybackEngine;
import com.midiraja.engine.PlaybackEngine.PlaybackStatus;
import com.midiraja.io.TerminalIO;
import com.midiraja.io.TerminalIO.TerminalKey;
import java.io.IOException;

public class LineUI implements PlaybackUI
{
    private final StatusPanel statusPanel = new StatusPanel();

    @Override
    public void runRenderLoop(PlaybackEngine engine)
    {
        var term = TerminalIO.CONTEXT.get();
        try {
            while (engine.isPlaying()) {
                int width = term.getWidth();
                StringBuilder sb = new StringBuilder();
                sb.append("\r");
                statusPanel.render(sb, width, 1, engine);
                term.print(sb.toString().replace("\n", ""));
                Thread.sleep(100);
            }
        } catch (InterruptedException _) {}
    }

    @Override
    public void runInputLoop(PlaybackEngine engine)
    {
        var term = TerminalIO.CONTEXT.get();
        try {
            while (engine.isPlaying()) {
                var key = term.readKey();
                handleCommonInput(engine, key);
            }
        } catch (IOException _) {
            engine.requestStop(PlaybackStatus.QUIT_ALL);
        }
    }

    public static void handleCommonInput(PlaybackEngine engine, TerminalKey key)
    {
        switch (key)
        {
            case VOLUME_UP -> engine.adjustVolume(0.05);
            case VOLUME_DOWN -> engine.adjustVolume(-0.05);
            case NEXT_TRACK -> engine.requestStop(PlaybackStatus.NEXT);
            case PREV_TRACK -> engine.requestStop(PlaybackStatus.PREVIOUS);
            case TRANSPOSE_UP -> engine.adjustTranspose(1);
            case TRANSPOSE_DOWN -> engine.adjustTranspose(-1);
            case SPEED_UP -> engine.adjustSpeed(0.1);
            case SPEED_DOWN -> engine.adjustSpeed(-0.1);
            case QUIT -> engine.requestStop(PlaybackStatus.QUIT_ALL);
            default -> {}
        }
    }
}