
package com.midiraja.dsp;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
public class DspVerification {
    public static void main(String[] args) throws Exception {
        int sampleRate = 44100;
        int frames = 44100 * 2; // 2 seconds
        OneBitAcousticSimulator sim = new OneBitAcousticSimulator(sampleRate, "pwm");
        
        float[] left = new float[frames];
        float[] right = new float[frames];
        
        // 1. Generate 0.5s of Sine, then 1.5s of Silence
        for (int i = 0; i < frames; i++) {
            if (i < 22050) {
                double val = Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 0.8;
                left[i] = (float) val;
                right[i] = (float) val;
            } else {
                left[i] = 0.0f;
                right[i] = 0.0f;
            }
        }
        
        // 2. Process
        sim.process(left, right, frames);
        
        // 3. Save to file
        try (FileOutputStream fos = new FileOutputStream("verify_pwm.raw")) {
            ByteBuffer buf = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < frames; i++) {
                // Apply soft-clipping like SynthProvider does
                float softL = (float) Math.tanh(left[i]);
                float softR = (float) Math.tanh(right[i]);
                buf.putShort((short) (softL * 32767));
                buf.putShort((short) (softR * 32767));
            }
            fos.write(buf.array());
        }
    }
}
