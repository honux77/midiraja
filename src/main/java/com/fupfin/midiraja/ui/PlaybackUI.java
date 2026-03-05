/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.ui;

import com.fupfin.midiraja.engine.PlaybackEngine;

/**
 * Interface representing a Face/UI implementation that can render the state of a PlaybackEngine and
 * handle user input.
 */
public interface PlaybackUI
{
    /**
     * Executes the rendering loop. This method should block until the engine stops playing.
     */
    void runRenderLoop(PlaybackEngine engine);

    /**
     * Executes the input polling loop. This method should block until the engine stops playing.
     */
    void runInputLoop(PlaybackEngine engine);
}
