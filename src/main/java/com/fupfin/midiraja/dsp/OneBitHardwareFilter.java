package com.fupfin.midiraja.dsp;

import java.util.Random;

public class OneBitHardwareFilter implements AudioProcessor
{
    private final boolean enabled;
    private final AudioProcessor next;
    private final String mode;

    private double carrierPhase = 0.0;
    private final double carrierStep;

    private double dsdErr = 0.0;
    private final Random rand = new Random();

    private float smoothL1 = 0.0f, smoothL2 = 0.0f;
    private final float smoothAlpha;

    // Duty cycle resolution (number of discrete levels)
    private final double levels;

    public OneBitHardwareFilter(boolean enabled, String mode, double carrierHz, double levels,
            float smoothAlpha, AudioProcessor next)
    {
        this.enabled = enabled;
        this.next = next;
        this.mode = mode != null ? mode.toLowerCase(java.util.Locale.ROOT) : "pwm";
        this.carrierStep = carrierHz / 44100.0;
        this.levels = levels;
        this.smoothAlpha = smoothAlpha;
    }

    @Override
    public void process(float[] left, float[] right, int frames)
    {
        if (!enabled)
        {
            next.process(left, right, frames);
            return;
        }

        for (int i = 0; i < frames; i++)
        {
            float filtered = processOneSample((left[i] + right[i]) * 0.5);
            left[i] = filtered;
            right[i] = filtered;
        }

        next.process(left, right, frames);
    }

    @Override
    public void processInterleaved(short[] interleavedPcm, int frames, int channels)
    {
        if (!enabled)
        {
            next.processInterleaved(interleavedPcm, frames, channels);
            return;
        }

        for (int i = 0; i < frames; i++)
        {
            int lIdx = i * channels;
            float l = interleavedPcm[lIdx] / 32768.0f;
            float r = channels > 1 ? interleavedPcm[lIdx + 1] / 32768.0f : l;

            float filtered = processOneSample((l + r) * 0.5);

            short outPcm = (short) Math.max(-32768, Math.min(32767, filtered * 32768.0));
            interleavedPcm[lIdx] = outPcm;
            if (channels > 1) interleavedPcm[lIdx + 1] = outPcm;
        }

        next.processInterleaved(interleavedPcm, frames, channels);
    }

    private float processOneSample(double monoIn)
    {
        double out;

        if (Math.abs(monoIn) < 1e-4)
        {
            out = 0.0;
        }
        else if ("dsd".equals(mode))
        {
            dsdErr += monoIn + (rand.nextDouble() - 0.5) * 0.1;
            out = dsdErr > 0.0 ? 1.0 : -1.0;
            dsdErr -= out;
        }
        else
        {
            double rawDuty = Math.max(0.0, Math.min(1.0, (monoIn + 1.0) * 0.5));
            // Discretize the duty cycle based on hardware clock limitations!
            double duty = Math.round(rawDuty * levels) / levels;
            out = integratePwm(carrierPhase, carrierStep, duty);
            carrierPhase = (carrierPhase + carrierStep) % 1.0;
        }

        smoothL1 += smoothAlpha * ((float) out - smoothL1);
        smoothL2 += smoothAlpha * (smoothL1 - smoothL2);
        if (Math.abs(smoothL1) < 1e-10f) smoothL1 = 0;
        if (Math.abs(smoothL2) < 1e-10f) smoothL2 = 0;

        return smoothL2;
    }

    private double integratePwm(double startPhase, double step, double duty)
    {
        double endPhase = startPhase + step;
        double highTime = 0.0;
        if (endPhase > 1.0)
        {
            if (startPhase < duty) highTime += (duty - startPhase);
            double remainder = endPhase - 1.0;
            if (remainder > duty) highTime += duty;
            else
                highTime += remainder;
        }
        else
        {
            if (endPhase <= duty) highTime = step;
            else if (startPhase >= duty) highTime = 0.0;
            else
                highTime = duty - startPhase;
        }
        return ((highTime / step) * 2.0) - 1.0;
    }

    @Override
    public void reset()
    {
        carrierPhase = 0.0;
        dsdErr = 0;
        smoothL1 = 0;
        smoothL2 = 0;
        next.reset();
    }
}
