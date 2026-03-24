# Retro Audio: Shared Design Decisions

Cross-cutting engineering topics that apply to multiple `--retro` modes.

## 1. Why 4× Oversampling (`OneBitHardwareFilter`)

`OneBitHardwareFilter` is shared by both `--retro apple2` and `--retro pc`. Three arguments
for the 4× oversampling factor were examined during development. Only one proved valid.

**❌ Argument 1: "Duty levels are not representable at 1×"**

Claim: with only ~2–3 output samples per carrier period, adjacent duty levels produce identical
±1 bit sequences and cannot be distinguished by the IIR.

- Apple II: 22,050 Hz carrier, 32 levels, 1/32 duty step. At 1× there are exactly 2.0
  samples per carrier period — the carrier sits exactly at Nyquist.
- PC: 15,200 Hz carrier, 78 levels, 1/78 duty step. At 1× there are ~2.9 samples per period.

This is incorrect. `duty = round(input × levels) / levels` is stored as a `double`, and
`carrierPhase` is also a `double` advancing by `carrierHz / sampleRate` per sample. The
comparison `carrierPhase < duty` uses full floating-point precision. Over many carrier cycles
the IIR's long-term average converges to a distinct value for every discrete duty level. No
integer sample alignment is required.

**❌ Argument 2: "Hardware clock resolution requires high oversampling"**

Claim: the original hardware generates duty cycle edges at the resolution of its master clock —
Apple II at 1.02 MHz (1/46 of the 22 kHz carrier period ≈ 980 ns), PC PIT at 1.19 MHz
(1/78 of the 15.2 kHz carrier period ≈ 838 ns). Reproducing this requires a sample rate of
~1 MHz, i.e., ~23–27× oversampling.

Also incorrect. The clock-tick resolution is the edge-timing precision of the *original analog
hardware*. In our simulation, `duty = k/levels` is a continuous floating-point threshold
compared against a floating-point phase accumulator. The transition point is not snapped to
integer sub-sample boundaries — it is evaluated with double precision at each step. The
floating-point representation already captures 1/levels precision (and far beyond) at 1×,
with no additional oversampling needed for timing fidelity.

**✅ Argument 3: "Cone IIR needs sufficient sub-samples per carrier period"**

The valid reason. The IIR models the speaker cone's mechanical inertia by filtering the ±1 PWM
bit stream. For accurate step response — and therefore the correct harmonic texture — the IIR
must be updated frequently enough within each carrier cycle. Too few steps per period produces
a coarse approximation that sounds wrong.

The rule of thumb for adequate IIR step response is 4–8 samples per period of the filtered signal:

| Oversampling | Internal rate | Steps / period (Apple II 22,050 Hz) | Steps / period (PC 15,200 Hz) | Assessment |
| :--- | :--- | :--- | :--- | :--- |
| 1× | 44,100 Hz | 2.0 ❌ (at Nyquist) | 2.9 ❌ | Inadequate |
| 2× | 88,200 Hz | 4.0 △ | 5.8 △ | Borderline |
| 4× | 176,400 Hz | **8.0 ✅** | **11.6 ✅** | Adequate |
| 8× | 352,800 Hz | 16.0 | 23.2 | Excessive |

4× is the minimum choice that clears the threshold for both modes. The Apple II carrier at
22,050 Hz divides 176,400 Hz exactly (8.0 sub-samples per period, no rounding), making 4× a
particularly clean fit. The PC's non-integer 11.6 produces rounding artefacts only above
88 kHz, which is inaudible.

## 2. Aliasing Strategies at 44.1 kHz

All retro modes face the aliasing problem — carrier frequencies are comparable to or exceed the
44.1 kHz output sample rate. Three strategies are used:

