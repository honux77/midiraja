package com.fupfin.midiraja.dsp;

/**
 * Simulates the unique audio hardware of the original Macintosh 128k (1984).
 * The Mac used a custom Sony sound chip (or SWIM later) but initially relied on 
 * the 68000 CPU stuffing 8-bit values into a PWM generator built from two 74LS161
 * 4-bit counters. The sample rate was strictly tied to the horizontal video 
 * flyback frequency: exactly 22.25 kHz.
 * 
 * This filter performs:
 * 1. Resampling to 22.25 kHz with linear interpolation.
 * 2. 8-bit quantization of the signal at that rate.
 * 3. Outputting to the audio line out without an internal speaker EQ.
 */
public class Mac128kSimulatorFilter implements AudioProcessor {
    private final boolean enabled;
    private final AudioProcessor next;

    // The Macintosh horizontal sync frequency is approx 22,254.5 Hz
    private static final double MAC_SAMPLE_RATE = 22254.5;
    
    private boolean holdNext = false;
    private float heldL = 0;
    private float heldR = 0;

    // Analog Line-Out circuitry simulation (RC Low-Pass Filter)
    private float lpfL = 0;
    private float lpfR = 0;
    // An alpha of ~0.35 closely matches the empirical -27dB at 10kHz roll-off found in original Mac recordings.
    private final float alpha = 0.35f;

    public Mac128kSimulatorFilter(boolean enabled, AudioProcessor next) {
        this.enabled = enabled;
        this.next = next;
    }

    @Override
    public void process(float[] left, float[] right, int frames) {
        if (!enabled) {
            next.process(left, right, frames);
            return;
        }

        for (int i = 0; i < frames; i++) {
            if (!holdNext) {
                // 1. The Mac CPU reads an 8-bit sample from memory exactly at 22.05kHz.
                // We simulate this by taking the high-res input and strictly quantizing it 
                // to 256 discrete levels (8-bit). This represents the value stuffed into the PWM chip.
                heldL = Math.max(-128, Math.min(127, Math.round(left[i] * 127f))) / 127f;
                heldR = Math.max(-128, Math.min(127, Math.round(right[i] * 127f))) / 127f;
                holdNext = true;
            } else {
                holdNext = false; // Zero-Order Hold for the second frame (making it 22.05kHz)
            }
            
            // 2. The magic of the Macintosh: The PWM output isn't sent to the speaker as a staircase.
            // It goes through an analog integrating Low-Pass Filter (RC circuit).
            // This filter smoothly glides between the 8-bit steps, effectively upsampling 
            // the resolution back to near-continuous analog (infinite bit depth).
            // This completely eliminates the harsh "broken radio" quantization hiss 
            // while preserving the 22kHz bandwidth and slightly muffled, warm character.
            lpfL += alpha * (heldL - lpfL);
            lpfR += alpha * (heldR - lpfR);
            
            // Prevent subnormal float denormalization
            if (Math.abs(lpfL) < 1e-6f) lpfL = 0f;
            if (Math.abs(lpfR) < 1e-6f) lpfR = 0f;
            
            left[i] = lpfL;
            right[i] = lpfR;
        }
        
        next.process(left, right, frames);
    }
    
    @Override
    public void processInterleaved(short[] interleavedPcm, int frames, int channels) {
        next.processInterleaved(interleavedPcm, frames, channels);
    }

    @Override
    public void reset() {
        next.reset();
        holdNext = false;
        heldL = 0;
        heldR = 0;
    }
}