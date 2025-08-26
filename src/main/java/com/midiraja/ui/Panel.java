/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import com.midiraja.engine.PlaybackEngine;

public interface Panel
{
    int calculateHeight(int availableHeight);

    void render(StringBuilder sb, int allocatedWidth, int allocatedHeight, boolean showHeaders, PlaybackEngine engine);

    default String truncate(String text, int maxLength)
    {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}