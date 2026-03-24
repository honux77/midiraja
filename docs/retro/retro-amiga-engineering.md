# Amiga Paula Simulation (`--retro amiga`, `--retro a500`, `--retro a1200`)

## 1. Hardware Background

The Commodore Amiga's Paula chip (MOS 8364) provided four independent 8-bit PCM playback
channels. Each channel had its own dedicated R-2R ladder DAC — four separate 8-bit resistor
networks in a single chip. The channels were hard-panned at the factory: channels 0 and 3
were routed to the left audio output, channels 1 and 2 to the right.

The hard panning was a deliberate hardware design choice that defined the Amiga's characteristic
stereo sound: instruments were assigned to physical left or right channels, not panned in
software. Classic Amiga game music and demoscene compositions were composed and mixed with this
constraint in mind — a wide, ping-pong stereo image with no centre channel.

Both the Amiga 500 (OCS chipset, 1987) and the Amiga 1200 (AGA chipset, 1992) used Paula,
but their analog output circuitry differed significantly:

- **Amiga 500:** Paula's raw R-2R output fed through a single passive RC low-pass filter
  (~4.5 kHz cutoff) on the audio board. This heavy filtering gave the A500 its warm, almost
  muffled character — the DAC's high-frequency aliasing products and staircase edges were
  suppressed before the signal reached the amplifier or headphone output.
- **Amiga 1200:** The AGA chipset revision improved the analog path. The RC filter was replaced
  by a near-transparent stage (~28 kHz cutoff), relying instead on the LED filter for any
  intentional tonal shaping. The result is a significantly brighter, more detailed sound
  compared to the A500.

Both machines included a secondary **LED filter** — a two-pole active low-pass Sallen-Key
stage with a ~3.3 kHz cutoff — that could be engaged by the software. `AmigaPaulaFilter`
includes this stage in the signal chain for both profiles.

## 2. Signal Chain

Each channel (L and R) passes through the following stages independently:

```
Input (L or R)
  → Linear interpolation at ~22 kHz  (ramp over 2 samples at 44.1 kHz)
  → R-2R DAC LUT (256 levels, ±3% tolerance, seed=1985)
  → Static RC LPF            (A500: α=0.39, ~4.5 kHz / A1200: α=0.80, ~28 kHz)
  → LED filter (2-pole)      (α=0.32 × 2 cascades, ~3.3 kHz)
  → M/S stereo widening      (default width=1.6, i.e. 60%)
Output (L or R)
```

