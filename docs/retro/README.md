# Retro Hardware Audio Simulation

`--retro` is a DSP post-processing stage that sits downstream of Midiraja's internal
synthesizers (OPL, OPN, MT-32, FluidSynth, JavaSynth, etc.). It takes the clean,
full-range PCM output of the synth and reshapes it to sound as if it were played back
through a specific piece of vintage hardware — reproducing the quantization noise,
carrier artefacts, and analog filtering that defined the sonic character of each machine.

The goal is not bit-accurate emulation of a particular chip, but **acoustic authenticity**:
recreating the listening experience of playing MIDI music on hardware you may remember
from the 1980s and 1990s. Each mode models the complete audio signal path of its target —
DAC nonlinearity, PWM carrier dynamics, speaker cone inertia, RC filtering — as faithfully
as possible within a 44.1 kHz digital environment.

## Position in the DSP Pipeline

`--retro` runs early in the signal chain, before the FX stage. The full execution order is:

```
Synth (PCM short[])
  → ShortToFloat
  → --retro          ← this stage
  → FX: --bass/--mid/--treble / --tube / --chorus / --reverb
  → MasterGain (--volume)
  → --compress
  → --speaker
  → Audio output (miniaudio ring buffer)
```

**Why this order:** Vintage hardware quantized and filtered the raw synthesizer signal before it
reached any amplification stage. Placing `--retro` first ensures it receives clean, linear PCM,
which it then reshapes exactly as the original hardware would have. The FX stage downstream
operates on the already-coloured signal — adding reverb to a Spectrum beeper sound, for example,
rather than reverberation that the beeper would then distort.

`--compress` and `--speaker` sit outside the float conversion layer and operate on the final
output mix, independent of which synth or retro mode is active.

> **Note on `--speaker`:** Applying `--speaker` on top of `--retro` doubles the speaker model
> (each retro mode already includes its hardware speaker). See
> [retro-common-engineering.md §4](retro-common-engineering.md#4-the---speaker-option-and-retro-modes)
> for details.

---

## Mode Summary

| CLI Flag | Aliases | Filter Class | Carrier | Levels | Character |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `--retro compactmac` | — | `CompactMacSimulatorFilter` | 22.25 kHz PWM | 256 (8-bit) | Warm, muffled, heavy mono |
| `--retro apple2` | — | `OneBitHardwareFilter` | 22.05 kHz PWM | 32 (5-bit) | 5-bit harmonic texture, cone rolloff |
| `--retro pc` | — | `OneBitHardwareFilter` | 15.2 kHz PWM | 78 (6.3-bit) | Gritty crunch, 6-pole cone IIR, carrier at −68 dB |
| `--retro spectrum` | — | `SpectrumBeeperFilter` | N/A (direct toggle) | 128 (7-bit) | Buzzy Z80 texture, beeper resonance |
| `--retro covox` | — | `CovoxDacFilter` | 11 kHz linear interp | 256 (8-bit) | R-2R harmonic warmth |
| `--retro disneysound` | — | `CovoxDacFilter` | 11 kHz linear interp | 256 (8-bit) | Parallel port DAC (LPT) |
| `--retro amiga`, `--retro a500` | — | `AmigaPaulaFilter` | 22 kHz linear interp | 256 (8-bit) | Warm stereo, LED-filtered, hard-pan |
| `--retro a1200` | — | `AmigaPaulaFilter` | 22 kHz linear interp | 256 (8-bit) | Bright stereo, AGA near-transparent |

## Per-Mode Documents

| Document | Covers |
| :--- | :--- |
| [retro-compactmac-engineering.md](retro-compactmac-engineering.md) | Mac 128k / Mac Plus (`--retro compactmac`) |
| [retro-apple2-engineering.md](retro-apple2-engineering.md) | Apple II DAC522 (`--retro apple2`) |
| [retro-pc(realsound)-engineering.md](retro-pc(realsound)-engineering.md) | IBM PC Speaker / RealSound (`--retro pc`) |
| [retro-spectrum-engineering.md](retro-spectrum-engineering.md) | ZX Spectrum beeper (`--retro spectrum`) |
| [retro-covox-engineering.md](retro-covox-engineering.md) | Covox Speech Thing / Disney Sound Source (`--retro covox`, `--retro disneysound`) |
| [retro-amiga-engineering.md](retro-amiga-engineering.md) | Amiga Paula chip (`--retro amiga`, `--retro a500`, `--retro a1200`) |
| [retro-common-engineering.md](retro-common-engineering.md) | Shared design decisions, aliasing strategies, `--speaker` interaction |
