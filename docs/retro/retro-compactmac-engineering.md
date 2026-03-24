# Compact Mac Audio Simulation (`--retro compactmac`)

## 1. Hardware Background

The original Macintosh (1984) had no PCM DAC. The 68000 CPU was interrupted at the horizontal
video flyback frequency (~22.2545 kHz) to write an 8-bit value into a buffer. This value
controlled the duty cycle of a high-speed 1-bit PWM pulse train, which was then integrated by
a physical RC low-pass filter on the logic board before reaching the internal 2-inch speaker.

The Mac had a single mono audio path. Both the internal speaker and the external audio jack
carried the same mono signal. `--retro compactmac` models the **electrical output** of the RC
stage — the signal at the audio jack. Users who want the internal speaker coloration can add
`--speaker` on top.

## 2. The Nyquist Barrier

Midiraja runs at 44.1 kHz. The Mac's PWM carrier runs at 22,254.5 Hz — just above the 22,050 Hz
Nyquist limit. Two aliasing problems arise:

1. **Direct carrier alias**: The carrier folds to `44,100 − 22,254.5 ≈ 21,845 Hz` in the digital
   domain. This is at the upper edge of the audible range and can be faintly audible as a high-pitched
   hiss on playback systems that reproduce 20 kHz+.

2. **Sideband aliasing**: The carrier amplitude-modulates with the audio signal, producing sidebands
   at `carrier ± audio`. For an audio frequency of 5 kHz the sidebands fall near 17 kHz and 27 kHz;
   after aliasing from above Nyquist, `27,254.5 − 44,100 ≈ 16,845 Hz` — clearly audible.

Drawing raw ±1 pulses at the 44.1 kHz grid would make both problems worse. The analytical
integration approach below eliminates the discrete-pulse artifacts, but the carrier alias remains
because 44.1 kHz simply cannot represent a 22.25 kHz signal without aliasing.

The structural solution is to carry a variable sample rate through the DSP pipeline so the output
rate can be aligned to an integer multiple of the Mac carrier. This is tracked as a future
architectural change (see §6). As a practical mitigation, a post-RC 18 kHz LPF is applied to
suppress the alias to inaudible levels (see §3.2).

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

### 3.2 Post-RC 18 kHz Low-Pass Filter

The RC voltage $x$ is fed through a 1-pole bilinear-transform IIR LP at 18 kHz before being
written to the output buffer:

$$y[n] = B_0\,(x[n] + x[n-1]) - A_1\,y[n-1]$$

where $K = \tan(\pi \cdot 18000 / 44100) \approx 3.309$, $B_0 = K/(1+K) \approx 0.768$,
$A_1 = (K-1)/(K+1) \approx 0.536$.

The bilinear transform places an **exact zero at the Nyquist frequency** ($z = -1$). Because the
aliased carrier at ~21,845 Hz is very close to Nyquist (22,050 Hz), this filter suppresses it
by ~26 dB. The −3 dB point is at 18 kHz, so audio content below 15 kHz is passed within 1.2 dB.

This models the perceptual bandlimit that the Mac's complete analog output path provided: the RC
stage, output coupling capacitors, and audio amplifier IC together attenuate frequencies near the
Nyquist of any practical playback chain. The 18 kHz cutoff is a simulation parameter, not
derived from a measured Mac circuit element.

## 4. Parameters

| Parameter | Value | Purpose |
| :--- | :--- | :--- |
| Channel mode | Forced mono (L+R)/2 | Matches physical mono-only hardware |
| Quantization | Symmetrical 8-bit | 256 duty levels (Math.round) |
| RC τ | 30.0 µs | f₋₃dB ≈ 5,300 Hz |
| Mac sample rate | 22,254.5 Hz | Tied to horizontal video flyback |
| Post-RC LPF | 18 kHz, bilinear 1-pole | Suppresses aliased carrier at ~21,845 Hz |

## 5. Measured Characteristics (Simulation, March 2026)

All measurements use a 0.5-amplitude (−6 dBFS) sine input through `CompactMacSimulatorFilter`
at 44,100 Hz sample rate. No real-hardware reference is available for comparison.

### 5.1 Frequency Response

