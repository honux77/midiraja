package com.midiraja.engine;

import com.midiraja.io.TerminalIO;
import com.midiraja.midi.MidiOutProvider;

import javax.sound.midi.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

public class PlaybackEngine {
    private final Sequence sequence;
    private final MidiOutProvider provider;
    private final TerminalIO terminalIO;
    
    private volatile long currentTick = 0;
    private volatile long seekTarget = -1;
    private volatile float currentBpm = 120.0f;
    private volatile double volumeScale = 1.0;
    private volatile boolean isPlaying = false;
    
    private final double[] channelLevels = new double[16];
    private final List<MidiEvent> sortedEvents;
    private final int resolution;

    public PlaybackEngine(Sequence sequence, MidiOutProvider provider, TerminalIO terminalIO, int initialVolumePercent) {
        this.sequence = sequence;
        this.provider = provider;
        this.terminalIO = terminalIO;
        this.volumeScale = initialVolumePercent / 100.0;
        this.resolution = sequence.getResolution();
        
        this.sortedEvents = Arrays.stream(sequence.getTracks())
                .flatMap(track -> IntStream.range(0, track.size()).mapToObj(track::get))
                .sorted(Comparator.comparingLong(MidiEvent::getTick))
                .toList();
    }

    public void start() throws Exception {
        isPlaying = true;
        
        // Start UI Thread (30 FPS)
        var uiThread = new Thread(this::uiLoop);
        uiThread.setDaemon(true);
        uiThread.start();

        // Start Input Thread (Async IoC)
        var inputThread = new Thread(this::inputLoop);
        inputThread.setDaemon(true);
        inputThread.start();

        try {
            playLoop();
        } finally {
            // Ensure all notes are silenced when playLoop exits (e.g., 'q' pressed or song finished)
            provider.panic();
            isPlaying = false;
        }
        
        uiThread.join(500);
    }

    private void playLoop() throws Exception {
        long lastTick = 0;
        int eventIndex = 0;
        long startTimeNanos = System.nanoTime();
        double ticksToNanos = (60000000000.0 / (currentBpm * resolution));

        while (isPlaying && eventIndex < sortedEvents.size()) {
            // Check if an external seek was requested
            if (seekTarget != -1) {
                long target = seekTarget;
                seekTarget = -1; // Reset flag
                
                // 1. Panic: Stop all currently ringing notes
                provider.panic();
                
                // 2. Reset UI levels and tempo
                currentBpm = 120.0f;
                Arrays.fill(channelLevels, 0.0);
                
                // 3. Fast-forward & Chase state from tick 0 to target
                int newIndex = 0;
                for (MidiEvent ev : sortedEvents) {
                    if (ev.getTick() >= target) break;
                    processChaseEvent(ev);
                    newIndex++;
                }
                
                // 4. Resume playback from the new position
                currentTick = target;
                lastTick = target;
                eventIndex = newIndex;
                
                // Reset timing reference after seek
                ticksToNanos = (60000000000.0 / (currentBpm * resolution));
                startTimeNanos = System.nanoTime() - (long) (currentTick * ticksToNanos);
                continue;
            }

            var event = sortedEvents.get(eventIndex);
            long tick = event.getTick();
            
            if (tick > lastTick) {
                long targetNanos = startTimeNanos + (long) (tick * ticksToNanos);
                
                // High-resolution delay
                long currentNanos = System.nanoTime();
                while (currentNanos < targetNanos) {
                    long remainingMs = (targetNanos - currentNanos) / 1000000;
                    if (remainingMs > 1) {
                        Thread.sleep(remainingMs - 1);
                    } else {
                        Thread.yield(); // Spin-wait for the last millisecond for accuracy
                    }
                    currentNanos = System.nanoTime();
                }
            }

            processEvent(event);
            lastTick = tick;
            currentTick = tick;
            eventIndex++;
            
            // Recalculate timing ratio if BPM changed during processEvent
            ticksToNanos = (60000000000.0 / (currentBpm * resolution));
        }
    }

