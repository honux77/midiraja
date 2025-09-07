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

        // Static lines: Top Banner(1) + Bottom Border(1) = 2
        int hMetadata = 1; // Pure title line
        int hStatus = 1;   // Base status line
        int hControls = 1; // Base controls line
        
        int hChannels = 0;
        int hPlaylist = 0;
        boolean isHorizontal = false;

        // Base structural overhead:
        // TopBanner(1) + NowPlaying[Header(1) + Bottom(1)] + Controls(hControls) + VeryBottom(1)
        // Overhead = 4 + hMetadata + hStatus + hControls
        
        // Target Two-Column: Channels needs Header(1) + 16 + BottomBorder(1) = 18 lines.
        // Total required for Two-Column = 4 + 1(Meta) + 1(Status) + 1(Control) + 18(Channels block) = 25 lines.
        
        if (termHeight >= 25) {
            isHorizontal = false;
            hChannels = 18;  // 18 lines so the inner panel gets 18 - 2 = 16
            hPlaylist = 18;  
            
            int surplus = termHeight - 25;
            
            // Distribute surplus
            // StatusPanel needs at least 5 lines (so hStatus = 5) to show full details.
            // It starts at 1, so we need to add up to 4. Wait, if we add 4, it becomes 5!
            // Why isn't it showing? Let's check TitledPanel overhead.
            // hNowPlaying = hMetadata(1) + hStatus(1+addStatus) + Overhead(2).
            // So if addStatus is 4, hStatus is 5.
            // Let's allow addStatus up to 6 so it's very clear.
            int addStatus = Math.min(surplus, 6);
            hStatus += addStatus;
            surplus -= addStatus;
            
            int addControls = Math.min(surplus, 2);
            hControls += addControls;
            surplus -= addControls;
            
            // If there's still surplus, give it to Channels and Playlist!
            hChannels += surplus;
            hPlaylist += surplus;
        } else {
            isHorizontal = true; // Stacked Mode
            // Calculate available lines for center blocks
            // Center = termHeight - 4 (struct) - hMetadata(1) - hStatus(1) - hControls(1) = termHeight - 7
            int centerSpace = termHeight - 7;
            
            // Stacked needs TitledChannels(Header+4+Bottom = 6) and TitledPlaylist(Header+M+Bottom = 3+).
            if (centerSpace >= 6) {
                hChannels = 6; // 6 lines so the inner panel gets 6 - 2 = 4
                if (showPlaylist) {
                    hPlaylist = Math.max(0, centerSpace - 6); 
                }
            } else {
                hChannels = 0;
                if (showPlaylist) {
                    hPlaylist = Math.max(0, centerSpace - 2);
                }
            }
        }
        
        // Composite Panel height is Metadata + Status
        int hNowPlaying = hMetadata + hStatus;

        layout.put(PanelId.METADATA, new LayoutConstraints(termWidth, hNowPlaying, true, false));
        layout.put(PanelId.CONTROLS, new LayoutConstraints(termWidth, hControls, false, false));
        
        if (isHorizontal) { // Stacked Mode
            layout.put(PanelId.CHANNELS, new LayoutConstraints(termWidth, hChannels, true, true));
            layout.put(PanelId.PLAYLIST, new LayoutConstraints(termWidth, hPlaylist, true, false));
        } else { // Two-Column Mode
            int leftColWidth = Math.max(35, termWidth / 2);
            int rightColWidth = termWidth - leftColWidth;
            // False for the 4th parameter because channels are NOT lying horizontally
            layout.put(PanelId.CHANNELS, new LayoutConstraints(leftColWidth, hChannels, true, false));
            layout.put(PanelId.PLAYLIST, new LayoutConstraints(rightColWidth, hPlaylist, true, false));
        }
        return layout;
    }
}
