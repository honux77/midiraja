# Apple II Audio Simulation (`--retro apple2`)

## 1. Hardware Background

The Apple II had no sound hardware at all. The CPU manually toggled a speaker I/O port ($C030)
using cycle-counted 6502 machine code. Every audio operation consumed 100% of the 1.0205 MHz CPU.

The early SoftDAC technique (used in *Digitized Sound for the Apple II* and similar programs)
drove the speaker at an ~11 kHz sample rate by counting CPU cycles:

$$1{,}020{,}500 \text{ Hz} \div 93 \text{ cycles} \approx 10{,}973 \text{ Hz} \approx 11 \text{ kHz}$$

Each 93-cycle period produced ~6.5 bits of effective resolution — but the 11 kHz carrier fell
squarely in the audible range, producing a painful screech.

## 2. The DAC522 Technique

*DAC522* (described by Scott Alfter at KansasFest) solved the screech problem by halving the
period from 93 cycles to 46 cycles, raising the carrier frequency to 22 kHz — above the human
hearing limit — while preserving the ~11 kHz audio sample rate through a two-pulse encoding scheme:

$$46 \text{ cycles} \times 2 = 92 \text{ cycles} \approx 93 \text{ cycles} \approx 11 \text{ kHz}$$

Each audio sample is encoded as **two consecutive 46-cycle pulses**. The pair together
approximates the original 93-cycle sample period. The 22 kHz carrier noise is inaudible; only
the audio content at 11 kHz is heard.

Each pulse uses discrete widths of 6 to 37 cycles out of a 46-cycle period — **32 levels**,
or approximately 5-bit resolution.

| | SoftDAC (original) | DAC522 |
| :--- | :--- | :--- |
| Period | 93 cycles | 46 cycles × 2 |
| Carrier | ~11 kHz (audible) | ~22 kHz (inaudible) |
| Levels per pulse | ~93 | 32 (6–37 cycle widths) |
| Screech | Yes | No |

## 3. Parameters

| Parameter | Value | Purpose |
| :--- | :--- | :--- |
| Carrier | 22,050 Hz | Above hearing limit |
| Levels | 32 | 5-bit: pulse widths 6–37/46 |
| τ (cone time constant) | 28.4 µs | Derived from original smoothAlpha=0.55 via τ = −1/(44100 × ln(1−0.55)) |
| IIR α (at 176,400 Hz) | 0.1487 | = 1 − exp(−1/(176400 × 28.4e-6)) |
| IIR poles | 6 (cascaded) | See [retro-common-engineering.md §1](retro-common-engineering.md) |

In the implementation, `carrierHz=22050` at `sampleRate=44100` means `carrierStep=0.5`:
each carrier period spans two consecutive 44.1 kHz audio output samples, mirroring the
two-pulse encoding of DAC522.

## 4. Simulation Algorithm

Earlier versions used `integratePwm()`, which computes the exact time-average of the PWM duty
cycle over each 44.1 kHz output sample. This produces a mathematically perfect linear DAC
output: quantisation harmonics are modulated onto carrier sidebands above 20 kHz, leaving the
audible band completely clean — too clean for authentic 1-bit character.

The current implementation uses **4× internal oversampling** (176,400 Hz). At each sub-sample,
the raw ±1 PWM bit is evaluated directly and fed to a **six-pole IIR** (six cascaded one-pole
stages) modelling the mechanical cone (τ = 28.4 µs, derived from the empirical rolloff
frequency). The result is decimated 4:1 to 44,100 Hz.

Why oversampling rather than RC integration (as in `--retro compactmac`)?
The Compact Mac had a physical RC capacitor on its logic board; τ was measured from hardware
captures. The Apple II has no such capacitor — the speaker is driven directly and filtered only
by the cone's mechanical inertia. Using the RC label for a mechanical system would be
physically inaccurate. Oversampling sidesteps this: it makes no assumptions about the filter
topology and lets the IIR model the cone empirically.

For the rationale behind the 4× oversampling factor (shared with `--retro pc`), see
[retro-common-engineering.md §1](retro-common-engineering.md).
