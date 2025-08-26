/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import com.midiraja.engine.PlaybackEngine;
import com.midiraja.engine.PlaylistContext;

public class MetadataPanel implements Panel
{
    @Override
    public int calculateHeight(int availableHeight)
    {
        return Math.min(availableHeight, 3);
    }

    @Override
    public void render(StringBuilder sb, int allocatedWidth, int allocatedHeight, PlaybackEngine engine)
    {
        if (allocatedHeight <= 0) return;
        PlaylistContext context = engine.getContext();
        String rawTitle = context.sequenceTitle() != null ? context.sequenceTitle() : context.files().get(context.currentIndex()).getName();

        if (allocatedHeight == 1) {
            sb.append(truncate("  [NOW] " + rawTitle, allocatedWidth)).append("\n");
        } else if (allocatedHeight == 2) {
            sb.append("  [NOW PLAYING]\n");
            sb.append(String.format("    Title:     %s\n", truncate(rawTitle, allocatedWidth - 16)));
        } else {
            sb.append("  [NOW PLAYING]\n\n");
            sb.append(String.format("    Title:     %s\n", truncate(rawTitle, allocatedWidth - 16)));
        }
    }
}