/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

import com.midiraja.engine.PlaylistContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NowPlayingPanel implements Panel {
    private LayoutConstraints constraints = new LayoutConstraints(80, 0, false, false);
    
    private long currentMicros = 0;
    private long totalMicros = 0;
    private float bpm = 120.0f;
    private double speed = 1.0;
    private double volumeScale = 1.0;
    private int transpose = 0;
    private boolean isPaused = false;
    @Nullable private PlaylistContext context;
    private final List<String> extraMetadata = new ArrayList<>();

    @Override
    public void onLayoutUpdated(LayoutConstraints bounds) {
        this.constraints = bounds;
    }

    public void updateState(long currentMicros, long totalMicros, float bpm, double speed, double volumeScale, int transpose, boolean isPaused, PlaylistContext context) {
        this.currentMicros = currentMicros;
        this.totalMicros = totalMicros;
        this.bpm = bpm;
        this.speed = speed;
        this.volumeScale = volumeScale;
        this.transpose = transpose;
        this.isPaused = isPaused;
        this.context = context;
    }
    
    public void setExtraMetadata(List<String> metadata) {
        this.extraMetadata.clear();
        this.extraMetadata.addAll(metadata);
    }

    @Override public void onPlaybackStateChanged() {}
    @Override public void onTick(long currentMicroseconds) { this.currentMicros = currentMicroseconds; }
    @Override public void onTempoChanged(float bpm) { this.bpm = bpm; }
    @Override public void onChannelActivity(int channel, int velocity) {}

    private String formatTime(long microseconds, boolean includeHours) {
        long totalSeconds = microseconds / 1000000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (includeHours) return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String buildProgressBar(int percent, int termWidth) {
        int barWidth = Math.max(10, termWidth - 40);
        int filled = (int) ((percent / 100.0) * barWidth);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barWidth; i++) {
            if (i < filled - 1) bar.append("=");
            else if (i == filled - 1) bar.append(">");
            else bar.append("-");
        }
        bar.append("]");
        return bar.toString();
    }

    @Override
    public void render(StringBuilder sb) {
        if (constraints.height() <= 0 || context == null) return;
        
        String title = context.sequenceTitle() != null ? context.sequenceTitle() : "";
        String fileName = context.files().get(context.currentIndex()).getName();
        String displayTitle = title.isEmpty() ? fileName : title + " (" + fileName + ")";
        
        boolean incHrs = (totalMicros / 1000000) >= 3600;
        String totStr = formatTime(totalMicros, incHrs);
        String curStr = formatTime(currentMicros, incHrs);
        int percent = (int) (totalMicros > 0 ? (currentMicros * 100 / totalMicros) : 0);
        percent = Math.min(100, Math.max(0, percent));
        String bar = buildProgressBar(percent, constraints.width());
        String pauseIndicator = isPaused ? "\033[1;33m[PAUSED]\033[0m " : "";
        String portInfo = String.format("[%d] %s", context.targetPort().index(), context.targetPort().name());
        
        int h = constraints.height();
        
        // Consistent Alignment Prefix
        String p1 = "    ";
        
        // Field Data
        String fTitle = String.format("%-10s %s", "Title:", truncate(displayTitle, constraints.width() - 15));
        String fTime  = String.format("%-10s %s%s / %s  %s  %3d%%", "Time:", pauseIndicator, curStr, totStr, bar, percent);
        String fVol   = String.format("Volume: %d%%", (int)(volumeScale * 100));
        String fPort  = String.format("Port: %s", portInfo);
        String fTempo = String.format("Tempo: %3.0f BPM (%.1fx)", bpm, speed);
        String fTrans = String.format("Transpose: %+d", transpose);
        
        if (h <= 3) {
            sb.append(p1).append(fTitle).append("\n");
            sb.append(truncate(p1 + fTime, constraints.width() + (isPaused ? 11 : 0))).append("\n");
            
            // Pack all remaining into 1 line
            String packed = String.format("%s | %s | %s | %s", fVol.replace("Volume: ", "Vol: "), fPort.replace("Port: ", ""), fTempo.replace("Tempo: ", "Spd:").replace(" BPM (", "("), fTrans.replace("Transpose: ", "Tr: "));
            sb.append(truncate(p1 + packed, constraints.width())).append("\n");
        } 
        else if (h == 4) {
            sb.append(p1).append(fTitle).append("\n");
            sb.append(truncate(p1 + fTime, constraints.width() + (isPaused ? 11 : 0))).append("\n");
            
            // Pack into 2 lines
            sb.append(truncate(p1 + String.format("%-15s | %s", fVol.replace("Volume: ", "Vol: "), fPort), constraints.width())).append("\n");
            sb.append(truncate(p1 + String.format("%-15s | %s", fTempo.replace("Tempo: ", "BPM: "), fTrans), constraints.width())).append("\n");
        }
        else if (h == 5) {
            sb.append(p1).append(fTitle).append("\n");
            sb.append(truncate(p1 + fTime, constraints.width() + (isPaused ? 11 : 0))).append("\n");
            sb.append(truncate(p1 + fVol, constraints.width())).append("\n");
            sb.append(truncate(p1 + fPort, constraints.width())).append("\n");
            
            // Pack Tempo and Transpose into 1 line
            sb.append(truncate(p1 + String.format("%-25s | %s", fTempo, fTrans), constraints.width())).append("\n");
        }
        else {
            // h >= 6 (Fully Unpacked)
            sb.append(p1).append(fTitle).append("\n");
            sb.append(truncate(p1 + fTime, constraints.width() + (isPaused ? 11 : 0))).append("\n");
            sb.append(truncate(p1 + String.format("%-10s %d%%", "Volume:", (int)(volumeScale * 100)), constraints.width())).append("\n");
            sb.append(truncate(p1 + String.format("%-10s %s", "Port:", portInfo), constraints.width())).append("\n");
            sb.append(truncate(p1 + String.format("%-10s %3.0f BPM (%.1fx)", "Tempo:", bpm, speed), constraints.width())).append("\n");
            sb.append(truncate(p1 + String.format("%-10s %+d", "Transpose:", transpose), constraints.width())).append("\n");
            
            // Fill remaining with extra metadata
            int linesUsed = 6;
            for (int i = 0; i < extraMetadata.size() && linesUsed < h; i++) {
                sb.append(String.format("      %s\n", truncate(extraMetadata.get(i), constraints.width() - 6)));
                linesUsed++;
            }
            
            // Fill any remaining space with blank lines
            while (linesUsed < h) {
                sb.append("\n");
                linesUsed++;
            }
        }
    }
}
