/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE
 * file in the root directory of this source tree.
 */

package com.midiraja.midi.psg;

import com.midiraja.midi.AudioEngine;
import com.midiraja.midi.MidiPort;
import com.midiraja.midi.SoftSynthProvider;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A Programmable Sound Generator (PSG) emulator based on the AY-3-8910 / YM2149F.
 * It implements a "Tracker-Driven Interception Layer" that applies 1980s demoscene
 * software hacks (Fast Arpeggios, Audio-Rate Hardware Envelopes) to modern MIDI files.
 */
@SuppressWarnings({"ThreadPriorityCheck", "EmptyCatch"})
public class PsgSynthProvider implements SoftSynthProvider
{
    private final AudioEngine audio;
    private final int sampleRate = 44100;
    
    private @Nullable Thread renderThread;
    private volatile boolean running = false;
    private volatile boolean renderPaused = false;
    
    // --- TRACKER STATE ---
    private static final int NUM_CHANNELS = 3;
    
    private static class PsgChannel
    {
        // Tracker Logic State
        int midiChannel = -1;
        int midiNote = -1;
        boolean active = false;
        long activeFrames = 0; // To track 50Hz ticks
        
        // 4-Bit Software Envelope State (0 to 15)
        int volume15 = 0;
        
        // Arpeggio Queue (up to 4 notes for a fake chord)
        int[] arpNotes = new int[4];
        int arpSize = 0;
        int arpIndex = 0;
        
        // Hardware State
        int phase16 = 0;
        int phaseStep16 = 0;
        boolean isNoise = false; // Interleaved Noise flag
        
        void reset()
        {
            active = false;
            volume15 = 0;
            arpSize = 0;
            arpIndex = 0;
            activeFrames = 0;
            isNoise = false;
        }
    }
    
    private final PsgChannel[] channels = new PsgChannel[NUM_CHANNELS];
    {
        for (int i = 0; i < NUM_CHANNELS; i++) channels[i] = new PsgChannel();
    }
    
    // Global Hardware Envelope (Buzzer)
    private int hwEnvPhase16 = 0;
    private int hwEnvStep16 = 0;
    private boolean hwEnvActive = false;
    
    // Noise Generator (LFSR)
    private int lfsr = 1;
    private int noisePhase16 = 0;
    private int noiseStep16 = 0; // Noise pitch
    
    public PsgSynthProvider(AudioEngine audio)
    {
        this.audio = audio;
    }

    @Override
    public List<MidiPort> getOutputPorts()
    {
        return List.of(new MidiPort(0, "AY-3-8910 (Tracker Hacks Mode)"));
    }

    @Override
    public void openPort(int portIndex) throws Exception
    {
        audio.init(sampleRate, 1, 4096);
        startRenderThread();
    }

    @Override public void loadSoundbank(String path) throws Exception {}

    private void startRenderThread()
    {
        running = true;
        renderThread = new Thread(() ->
        {
            final int framesToRender = 512;
            short[] pcmBuffer = new short[framesToRender];
            
            // 4-Bit DAC translation table (Non-linear logarithmic output like real hardware)
            // Values 0-15 mapped to amplitudes
            double[] dacTable = new double[16];
            for (int i = 0; i < 16; i++) {
                dacTable[i] = Math.pow(10.0, (i - 15) * 1.5 / 20.0);
            }
            dacTable[0] = 0.0; // Absolute silence

            while (running)
            {
                if (renderPaused)
                {
                    try { Thread.sleep(1); } catch (InterruptedException e) { break; }
                    continue;
                }
                
                // Audio rendering loop
                for (int i = 0; i < framesToRender; i++)
                {
                    double sumOutput = 0.0;
                    
                    // 1. Update Noise Generator (Clocked by noisePhase)
                    noisePhase16 = (noisePhase16 + noiseStep16) & 0xFFFF;
                    if (noisePhase16 < noiseStep16) { // Overflow occurred
                        // Standard 17-bit LFSR for AY-3-8910
                        int bit0 = lfsr & 1;
                        int bit3 = (lfsr >> 3) & 1;
                        lfsr = (lfsr >> 1) | ((bit0 ^ bit3) << 16);
                    }
                    boolean noiseBit = (lfsr & 1) == 1;
                    
                    // 2. Update Hardware Envelope (Sawtooth down for Buzzer Bass)
                    if (hwEnvActive) {
                        hwEnvPhase16 = (hwEnvPhase16 + hwEnvStep16) & 0xFFFF;
                    }
                    // The envelope shape is \ (Sawtooth Down). Value is 15 -> 0.
                    int hwEnvVal15 = 15 - (hwEnvPhase16 >> 12); // top 4 bits reversed
                    
                    // 3. Render Channels
                    for (int ch = 0; ch < NUM_CHANNELS; ch++)
                    {
                        PsgChannel c = channels[ch];
                        if (!c.active) continue;
                        
                        // 50Hz Tracker Tick (approx every 882 frames at 44100Hz)
                        if (c.activeFrames % 882 == 0) {
                            // --- HACK 1: FAST ARPEGGIOS ---
                            if (c.arpSize > 1) {
                                c.arpIndex = (c.arpIndex + 1) % c.arpSize;
                                double freq = 440.0 * Math.pow(2.0, (c.arpNotes[c.arpIndex] - 69) / 12.0);
                                c.phaseStep16 = (int) ((freq * 65536.0) / sampleRate);
                            }
                            
                            // --- HACK 2: SOFTWARE 4-BIT DECAY ---
                            // Decrement volume by 1 every tick (creates stepped zipper effect)
                            if (c.volume15 > 0) {
                                c.volume15--;
                            } else {
                                c.active = false;
                                continue;
                            }
                            
                            // --- HACK 3: INTERLEAVED NOISE (SNARE) ---
                            // If this is a drum channel, toggle between noise and tone every tick!
                            if (c.midiChannel == 9) {
                                c.isNoise = !c.isNoise;
                            }
                        }
                        
                        c.phase16 = (c.phase16 + c.phaseStep16) & 0xFFFF;
                        boolean toneBit = c.phase16 > 32767;
                        
                        // Mixer logic: Tone AND/OR Noise
                        boolean outBit = c.isNoise ? noiseBit : toneBit;
                        
                        // Volume selection: Software or Hardware Env?
                        int finalVol15 = c.volume15;
                        if (ch == 2 && hwEnvActive) {
                            finalVol15 = hwEnvVal15; // Channel 3 can use the Hardware Env Bass
                            outBit = true; // When using Env as Audio, the envelope IS the wave!
                        }
                        
                        // Apply DAC table
                        double amplitude = dacTable[finalVol15] / 3.0; // Divide by 3 to prevent clipping
                        sumOutput += outBit ? amplitude : -amplitude;
                        
                        c.activeFrames++;
                    }
                    
                    pcmBuffer[i] = (short) (Math.max(-1.0, Math.min(1.0, sumOutput)) * 32767);
                }
                audio.push(pcmBuffer);
            }
        });
        renderThread.setPriority(Thread.MAX_PRIORITY);
        renderThread.setDaemon(true);
        renderThread.start();
    }

