# Retro `--aux` Speaker Bypass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `--aux` CLI flag so retro modes with a built-in speaker model default to speaker-on; `--aux` bypasses the speaker stage to simulate the electrical output (audio jack).

**Architecture:** Each retro filter that models a physical speaker gains a `boolean auxOut` constructor parameter. When `auxOut=true` the speaker stage is skipped and the filter returns the raw electrical signal. `CommonOptions` gains `--aux` and passes it into `buildRetroFilter()`. No code is moved out of the filters — only a conditional is added inside each one. Amiga and Covox have no speaker model and are unchanged.

**Tech Stack:** Java 25, JUnit 5, Gradle. All filter code is in `src/main/java/com/fupfin/midiraja/dsp/`. CLI in `src/main/java/com/fupfin/midiraja/cli/CommonOptions.java`.

---

## Files changed

| File | Change |
|------|--------|
| `dsp/OneBitHardwareFilter.java` | Add `boolean auxOut`; skip cone IIR + biquads when true |
| `dsp/SpectrumBeeperFilter.java` | Add `boolean auxOut`; skip HP + LP when true |
| `dsp/CompactMacSimulatorFilter.java` | Add speaker biquad stage; add `boolean auxOut`; skip speaker when true |
| `cli/CommonOptions.java` | Add `--aux` flag; pass `auxOut` to three filters in `buildRetroFilter()` |
| `test/.../dsp/RetroFiltersTest.java` | Update all constructor calls; add `auxOut` behaviour tests |
| `test/.../dsp/DspAnalyzer.java` | Update `CompactMacSimulatorFilter` constructor call |
| `docs/retro/retro-common-engineering.md` | Document `--aux` and speaker-default design |
| `docs/retro/retro-compactmac-engineering.md` | Document speaker stage and `--aux` |

---

## Task 1: `OneBitHardwareFilter` — add `auxOut`

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/dsp/OneBitHardwareFilter.java`
- Modify: `src/test/java/com/fupfin/midiraja/dsp/RetroFiltersTest.java`

**Speaker vs. electrical boundary:**
- Electrical: PWM generation + voice-coil inductance (`iirStatePre`)
- Speaker: 6 mechanical cone poles (`iirState1–6`) + biquad resonance peaks
- `auxOut=true`: advance `iirStatePre` normally, skip `iirState1–6` and biquads, return `iirStatePre * invDriveGain`

- [ ] **Step 1: Write failing tests**

Add to `RetroFiltersTest`:

```java
@Test
void testPcAuxOutBypasessConePoles() {
    int n = 4096;
    float[] leftSpeaker = new float[n], rightSpeaker = new float[n];
    float[] leftAux     = new float[n], rightAux     = new float[n];
    for (int i = 0; i < n; i++)
        leftSpeaker[i] = rightSpeaker[i] = leftAux[i] = rightAux[i] =
                (float) Math.sin(2.0 * Math.PI * 8000.0 * i / 44100.0);

    MockProcessor mockSpeaker = new MockProcessor();
    MockProcessor mockAux     = new MockProcessor();
    new OneBitHardwareFilter(true, "pwm", 15200.0, 78.0, 37.9, 8, 1.0, null, false, mockSpeaker)
            .process(leftSpeaker, rightSpeaker, n);
    new OneBitHardwareFilter(true, "pwm", 15200.0, 78.0, 37.9, 8, 1.0, null, true, mockAux)
            .process(leftAux, rightAux, n);

    // Speaker mode heavily attenuates 8 kHz; aux mode is louder (cone poles bypassed)
    float peakSpeaker = 0, peakAux = 0;
    for (int i = 512; i < n; i++) {
        peakSpeaker = Math.max(peakSpeaker, Math.abs(mockSpeaker.lastLeft[i]));
        peakAux     = Math.max(peakAux,     Math.abs(mockAux.lastLeft[i]));
    }
    assertTrue(peakAux > peakSpeaker * 2.0f,
            "aux mode should be louder at 8 kHz (cone bypassed). speaker=" + peakSpeaker + " aux=" + peakAux);
}

