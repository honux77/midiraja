# IBM PC Speaker / RealSound Simulation (`--retro pc`)

RealSound was an audio technique pioneered by Access Software in the late 1980s that played
sampled audio through the IBM PC's internal speaker — a device never designed for PCM playback.
This document describes the hardware, the empirical measurements used to characterise it, and
the simulation implemented in `OneBitHardwareFilter`.

## 1. Hardware Background

### 1.1 The PC Speaker as a 1-Bit Device

The IBM PC internal speaker was a pure 1-bit physical device: it could only be ON or OFF.
To play analog waveforms through it, RealSound used the **Intel 8253 PIT (Programmable Interval
Timer)** channel 2 as a timing reference for **Pulse Width Modulation (PWM)**.

By turning the speaker on and off at high speed (the carrier frequency) and varying the
proportion of time it stayed on during one cycle (the duty cycle), developers could trick the
sluggish paper-cone speaker into hovering at intermediate analog positions.

### 1.2 PWM as Time-Domain Quantization

The most critical insight is that **PWM inherently acts as a DAC**. The resolution is
determined entirely by how many discrete duty-cycle steps fit within one carrier period:

$$\text{levels} = \frac{f_\text{PIT clock}}{f_\text{carrier}}$$

The theoretical maximum — using all 64 PIT ticks per period — yields 18.64 kHz at 6-bit
(64 levels). However, empirical FFT analysis of original RealSound `.wav` demos found
the actual carrier at **15.2 kHz**, because developers used **78 steps** per period instead
of 64, giving the 8088 CPU more time between samples to decode compressed audio:

$$1{,}193{,}182 \text{ Hz} \div 78 \approx 15{,}297 \text{ Hz} \approx 15.2 \text{ kHz}, \quad \approx 6.3 \text{ bits}$$

Since $2^6 = 64$ and $2^7 = 128$, having 78 discrete steps places the resolution at ~6.3 bits.
Any pristine audio fed into this timer is squeezed into 78 temporal steps — a form of
quantization that is physically authentic rather than algorithmically applied.

## 2. Acoustic Characteristics: Reference Recording Analysis

Welch PSD analysis of `samples/realsound_sample.wav` (original RealSound recording,
48,000 Hz stereo, 37.8 s, mono RMS −38 dBFS):

| Frequency | Level relative to 1 kHz | Interpretation |
| :--- | :--- | :--- |
| 100 Hz | −1.7 dB | Normal music content |
| 800 Hz | −2.1 dB | Near-flat |
| 1,000 Hz | 0 dB | Reference |
| 1,346 Hz | −2.4 dB | Rolloff beginning |
| 2,000 Hz | −18.7 dB | Steep cone rolloff |
| 2,500 Hz | −23.2 dB | Deep rolloff (no resonance peak) |
| 6,700 Hz | −22.4 dB | Slight recovery — but see §4 |
| 15,200 Hz (carrier) | −31.5 dB | ≈ noise floor at 18 kHz (−32.7 dB) |
| 18,000 Hz | −32.7 dB | Recording noise floor |
| 20,000 Hz | −77.4 dB | Recording chain cutoff |

Key findings:

- **The carrier is not a visible spike.** At 15,200 Hz the reference measures −31.5 dB, which
  is effectively the same level as the 18 kHz noise floor (−32.7 dB). The real hardware's
  cone mass suppressed the carrier well below the noise floor of the recording.

- **The −3 dB rolloff is near 1.4 kHz.** Response drops steeply above 1–1.5 kHz: −18.7 dB
  at 2 kHz, −29.3 dB at 4 kHz. This matches the 6-pole IIR rolloff (calculated −3 dB at 1,435 Hz).

- **The bumps at 6,947 Hz and 9,626 Hz are music content, not speaker resonances.**
  STFT temporal analysis showed a coefficient of variation (CV) > 1.2 at both frequencies —
  meaning their energy varies strongly over time, tracking the music, not maintaining a steady
  resonance level. A physical speaker resonance would appear as a constant-amplitude peak
  independent of what music is playing.

