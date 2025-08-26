/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import com.midiraja.engine.PlaybackEngine;

public class ControlsPanel implements Panel
{
    @Override
    public int calculateHeight(int availableHeight)
    {
        if (availableHeight >= 3) return 3;
        return 1;
    }

    @Override
    public void render(StringBuilder sb, int allocatedWidth, int allocatedHeight, PlaybackEngine engine)
    {
        if (allocatedHeight <= 0) return;
        if (allocatedHeight >= 3) {
            sb.append(" [CONTROLS]\n");
            sb.append("  [Space] Pause/Resume  |  [<] [>] Prev/Next Track  |  [+] [-] Transpose\n");
            sb.append("  [Up] [Down] Volume    |  [Q] Quit                 |\n");
        } else {
            String minLine = "  [Spc]Pause [<>]Skip [+-]Trans [^v]Vol [Q]Quit";
            sb.append(truncate(minLine, allocatedWidth)).append("\n");
        }
    }
}