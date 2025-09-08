package com.midiraja.ui;

import java.util.HashMap;
import java.util.Map;

public class DashboardLayoutManager
{
    public enum PanelId { METADATA, CHANNELS, PLAYLIST, CONTROLS }

    public Map<PanelId, LayoutConstraints> calculateLayout(int termWidth, int termHeight, int listSize)
    {
        Map<PanelId, LayoutConstraints> layout = new HashMap<>();
        boolean showPlaylist = listSize > 1;

        // Safety Margin: Some terminals scroll if we print to the very last character of the last line.
        // We subtract 1 from the total height to be safe.
        int safeHeight = termHeight - 1;

        // Static lines: Top Banner(1) + Bottom Border(1) = 2
        int hNowPlaying = 3; 
        int hControls = 1;   
        int staticOverhead = 2 + 2; // Banner/Bottom + NowPlaying wrapper
        
        int hChannels = 0;
        int hPlaylist = 0;
        boolean isHorizontal = false;

        // Target Two-Column requires:
        // Struct(4) + NowPlayingMin(3) + Center(18) + Controls(1) = 26
        if (safeHeight >= 26) {
            isHorizontal = false;
            hChannels = 18;
            hPlaylist = 18;
            
            int surplus = safeHeight - 26;
            
            // 1. Give NowPlaying up to 10 lines (+7) to show extra metadata
            int addNow = Math.min(surplus, 7);
            hNowPlaying += addNow;
            surplus -= addNow;
            
            // 2. Give Controls up to 3 lines (+2)
            int addControls = Math.min(surplus, 2);
            hControls += addControls;
            surplus -= addControls;
            
            // 3. Give remaining to Playlist and Channels instead of infinite NowPlaying
            // NowPlaying max out at 8 lines (enough for full details + 2 lines of metadata)
            int addNowExtra = Math.min(surplus, 2);
            hNowPlaying += addNowExtra;
            surplus -= addNowExtra;
            
            // Give all remaining surplus to Channels and Playlist
            hChannels += surplus;
            hPlaylist += surplus;
            
        } else {
            isHorizontal = true; // Stacked Mode
            // Calculate available lines for center blocks
            // Center = termHeight - 4 (struct) - hNowPlaying(3) - hControls(1) = termHeight - 8
            int centerSpace = safeHeight - 8;
            
            if (centerSpace >= 6) { // Enough for Channels block (Header+4+Bottom=6)
                hChannels = 6;
                if (showPlaylist) {
                    hPlaylist = Math.max(0, centerSpace - 6); 
                }
            } else {
                hChannels = 0;
                if (showPlaylist) {
                    hPlaylist = Math.max(0, centerSpace);
                }
            }
            
            // If we have extra space inside center, distribute to NowPlaying or Controls?
            // Wait, we just gave all to hPlaylist. If hPlaylist > 6, maybe steal some for NowPlaying?
            if (hPlaylist > 8) {
                int steal = Math.min(3, hPlaylist - 8);
                hPlaylist -= steal;
                hNowPlaying += steal;
            }
        }

        layout.put(PanelId.METADATA, new LayoutConstraints(termWidth, hNowPlaying, true, false));
        layout.put(PanelId.CONTROLS, new LayoutConstraints(termWidth, hControls, false, false));
        
        if (isHorizontal) {
            layout.put(PanelId.CHANNELS, new LayoutConstraints(termWidth, hChannels, true, true));
            layout.put(PanelId.PLAYLIST, new LayoutConstraints(termWidth, hPlaylist, true, false));
        } else {
            int leftColWidth = Math.max(35, termWidth / 2);
            int rightColWidth = termWidth - leftColWidth;
            layout.put(PanelId.CHANNELS, new LayoutConstraints(leftColWidth, hChannels, true, false));
            layout.put(PanelId.PLAYLIST, new LayoutConstraints(rightColWidth, hPlaylist, true, false));
        }
        return layout;
    }
}
