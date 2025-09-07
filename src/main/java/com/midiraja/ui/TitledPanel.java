/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

public class TitledPanel implements Panel {
    private final String title;
    private final Panel content;
    private LayoutConstraints constraints = new LayoutConstraints(80, 0, false, false);
    private boolean noBottomBorder;

    public TitledPanel(String title, Panel content) {
        this.title = title;
        this.content = content;
        this.noBottomBorder = false;
    }

    public TitledPanel(String title, Panel content, boolean noBottomBorder) {
        this.title = title;
        this.content = content;
        this.noBottomBorder = noBottomBorder;
    }

    @Override
    public void onLayoutUpdated(LayoutConstraints bounds) {
        this.constraints = bounds;
        int overhead = noBottomBorder ? 1 : 2; // 1 for title, 1 for bottom border
        int contentHeight = Math.max(0, bounds.height() - overhead);
        // Reduce width by 2 to account for left and right padding spaces!
        int contentWidth = Math.max(0, bounds.width() - 2);
        content.onLayoutUpdated(new LayoutConstraints(contentWidth, contentHeight, bounds.showHeaders(), bounds.isHorizontal()));
    }

    @Override public void onPlaybackStateChanged() { content.onPlaybackStateChanged(); }
    @Override public void onTick(long currentMicroseconds) { content.onTick(currentMicroseconds); }
    @Override public void onTempoChanged(float bpm) { content.onTempoChanged(bpm); }
    @Override public void onChannelActivity(int channel, int velocity) { content.onChannelActivity(channel, velocity); }

    @Override
    public void render(StringBuilder sb) {
        if (constraints.height() <= 0) return;
        
        // Draw Header
        String header = " ≡≡[ " + title + " ]";
        int padding = Math.max(0, constraints.width() - header.length() - 1);
        sb.append(header).append("≡".repeat(padding)).append(" \n");

        // Draw Content
        if (constraints.height() > 1) {
            StringBuilder innerSb = new StringBuilder();
            content.render(innerSb);
            
            String[] lines = innerSb.toString().split("\n");
            int innerHeight = Math.max(0, constraints.height() - (noBottomBorder ? 1 : 2));
            int innerWidth = Math.max(0, constraints.width() - 2);
            
            // Render exact number of inner lines to fill the allocated height (no short panels!)
            for (int i = 0; i < innerHeight; i++) {
                String line = (i < lines.length) ? lines[i] : "";
                
                // Truncate or pad inner line to exact innerWidth
                if (line.length() > innerWidth) {
                    line = line.substring(0, innerWidth);
                } else if (line.length() < innerWidth) {
                    line = line + " ".repeat(innerWidth - line.length());
                }
                
                // Apply left and right padding!
                sb.append(" ").append(line).append(" \n");
            }
        }

        // Draw Bottom Border
        if (!noBottomBorder && constraints.height() > 1) {
            sb.append("-".repeat(constraints.width())).append("\n");
        }
    }
}
