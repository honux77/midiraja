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

        // Minimum Heights:
        // NowPlaying: Header(1) + MinContent(2) + Bottom(1) = 4
        // Controls: 1
        // Channels Stacked: Header(1) + Content(4) + Bottom(1) = 6
        // Playlist Stacked: Header(1) + MinContent(Math.min(3, listSize)) + Bottom(1) = 2 + MinItems
        
        int playListContentMin = Math.min(3, listSize);
        int hPlaylistMin = showPlaylist ? (2 + playListContentMin) : 0;
        
        int minStackedHeight = 1 + 4 + 6 + hPlaylistMin + 1 + 1; // Banner + NowPlaying + Chan + Play + Ctrl + Bottom = 13 + hPlaylistMin
        
        // Two-Column mode threshold:
        // Banner(1) + NowPlaying(4) + TwoCol(Header+16+Bot=18) + Ctrl(1) + Bot(1) = 25
        if (safeHeight >= 25) {
            isHorizontal = false;
            hNowPlaying = 4; // 2 lines of content
            hChannels = 18;
            hPlaylist = showPlaylist ? 18 : 0;
            hControls = 1;
            
            int surplus = safeHeight - 25;
            
            // 1. Give NowPlaying up to 6 lines of content (hNowPlaying = 8)
            int addNow = Math.min(surplus, 4);
            hNowPlaying += addNow;
            surplus -= addNow;
            
            // 2. Give Controls up to 3 lines (hControls = 3)
            int addCtrl = Math.min(surplus, 2);
            hControls += addCtrl;
            surplus -= addCtrl;
            
            // 3. Give remaining to extra metadata in NowPlaying (up to 12 lines)
            int addMeta = Math.min(surplus, 4);
            hNowPlaying += addMeta;
            surplus -= addMeta;
            
            // 4. Any remaining infinite space goes to Channels and Playlist
            hChannels += surplus;
            if (showPlaylist) hPlaylist += surplus;
            
        } else {
            isHorizontal = true; // Stacked Mode
            
            hNowPlaying = 4; // 2 lines of content
            hControls = 1;
            
            // Distribute remaining space carefully
            int centerSpace = safeHeight - 1 - hNowPlaying - hControls - 1; // Subtract static lines
            
            if (centerSpace >= 6 + hPlaylistMin) {
                hChannels = 6;
                hPlaylist = showPlaylist ? (centerSpace - 6) : 0;
                
                // If playlist is huge, maybe steal for NowPlaying?
                if (hPlaylist > hPlaylistMin + 2) {
                    int steal = Math.min(2, hPlaylist - (hPlaylistMin + 2));
                    hPlaylist -= steal;
                    hNowPlaying += steal;
                }
            } else if (centerSpace >= hPlaylistMin) {
                // Not enough for both. Drop channels to guarantee playlist visibility!
                hChannels = 0;
                hPlaylist = showPlaylist ? centerSpace : 0;
            } else {
                // Extreme squeeze. Drop playlist too? No, just give it whatever is left.
                hChannels = 0;
                hPlaylist = showPlaylist ? centerSpace : 0;
                
                // If still negative, we must squeeze NowPlaying
                if (hPlaylist <= 2 && showPlaylist) {
                    hNowPlaying = Math.max(3, hNowPlaying - 1); // Compress to 1 line of content
                    hPlaylist = safeHeight - 1 - hNowPlaying - hControls - 1;
                }
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
