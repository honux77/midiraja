/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.midi.beep;

import com.midiraja.midi.MidiPort;
import com.midiraja.midi.NativeAudioEngine;
import com.midiraja.midi.SoftSynthProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

@SuppressWarnings({"ThreadPriorityCheck", "EmptyCatch"})
public class BeepSynthProvider implements SoftSynthProvider
{
    private final NativeAudioEngine audio;
    private final String mode;
    private final int sampleRate = 44100;
    
    private @Nullable Thread renderThread;
    private volatile boolean running = false;
    private volatile boolean renderPaused = false;

    // Track active notes across all channels
    private static class ActiveNote {
        int channel;
        int note;
        double frequency;
        double phase = 0.0;
        long activeFrames = 0; // For envelope/decay tracking
    }
    private final List<ActiveNote> activeNotes = new CopyOnWriteArrayList<>();

    // 1-bit Delta-Sigma state
    private double errorAccumulator = 0.0;

    // Arpeggio state
    private int arpeggioIndex = 0;
    private int framesSinceSwitch = 0;
    private final int framesPerSwitch = sampleRate / 30; // 30 Hz switch rate (approx 33ms per note)
    private double globalPhase = 0.0;

    // Pitches for MIDI note numbers
    private static final double[] PITCHES = new double[128];
    static {
        for (int i = 0; i < 128; i++) {
            PITCHES[i] = 440.0 * Math.pow(2.0, (i - 69) / 12.0);
        }
    }

    public BeepSynthProvider(NativeAudioEngine audio, String mode)
    {
        this.audio = audio;
        this.mode = mode.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public List<MidiPort> getOutputPorts()
    {
        return List.of(new MidiPort(0, "Midiraja 1-Bit " + (mode.equals("pwm") ? "PWM" : "Arpeggiator")));
    }

    @Override
    public void openPort(int portIndex) throws Exception
    {
        audio.init(sampleRate, 1, 4096); // Mono output
        startRenderThread();
    }

    @Override
    public void loadSoundbank(String path) throws Exception
    {
        // Not applicable for mathematical synthesis
    }

    private void startRenderThread()
    {
        running = true;
        renderThread = new Thread(() -> {
            final int framesToRender = 512;
            short[] pcmBuffer = new short[framesToRender]; // Mono

            while (running)
            {
                if (renderPaused)
                {
                    try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    continue;
                }

                // Snap active notes to a local array to prevent modification during loop
                List<ActiveNote> currentNotes = new ArrayList<>(activeNotes);
                // Simple decay: remove notes that have been playing too long to prevent infinite drones
                // (PC speaker has no real velocity envelope, so we just cut them off after ~2 seconds)
                activeNotes.removeIf(n -> n.activeFrames > sampleRate * 2.0);

                if (currentNotes.isEmpty()) {
                    for (int i = 0; i < framesToRender; i++) pcmBuffer[i] = 0;
                } else if (mode.equals("pwm")) {
                    renderPwm(currentNotes, pcmBuffer, framesToRender);
                } else {
                    renderArpeggio(currentNotes, pcmBuffer, framesToRender);
                }

                audio.push(pcmBuffer);
            }
        });
        renderThread.setPriority(Thread.MAX_PRIORITY);
        renderThread.setDaemon(true);
        renderThread.start();
    }

    private void renderPwm(List<ActiveNote> notes, short[] buffer, int frames)
    {
        double volumeScale = 1.0 / Math.max(1, notes.size());
        
        for (int i = 0; i < frames; i++) {
            double mixedSample = 0.0;
            
            for (ActiveNote n : notes) {
                // Generate square wave
                n.phase += n.frequency / sampleRate;
                if (n.phase >= 1.0) n.phase -= 1.0;
                double square = n.phase < 0.5 ? 1.0 : -1.0;
                mixedSample += square;
                n.activeFrames++;
            }
            
            // Normalize sum to roughly [-1.0, 1.0]
            mixedSample *= volumeScale;

            // 1-Bit Quantization via First-Order Delta-Sigma Modulator
            double target = mixedSample;
            double outputBit = (target + errorAccumulator) > 0.0 ? 1.0 : -1.0;
            errorAccumulator += (target - outputBit);

            // Output purely 1 or -1 (scaled to 16-bit max amplitude)
            buffer[i] = (short) (outputBit * 8000); // 8000 instead of 32767 to save ears!
        }
    }

    private void renderArpeggio(List<ActiveNote> notes, short[] buffer, int frames)
    {
        // For classic arpeggio, restrict to maximum 4 notes (most recently played)
        List<ActiveNote> arpeggioNotes;
        if (notes.size() > 4) {
            arpeggioNotes = notes.subList(notes.size() - 4, notes.size());
        } else {
            arpeggioNotes = notes;
        }

        for (int i = 0; i < frames; i++) {
            if (arpeggioIndex >= arpeggioNotes.size()) {
                arpeggioIndex = 0;
            }
            
            ActiveNote currentNote = arpeggioNotes.get(arpeggioIndex);
            
            // Use a continuous global phase to prevent popping/clicking on note switch
            globalPhase += currentNote.frequency / sampleRate;
            if (globalPhase >= 1.0) globalPhase -= 1.0;
            
            double square = globalPhase < 0.5 ? 1.0 : -1.0;
            
            // Raw 1-bit output
            buffer[i] = (short) (square * 8000);
            
            // Age all notes
            for (ActiveNote n : notes) n.activeFrames++;

            framesSinceSwitch++;
            if (framesSinceSwitch >= framesPerSwitch) {
                framesSinceSwitch = 0;
                arpeggioIndex++;
            }
        }
    }
    @Override
    public void prepareForNewTrack(javax.sound.midi.Sequence sequence)
    {
        if (audio == null) return;
        renderPaused = true;
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        audio.flush();
        activeNotes.clear();
        errorAccumulator = 0.0;
    }

    @Override
    public void onPlaybackStarted() { renderPaused = false; }

    @Override
    public void sendMessage(byte[] data)
    {
        if (data == null || data.length < 1) return;
        int status = data[0] & 0xFF;
        int cmd = status & 0xF0;
        int ch = status & 0x0F;

        if (cmd == 0x90 && data.length >= 3)
        {
            int note = data[1] & 0xFF;
            int velocity = data[2] & 0xFF;
            if (velocity > 0) noteOn(ch, note);
            else noteOff(ch, note);
        }
        else if (cmd == 0x80 && data.length >= 3)
        {
            noteOff(ch, data[1] & 0xFF);
        }
    }

    private void noteOn(int channel, int note)
    {
        if (channel == 9) return; // Ignore drums for pure 1-bit beep

        // Remove existing note to re-trigger
        activeNotes.removeIf(n -> n.channel == channel && n.note == note);

        ActiveNote an = new ActiveNote();
        an.channel = channel;
        an.note = note;
        an.frequency = PITCHES[note];
        activeNotes.add(an);
        
        // Limit total active notes to 8 to prevent complete chaos
        if (activeNotes.size() > 8) {
            activeNotes.remove(0);
        }
    }

    private void noteOff(int channel, int note)
    {
        activeNotes.removeIf(n -> n.channel == channel && n.note == note);
    }

    @Override
    public void panic() { activeNotes.clear(); if (audio != null) audio.flush(); }

    @Override
    public void closePort()
    {
        running = false;
        if (renderThread != null)
        {
            renderThread.interrupt();
            try { renderThread.join(500); } catch (InterruptedException ignored) {}
        }
        if (audio != null) audio.close();
        activeNotes.clear();
    }
}
