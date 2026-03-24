# Covox Speech Thing / Disney Sound Source Simulation (`--retro covox`, `--retro disneysound`)

## 1. Hardware Background

The Covox Speech Thing (1986) was an 8-bit R-2R resistor ladder DAC connected directly to the
IBM PC parallel (LPT) port. Unlike PWM-based audio, it provided true 8-bit PCM output — but
with characteristic limitations:

- **R-2R resistor variance:** Real-world ±3% component tolerances introduced gentle harmonic
  distortion across all 256 levels.
- **CPU-limited sample rate:** The LPT port driven by DOS software rarely exceeded ~11 kHz in
  practice (some sources cite ~22 kHz possible on fast 286/386 systems; 11 kHz was typical in
  8088-era games).
- **Minimal analog filtering:** A simple RC filter smoothed the 8-bit steps, with a
  characteristic ~2.12 kHz cutoff (15 kΩ + 5 nF).

> `--retro disneysound` uses the same filter. The Disney Sound Source was also a parallel port
> R-2R DAC, structurally identical to the Covox Speech Thing.

## 2. Simulation Model

The filter builds a fixed LUT of 256 R-2R DAC output levels using a seeded pseudo-random
resistor variance (±3%), computed once at initialization:

- **Linear interpolation at ~11 kHz** (ramp over 4 samples at 44.1 kHz): simulates the DOS
  CPU overhead bottleneck. Linear interpolation is used in preference to ZOH (nearest-neighbour)
  to avoid aliasing artefacts when resampling 44.1 kHz input to the ~11 kHz effective rate.
- **R-2R nonlinearity via LUT** (seed=1987): produces authentic harmonic distortion characteristic
  of parallel-port DAC hardware.
- **RC LPF α = 0.26** (≈ 2.12 kHz cutoff): simulates the physical capacitor in the circuit.

The Covox was mono. Both channels receive the same mixed-down signal.

## 3. Parameters

| Parameter | Value | Purpose |
| :--- | :--- | :--- |
| Effective sample rate | ~11 kHz (4-sample hold) | Typical 286/386 DOS CPU bandwidth |
| Resampling | Linear interpolation, 4 samples | Avoids ZOH aliasing when downsampling |
| DAC levels | 256 (8-bit) | R-2R ladder resolution |
| DAC tolerance | ±3%, seed=1987 | Resistor mismatch nonlinearity |
| RC LPF α | 0.26 | ~2.12 kHz (15 kΩ + 5 nF) |
| Channel mode | Mono (L+R mix-down) | Covox was a mono device |