| Frequency | Rolloff vs dry | Note |
| :--- | :--- | :--- |
| 100 Hz | −0.0 dB | Flat pass-band |
| 500 Hz | −0.0 dB | Flat pass-band |
| 1,000 Hz | −0.2 dB | RC rolloff beginning |
| 2,000 Hz | −0.6 dB | |
| 3,000 Hz | −1.3 dB | |
| **5,000 Hz** | **−3.1 dB** | −3 dB point (RC theory: 5,305 Hz for τ=30 µs) |
| 8,000 Hz | −6.0 dB | |
| 10,000 Hz | −7.9 dB | |

The −3 dB point is near 5,000 Hz, consistent with the first-order RC at τ=30 µs
(f₋₃dB = 1/(2π×30µs) ≈ 5,305 Hz). The post-RC 18 kHz LPF adds at most 0.3 dB of extra
attenuation in the 100–10,000 Hz range.

### 5.2 Harmonic Distortion (PWM Quantization THD)

| Frequency | THD% | 2nd harmonic | 3rd harmonic | Noise floor |
| :--- | :--- | :--- | :--- | :--- |
| 100 Hz | 0.375% | — | — | — |
| 440 Hz | **1.578%** | −48.2 dB | −67.5 dB | −72.9 dBFS |
| 1,000 Hz | 3.430% | — | — | — |
| 3,000 Hz | 7.776% | — | — | — |
| 5,000 Hz | 9.593% | — | — | — |
| 8,000 Hz | 7.302% | — | — | — |
| 10,000 Hz | 3.074% | — | — | — |

**Distortion source**: Unlike the Amiga's R-2R DAC nonlinearity, CompactMac THD originates
from **PWM duty-cycle quantization**. The 256 discrete duty levels map the continuous input to
a staircase waveform; the RC then integrates these steps imperfectly, particularly at higher
signal frequencies where the RC has less time to charge/discharge between duty level changes.
THD rises steeply with frequency up to ~5 kHz, then falls again because the post-RC LPF
attenuates the harmonics at 8 kHz (2nd harmonic at 16 kHz is near the LPF cutoff) and
10 kHz (2nd harmonic at 20 kHz is heavily attenuated).

**Comparison with Amiga A500** (0.160% THD at 440 Hz): CompactMac has roughly 10× more
harmonic distortion. Both are authentic hardware characteristics — the Amiga's comes from
resistor tolerance nonlinearity in the R-2R DAC ladder; the Mac's comes from the coarseness of
8-bit PWM reconstruction through a first-order RC.

### 5.3 Carrier Suppression

The 22,254.5 Hz carrier exceeds the 22,050 Hz Nyquist limit of the 44.1 kHz output and aliases
to ~21,845 Hz. This is within the audible range and was faintly audible as a high-pitched hiss
before the 18 kHz post-RC LPF was added.

With the bilinear-transform LPF in place, the aliased carrier at ~21,845 Hz (close to Nyquist)
is suppressed by approximately **26 dB** relative to the RC output level. This is sufficient to
render the alias inaudible under normal listening conditions.

## 6. Design Notes

**`--speaker` interaction**: `--retro compactmac` models the RC electrical output only, not
the 2-inch cone's mechanical response. The steep −84 dB at 10 kHz measured from a Mac Plus
recording reflects the physical speaker's rolloff, not the RC circuit alone (which gives only
−7.9 dB at 10 kHz). To add the speaker coloration, combine with `--speaker`.

**Mac Plus capture caveat**: The spectral measurements previously cited (−84 dB at 10 kHz)
were taken from a music recording, not a flat-spectrum sweep. Those levels reflect the musical
content at each frequency, not the RC filter's transfer function. The τ=30 µs parameter is
derived from the RC component values on the Mac logic board, not from that recording.

**44.1 kHz structural limitation**: The root cause of carrier aliasing is the mismatch between
the 44,100 Hz output rate and the 22,254.5 Hz Mac carrier (ratio ≈ 1.982, not an integer). A
structural fix would allow the DSP pipeline to carry an arbitrary sample rate metadata tag
(e.g., via an `AudioBuffer` record) so the audio backend could resample the final output to the
correct rate. This would let the simulation run at 22,254.5 Hz or 44,509 Hz (2× carrier) and
avoid aliasing entirely. The 18 kHz LPF is a pragmatic mitigation pending that architectural
change.