    @Override public void closePort()
    {
        running = false;
        if (renderThread != null) renderThread.interrupt();
    }

    @Override public void prepareForNewTrack(javax.sound.midi.Sequence seq)
    {
        renderPaused = true;
        if (audio != null) audio.flush();
        for (int i = 0; i < NUM_CHANNELS; i++) channels[i].reset();
        hwEnvActive = false;
    }

    @Override public void onPlaybackStarted() { renderPaused = false; }

    @Override
    public void sendMessage(byte[] data) throws Exception
    {
        if (data.length < 1) return;
        int cmd = data[0] & 0xF0;
        int ch = data[0] & 0x0F;
        
        if (cmd == 0x90 && data.length >= 3)
        {
            int note = data[1] & 0xFF;
            int velocity = data[2] & 0xFF;
            
            if (velocity > 0)
            {
                // Note On Routing Logic
                
                // 1. Drum Routing (Channel 10 -> PsgChannel 0)
                if (ch == 9) {
                    PsgChannel drumCh = channels[0];
                    drumCh.reset();
                    drumCh.active = true;
                    drumCh.midiChannel = 9;
                    drumCh.volume15 = 15; // Max strike
                    drumCh.isNoise = true;
                    
                    if (note == 35 || note == 36) { // Kick
                        noiseStep16 = 500; // Low rumble
                    } else if (note == 38 || note == 40) { // Snare
                        noiseStep16 = 3000; // High hiss
                    } else { // Hi-hats
                        noiseStep16 = 6000;
                        drumCh.volume15 = 8; // Softer
                    }
                    return;
                }
                
                // 2. Bass Routing (Low notes -> Hardware Envelope Buzzer on PsgChannel 2)
                if (note < 48) { // Below C3
                    PsgChannel bassCh = channels[2];
                    bassCh.reset();
                    bassCh.active = true;
                    bassCh.midiChannel = ch;
                    bassCh.midiNote = note;
                    
                    // Tie the Hardware Envelope frequency to the note frequency!
                    double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
                    hwEnvStep16 = (int) ((freq * 65536.0) / sampleRate);
                    hwEnvActive = true;
                    return;
                }
                
                // 3. Melody/Chord Routing (Arpeggiator on PsgChannel 1)
                PsgChannel melCh = channels[1];
                if (melCh.active && melCh.midiChannel == ch && melCh.activeFrames < 20000) {
                    // It's a chord being struck! Add to arpeggio queue.
                    if (melCh.arpSize < 4) {
                        melCh.arpNotes[melCh.arpSize++] = note;
                    }
                } else {
                    // Fresh melody note
                    melCh.reset();
                    melCh.active = true;
                    melCh.midiChannel = ch;
                    melCh.midiNote = note;
                    melCh.volume15 = (int) ((velocity / 127.0) * 15.0);
                    melCh.arpNotes[0] = note;
                    melCh.arpSize = 1;
                    melCh.arpIndex = 0;
                    
                    double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
                    melCh.phaseStep16 = (int) ((freq * 65536.0) / sampleRate);
                }
                
            } else {
                // Note Off
                for (int i = 0; i < NUM_CHANNELS; i++) {
                    if (channels[i].active && channels[i].midiChannel == ch && channels[i].midiNote == note) {
                        // Instead of killing it instantly, just drop volume slightly to let Tracker finish it?
                        // Actually, for pure 8-bit, Note Off often means instant zero.
                        channels[i].active = false;
                        if (i == 2) hwEnvActive = false; // Turn off bass buzzer
                    }
                }
            }
        } else if (cmd == 0x80 && data.length >= 2) {
            int note = data[1] & 0xFF;
            for (int i = 0; i < NUM_CHANNELS; i++) {
                if (channels[i].active && channels[i].midiChannel == ch && channels[i].midiNote == note) {
                    channels[i].active = false;
                    if (i == 2) hwEnvActive = false;
                }
            }
        }
    }
}
