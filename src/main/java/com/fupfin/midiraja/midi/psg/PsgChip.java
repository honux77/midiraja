package com.fupfin.midiraja.midi.psg;

/**
 * Represents a single physical AY-3-8910 / YM2149F hardware chip.
 * Contains 3 Tone Channels, 1 Noise Generator, and 1 Hardware Envelope Generator.
 */
class PsgChip implements TrackerSynthChip
{
    private static final int NUM_CHANNELS = 3;
    private final int sampleRate;
    
    private static class PsgChannel
    {
        int midiChannel = -1;
        int midiNote = -1;
        boolean active = false;
        long activeFrames = 0; 
        
        int volume15 = 0;
        int[] arpNotes = new int[4];
        int arpSize = 0;
        int arpIndex = 0;
        
        double baseFrequency = 0.0;
        int dutyCycle16 = 32767; // Default 50% square wave
        int phase16 = 0;
        int phaseStep16 = 0;
        boolean isNoise = false; 
        
        void reset()
        {
            active = false;
            volume15 = 0;
            arpSize = 0;
            arpIndex = 0;
            activeFrames = 0;
            isNoise = false;
            midiChannel = -1;
            midiNote = -1;
            baseFrequency = 0.0;
            dutyCycle16 = 32767;
        }
    }
    
    private final PsgChannel[] channels = new PsgChannel[NUM_CHANNELS];
    private int hwEnvPhase16 = 0;
    private int hwEnvStep16 = 0;
    private boolean hwEnvActive = false;
    
    private int lfsr = 1;
    private int noisePhase16 = 0;
    private int noiseStep16 = 0; 
    
    private final double[] dacTable = new double[16];
    private final double vibratoDepth;
    private final double dutySweep;
    
    public PsgChip(int sampleRate, double vibratoDepth, double dutySweep)
    {
        this.vibratoDepth = Math.max(0.0, Math.min(100.0, vibratoDepth)) / 1000.0; // convert per mille 5.0 -> 0.005, max 10% pitch bend
        this.dutySweep = Math.max(0.0, Math.min(50.0, dutySweep)) / 100.0; // convert percentage 25.0 -> 0.25, cap max sweep to avoid inverting phase
        this.sampleRate = sampleRate;
        for (int i = 0; i < NUM_CHANNELS; i++) channels[i] = new PsgChannel();
        
        for (int i = 0; i < 16; i++) {
            dacTable[i] = Math.pow(10.0, (i - 15) * 1.5 / 20.0);
        }
        dacTable[0] = 0.0;
    }
    
    @Override public void reset() {
        for (int i = 0; i < NUM_CHANNELS; i++) channels[i].reset();
        hwEnvActive = false;
    }
    
    @Override public void setProgram(int ch, int program) {
        // PSG does not support instruments (could tweak duty cycle based on program later)
    }
    