| Mode | Strategy | Rationale |
| :--- | :--- | :--- |
| `compactmac` | Analytical RC integration (exact exponential formula) | Physical RC capacitor on the Mac logic board; τ measured from hardware captures. Zero aliasing by definition. |
| `pc`, `apple2` | 4× oversampling (176,400 Hz) + 6–7 pole IIR | No physical capacitor; speaker cone is filtered by mechanical inertia. Oversampling avoids aliasing without assuming RC topology. |
| `spectrum` | No carrier; direct amplitude quantization | Z80 directly toggles speaker voltage. No carrier integration step needed. |
| `covox`, `amiga` | Linear interpolation between DAC samples | R-2R DAC with effective rate ~11–22 kHz. Linear interp is the minimum-quality resampler for downsampling from 44.1 kHz. |

## 3. The 44.1 kHz / Carrier Frequency Relationship

For `OneBitHardwareFilter`, `carrierStep = carrierHz / sampleRate`:

| Mode | carrierStep | Carrier periods per output sample |
| :--- | :--- | :--- |
| apple2 | 0.500 | 2 output samples per carrier cycle |
| pc | 0.345 | ~2.9 output samples per carrier cycle |

The apple2 mode's exact `carrierStep=0.5` is what makes the two-pulse encoding natural: each
carrier period spans exactly two output samples, mirroring the DAC522 hardware behaviour.

## 4. The `--speaker` Option and Retro Modes

The `--speaker` flag applies an `AcousticSpeakerFilter` — a post-processing acoustic
coloration stage that models the frequency response of vintage speaker cabinets. It is a
useful standalone effect for modern synthesis engines, but it interacts badly with `--retro`
modes.

### Why They Conflict

Every `--retro` mode already models its physical speaker as an integral part of its signal
chain. The speaker is not a separable add-on; it defines the mode's sound:

| Retro Mode | Speaker Model Built In |
| :--- | :--- |
| `compactmac` | RC integration (τ = 30 µs) models the 2-inch Mac speaker; −84 dB at 10 kHz |
| `spectrum` | HP (α = 0.930, ~510 Hz) + 2× LP (α = 0.600, ~4.5 kHz) models the 22mm 40Ω beeper |
| `pc` | 7-pole IIR (1 electrical: τ_e=10 µs + 6 mechanical: τ_m=37.9 µs, all at 176,400 Hz); −3 dB at 1.4 kHz, −68 dB carrier suppression |
| `apple2` | 6-pole IIR (τ_m=28.4 µs) models the Apple II speaker cone rolloff |
| `covox` / `disneysound` | RC LPF (α = 0.26, ~2.12 kHz) models the parallel-port analog circuit |
| `amiga` / `a500` | Static RC LPF (~4.5 kHz) + LED 2-pole (~3.3 kHz) models Paula's analog output stage |
| `a1200` | AGA DAC filter (~28 kHz, near-transparent) + LED 2-pole (~3.3 kHz) |

Applying `--speaker` on top of any of these chains a second EQ/filter stage with no physical
basis. The result is an over-filtered signal.

### The Pipeline Execution Order

The `wrapRetroPipeline()` method in `CommonOptions` applies `--speaker` first (innermost),
then `--retro` (outermost). In the pull-based pipeline, processing flows from outermost inward,
so the actual execution order is:

```
retro filter → speaker filter → sink
```

This means the retro mode's quantization and carrier integration sees the already-speaker-
coloured signal as its input — which is also physically incorrect. Real hardware quantized the
raw audio signal before the speaker received it.

### When `--speaker` Is Appropriate

`--speaker` is designed for use **without** `--retro` — to add vintage character to a modern
synthesis engine that produces a clean, full-range output:

```bash
# Correct: adds tin-can coloring to a clean FluidSynth render
midra fluidsynth piano.sf2 --speaker tin-can song.mid

# Correct: warm-radio coloring over OPL (no built-in speaker model)
midra opl --speaker warm-radio song.mid

# Not recommended: doubles the speaker model
midra opl --retro pc --speaker tin-can song.mid
```

Midiraja does not automatically suppress `--speaker` when `--retro` is active — the combination
is permitted but physically inaccurate. For authentic period sound, use `--retro` alone and
rely on its built-in speaker model.
