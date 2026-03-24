# Compact Mac Audio Simulation (`--retro compactmac`)

## 1. Hardware Background

The original Macintosh (1984) had no PCM DAC. The 68000 CPU was interrupted at the horizontal
video flyback frequency (~22.2545 kHz) to write an 8-bit value into a buffer. This value
controlled the duty cycle of a high-speed 1-bit PWM pulse train, which was then integrated by
a physical RC low-pass filter on the logic board before reaching the internal 2-inch speaker.

## 2. The Nyquist Barrier

Midiraja runs at 44.1 kHz. Simulating a 22+ kHz PWM carrier inside a 44.1 kHz buffer creates
a fundamental problem: the Nyquist limit. Drawing raw ±1 pulses at 44.1 kHz causes aliasing —
a "siren tone" at ~21 kHz (the mirror image of the 22 kHz carrier) that has no physical basis.

## 3. Solution: Event-Driven Analytical Integration

Instead of approximating PWM pulses, the filter treats the analog RC stage as a
continuous-time system:

$$\frac{dx}{dt} = \frac{u(t) - x(t)}{\tau}$$

Where $u(t)$ is the 1-bit PWM input (+1 or -1) and $x(t)$ is the capacitor voltage.
For any constant-input interval, the exact solution is:

$$x(t_2) = u + (x(t_1) - u) \cdot e^{-\Delta t / \tau}$$

The implementation tracks Mac clock events (~22.25 kHz) and PWM transition events at
sub-microsecond precision, stepping through them analytically and reading the capacitor
voltage $x$ at each 44.1 kHz output sample point. This eliminates aliasing mathematically
without oversampling.

## 4. Authentic Hardware Measurement (Mac Plus Capture, March 2026)

Spectral analysis of a pristine recording from a real Mac Plus ("Hard Stars Studio Session")
revealed:

- **Carrier suppression:** The 22.25 kHz carrier was at **−90.8 dB** — the analog stage was near-perfect.
- **No "grit":** No asymmetric truncation or zero-crossing distortion was found.
- **Steep roll-off:** Response drops sharply from 5 kHz, reaching **−84 dB at 10 kHz**.

| Frequency | Level | Character |
| :--- | :--- | :--- |
| 100 Hz | −28.6 dB | Solid bass support |
| 500 Hz | −31.5 dB | Fundamental body |
| 1,000 Hz | −41.3 dB | Lower mids |
| 3,000 Hz | −47.9 dB | Presence region |
| 5,000 Hz | −51.6 dB | Start of steep roll-off |
| 7,000 Hz | −64.1 dB | Aggressive attenuation |
| 10,000 Hz | **−83.8 dB** | Measured "muffle" point |
| 22,050 Hz | −99.3 dB | PWM carrier suppressed |

## 5. Parameters

| Parameter | Value | Purpose |
| :--- | :--- | :--- |
| Channel mode | Forced mono | Matches physical mono-only hardware |
| Quantization | Symmetrical 8-bit | Clean 8-bit buffer (Math.round) |
| RC τ | 30.0 µs | Matches the measured −84 dB @ 10 kHz curve |
| Mac sample rate | 22,254.5 Hz | Tied to horizontal video flyback |