- **No evidence for resonance peaks at 2.5 kHz or 6.7 kHz as speaker characteristics.**
  The reference records −23 dB at 2.5 kHz and −22 dB at 6.7 kHz — both in the heavily
  attenuated rolloff region. Adding peaking biquads at these frequencies would produce
  unphysical mid-frequency boosts in a region the real cone barely moved in.

## 3. Parameters

| Parameter | Value | Purpose |
| :--- | :--- | :--- |
| Carrier | 15,200 Hz | Empirically measured from original demos |
| Levels | 78 | 1.19318 MHz / 78 ≈ 15.3 kHz, ~6.3-bit |
| Pre-quantization | 8-bit (128 levels) | Source material bit-depth simulation — see §4 |
| Drive gain | 4.0× (default) | Improves PWM duty-cycle SNR — see §4 and §6 |
| τ_e (electrical, voice coil) | 10 µs | RL inductance of cheap speaker coil (L ≈ 0.1 mH, R ≈ 8 Ω) |
| IIR α_e (at 176,400 Hz) | 0.433 | = 1 − exp(−1/(176400 × 10e-6)) |
| τ_m (mechanical, cone) | 37.9 µs | Derived from original smoothAlpha=0.45 via τ = −1/(44100 × ln(1−0.45)) |
| IIR α_m (at 176,400 Hz) | 0.1395 | = 1 − exp(−1/(176400 × 37.9e-6)) |
| IIR poles | 7 total (1 electrical + 6 mechanical) | See §4 and §5 |
| Resonance biquads | none | See §2 — reference provides no evidence for speaker peaks |

## 4. Simulation Algorithm

Uses **4× internal oversampling** (176,400 Hz), shared with `--retro apple2`.
For the rationale behind this choice, see [retro-common-engineering.md §1](retro-common-engineering.md).

Signal path per output sample:

```
monoIn × driveGain → clamp[−1,1] → 8-bit pre-quantize
  → PWM duty cycle (78 levels)
  → [4× oversampled loop]
      bit (±1)
        → iirStatePre  (electrical: τ_e = 10 µs, α_e = 0.433)
        → iirState1..6 (mechanical: τ_m = 37.9 µs, α_m = 0.1395)
  → iirState6 ÷ driveGain
  → output
```

**8-bit pre-quantization.** The source material available to 1980s PC Speaker developers was
8-bit PCM. Before being sent to the PWM encoder, audio was already limited to 256 discrete
amplitude levels (128 bipolar). Applying `round(monoIn × 128) / 128` before PWM encodes this
constraint: the signal arrives at the duty-cycle quantizer in coarser steps, producing the
characteristic "staircase" texture of period hardware. No dither is applied — 1980s tools used
simple rounding.

Note: since 128 pre-quantization levels > 78 PWM levels, the pre-quantization does not narrow
the effective resolution. Its role is temporal: it makes the duty cycle hold steady between
steps instead of tracking every floating-point fluctuation, which creates the audible step
texture without adding noise.

**Drive gain.** The input is scaled by driveGain before quantization and divided by the same
factor after the IIR. Higher gain forces the signal to use more of the 78-level PWM duty-cycle
range, reducing the PWM quantization error at carrier sideband frequencies (see §6). The
default is 4.0×, chosen to optimise S/N for typical synthesizer output levels around −18 dBFS
with no hard clipping. Signals above 1/driveGain (= 0.25, i.e. above −12 dBFS) are
hard-clipped before PWM encoding — which is authentic: real 1980s PC Speaker developers drove
at maximum amplitude. The gain can be overridden with `--retro-drive`.