@Test
void testApple2AuxOutBypasessConePoles() {
    int n = 4096;
    float[] leftSpeaker = new float[n], rightSpeaker = new float[n];
    float[] leftAux     = new float[n], rightAux     = new float[n];
    for (int i = 0; i < n; i++)
        leftSpeaker[i] = rightSpeaker[i] = leftAux[i] = rightAux[i] =
                (float) Math.sin(2.0 * Math.PI * 8000.0 * i / 44100.0);

    MockProcessor mockSpeaker = new MockProcessor();
    MockProcessor mockAux     = new MockProcessor();
    new OneBitHardwareFilter(true, "pwm", 22050.0, 32.0, 28.4, 8, 1.0, null, false, mockSpeaker)
            .process(leftSpeaker, rightSpeaker, n);
    new OneBitHardwareFilter(true, "pwm", 22050.0, 32.0, 28.4, 8, 1.0, null, true, mockAux)
            .process(leftAux, rightAux, n);

    float peakSpeaker = 0, peakAux = 0;
    for (int i = 512; i < n; i++) {
        peakSpeaker = Math.max(peakSpeaker, Math.abs(mockSpeaker.lastLeft[i]));
        peakAux     = Math.max(peakAux,     Math.abs(mockAux.lastLeft[i]));
    }
    assertTrue(peakAux > peakSpeaker * 2.0f,
            "Apple II aux mode should be louder at 8 kHz. speaker=" + peakSpeaker + " aux=" + peakAux);
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./gradlew test --tests "*.RetroFiltersTest.testPcAuxOutBypasessConePoles" 2>&1 | tail -10
```

Expected: compile error (constructor does not accept 10 args)

- [ ] **Step 3: Add `auxOut` field and update constructor**

In `OneBitHardwareFilter.java`:

Add field after `driveGain`/`invDriveGain`:
```java
private final boolean auxOut;
```

Change constructor signature (add `boolean auxOut` before `AudioProcessor next`):
```java
public OneBitHardwareFilter(boolean enabled, String mode,
        double carrierHz, double levels, double tauUs, int preBitDepth, double driveGain,
        double @Nullable [] resonancePeaks, boolean auxOut, AudioProcessor next)
```

Add in constructor body:
```java
this.auxOut = auxOut;
```

- [ ] **Step 4: Conditionally skip cone IIR in `processOneSample()`**

In the PWM silence fast-path loop and normal loop, wrap the 6 cone pole updates:

```java
// Silence fast-path
for (int s = 0; s < OVERSAMPLE; s++) {
    iirStatePre += iirAlphaPre * (0.0 - iirStatePre);
    if (!auxOut) {
        iirState1 += iirAlpha * (iirStatePre - iirState1);
        iirState2 += iirAlpha * (iirState1  - iirState2);
        iirState3 += iirAlpha * (iirState2  - iirState3);
        iirState4 += iirAlpha * (iirState3  - iirState4);
        iirState5 += iirAlpha * (iirState4  - iirState5);
        iirState6 += iirAlpha * (iirState5  - iirState6);
    }
    carrierPhase = (carrierPhase + subCarrierStep) % 1.0;
}
if (auxOut) return (float)(iirStatePre * invDriveGain);
double out = iirState6 * invDriveGain;
double[] c1 = biquad1Coeffs, s1 = biquad1State;
if (c1 != null && s1 != null) out = applyBiquad(c1, s1, out);
double[] c2 = biquad2Coeffs, s2 = biquad2State;
if (c2 != null && s2 != null) out = applyBiquad(c2, s2, out);
return (float) out;
```

Apply the same pattern to the normal (non-silence) PWM loop at the bottom of `processOneSample()`.

- [ ] **Step 5: Fix existing test constructor calls** (add `false` before `mock`/`next`)

In `RetroFiltersTest`, update:
```java
// testPcDacBoundary
new OneBitHardwareFilter(true, "pwm", 15200.0, 78.0, 37.9, 8, 1.0, null, false, mock)
// testApple2DacToggle
new OneBitHardwareFilter(true, "pwm", 22050.0, 32.0, 28.4, 8, 1.0, null, false, mock)
// testApple2ProducesAudibleHarmonics
new OneBitHardwareFilter(true, "pwm", 22050.0, 32.0, 28.4, 8, 1.0, null, false, mock)
// testPcBiquadsAddResonanceAt2500And6700Hz (two calls)
new OneBitHardwareFilter(true, "pwm", 15200.0, 78.0, 37.9, 0, 1.0, null, false, mockNoBiquad)
new OneBitHardwareFilter(true, "pwm", 15200.0, 78.0, 37.9, 0, 1.0, new double[]{...}, false, mockBiquad)
// testPcSilenceProducesNoAudibleCarrier
new OneBitHardwareFilter(true, "pwm", 15200.0, 78.0, 37.9, 8, 1.0, null, false, mock)
// testApple2SilenceProducesNoAudibleCarrier
new OneBitHardwareFilter(true, "pwm", 22050.0, 32.0, 28.4, 8, 1.0, null, false, mock)
```

- [ ] **Step 6: Run tests**

```bash
./gradlew test --tests "*.RetroFiltersTest" 2>&1 | tail -15
```

Expected: all RetroFiltersTest tests pass including two new aux tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/dsp/OneBitHardwareFilter.java \
        src/test/java/com/fupfin/midiraja/dsp/RetroFiltersTest.java
git commit -m "feat: add auxOut to OneBitHardwareFilter; bypass cone IIR when true"
```

---

## Task 2: `SpectrumBeeperFilter` — add `auxOut`

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/dsp/SpectrumBeeperFilter.java`
- Modify: `src/test/java/com/fupfin/midiraja/dsp/RetroFiltersTest.java`

**Speaker vs. electrical boundary:**
- Electrical: Z80 amplitude quantisation (128 levels)
- Speaker: HP filter (~510 Hz) + 2× LP filter (~4.5 kHz)
- `auxOut=true`: return quantised value directly, skip HP and LP

- [ ] **Step 1: Write failing test**

```java
@Test
void testSpectrumAuxOutSkipsSpeakerFilters() {
    int n = 4096;
    float[] leftSpeaker = new float[n], rightSpeaker = new float[n];
    float[] leftAux     = new float[n], rightAux     = new float[n];
    for (int i = 0; i < n; i++)
        leftSpeaker[i] = rightSpeaker[i] = leftAux[i] = rightAux[i] =
                (float) Math.sin(2.0 * Math.PI * 8000.0 * i / 44100.0);

    MockProcessor mockSpeaker = new MockProcessor();
    MockProcessor mockAux     = new MockProcessor();
    new SpectrumBeeperFilter(true, false, mockSpeaker).process(leftSpeaker, rightSpeaker, n);
    new SpectrumBeeperFilter(true, true,  mockAux    ).process(leftAux,     rightAux,     n);

    // Speaker LP at 4.5 kHz cuts 8 kHz heavily; aux mode bypasses it
    float peakSpeaker = 0, peakAux = 0;
    for (int i = 512; i < n; i++) {
        peakSpeaker = Math.max(peakSpeaker, Math.abs(mockSpeaker.lastLeft[i]));
        peakAux     = Math.max(peakAux,     Math.abs(mockAux.lastLeft[i]));
    }
    assertTrue(peakAux > peakSpeaker * 3.0f,
            "Spectrum aux should be louder at 8 kHz. speaker=" + peakSpeaker + " aux=" + peakAux);
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew test --tests "*.RetroFiltersTest.testSpectrumAuxOutSkipsSpeakerFilters" 2>&1 | tail -5
```

- [ ] **Step 3: Add `auxOut` to `SpectrumBeeperFilter`**

Add field:
```java
private final boolean auxOut;
```

Change constructor:
```java
public SpectrumBeeperFilter(boolean enabled, boolean auxOut, AudioProcessor next)
{
    this.enabled = enabled;
    this.auxOut  = auxOut;
    this.next    = next;
}
```

In `processSample()`, return early after quantisation when `auxOut`:
```java
private float processSample(float monoIn)
{
    float clamped  = Math.max(-1.0f, Math.min(1.0f, monoIn));
    int level      = Math.round((clamped * 0.5f + 0.5f) * (LEVELS - 1));
    float quantized = (level / (float)(LEVELS - 1)) * 2.0f - 1.0f;

    if (auxOut) return quantized;

    hpOut = HP_ALPHA * (hpOut + quantized - hpPrev);
    hpPrev = quantized;
    lp1 += LP_ALPHA * (hpOut - lp1);
    lp2 += LP_ALPHA * (lp1  - lp2);
    return lp2;
}
```

- [ ] **Step 4: Fix existing constructor calls in `RetroFiltersTest`**

```java
new SpectrumBeeperFilter(true, false, mock)   // testSpectrumBeeperBasic
new SpectrumBeeperFilter(false, false, mock)  // testSpectrumBeeperDisabled
```

- [ ] **Step 5: Run tests**

```bash
./gradlew test --tests "*.RetroFiltersTest" 2>&1 | tail -15
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/dsp/SpectrumBeeperFilter.java \
        src/test/java/com/fupfin/midiraja/dsp/RetroFiltersTest.java
git commit -m "feat: add auxOut to SpectrumBeeperFilter; skip HP+LP when true"
```

---

## Task 3: `CompactMacSimulatorFilter` — add speaker stage + `auxOut`

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/dsp/CompactMacSimulatorFilter.java`
- Modify: `src/test/java/com/fupfin/midiraja/dsp/RetroFiltersTest.java`
- Modify: `src/test/java/com/fupfin/midiraja/dsp/DspAnalyzer.java`

**Speaker model:** 2-pole Butterworth LPF at 10 kHz, based on measured cliff at 10–11 kHz in real Mac Plus recording (compact_mac_sample.wav). Bilinear transform, designed at 44,100 Hz.

**Speaker vs. electrical boundary:**
- Electrical: RC (τ=30µs) + post-RC 18 kHz anti-alias LPF (current implementation)
- Speaker: 2-pole Butterworth LPF at 10 kHz (new)
- `auxOut=true`: skip speaker biquad → current electrical-only behavior

- [ ] **Step 1: Write failing tests**

```java
@Test
void testCompactMacSpeakerAttenuatesHighFreq() {
    // Speaker mode (-3dB at 10kHz) should attenuate 15kHz more than aux mode
    int n = 4096;
    float[] leftSpeaker  = new float[n], rightSpeaker  = new float[n];
    float[] leftAux      = new float[n], rightAux      = new float[n];
    for (int i = 0; i < n; i++)
        leftSpeaker[i] = rightSpeaker[i] = leftAux[i] = rightAux[i] =
                (float) Math.sin(2.0 * Math.PI * 15000.0 * i / 44100.0) * 0.5f;

    MockProcessor mockSpeaker = new MockProcessor();
    MockProcessor mockAux     = new MockProcessor();
    new CompactMacSimulatorFilter(true, false, mockSpeaker).process(leftSpeaker, rightSpeaker, n);
    new CompactMacSimulatorFilter(true, true,  mockAux    ).process(leftAux,     rightAux,     n);

    float peakSpeaker = 0, peakAux = 0;
    for (int i = 512; i < n; i++) {
        peakSpeaker = Math.max(peakSpeaker, Math.abs(mockSpeaker.lastLeft[i]));
        peakAux     = Math.max(peakAux,     Math.abs(mockAux.lastLeft[i]));
    }
    assertTrue(peakAux > peakSpeaker * 2.0f,
            "Speaker mode should attenuate 15kHz more than aux. speaker=" + peakSpeaker + " aux=" + peakAux);
}

@Test
void testCompactMacAuxPreservesLowFreq() {
    // Both modes should pass 1kHz with similar amplitude (speaker -3dB is at 10kHz)
    int n = 4096;
    float[] leftSpeaker = new float[n], rightSpeaker = new float[n];
    float[] leftAux     = new float[n], rightAux     = new float[n];
    for (int i = 0; i < n; i++)
        leftSpeaker[i] = rightSpeaker[i] = leftAux[i] = rightAux[i] =
                (float) Math.sin(2.0 * Math.PI * 1000.0 * i / 44100.0) * 0.5f;

    MockProcessor mockSpeaker = new MockProcessor();
    MockProcessor mockAux     = new MockProcessor();
    new CompactMacSimulatorFilter(true, false, mockSpeaker).process(leftSpeaker, rightSpeaker, n);
    new CompactMacSimulatorFilter(true, true,  mockAux    ).process(leftAux,     rightAux,     n);

    float peakSpeaker = 0, peakAux = 0;
    for (int i = 512; i < n; i++) {
        peakSpeaker = Math.max(peakSpeaker, Math.abs(mockSpeaker.lastLeft[i]));
        peakAux     = Math.max(peakAux,     Math.abs(mockAux.lastLeft[i]));
    }
    // Both should be within 30% of each other at 1kHz (speaker at 1kHz ≈ -0.1dB)
    assertTrue(peakSpeaker > peakAux * 0.7f,
            "Speaker 1kHz should be within 30% of aux. speaker=" + peakSpeaker + " aux=" + peakAux);
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./gradlew test --tests "*.RetroFiltersTest.testCompactMacSpeakerAttenuatesHighFreq" 2>&1 | tail -5
```

- [ ] **Step 3: Add speaker biquad constants and state to `CompactMacSimulatorFilter`**

Add after the existing LPF constants:
```java
// Mac internal speaker: 2-pole Butterworth LPF at 10 kHz
// Based on measured sharp rolloff in real Mac Plus sample (cliff at 10–11 kHz).
private static final double SPK_K    = Math.tan(Math.PI * 10000.0 / 44100.0);
private static final double SPK_NORM = 1.0 + SPK_K / 0.7071 + SPK_K * SPK_K;
private static final double SPK_B0   = (SPK_K * SPK_K) / SPK_NORM;
private static final double SPK_A1   = 2.0 * (SPK_K * SPK_K - 1.0) / SPK_NORM;
private static final double SPK_A2   = (1.0 - SPK_K / 0.7071 + SPK_K * SPK_K) / SPK_NORM;
// b1 = 2*b0, b2 = b0  (standard 2-pole LP biquad)
```

Add instance state fields (after `lpfX`, `lpfY`):
```java
private final boolean auxOut;
private double spkX1 = 0.0, spkX2 = 0.0;
private double spkY1 = 0.0, spkY2 = 0.0;
```

- [ ] **Step 4: Update constructor**

```java
public CompactMacSimulatorFilter(boolean enabled, boolean auxOut, AudioProcessor next)
{
    this.enabled = enabled;
    this.auxOut  = auxOut;
    this.next    = next;
}
```

- [ ] **Step 5: Apply speaker stage in `process()`**

In `process()`, replace the final output assignment with:
```java
double out = yLpf;
if (!auxOut) {
    double y = SPK_B0 * out + 2.0 * SPK_B0 * spkX1 + SPK_B0 * spkX2
                            - SPK_A1 * spkY1 - SPK_A2 * spkY2;
    spkX2 = spkX1; spkX1 = out;
    spkY2 = spkY1; spkY1 = y;
    out = y;
}
left[i]  = (float) out;
right[i] = (float) out;
```

Apply the same pattern in `processInterleaved()`.

- [ ] **Step 6: Reset speaker state in `reset()`**

```java
spkX1 = 0.0; spkX2 = 0.0;
spkY1 = 0.0; spkY2 = 0.0;
```

- [ ] **Step 7: Fix constructor calls**

In `DspAnalyzer.java`:
```java
new CompactMacSimulatorFilter(true, false, sink)  // runCompactMac — speaker on (default)
```

- [ ] **Step 8: Run tests**

```bash
./gradlew test --tests "*.RetroFiltersTest" 2>&1 | tail -15
```

Expected: all pass including new CompactMac tests.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/dsp/CompactMacSimulatorFilter.java \
        src/test/java/com/fupfin/midiraja/dsp/RetroFiltersTest.java \
        src/test/java/com/fupfin/midiraja/dsp/DspAnalyzer.java
git commit -m "feat: add speaker stage and auxOut to CompactMacSimulatorFilter"
```

---

## Task 4: CLI — add `--aux` flag

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/cli/CommonOptions.java`

- [ ] **Step 1: Add `--aux` option field**

In `CommonOptions`, after the `speakerProfile` field (line ~136):
```java
@Option(names = {"--aux"},
        description = "Bypass internal speaker simulation for retro modes that model one "
                + "(compactmac, pc, apple2, spectrum). Outputs the raw electrical signal "
                + "instead of the speaker-filtered sound. Ignored by amiga, covox.")
public boolean auxOut = false;
```

- [ ] **Step 2: Pass `auxOut` to filters in `buildRetroFilter()`**

```java
case "compactmac" -> new CompactMacSimulatorFilter(true, auxOut, next);
case "pc"         -> new OneBitHardwareFilter(true, "pwm", 15200.0, 78.0, 37.9, 8,
                             retroDrive, null, auxOut, next);
case "apple2"     -> new OneBitHardwareFilter(true, "pwm", 22050.0, 32.0, 28.4, 8,
                             retroDrive, null, auxOut, next);
case "spectrum"   -> new SpectrumBeeperFilter(true, auxOut, next);
// amiga, covox — unchanged (no speaker stage)
```

- [ ] **Step 3: Build**

```bash
./gradlew classes 2>&1 | grep -E "^(error|BUILD)" | head -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run full test suite**

```bash
./gradlew test 2>&1 | tail -20
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/cli/CommonOptions.java
git commit -m "feat: add --aux CLI flag; wire to retro filters"
```

---

## Task 5: Documentation

**Files:**
- Modify: `docs/retro/retro-common-engineering.md`
- Modify: `docs/retro/retro-compactmac-engineering.md`
- Modify: `docs/retro/README.md`

- [ ] **Step 1: Update `retro-common-engineering.md`**

Replace the "When `--speaker` Is Appropriate" and "Why They Conflict" section. Key points:
- Each retro mode defaults to speaker-on (except amiga/covox which have no built-in speaker)
- `--aux` bypasses the speaker stage to simulate the electrical output
- `--speaker` remains for non-retro use; using `--speaker` on top of `--retro` is still not recommended

- [ ] **Step 2: Update `retro-compactmac-engineering.md`**

- Add a "§6.x Mac Speaker Model" subsection documenting:
  - 2-pole Butterworth LPF at 10 kHz
  - Based on measured cliff in `samples/compact_mac_sample.wav`
  - `--aux` bypasses this stage
- Update §4 parameters table to include speaker row
- Update §5.1 frequency response to note speaker mode vs. aux mode

- [ ] **Step 3: Update `docs/retro/README.md`**

Add `--aux` to the pipeline diagram and options table.

- [ ] **Step 4: Commit**

```bash
git add docs/retro/
git commit -m "docs: document --aux option and CompactMac speaker model"
```

---

## Verification

```bash
# Full test suite
./gradlew test

# Manual smoke test (speaker on by default)
midra compactmac song.mid

# Aux out (electrical output only — old behavior)
midra compactmac --aux song.mid

# PC speaker default (speaker on)
midra pc song.mid

# PC aux (voice-coil only, no cone poles)
midra pc --aux song.mid
```