    @Override public double render()
    {
        double sumOutput = 0.0;
        
        noisePhase16 = (noisePhase16 + noiseStep16) & 0xFFFF;
        if (noisePhase16 < noiseStep16) { 
            int bit0 = lfsr & 1;
            int bit3 = (lfsr >> 3) & 1;
            lfsr = (lfsr >> 1) | ((bit0 ^ bit3) << 16);
        }
        boolean noiseBit = (lfsr & 1) == 1;
        
        if (hwEnvActive) {
            hwEnvPhase16 = (hwEnvPhase16 + hwEnvStep16) & 0xFFFF;
        }
        int hwEnvVal15 = 15 - (hwEnvPhase16 >> 12);
        
        for (int ch = 0; ch < NUM_CHANNELS; ch++)
        {
            PsgChannel c = channels[ch];
            if (!c.active) continue;
            
            if (c.activeFrames % 882 == 0) {
                // If Arpeggio is active, do NOT apply vibrato or duty sweeps (too chaotic!)
                if (c.arpSize > 1) {
                    c.arpIndex = (c.arpIndex + 1) % c.arpSize;
                    c.baseFrequency = 440.0 * Math.pow(2.0, (c.arpNotes[c.arpIndex] - 69) / 12.0);
                    c.phaseStep16 = (int) ((c.baseFrequency * 65536.0) / sampleRate);
                    c.dutyCycle16 = 32767; // Lock to pure 50% square for crisp chords
                } else if (c.baseFrequency > 0.0) {
                    // --- HACK 6: DUTY CYCLE SWEEP (FAKE FM) & DEEP VIBRATO ---
                    // By sweeping the duty cycle from 10% to 50%, we create a harsh, nasal 
                    // "Phaser/Wah" effect that tricks the ear into hearing FM synthesis on a 
                    // pure square wave chip.
                    double timeSec = (c.activeFrames / (double) sampleRate);
                    
                    // 1. Dynamic Duty Cycle Sweep (Fake FM)
                    if (dutySweep > 0.001) {
                        double dutyLfo = Math.sin(timeSec * 0.5 * 2.0 * Math.PI); 
                        double dutyPercent = 0.5 + (dutySweep * dutyLfo);
                        c.dutyCycle16 = (int) (65535.0 * dutyPercent);
                    } else {
                        c.dutyCycle16 = 32767; // Pure 50% square
                    }
                    
                    // 2. Delayed, Dynamic Vibrato
                    if (vibratoDepth > 0.0001 && c.activeFrames > 25 * 882) { 
                        double pitchLfo = Math.sin(timeSec * 3.5 * 2.0 * Math.PI);
                        double vibratoFreq = c.baseFrequency * (1.0 + (vibratoDepth * pitchLfo));
                        c.phaseStep16 = (int) ((vibratoFreq * 65536.0) / sampleRate);
                    }
                }
                
                // 3. Envelope Decay (Drums decay super fast, Melody decays slowly)
                if (c.midiChannel == 9) {
                    // FAST DRUM DECAY: Decrement volume violently every single tick (50Hz)
                    if (c.activeFrames % 882 == 0) {
                        c.volume15 -= 3; // Crash the volume down quickly!
                        
                        // Kick Drum Pitch Drop: Sweep the tone frequency downwards rapidly
                        if (c.midiNote == 35 || c.midiNote == 36) {
                            c.baseFrequency *= 0.7; // Drop pitch by 30% every tick!
                            c.phaseStep16 = (int) ((c.baseFrequency * 65536.0) / sampleRate);
                        }
                    }
                } else {
                    // SLOW MELODY DECAY: Decrement every 4 ticks (12.5Hz)
                    if (c.activeFrames % (882 * 4) == 0) {
                        if (c.volume15 > 0) c.volume15--;
                    }
                }
                
                if (c.volume15 <= 0) {
                    c.active = false;
                    continue;
                }
                
                // 4. Interleaved Noise for Snare (Toggle between Tone Body and Noise Tail)
                if (c.midiChannel == 9 && (c.midiNote == 38 || c.midiNote == 40)) {
                    if (c.activeFrames % 882 == 0) {
                        c.isNoise = !c.isNoise; // Toggle!
                    }
                }
            }
            
            c.phase16 = (c.phase16 + c.phaseStep16) & 0xFFFF;
            // Use the dynamically sweeping duty cycle instead of hardcoded 50% (32767)
            boolean toneBit = c.phase16 > c.dutyCycle16;
            boolean outBit = c.isNoise ? noiseBit : toneBit;
            
            int finalVol15 = c.volume15;
            if (ch == 2 && hwEnvActive) {
                finalVol15 = hwEnvVal15;
                outBit = true;
            }
            
            double amplitude = dacTable[finalVol15] / 3.0;
            sumOutput += outBit ? amplitude : -amplitude;
            
            c.activeFrames++;
        }
        
        return sumOutput;
    }
    