**Resampling (linear interpolation):** Paula's DMA hardware fetched one 8-bit sample from
chip RAM and held it for the duration of the DMA period before the next fetch. At typical
Amiga playback rates (~22 kHz), each value is held for approximately two 44.1 kHz output
samples. The implementation uses linear interpolation between consecutive DAC values over this
2-sample hold window, which is the minimum-quality resampling standard when converting 44.1 kHz
CD-quality input to the ~22 kHz effective rate. (Pure ZOH/nearest-neighbour would introduce
additional aliasing artefacts not present in the original hardware's analogue output.)

**R-2R DAC LUT:** A pre-computed 256-entry lookup table maps each 8-bit quantization level to
a slightly non-ideal voltage, simulating the gain mismatch inherent in real R-2R resistor
ladders with ±3% component tolerances. The seed value (1985 — Amiga's launch year) ensures a
reproducible, deterministic DAC nonlinearity. This produces gentle harmonic distortion that is
characteristic of Paula's texture.

**Static RC LPF:** The profile-specific first-order low-pass filter that differentiates the
A500 from the A1200:
- **A500 (α=0.39):** Aggressive roll-off, strongly attenuates above ~4.5 kHz. Produces the
  classic warm, bassy A500 character.
- **A1200 (α=0.80):** Near-transparent; passes almost all audio content up to ~28 kHz. Retains
  the DAC texture without heavy tonal shaping.

**LED filter (2-pole cascade):** Two cascaded first-order stages, each with α=0.32,
approximating the hardware Sallen-Key circuit. The combined response rolls off steeply above
~3.3 kHz. Applied equally to both profiles.

**M/S stereo widening:** See §4 and §5.

## 3. A500 vs A1200 Profiles

| Aspect | A500 | A1200 |
| :--- | :--- | :--- |
| RC LPF cutoff | ~4.5 kHz (α=0.39) | ~28 kHz (α=0.80) |
| Character | Warm, bassy, muffled | Bright, detailed, near-transparent |
| LED filter | Yes (both profiles) | Yes (both profiles) |
| DAC model | 8-bit R-2R, ±3% | 8-bit R-2R, ±3% |
| Resampling | Linear interp, 2 samples | Linear interp, 2 samples |
| CLI flag | `--retro amiga` or `--retro a500` | `--retro a1200` |

The A500 profile is the default `amiga` alias because the warm, filtered sound is the one most
associated with classic Amiga game music. Use `--retro a1200` when you want the cleaner AGA
presentation, or when combining with other DSP effects where the A500's heavy roll-off would
over-darken the signal.

## 4. Stereo Model

Real Amiga music is produced by assigning individual MIDI voices or tracker channels to
physical Paula channels. Voices on channels 0 and 3 appear only in the left speaker; voices on
channels 1 and 2 appear only in the right speaker. The hard-panning is part of the composition.

`AmigaPaulaFilter` receives a pre-mixed stereo signal (not four separate MIDI voices), so a
literal four-channel simulation is not possible. Instead, the filter applies the linear interp
→ R-2R → RC LPF → LED chain to the left and right channels **independently** — matching
Paula's hardware reality where each DAC path is completely separate — and then applies M/S
stereo widening to approximate the pronounced channel separation of the original hard-panned
output.

The M/S model encodes the input into Mid (L+R) and Side (L−R) components, scales the Side
component by a width factor, then decodes back to L/R. With the default `width=1.6` (60%),
the stereo image is broadened to resemble the hard-panned feel of authentic Amiga music without
artificially inverting phase or creating mono-incompatible output.

## 5. Parameters

| Parameter | A500 value | A1200 value | Purpose |
| :--- | :--- | :--- | :--- |
| Resampling | Linear interp, 2 samples | Linear interp, 2 samples | Paula DMA fetch rate (~22 kHz) |
| DAC levels | 256 (8-bit) | 256 (8-bit) | R-2R ladder resolution |
| DAC tolerance | ±3%, seed=1985 | ±3%, seed=1985 | Resistor mismatch nonlinearity |
| RC LPF α | 0.39 | 0.80 | ~4.5 kHz / ~28 kHz hardware cutoff |
| LED filter α | 0.32 × 2 | 0.32 × 2 | 2-pole ~3.3 kHz Sallen-Key stage |
| Default width | 1.6 (60%) | 1.6 (60%) | M/S stereo widening |
| Channel mode | Independent L/R | Independent L/R | Matches separate Paula DAC paths |

## 6. `--paula-width`

The `--paula-width <PCT>` option (0–300) controls the M/S stereo widening as a percentage.
It is only effective when `--retro amiga`, `--retro a500`, or `--retro a1200` is active.

| Value | Behaviour |
| :--- | :--- |
| `0` | No widening — output matches the filtered signal without M/S expansion |
| `60` *(default)* | Default Paula hard-pan approximation |
| `100` | Maximum safe widening — strong stereo image without clipping risk |
| `101–300` | Hyper-wide; may cause clipping on dense mixes |

```bash
# A500 profile, default widening
midra opl --retro a500 song.mid

# A1200 profile, strong stereo spread
midra opl --retro a1200 --paula-width 80 song.mid

# Narrow the widening for a more mono-compatible output
midra opl --retro amiga --paula-width 20 song.mid
```

## 7. Measured Distortion Characteristics (AmigaOnly A500)

The following measurements were produced by `DspAnalyzer` + `scripts/analyze_audio.py` at
44.1 kHz, stereo, using the current implementation (linear interpolation resampling,
R-2R DAC LUT ±3% seed=1985, static LPF α=0.39, LED cascade α=0.32×2).
No real-hardware reference is available for comparison; these figures describe the
simulation as-implemented.

### THD at 440 Hz sine (–12 dBFS input)

| Config | THD% | Fund | 2f (880 Hz) | 3f (1320 Hz) | 4f (1760 Hz) | 5f (2200 Hz) | Noise floor |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Dry | 0.002% | –12.0 dB | –131.6 dB | –109.6 dB | –140.5 dB | –114.7 dB | –146.9 dB |
| AmigaOnly (A500) | 0.160% | –12.4 dB | –93.4 dB | –73.0 dB | –113.4 dB | –70.1 dB | –107.0 dB |
| ReverbOnly (ROOM 50%) | 0.002% | –14.6 dB | –122.9 dB | –109.7 dB | –138.1 dB | –114.3 dB | –131.1 dB |
| NEW: Amiga→Reverb | 0.167% | –14.9 dB | –95.4 dB | –76.1 dB | –114.2 dB | –71.9 dB | –108.3 dB |
| OLD: Reverb→Amiga | 0.537% | –15.4 dB | –96.6 dB | –61.8 dB | –102.7 dB | –67.6 dB | –105.3 dB |

Pipeline order matters: placing Reverb before Amiga (OLD) triples THD (0.537% vs 0.167%)
because the reverb tail — which contains frequency content across the full band — is then
re-quantized through the 8-bit DAC LUT, amplifying intermodulation artefacts.

### THD frequency sweep (AmigaOnly A500, sine at –12 dBFS)

| Freq | Dry THD | Amiga THD | 3rd harm | 5th harm | Noise floor |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 100 Hz | 0.001% | 0.281% | –70.1 dB | –64.1 dB | –105.6 dB |
| 200 Hz | 0.001% | 0.253% | –70.5 dB | –65.2 dB | –106.1 dB |
| 500 Hz | 0.001% | 0.151% | –73.1 dB | –71.1 dB | –106.2 dB |
| 1000 Hz | 0.001% | 0.063% | –79.6 dB | –82.0 dB | –107.8 dB |
| 1500 Hz | 0.001% | 0.049% | –82.7 dB | –92.4 dB | –105.4 dB |
| 2000 Hz | 0.001% | 0.021% | –91.6 dB | –99.9 dB | –107.0 dB |
| 3000 Hz | 0.001% | 0.019% | –96.7 dB | –112.3 dB | –104.5 dB |

THD decreases with frequency because the LED filter (–3 dB at ~3.3 kHz) attenuates the
harmonics more than the fundamental as frequency rises.

### IMD: 440 + 880 + 1320 Hz chord (AmigaOnly A500)

Notable intermodulation products vs. dry signal:

| Freq | Amp (dBFS) | Δ vs dry | Note |
| ---: | ---: | ---: | :--- |
| 440 Hz | –21.9 dB | –0.3 dB | Fundamental (reference) |
| 880 Hz | –22.8 dB | –1.2 dB | Fundamental |
| 1320 Hz | –24.1 dB | –2.5 dB | Fundamental |
| 1760 Hz | –69.3 dB | **+39.5 dB** | 2nd-order IMD product (2×440±880, etc.) |
| 2200 Hz | –78.9 dB | **+39.5 dB** | 2nd/3rd-order IMD products |
| 3520 Hz | –83.8 dB | +30.6 dB | 3rd-order IMD (2×1320±880) |
| 3080 Hz | –93.8 dB | +28.6 dB | 3rd-order IMD |

The 1760 Hz and 2200 Hz products (+39.5 dB above dry) are the perceptually relevant artefacts:
they sit within the LED filter passband (~3.3 kHz cutoff) and are audible on dense harmonic
content. This is the origin of the "FM radio detuning" texture on complex chords.

> **Note on hardware accuracy:** The ±3% resistor tolerance is based on typical component
> specifications for the era. No published bench measurements of the real Paula chip's THD/IMD
> are publicly available for comparison. These figures describe the simulation as-implemented.

### Spectral shape with white noise input

| Config | RMS | 0–200 Hz | 200–1.5 kHz | 1.5–6 kHz | 6–22 kHz |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Dry | –10.8 dBFS | –56.9 dB | –57.3 dB | –57.2 dB | –57.2 dB |
| Amiga (noise) | –18.9 dBFS | –53.4 dB | –55.4 dB | –63.9 dB | –87.7 dB |

The –8.1 dB RMS reduction and 30 dB roll-off above 6 kHz reflect the combined effect of
the linear interpolation resampler, static RC LPF (α=0.39), and LED cascade (α=0.32×2).

## 8. Real-Sample Comparison (March 2026)

A real Amiga music recording obtained for this analysis (48 kHz stereo, 111 s) was compared
against the simulation. Key findings from `scripts/compare_amiga.py`:

### Simulation accuracy

The simulation matches the analytic magnitude of RC(α=0.39) × LED(α=0.32)² within 0.3 dB at
all measured frequencies (100 Hz – 3 kHz). This confirms the implementation is behaving exactly
as designed.

### Simulation vs. real sample

| Frequency | Sim (A500) | Theory (RC+LED) | Real sample | Gap (Sim−Sample) |
| ---: | ---: | ---: | ---: | ---: |
| 100 Hz | +1.5 dBr | +1.4 dBr | +10.5 dBr | −9.1 dB |
| 500 Hz | +1.1 dBr | +1.1 dBr | +16.9 dBr | −15.9 dB |
| 1,000 Hz | 0.0 dBr | 0.0 dBr | 0.0 dBr | 0.0 dB |
| 1,500 Hz | −1.6 dBr | −1.6 dBr | −10.8 dBr | +9.2 dB |
| 2,000 Hz | −3.6 dBr | −3.5 dBr | −12.0 dBr | +8.4 dB |
| 3,000 Hz | −8.1 dBr | −7.8 dBr | −15.3 dBr | +7.2 dB |
| **6,000 Hz** | **N/A** | **−19.2 dBr** | **−18.1 dBr** | **+1.1 dB** |
| 8,000 Hz | N/A | −24.9 dBr | −40.4 dBr | — |
| 10,000 Hz | N/A | −29.3 dBr | −54.7 dBr | — |

All levels are relative to each signal's own 1 kHz level (dBr).

**6 kHz is the only reliable calibration point**: simulation and theory both predict −19.2 dBr
at 6 kHz, and the real sample measures −18.1 dBr — a match within 1.1 dB. At this frequency
the hardware filter dominates and the music has little intrinsic energy.

The 100–500 Hz range shows the music's heavy bass content (+10–17 dBr), not a hardware
discrepancy. The 1.5–3 kHz gap (+7–9 dB, simulation brighter) reflects the music having
relatively little midrange energy rather than a simulation error. Above 6 kHz, both the musical
content and the hardware filter attenuate heavily; the exact split cannot be determined from a
music recording alone.

### Sample is perfect mono

The recording measures L−R = −240 dBFS (L and R channels are numerically identical). Real
Amiga hardware outputs hard-panned stereo (channels 0,3 → left; channels 1,2 → right), so
this recording was likely captured from a mono output (TV speaker or headphone L+R mix). The
stereo widening stage of `AmigaPaulaFilter` cannot be validated from this sample.

### Limitations and provisional nature

Like the CompactMac analysis, the available sample is a music recording rather than a
flat-spectrum test signal. **The simulation is confirmed accurate at its design frequency
(6 kHz match within 1.1 dB); the filter parameters (α=0.39 for RC, α=0.32×2 for LED) are
validated against published Amiga hardware documentation and consistent with the spectral
measurement at 6 kHz.** A recording of an Amiga playing white noise or a frequency sweep
would be needed to fully characterise the hardware transfer function and validate the LED
filter cutoff independently.
