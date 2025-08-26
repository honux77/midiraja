/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import com.midiraja.engine.PlaybackEngine;
import com.midiraja.engine.PlaylistContext;
import com.midiraja.io.TerminalIO;
import java.io.IOException;

public class DashboardUI implements PlaybackUI
{
    private final MetadataPanel metadataPanel = new MetadataPanel();
    private final StatusPanel statusPanel = new StatusPanel();
    private final ChannelActivityPanel channelPanel = new ChannelActivityPanel();
    private final ControlsPanel controlsPanel = new ControlsPanel();

    @Override
    public void runRenderLoop(PlaybackEngine engine)
    {
        var term = TerminalIO.CONTEXT.get();
        if (!term.isInteractive()) return;

        try
        {
            while (engine.isPlaying())
            {
                engine.decayChannelLevels(0.05);

                int termWidth = term.getWidth();
                int termHeight = term.getHeight();

                StringBuilder sb = new StringBuilder();
                sb.append("\033[H");

                String doubleLine = "=".repeat(termWidth) + "\n";
                String singleLine = "-".repeat(termWidth) + "\n";

                // Minimum Playlist requirements based on list size
                int listSize = engine.getContext().files().size();
                boolean showPlaylist = listSize > 1;
                int minPlaylistHeight = showPlaylist ? Math.min(listSize, 4) : 0;

                int contentHeight = termHeight - 4; // Subtract 4 for the horizontal separator lines
                boolean showHeaders = true;
                int headerOverhead = 2; // Extra height used by string headers like "[MIDI CHANNELS]"

                int hMetadata = 3;
                int hStatus = 5;
                int hControls = 3;
                int hChannels = 16;
                boolean useHorizontalChannels = false;

                // Priority 1: Primary Layout
                int usedHeight = hMetadata + hStatus + hControls + hChannels + headerOverhead;
                int hPlaylist = showPlaylist ? Math.max(minPlaylistHeight, contentHeight - usedHeight) : 0;

                if (usedHeight + (showPlaylist ? minPlaylistHeight : 0) > contentHeight) {
                    // Priority 2: Horizontal Channels
                    hChannels = 4;
                    useHorizontalChannels = true;
                    headerOverhead = showPlaylist ? 4 : 2; // [CHANNELS] + [PLAYLIST]
                    usedHeight = hMetadata + hStatus + hControls + hChannels + headerOverhead;
                    hPlaylist = showPlaylist ? Math.max(minPlaylistHeight, contentHeight - usedHeight) : 0;

                    if (usedHeight + (showPlaylist ? minPlaylistHeight : 0) > contentHeight) {
                        // Priority 3: Squeeze Status and Controls
                        hStatus = statusPanel.calculateHeight(Math.max(1, contentHeight - hMetadata - hChannels - minPlaylistHeight - headerOverhead - 1));
                        hControls = controlsPanel.calculateHeight(Math.max(1, contentHeight - hMetadata - hChannels - hStatus - minPlaylistHeight - headerOverhead));
                        usedHeight = hMetadata + hStatus + hControls + hChannels + headerOverhead;
                        hPlaylist = showPlaylist ? Math.max(minPlaylistHeight, contentHeight - usedHeight) : 0;

                        if (usedHeight + (showPlaylist ? minPlaylistHeight : 0) > contentHeight) {
                            // Priority 4: Drop Headers completely to save space
                            showHeaders = false;
                            headerOverhead = 0;
                            hMetadata = 1;
                            hControls = 1;
                            hStatus = statusPanel.calculateHeight(Math.max(1, contentHeight - hMetadata - hChannels - minPlaylistHeight));
                            usedHeight = hMetadata + hStatus + hControls + hChannels;
                            hPlaylist = showPlaylist ? Math.max(minPlaylistHeight, contentHeight - usedHeight) : 0;

                            if (usedHeight + (showPlaylist ? minPlaylistHeight : 0) > contentHeight) {
                                // Priority 5: Absolute Minimum Clamp (Force display even if it overflows)
                                hMetadata = 1;
                                hStatus = 1;
                                hControls = 1;
                                hChannels = 4;
                                hPlaylist = minPlaylistHeight;
                            }
                        }
                    }
                }

                // Render Phase
                sb.append(doubleLine);
                sb.append("  Midiraja v").append(com.midiraja.Version.VERSION).append(" - Java 25 Native MIDI Player\n");
                sb.append(doubleLine);

                metadataPanel.render(sb, termWidth, hMetadata, showHeaders, engine);
                statusPanel.render(sb, termWidth, hStatus, showHeaders, engine);
                sb.append(singleLine);

                if (useHorizontalChannels) {
                    if (showHeaders) sb.append(" [MIDI CHANNELS ACTIVITY]\n");
                    channelPanel.render(sb, termWidth, hChannels, showHeaders, engine);
                    if (showPlaylist && hPlaylist > 0) {
                        sb.append(singleLine);
                        if (showHeaders) sb.append(" [PLAYLIST]\n\n");
                        renderPlaylist(sb, engine, termWidth, hPlaylist);
                    }
                } else {
                    int leftColWidth = Math.max(35, termWidth / 2);
                    int rightColWidth = termWidth - leftColWidth;

                    if (showHeaders) {
                        String leftHeader = " [MIDI CHANNELS ACTIVITY]";
                        String rightHeader = showPlaylist ? " [PLAYLIST]" : "";
                        sb.append(String.format("%-" + leftColWidth + "s%s\n\n", leftHeader, rightHeader));
                    }

                    StringBuilder channelSb = new StringBuilder();
                    channelPanel.render(channelSb, leftColWidth, hChannels, showHeaders, engine);
                    String[] channelLines = channelSb.toString().split("\n");

                    StringBuilder playlistSb = new StringBuilder();
                    if (showPlaylist) {
                        renderPlaylist(playlistSb, engine, rightColWidth, hChannels);
                    }
                    String[] playlistLines = playlistSb.toString().split("\n");

                    for (int i = 0; i < hChannels; i++) {
                        String leftStr = i < channelLines.length ? channelLines[i] : "";
                        if (leftStr.length() > leftColWidth) leftStr = leftStr.substring(0, leftColWidth);
                        else leftStr = leftStr + " ".repeat(leftColWidth - leftStr.length());
                        
                        String rightStr = i < playlistLines.length ? playlistLines[i] : "";
                        sb.append(leftStr).append(rightStr).append("\n");
                    }
                }

                sb.append(singleLine);
                controlsPanel.render(sb, termWidth, hControls, showHeaders, engine);
                sb.append(doubleLine);

                String finalStr = sb.toString().replace("\n", "\033[K\n");
                term.print(finalStr + "\033[J");

                Thread.sleep(50);
            }
        }
        catch (InterruptedException _) {}
    }