**Electrical pre-filter (voice coil inductance).** A cheap PC speaker voice coil is an
inductor (L ≈ 0.1–0.3 mH, R ≈ 8 Ω). The current through an RL circuit does not follow a step
in voltage instantaneously — it rises exponentially with τ_e = L/R ≈ 10 µs. Because the cone
moves in proportion to current (not voltage), the ideal ±1 square wave voltage becomes a
triangle-wave-like current waveform before the mechanical system sees it. `iirStatePre` models
this: it is a single one-pole IIR at 176,400 Hz with α_e = 0.433 (τ_e = 10 µs), inserted
before the six mechanical poles.

**Six cascaded mechanical IIR poles** model the speaker cone's inertia — see §5 for the
carrier suppression analysis that determines the pole count.

The combined 7-pole response gives:
- −3 dB at ~1,400 Hz (cone rolloff beginning)
- −68 dB at 15,200 Hz carrier (inaudible even at −50 dBFS signal levels)

**No resonance biquads.** Earlier versions included peaking EQ biquads at 2.5 kHz and 6.7 kHz.
Welch PSD analysis of the reference recording found no evidence of speaker resonance at these
frequencies (§2). The spectral bumps observed near 6.9–9.6 kHz were confirmed to be music
harmonics via temporal variance analysis (CV > 1.2). The biquads were removed.

## 5. Carrier Noise Analysis: Why Six IIR Poles

The IIR model must suppress the 15.2 kHz carrier sufficiently that it remains inaudible even
during quiet passages. The pole count determines this suppression.

**Single-pole magnitude at 15,200 Hz** (α = 0.1395, fs = 176,400 Hz):

$$|H_1(15\,200)| = \frac{\alpha}{\sqrt{(1-(1-\alpha)\cos\omega)^2 + ((1-\alpha)\sin\omega)^2}} \approx 0.270$$

where $\omega = 2\pi \times 15200 / 176400$.

With $N$ cascaded poles, $|H_N| = 0.270^N$. Carrier suppression vs. audibility:

| Poles | Carrier attenuation | Carrier audible above | Engineering assessment |
| :---: | :---: | :---: | :--- |
| 2 | −23 dB | −23 dBFS signals | Overwhelms all quiet passages |
| 4 | −46 dB | −46 dBFS signals | Still audible over quiet segments (< −10 dBFS) |
| 6 | **−68 dB** | **−68 dBFS signals** | Inaudible even at −50 dBFS signal levels |

**Why 2 poles were insufficient (observed in testing):**

At duty cycle 50% (zero DC content), the PWM fundamental amplitude is 2/π ≈ 0.637. With
2-pole attenuation (−23 dB), the carrier residual is ~4.7% of full scale. For a signal at
−30 dBFS (a quiet passage), the carrier-to-signal ratio is only −7 dB — the carrier is
louder than the music.

**Why 6 poles suffice:**

The reference recording confirms the real hardware suppressed the carrier below the recording
noise floor (−31.5 dB at 15.2 kHz ≈ −32.7 dB noise floor). This means the actual hardware
attenuation was at least 32 dB, but the mechanical cone mass provided substantially more.
Six poles at −68 dB comfortably exceeds this threshold. The rolloff shape (−3 dB at 1,435 Hz)
also matches the reference spectrum shape well.

**Six poles also better models the physics:** A real PC speaker cone barely moves at 15 kHz.
The 36 dB/octave asymptotic slope of 6 cascaded first-order filters approximates the steep
mechanical rolloff above the cone's resonance frequency.

## 6. PWM Carrier Sideband Characteristics

### What Sidebands Are

PWM modulation produces not just the carrier frequency but sum-and-difference intermodulation
products. When a signal of frequency `f` is encoded as PWM with carrier `f_c`, spectral
energy appears at:

$$f_\text{sideband} = f_c - k \cdot f \quad (k = 1, 2, 3, \ldots)$$

At carrier 15,200 Hz with A4 (440 Hz), sidebands at k=29,30,31 land in the audio band:

| k | f_c − k × 440 | In band? |
| :---: | :---: | :---: |
| 29 | 2,440 Hz | Yes |
| 30 | 2,000 Hz | Yes |
| 31 | 1,560 Hz | Yes |

