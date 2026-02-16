# Macintosh 128k Audio Hardware Simulation

This document chronicles the engineering journey to create a highly authentic real-time simulation of the original 1984 Macintosh 128k audio circuitry.

## 1. Hardware Background (The "SWIM" and 68000 PWM)
The original Macintosh did not have a standard PCM DAC. Instead, it used a clever high-speed Pulse Width Modulation (PWM) scheme:
- **CPU Feed:** The 68000 CPU was interrupted exactly at the horizontal video flyback frequency (~22.2545 kHz) to stuff an 8-bit value into a buffer.
- **PWM Logic:** This 8-bit value (0-255) controlled the duty cycle of a very high-frequency 1-bit pulse train.
- **Analog Integration:** The 1-bit pulses passed through a physical resistor-capacitor (RC) low-pass filter on the logic board, which integrated the pulses back into an analog voltage before reaching the internal 2-inch speaker.

## 2. The Theoretical Challenge: The 44.1kHz Buffer Trap
Midiraja operates at a standard 44.1 kHz output. Simulating a 350kHz+ PWM signal inside a 44.1 kHz buffer presents a fundamental mathematical contradiction: **The Nyquist Limit.**

If we try to "draw" the 1-bit pulses directly, we create massive aliasing (artifacts) because the pulse edges don't line up with the 44.1kHz sample points. This results in a "siren" or "dog whistle" noise around 21kHz (the mirror image of the 22kHz sampling rate).

## 3. Evolutionary Attempts

### 3.1 Naive Average-based Model
- **Logic:** Convert 8-bit duty cycle to a proportional voltage (-1.0 to 1.0) and apply a digital LPF.
- **Evaluation:** Failed. The result was "too clean" and lacked the gritty, electric texture of the original hardware. It sounded like modern 8-bit PCM, not a PWM circuit.

### 3.2 Discrete PWM Rendering
- **Logic:** Render raw +1/-1 pulses at 44.1kHz based on duty cycle.
- **Evaluation:** Failed. Created a deafening 21.05kHz aliasing tone (the "Siren"). The high-frequency switching energy of the PWM was being folded back into the audible spectrum due to the low 44.1kHz sample rate.

### 3.3 Hiss Injection (Fake Texture)
- **Logic:** Add 3.5% white noise to simulate "grit."
- **Evaluation:** Failed. While it added "dust," it didn't "breathe" with the music. It was just static noise, not physical switching ripple.

## 4. The Breakthrough: Event-Driven Analytical Integration

Instead of approximating the PWM pulses or averaging them, we moved to **Physical Event Simulation.** We treat the analog RC filter as a continuous-time system governed by a differential equation:

$$ \frac{dx}{dt} = \frac{u(t) - x(t)}{\tau} $$

Where $u(t)$ is the raw 1-bit PWM input (+1 or -1) and $x(t)$ is the voltage across the capacitor.

### 4.1 The Analytical Solution
For any time interval where the input $u$ is constant, the solution is:
$$ x(t_2) = u + (x(t_1) - u) \cdot e^{-\Delta t/\tau} $$

### 4.2 Implementation Strategy
1.  **Event Tracking:** We track two types of events in sub-microsecond precision:
    -   **Mac Clock Event:** Every ~44.93 µs, a new 8-bit sample is fetched.
    -   **PWM Transition Event:** Within that 44.93 µs, the point where the signal drops from High to Low (based on the duty cycle).
2.  **Analytical Integration:** We step through these events and update the capacitor voltage $x$ using the exponential decay formula.
3.  **Perfect Resampling:** We read the value of $x$ exactly at the 44.1 kHz output intervals (~22.67 µs).

## 5. Spectral Analysis & Evaluation

A 1kHz Sine wave test was used to verify the final "Event-Driven" model.

| Metric | Previous (IIR Filter) | Final (Event-Driven Integration) | Improvement |
| :--- | :--- | :--- | :--- |
| **Fundamental (1kHz)** | -2.1 dB | -2.0 dB | Transparent |
| **ZOH Siren (21.05kHz)** | **-27.6 dB (Loud)** | **-76.4 dB (Inaudible)** | **+48.8 dB Reduction** |
| **PWM Texture** | None (Clean) | **Rich Intermodulation** | Authentic Physics |

### 5.1 Aliasing Suppression
The event-driven model mathematically eliminates the ZOH "mirror" reflections because it doesn't perform a discrete upsampling; it simulates a continuous analog path. The 21kHz siren dropped from a painful -27dB to a negligible -76dB.

### 5.2 Texture Authenticity
The spectrum shows complex harmonics at `-60dB` to `-70dB` (e.g., at 2400Hz, 4450Hz, 7050Hz). These aren't random noise; they are the **physical PWM ripple** riding on the signal. The "grit" now changes dynamically with the volume and frequency of the music, just like the real 1984 Macintosh hardware.


### 5.3 The "Whine" in Silence (Carrier Leakage)
During silence (input = 0.0), the 8-bit register rests at `128` (a 50% duty cycle). This produces a continuous, perfect 22.25kHz square wave. The 1-pole RC filter is not steep enough to completely suppress this carrier frequency. When this continuous analog signal is sampled at 44.1kHz, the 22.25kHz carrier aliases down to `21.85kHz` (44.1 - 22.25), producing an audible high-frequency "whine" at roughly -21dB.

To resolve this, we added a secondary, highly-damped 1-pole LPF immediately after the RC integration. This simulates the **physical inertia of the speaker cone**, which simply cannot vibrate fast enough to reproduce the 22kHz carrier. This completely eliminated the whine during silence without destroying the audible PWM intermodulation texture.

## 6. Conclusion
The `--mac128k` filter is no longer a "sound effect." It is a real-time, event-driven physical simulation of an analog RC circuit driven by a 22.25kHz 8-bit PWM source. It achieves perfect aliasing suppression and authentic physical texture without the need for high-latency oversampling.