    private void renderPlaylist(StringBuilder sb, PlaybackEngine engine, int width, int allocatedHeight)
    {
        if (allocatedHeight <= 0) return;
        PlaylistContext context = engine.getContext();
        int listSize = context.files().size();
        int idx = context.currentIndex();

        int maxItems = allocatedHeight;
        int half = maxItems / 2;
        int startIdx = Math.max(0, idx - half);
        int endIdx = Math.min(listSize - 1, startIdx + maxItems - 1);
        startIdx = Math.max(0, endIdx - maxItems + 1);

        for (int i = startIdx; i <= endIdx; i++) {
            String marker = (i == idx) ? " >" : "  ";
            String name = context.files().get(i).getName();
            String status = (i == idx) ? "  (Playing)" : "";
            
            if (name.length() > width - status.length() - 8) {
                name = name.substring(0, Math.max(0, width - status.length() - 11)) + "...";
            }
            sb.append(String.format(" %s %d. %s%s\n", marker, i + 1, name, status));
        }
    }

    @Override
    public void runInputLoop(PlaybackEngine engine)
    {
        var term = TerminalIO.CONTEXT.get();
        try {
            while (engine.isPlaying()) {
                var key = term.readKey();
                LineUI.handleCommonInput(engine, key);
            }
        } catch (IOException _) {
            engine.requestStop(PlaybackEngine.PlaybackStatus.QUIT_ALL);
        }
    }
}