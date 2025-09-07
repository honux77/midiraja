/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import com.midiraja.engine.PlaybackEngine;
import com.midiraja.io.TerminalIO;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class DashboardUI implements PlaybackUI
{
    private final MetadataPanel metadataPanel = new MetadataPanel();
    private final StatusPanel statusPanel = new StatusPanel();
    private final ChannelActivityPanel rawChannelPanel = new ChannelActivityPanel();
    private final ControlsPanel controlsPanel = new ControlsPanel();
    private final PlaylistPanel rawPlaylistPanel = new PlaylistPanel();
    
    private final CompositePanel metaStatusComposite = new CompositePanel(metadataPanel, statusPanel);
    private final TitledPanel nowPlayingPanel = new TitledPanel("NOW PLAYING", metaStatusComposite);
    private final TitledPanel channelPanel = new TitledPanel("MIDI CHANNELS", rawChannelPanel);
    private final TitledPanel titledPlaylistPanel = new TitledPanel("PLAYLIST", rawPlaylistPanel);

    private final DashboardLayoutManager layoutManager = new DashboardLayoutManager();

    @Override
    public void runRenderLoop(PlaybackEngine engine)
    {
        var term = TerminalIO.CONTEXT.get();
        if (!term.isInteractive()) return;

        // Wire up listeners
        engine.addPlaybackEventListener(metadataPanel);
        engine.addPlaybackEventListener(statusPanel);
        engine.addPlaybackEventListener(rawChannelPanel);
        engine.addPlaybackEventListener(controlsPanel);

        metadataPanel.updateContext(engine.getContext());
        rawPlaylistPanel.updateContext(engine.getContext());
        rawChannelPanel.updatePrograms(engine.getChannelPrograms());
        metaStatusComposite.setHeights(1); // Metadata + Header

        int lastWidth = -1;
        int lastHeight = -1;

        try {
            while (engine.isPlaying()) {
                int termWidth = term.getWidth();
                int termHeight = term.getHeight();

                if (termWidth != lastWidth || termHeight != lastHeight) {
                    recalculateLayout(termWidth, termHeight, engine.getContext().files().size());
                    lastWidth = termWidth;
                    lastHeight = termHeight;
                }

                statusPanel.updateState(engine.getCurrentMicroseconds(), engine.getTotalMicroseconds(), 
                    engine.getCurrentBpm(), engine.getCurrentSpeed(), engine.getVolumeScale(), 
                    engine.getCurrentTranspose(), engine.isPaused(), engine.getContext());

                StringBuilder sb = new StringBuilder();
                sb.append("\033[H");

                String banner = String.format(" Midiraja v%s - Terminal Lover's MIDI Player", com.midiraja.Version.VERSION);
                int bannerPadding = Math.max(0, termWidth - banner.length());
                sb.append("\033[7m").append(banner).append(" ".repeat(bannerPadding)).append("\033[0m\n");

                nowPlayingPanel.render(sb);

                Map<DashboardLayoutManager.PanelId, LayoutConstraints> layout = 
                    layoutManager.calculateLayout(termWidth, termHeight, engine.getContext().files().size());
                
                LayoutConstraints chanC = Objects.requireNonNull(layout.get(DashboardLayoutManager.PanelId.CHANNELS));
                LayoutConstraints playC = Objects.requireNonNull(layout.get(DashboardLayoutManager.PanelId.PLAYLIST));

                if (chanC.isHorizontal()) {
                    channelPanel.render(sb);
                    if (playC.height() > 0) {
                        titledPlaylistPanel.render(sb);
                    }
                } else {
                    StringBuilder chanSb = new StringBuilder();
                    channelPanel.render(chanSb);
                    String[] chanLines = chanSb.toString().split("\n");

                    StringBuilder playSb = new StringBuilder();
                    if (engine.getContext().files().size() > 1) {
                        titledPlaylistPanel.render(playSb);
                    }
                    String[] playLines = playSb.toString().split("\n");

                    for (int i = 0; i < chanC.height(); i++) {
                        String left = i < chanLines.length ? chanLines[i] : "";
                        if (left.length() > chanC.width()) left = left.substring(0, chanC.width());
                        else left = left + " ".repeat(chanC.width() - left.length());
                        
                        String right = i < playLines.length ? playLines[i] : "";
                        sb.append(left).append(right).append("\n");
                    }
                }

                controlsPanel.render(sb);
                sb.append("=".repeat(termWidth));

                String finalStr = sb.toString().replace("\n", "\033[K\n");
                term.print(finalStr + "\033[J");

                Thread.sleep(50);
            }
        } catch (InterruptedException _) {}
    }

    private void recalculateLayout(int width, int height, int listSize) {
        Map<DashboardLayoutManager.PanelId, LayoutConstraints> layout = layoutManager.calculateLayout(width, height, listSize);
        nowPlayingPanel.onLayoutUpdated(Objects.requireNonNull(layout.get(DashboardLayoutManager.PanelId.METADATA)));
        channelPanel.onLayoutUpdated(Objects.requireNonNull(layout.get(DashboardLayoutManager.PanelId.CHANNELS)));
        titledPlaylistPanel.onLayoutUpdated(Objects.requireNonNull(layout.get(DashboardLayoutManager.PanelId.PLAYLIST)));
        controlsPanel.onLayoutUpdated(Objects.requireNonNull(layout.get(DashboardLayoutManager.PanelId.CONTROLS)));
    }

    @Override
    public void runInputLoop(PlaybackEngine engine)
    {
        var term = TerminalIO.CONTEXT.get();
        try {
            while (engine.isPlaying()) {
                var key = term.readKey();
                switch (key) {
                    case PAUSE -> engine.togglePause();
                    case VOLUME_UP -> engine.adjustVolume(0.05);
                    case VOLUME_DOWN -> engine.adjustVolume(-0.05);
                    case NEXT_TRACK -> engine.requestStop(PlaybackEngine.PlaybackStatus.NEXT);
                    case PREV_TRACK -> engine.requestStop(PlaybackEngine.PlaybackStatus.PREVIOUS);
                    case TRANSPOSE_UP -> engine.adjustTranspose(1);
                    case TRANSPOSE_DOWN -> engine.adjustTranspose(-1);
                    case SPEED_UP -> engine.adjustSpeed(0.1);
                    case SPEED_DOWN -> engine.adjustSpeed(-0.1);
                    case SEEK_FORWARD -> engine.seekRelative(10_000_000);
                    case SEEK_BACKWARD -> engine.seekRelative(-10_000_000);
                    case QUIT -> engine.requestStop(PlaybackEngine.PlaybackStatus.QUIT_ALL);
                    default -> {}
                }
            }
        } catch (IOException _) {
            engine.requestStop(PlaybackEngine.PlaybackStatus.QUIT_ALL);
        }
    }
}
