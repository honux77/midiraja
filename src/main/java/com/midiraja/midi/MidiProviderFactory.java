package com.midiraja.midi;

import com.midiraja.midi.os.LinuxProvider;
import com.midiraja.midi.os.MacProvider;
import com.midiraja.midi.os.WindowsProvider;

public class MidiProviderFactory {
    private enum OS { MAC, WINDOWS, LINUX, UNKNOWN }

    public static MidiOutProvider createProvider() {
        String osName = System.getProperty("os.name").toLowerCase();
        
        OS os = osName.contains("mac") ? OS.MAC :
                osName.contains("win") ? OS.WINDOWS :
                osName.contains("nix") || osName.contains("nux") || osName.contains("aix") ? OS.LINUX : 
                OS.UNKNOWN;

        return switch (os) {
            case MAC -> new MacProvider();
            case WINDOWS -> new WindowsProvider();
            case LINUX -> new LinuxProvider();
            case UNKNOWN -> throw new UnsupportedOperationException("Unsupported OS for native MIDI: " + osName);
        };
    }
}