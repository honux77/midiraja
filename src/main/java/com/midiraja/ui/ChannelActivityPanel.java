/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import java.util.Arrays;

/**
 * Responsive VU meter display for 16 MIDI channels.
 */
public class ChannelActivityPanel implements Panel
{
    private static final String[] GM_FAMILIES = {"Piano", "Chrom Perc", "Organ", "Guitar", "Bass",
        "Strings", "Ensemble", "Brass", "Reed", "Pipe", "Synth Lead", "Synth Pad", "Synth FX",
        "Ethnic", "Percussive", "SFX"};

    private LayoutConstraints constraints = new LayoutConstraints(80, 16, false, false);
    private final double[] channelLevels = new double[16];
    private final int[] channelPrograms = new int[16];

    @Override public void onLayoutUpdated(LayoutConstraints bounds)
    {
        this.constraints = bounds;
    }

    @Override public void onPlaybackStateChanged()
    {
    }

    @Override public void onTick(long currentMicroseconds)
    {
    }

    @Override public void onTempoChanged(float bpm)
    {
    }

    @Override public void onChannelActivity(int channel, int velocity)
    {
        if (channel >= 0 && channel < 16)
        {
            channelLevels[channel] = Math.max(channelLevels[channel], velocity / 127.0);
        }
    }

    public void updatePrograms(int[] programs)
    {
        System.arraycopy(programs, 0, channelPrograms, 0, 16);
    }

    private String getChannelName(int ch)
    {
        if (ch == 9)
            return "Drums";
        int family = channelPrograms[ch] / 8;
        if (family >= 0 && family < GM_FAMILIES.length)
            return GM_FAMILIES[family];
        return "Unknown";
    }

    @Override public void render(ScreenBuffer buffer)
    {
        if (constraints.height() <= 0)
            return;

        for (int i = 0; i < 16; i++)
        {
            channelLevels[i] = Math.max(0, channelLevels[i] - 0.05);
        }

        int w = constraints.width();
        int h = constraints.height();

        // Determine optimal number of columns based on available space
        int numCols;
        if (h >= 16 && w < 80) {
            numCols = 1;
        } else if (h >= 8 && w >= 60) {
            numCols = 2;
        } else if (h >= 4 && w >= 40) {
            numCols = 4;
        } else if (h >= 16) {
            numCols = 1;
        } else {
            numCols = 4;
        }

        int numRows = (int) Math.ceil(16.0 / numCols);
        int rowsToDraw = Math.min(numRows, h);
        int colWidth = w / numCols;

        for (int r = 0; r < rowsToDraw; r++)
        {
            StringBuilder rowSb = new StringBuilder();
            for (int c = 0; c < numCols; c++)
            {
                int ch = r + (c * numRows);
                if (ch >= 16) break;

                String cell;
                if (numCols == 4) {
                    // Format: "C01:[███··]"
                    // CXX: (4 static) + brackets from ProgressBar (2 static) = 6 static
                    int maxMeter = Math.max(2, colWidth - 6); 
                    int meterLen = (int) (channelLevels[ch] * maxMeter);
                    
                    String meter = ProgressBar.render(meterLen, maxMeter, ProgressBar.Style.DOTTED_BACKGROUND, true);
                    cell = String.format("C%02d:%s", ch + 1, meter);
                    
                    int visibleLen = 4 + 2 + maxMeter;
                    cell += " ".repeat(Math.max(0, colWidth - visibleLen));
                } else {
                    String instName = getChannelName(ch);
                    if (instName.length() > 11) {
                        instName = instName.substring(0, 11);
                    }
                    
                    // Format: "CH 01 (Piano      ): [%s]"
                    // "CH 01 " (6) + "(Piano      )" (13) + ": " (2) + brackets (2) = 23 static
                    int staticLen = 23;
                    int maxMeter = Math.max(2, colWidth - staticLen); 
                    int meterLen = (int) (channelLevels[ch] * maxMeter);
                    
                    String meter = ProgressBar.render(meterLen, maxMeter, ProgressBar.Style.DOTTED_BACKGROUND, true);
                        
                    cell = String.format("CH %02d %-13s: %s", ch + 1, "(" + instName + ")", meter);
                    
                    int visibleLen = staticLen + maxMeter; 
                    cell += " ".repeat(Math.max(0, colWidth - visibleLen));
                }
                rowSb.append(cell);
            }
            buffer.append(truncate(rowSb.toString(), w)).append("\n");
        }
    }
}