    private void processChaseEvent(MidiEvent event) {
        var msg = event.getMessage();
        var raw = msg.getMessage();
        int status = raw[0] & 0xFF;

        // Meta Tempo
        if (status == 0xFF && raw.length >= 6 && (raw[1] & 0xFF) == 0x51) {
            int mspqn = ((raw[3] & 0xFF) << 16) | ((raw[4] & 0xFF) << 8) | (raw[5] & 0xFF);
            if (mspqn > 0) {
                currentBpm = 60000000.0f / mspqn;
            }
            return;
        }

        if (status < 0xF0) {
            int cmd = status & 0xF0;
            // CHASE ONLY: Program Change(0xC0), Control Change(0xB0), Pitch Bend(0xE0)
            if (cmd == 0xC0 || cmd == 0xB0 || cmd == 0xE0) {
                // Apply volume scaling if it's CC 7
                if (cmd == 0xB0 && raw.length >= 3 && raw[1] == 7) {
                    int vol = (int) ((raw[2] & 0xFF) * volumeScale);
                    raw[2] = (byte) Math.max(0, Math.min(127, vol));
                }
                try {
                    provider.sendMessage(raw);
                } catch (Exception _) {}
            }
        }
    }

    private void processEvent(MidiEvent event) {
        var msg = event.getMessage();
        var raw = msg.getMessage();
        int status = raw[0] & 0xFF;

        if (status == 0xFF && raw.length >= 6 && (raw[1] & 0xFF) == 0x51) {
            int mspqn = ((raw[3] & 0xFF) << 16) | ((raw[4] & 0xFF) << 8) | (raw[5] & 0xFF);
            if (mspqn > 0) {
                currentBpm = 60000000.0f / mspqn;
            }
            return;
        }

        if (status < 0xF0) {
            int cmd = status & 0xF0;
            int ch = status & 0x0F;

            if (cmd == 0xB0 && raw.length >= 3 && raw[1] == 7) {
                int vol = (int) ((raw[2] & 0xFF) * volumeScale);
                raw[2] = (byte) Math.max(0, Math.min(127, vol));
            }

            if (cmd == 0x90 && raw.length >= 3 && (raw[2] & 0xFF) > 0) {
                channelLevels[ch] = Math.max(channelLevels[ch], (raw[2] & 0xFF) / 127.0);
            }

            try {
                provider.sendMessage(raw);
            } catch (Exception _) {}
        }
    }

    private void inputLoop() {
        try {
            while (isPlaying) {
                var key = terminalIO.readKey();
                switch (key) {
                    case VOLUME_UP -> {
                        volumeScale = Math.min(1.0, volumeScale + 0.05);
                        applyVolumeInstantly();
                    }
                    case VOLUME_DOWN -> {
                        volumeScale = Math.max(0.0, volumeScale - 0.05);
                        applyVolumeInstantly();
                    }
                    case SEEK_FORWARD -> {
                        // Seek roughly +10 seconds based on current BPM
                        long ticksToSeekFwd = (long) ((10000.0 * currentBpm * resolution) / 60000.0);
                        long targetF = currentTick + ticksToSeekFwd;
                        seekTarget = Math.min(targetF, sequence.getTickLength());
                    }
                    case SEEK_BACKWARD -> {
                        // Seek roughly -10 seconds based on current BPM
                        long ticksToSeekBwd = (long) ((10000.0 * currentBpm * resolution) / 60000.0);
                        seekTarget = Math.max(0, currentTick - ticksToSeekBwd);
                    }
                    case QUIT -> isPlaying = false;
                    default -> {}
                }
            }
        } catch (IOException _) {}
    }

    private void applyVolumeInstantly() {
        int vol = (int) (100 * volumeScale);
        byte volByte = (byte) Math.max(0, Math.min(127, vol));
        
        IntStream.range(0, 16).forEach(ch -> {
            try {
                provider.sendMessage(new byte[]{(byte) (0xB0 | ch), 7, volByte});
            } catch (Exception _) {}
        });
    }

    private void uiLoop() {
        if (!terminalIO.isInteractive()) {
            terminalIO.println("Playing (Interactive UI disabled)...");
            return; // UI 루프 종료, 시스템 자원 절약
        }

        String[] blocks = {" ", " ", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
        while (isPlaying) {
            var sb = new StringBuilder("\rVol:[");
            for (int i = 0; i < 16; i++) {
                int lv = (int) Math.round(channelLevels[i] * 8);
                sb.append(blocks[Math.max(0, Math.min(8, lv))]);
                channelLevels[i] = Math.max(0, channelLevels[i] - 0.1);
            }
            sb.append("] ");
            double pct = sequence.getTickLength() > 0 ? (double) currentTick / sequence.getTickLength() : 0;
            sb.append(String.format("%3d%% (BPM: %5.1f, Vol: %3d%%) ", 
                (int)(pct*100), currentBpm, (int)(volumeScale*100)));
            terminalIO.print(sb.toString());
            try { Thread.sleep(50); } catch (InterruptedException _) {}
        }
        terminalIO.println("");
    }
}