These sidebands are a real artifact of the hardware. What matters perceptually is how close
they fall to existing signal harmonics — if they fall on top of harmonics, they are masked
and inaudible; if they fall between harmonics, they emerge as distinct noise.

### The Sideband-Harmonic Distance

For a signal of fundamental frequency `f`, harmonics land at `k × f` (k = 1, 2, 3…).
A sideband `f_c − k × f` falls at the same frequency as the nearest harmonic only if
`f_c / f` is an integer. The maximum sideband-harmonic distance is:

$$d_\text{max} = \min\!\left(\text{frac}\!\left(\frac{f_c}{f}\right),\; 1 - \text{frac}\!\left(\frac{f_c}{f}\right)\right) \times f$$

where frac(x) is the fractional part. This is maximised when `f_c / f` is exactly a
half-integer — the sidebands land precisely halfway between harmonics.

### Why A4 Is Particularly Problematic

$$\frac{15{,}200}{440} = 34.545\ldots \approx 34.5$$

The ratio is nearly exactly a half-integer. Sidebands land ~200 Hz from the nearest harmonics
(1760 Hz and 2200 Hz), and the 7-pole IIR barely attenuates them at 1,560–2,440 Hz — this is
well below the −3 dB point. Spectral measurements of the simulation confirm sidebands at
−47 dB, compared to a true noise floor of −137 to −145 dB between harmonics.

Note-by-note sideband exposure (carrier 15,200 Hz, 4× oversampled):

| Note | Freq (Hz) | 15200/f | Max sideband dist | Verdict |
| :--- | :---: | :---: | :---: | :--- |
| E1 | 41.2 | 368.9 | 37 Hz | Favorable — sidebands near harmonics |
| A2 | 110.0 | 138.2 | 22 Hz | Favorable |
| E2 | 82.4 | 184.5 | 41 Hz | Borderline |
| A3 | 220.0 | 69.1 | 22 Hz | Favorable |
| D4 | 293.7 | 51.8 | 176 Hz | Exposed |
| E4 | 329.6 | 46.1 | 33 Hz | Favorable (near-integer) |
| **A4** | **440.0** | **34.5** | **200 Hz** | **Maximally exposed** |
| B4 | 493.9 | 30.8 | 176 Hz | Exposed |
| E5 | 659.3 | 23.1 | 33 Hz | Favorable (near-integer) |

Notes whose `f_c / f` ratio is near an integer or half-integer are favorable or maximally
exposed respectively. No single carrier frequency is simultaneously favorable for all notes
in equal temperament, since the frequency ratios are irrational.

### Why the Reference Recording Doesn't Show It

The reference WAV (`samples/realsound_sample.wav`) has a dominant fundamental near 82 Hz.
For this note:

$$\frac{15{,}200}{82} \approx 185.4 \quad \Rightarrow \quad d_\text{max} = 0.4 \times 82 \approx 33 \text{ Hz}$$

The sidebands fall within 33 Hz of the 82 Hz harmonics — too close to separate perceptually.
RealSound composers apparently gravitated toward lower registers empirically for their warmer
sound — which coincidentally kept carrier sidebands close to existing harmonics, hiding them.

### Why Carrier Frequency Substitution Doesn't Help

A brute-force search over candidate carriers from 14 kHz to 22 kHz found that the optimal
choice — 15,320 Hz — reduces the maximum sideband distance for A4 from 640 Hz to 520 Hz.
The improvement is minor because equal temperament's irrational frequency ratios mean no
single carrier can be simultaneously near-integer for all 12 semitone classes. The 15,200 Hz
carrier is historically determined (8253 PIT, 78 ticks); changing it would be inauthentic.

### Why 8-Bit Quantization Doesn't Mask It