    @Override public boolean updateNote(int ch, int note, int velocity) {
        for (int i = 0; i < NUM_CHANNELS; i++) {
            PsgChannel c = channels[i];
            if (c.active && c.midiChannel == ch) {
                if (c.arpSize > 0) {
                    for (int j = 0; j < c.arpSize; j++) {
                        if (c.arpNotes[j] == note) {
                            c.volume15 = (int) ((velocity / 127.0) * 15.0);
                            return true;
                        }
                    }
                } else if (c.midiNote == note) {
                    c.volume15 = (int) ((velocity / 127.0) * 15.0);
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override public boolean tryAllocateFree(int ch, int note, int velocity) {
        int targetCh = -1;
        for (int i = 0; i < NUM_CHANNELS; i++) {
            if (!channels[i].active) {
                targetCh = i;
                break;
            }
        }
        
        if (targetCh == -1) return false;
        
        if (ch == 9) {
            targetCh = 0; // Force to channel 0 for drums if possible
            PsgChannel c = channels[targetCh];
            c.reset();
            c.active = true;
            c.midiChannel = 9;
            c.midiNote = note;
            c.volume15 = 15;
            
            // Drum Crafting!
            if (note == 35 || note == 36) { 
                // KICK: Start with a punchy 120Hz tone, NO noise! The Tracker will pitch-drop this.
                c.isNoise = false;
                c.baseFrequency = 120.0;
                c.phaseStep16 = (int) ((c.baseFrequency * 65536.0) / sampleRate);
                c.dutyCycle16 = 32767;
            } else if (note == 38 || note == 40) { 
                // SNARE: Start with a sharp 200Hz tone for the "Crack", Tracker will interleave Noise for the "Tail".
                c.isNoise = false;
                c.baseFrequency = 200.0;
                c.phaseStep16 = (int) ((c.baseFrequency * 65536.0) / sampleRate);
                c.dutyCycle16 = 32767;
                noiseStep16 = 3000; // Medium hiss
            } else { 
                // HI-HATS: Pure high-frequency noise, decays instantly
                c.isNoise = true;
                noiseStep16 = 6000;
                c.volume15 = 8; // Don't overpower
            }
            return true;
        }
        
        if (note < 45 && targetCh == -1) {
            targetCh = 2; // Prefer ch 2 for bass
        }
        
        PsgChannel c = channels[targetCh];
        c.reset();
        c.active = true;
        c.midiChannel = ch;
        c.midiNote = note;
        c.volume15 = (int) ((velocity / 127.0) * 15.0);
        c.arpNotes[0] = note;
        c.arpSize = 1;
        
        c.baseFrequency = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
        c.phaseStep16 = (int) ((c.baseFrequency * 65536.0) / sampleRate);
        
        if (note < 45) {
            hwEnvStep16 = c.phaseStep16;
            hwEnvActive = true;
        }
        return true;
    }
    
        @Override public boolean tryStealChannel(int ch, int note, int velocity) {
        // Steal the channel with the lowest current volume (least noticeable).
        // If volumes are equal, prefer stealing from a higher MIDI channel number.
        int targetCh = -1;
        int minVolume = Integer.MAX_VALUE;
        int targetMidiCh = -1;
        
        for (int i = 0; i < NUM_CHANNELS; i++) {
            if (i == 2) continue; // don't steal the hardware envelope/noise channel
            PsgChannel c = channels[i];
            if (c.active && c.midiChannel != 9) {
                if (c.volume15 < minVolume || (c.volume15 == minVolume && c.midiChannel > targetMidiCh)) {
                    minVolume = c.volume15;
                    targetMidiCh = c.midiChannel;
                    targetCh = i;
                }
            }
        }
        
        // Only steal if the target is quieter than our new note, or if our new note is 
        // high priority (ch 0-3) and the target is low priority (ch > 3).
        int newVol = (int) ((velocity / 127.0) * 15.0);
        if (targetCh != -1 && (minVolume <= newVol || (ch < 4 && targetMidiCh >= 4))) {
            PsgChannel c = channels[targetCh];
            c.reset();
            c.active = true;
            c.midiChannel = ch;
            c.midiNote = note;
            c.baseFrequency = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
            c.phaseStep16 = (int) ((c.baseFrequency * 65536.0) / sampleRate);
            c.volume15 = newVol;
            c.dutyCycle16 = 32767;
            return true;
        }
        return false;
    }
    
    @Override public void forceArpeggioFallback(int ch, int note, int velocity) {
        int targetCh = -1;
        
        // 1. Try to find a tone channel already playing the SAME instrument (midiChannel) 
        // that has room in its arpeggio buffer (< 4 notes).
        for (int i = 0; i < NUM_CHANNELS; i++) {
            if (i == 2) continue; // Skip noise channel for arpeggios
            PsgChannel c = channels[i];
            if (c.active && c.midiChannel == ch && c.arpSize < 4) {
                targetCh = i;
                break;
            }
        }
        
        // 2. If no matching instrument has room, we must steal a channel.
        // Let's steal from channel 1 (or the oldest active tone channel).
        if (targetCh == -1) {
            targetCh = 1; // Fallback hard steal
            PsgChannel c = channels[targetCh];
            c.reset();
            c.active = true;
            c.midiChannel = ch;
            c.midiNote = note;
            c.volume15 = (int) ((velocity / 127.0) * 15.0);
            c.arpNotes[0] = note;
            c.arpSize = 1;
            c.baseFrequency = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
            c.phaseStep16 = (int) ((c.baseFrequency * 65536.0) / sampleRate);
            return;
        }
        
        // 3. Append to existing arpeggio buffer
        PsgChannel c = channels[targetCh];
        
        // If it was playing a single note, convert it to an arpeggio array
        if (c.arpSize == 0 && c.active) {
            c.arpNotes[0] = c.midiNote;
            c.arpSize = 1;
        }
        
        // Avoid duplicate notes in the arpeggio
        boolean exists = false;
        for(int i=0; i<c.arpSize; i++) {
            if (c.arpNotes[i] == note) exists = true;
        }
        
        if (!exists) {
            c.arpNotes[c.arpSize++] = note;
            // Optionally boost volume slightly since it's now playing chords
            c.volume15 = Math.max(c.volume15, (int) ((velocity / 127.0) * 15.0));
        }
    }

    @Override public void handleNoteOff(int ch, int note) {
        for (int i = 0; i < NUM_CHANNELS; i++) {
            PsgChannel c = channels[i];
            if (c.active && c.midiChannel == ch) {
                if (c.arpSize > 1) {
                    // It's playing an arpeggio chord. Try to remove the specific note.
                    int removeIdx = -1;
                    for (int j = 0; j < c.arpSize; j++) {
                        if (c.arpNotes[j] == note) {
                            removeIdx = j;
                            break;
                        }
                    }
                    
                    if (removeIdx != -1) {
                        // Shift remaining notes left
                        for (int j = removeIdx; j < c.arpSize - 1; j++) {
                            c.arpNotes[j] = c.arpNotes[j + 1];
                        }
                        c.arpSize--;
                        
                        // If index is now out of bounds, reset it
                        if (c.arpIndex >= c.arpSize) {
                            c.arpIndex = 0;
                        }
                    }
                } else if (c.midiNote == note) {
                    // Single note playing, just kill the channel
                    c.active = false;
                    if (i == 2) hwEnvActive = false;
                }
            }
        }
    }
}
