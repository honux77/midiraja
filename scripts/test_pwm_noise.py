import sys

with open('src/main/java/com/midiraja/midi/beep/BeepSynthProvider.java', 'r') as f:
    text = f.read()

# 1. Apply absolute Hard/Soft Clipping to the analog mixer
old_mix = """            float mixed = (float) (analogSum / NUM_SPEAKERS);
            analogL[i] = mixed; analogR[i] = mixed;
            for (ActiveNote n : notes) n.activeFrames++;
        }
        dspSimulator.process(analogL, analogR, frames);
        for (int i = 0; i < frames; i++) buffer[i] = (short) (analogL[i] * 32767);"""

new_mix = """            // Aggressive Volume scaling to prevent ANY possibility of clipping
            double safeMix = (analogSum / NUM_SPEAKERS) * 0.7; // Headroom
            
            // Hard clip just to be 1000% safe before PWM
            safeMix = Math.max(-0.95, Math.min(0.95, safeMix));
            
            analogL[i] = (float) safeMix; 
            analogR[i] = (float) safeMix;
            for (ActiveNote n : notes) n.activeFrames++;
        }
        
        // TEMPORARY DIAGNOSTIC: 
        // Bypass the 1-bit PWM Simulator completely! 
        // Output the pure 16-bit analog float mix directly to the soundcard.
        // If this sounds clean, then the PwmAcousticSimulator is the culprit.
        // dspSimulator.process(analogL, analogR, frames);
        
        for (int i = 0; i < frames; i++) {
            buffer[i] = (short) (analogL[i] * 32767);
        }"""

text = text.replace(old_mix, new_mix)

with open('src/main/java/com/midiraja/midi/beep/BeepSynthProvider.java', 'w') as f:
    f.write(text)