8-bit pre-quantization produces its own noise at non-harmonic frequencies. For a single clean
sinusoidal input (440 Hz, −6 dBFS), quantization noise in the 1.5–2.5 kHz band measures
approximately −78 dB (relative to peak). The carrier sideband at the same location measures
−47 dB. The sideband is 31 dB louder than quantization noise — masking is impossible. Even
for complex multi-voice input where quantization noise approaches a quasi-white spectrum
(−73 dB), the sideband remains 26 dB above.

### Historical Interpretation

The audibility of carrier sidebands is authentic PC speaker hardware behaviour. It manifests
most strongly when feeding high-quality, spectrally sparse audio (e.g. FluidSynth soundfont
rendering) into the hardware model. Original PC game music — FM synthesis (OPL2), multi-voice
arrangements, complex timbres — had dense harmonic coverage that filled the sideband positions
naturally, masking them. This is not a simulation defect; it is the correct response of the
hardware to clean input.

## 7. Drive Gain and S/N Optimisation

### The Mechanism

The dominant noise source in the simulation is not broadband quantization noise but carrier
sideband energy at specific frequencies (§6). These sidebands arise primarily from **PWM
duty-cycle quantization error** — the error introduced by rounding the continuous duty cycle
to one of 78 discrete levels. When the signal is quiet and the duty cycle barely deviates
from 0.5, only a few of the 78 levels are exercised and the quantization error is large
relative to the signal.

Drive gain addresses this directly: scaling the signal up by D before PWM forces more
duty-cycle levels into play. After the IIR filter the output is divided by D, restoring the
original level. The carrier noise (which scales with the signal) cancels through the gain–invert
pair; the duty-cycle quantization error (which is reduced by using more levels) does not fully
cancel — so the net effect is improved S/N at sideband frequencies.

This is distinct from the simpler "8-bit pre-quantization SNR" argument (§4): that mechanism
applies to the 128-level pre-quantizer, while the dominant effect here is on the 78-level PWM
duty-cycle quantizer.

### Measured S/N vs Drive Gain (440 Hz, 3 sideband frequencies: 1560/2000/2440 Hz)

| Input level | driveGain | S/N (fund − SB avg) | Hard-clip % |
| :---: | :---: | :---: | :---: |
| −6 dBFS | 2.0 | +51.7 dB | 4.4% |
| −6 dBFS | 4.0 | +56.2 dB | 66.8% |
| −12 dBFS | 2.0 | +47.6 dB | 0% |
| −12 dBFS | 4.0 | **+53.0 dB** | 6.2% |
| −18 dBFS | 2.0 | +37.6 dB | 0% |
| −18 dBFS | 4.0 | **+48.0 dB** | 0% |
| −18 dBFS | 8.0 | +53.3 dB | 7.6% |
| −24 dBFS | 8.0 | +46.9 dB | 0% |
| −24 dBFS | 16.0 | +53.0 dB | 8.8% |

The optimal drive gain for a given input level is approximately `1 / peak_amplitude`. For a
sine wave at −18 dBFS (amplitude ≈ 0.126), driveGain = 4.0 maps the peak to 0.50 — well
inside the [−1, 1] range — while using 50% of the available 78 duty-cycle levels.

### Default and CLI Override

The default driveGain is **4.0**, optimised for typical synthesizer output levels near −18 dBFS.
Override with `--retro-drive`:

```bash
# Default (−18 dBFS input, no clipping, good S/N)
midra munt --retro pc song.mid

# Loud input (−6 dBFS peaks) — lower gain to avoid clipping
midra munt --retro pc --retro-drive 2 song.mid

# Very quiet input (−24 dBFS) — higher gain for better S/N
midra munt --retro pc --retro-drive 8 song.mid
```

Rule of thumb: `--retro-drive` × peak amplitude ≤ 1.0 to avoid hard clipping.

### Compressor as a Dynamic Drive Gain

`--compress` achieves the same mechanism dynamically: it raises the signal level before the
retro filter when the input is quiet and holds back when the input is loud, staying below the
clipping threshold automatically. `--retro-drive` is the static, predictable version;
`--compress` is the adaptive version.

| | `--retro-drive` | `--compress` |
| :--- | :--- | :--- |
| Quiet input | ✅ S/N improves | ✅ S/N improves |
| Loud input | ❌ clips if drive too high | ✅ handles gracefully |
| Behaviour | static, predictable | adaptive, level-dependent |
| Interaction | inside PWM loop | upstream of retro filter |

### Measured S/N vs Compress Preset (`--retro pc`, driveGain 4.0)

Simulation: 440 Hz sine through `DynamicsCompressor` → `OneBitHardwareFilter`. S/N =
fundamental level minus average of carrier sidebands at 1560/2000/2440 Hz. All levels
relative to each preset's output peak.

**S/N by input level and preset:**

| Input level | none | soft | gentle | moderate | aggressive |
| :---: | :---: | :---: | :---: | :---: | :---: |
| −6 dBFS | +51.7 dB | +51.7 dB | +47.9 dB | +47.8 dB | +45.8 dB |
| −12 dBFS | +47.6 dB | +47.6 dB | +46.7 dB | **+50.1 dB** | +46.1 dB |
| −18 dBFS | +37.6 dB | +37.6 dB | +40.5 dB | **+48.3 dB** | +46.0 dB |
| −24 dBFS | +35.7 dB | +35.7 dB | +35.0 dB | +37.6 dB | **+43.8 dB** |

Key observations:
- `soft` mirrors `none` at all levels — its threshold of −3 dBFS is above typical synthesizer
  output, so compression rarely engages.
- `moderate` gives the best result at −12 and −18 dBFS (+10.7 dB improvement over `none` at
  −18 dBFS), the range where its threshold of −18 dBFS produces useful drive into the PWM
  quantizer.
- `aggressive` wins at very quiet levels (−24 dBFS) where its lower threshold of −24 dBFS
  kicks in.
- At −6 dBFS (already loud), all presets reduce S/N slightly because makeup gain pushes signal
  closer to the drive-gain clip boundary.

**Frequency detail at −18 dBFS (dB relative to each preset's output peak):**

| Frequency | none | soft | gentle | moderate | aggressive |
| :--- | :---: | :---: | :---: | :---: | :---: |
| 440 Hz (fundamental) | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |
| 880 Hz (2nd harmonic) | −37.5 | −37.5 | −44.3 | −47.4 | −41.3 |
| 1320 Hz (3rd harmonic) | −40.6 | −40.6 | −45.4 | −44.3 | −41.7 |
| 1560 Hz (carrier SB k=31) | −37.3 | −37.3 | −39.3 | −44.0 | −48.0 |
| 1760 Hz (4th harmonic) | −38.4 | −38.4 | −48.3 | −53.2 | −51.3 |
| 2000 Hz (carrier SB k=30) | −29.6 | −29.6 | −35.5 | −42.4 | −40.9 |
| 2200 Hz (5th harmonic) | −46.5 | −46.5 | −45.3 | −44.7 | −47.6 |
| 2440 Hz (carrier SB k=29) | −45.8 | −45.8 | −46.6 | −53.9 | −48.9 |

The 2000 Hz carrier sideband (k=30) is the loudest noise component in the uncompressed path
(−29.6 dB). `moderate` suppresses it by 12.8 dB (to −42.4 dB), and `aggressive` by 11.3 dB
(to −40.9 dB). Both also visibly suppress the even harmonics (880/1760 Hz) — a compression
artefact from the soft-knee gain shaper reducing transient peaks.

## 8. References

- **Access Software, *RealSound***: Original commercial PC game technology that hijacked the
  Intel 8253 PIT to produce digitized speech via PWM on the IBM PC internal speaker.
- **Michael J. Mahon, *Real Sound for 8-bit Apple IIs***: KansasFest presentation detailing
  the mathematics of time-domain quantization via cycle-counted PWM.
