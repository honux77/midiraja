# FM Synthesis Engineering (OPL & OPN)

**Status:** Implementation Complete  
**Context:** This document outlines the architectural decisions, thread models, and DSP (Digital Signal Processing) refinements made while integrating the `libADLMIDI` and `libOPNMIDI` C-libraries into Midiraja.

---

## 1. Background: FM Synthesis and Target Hardware

### What is FM Synthesis?
[Frequency Modulation (FM) synthesis](https://en.wikipedia.org/wiki/Frequency_modulation_synthesis) generates complex harmonic timbres by modulating the frequency of one waveform (the carrier) with another (the modulator). This technique, pioneered by John Chowning and popularized by Yamaha, was the dominant method for computer and console audio in the 1980s and early 1990s due to its ability to produce rich, metallic, and "electric" sounds with very low memory overhead.

### Target Hardware
*   **[OPL (Yamaha YM3812/YMF262)](https://en.wikipedia.org/wiki/Yamaha_OPL):** The heart of the AdLib and Sound Blaster series. It defined the sound of the DOS gaming era.
*   **[OPN (Yamaha YM2612/YM2608)](https://en.wikipedia.org/wiki/Yamaha_YM2612)** (Related to [YM2203](https://en.wikipedia.org/wiki/Yamaha_YM2203)):  Used in the Sega Genesis (Mega Drive) and Japanese PC-98 computers. It is known for its distinctively gritty FM bass and drum sounds.

### Native Libraries
Midiraja utilizes two high-fidelity native emulation libraries via Java's Foreign Function & Memory (FFM) API:
*   **libADLMIDI:** A multi-backend OPL3 emulator support library with internal instrument banks.
*   **libOPNMIDI:** A specialized library for OPN2/OPNA emulation, supporting various OPN-specific bank formats (WOPN).

---

## 2. Thread Safety Architecture

Both `libADLMIDI` and `libOPNMIDI` maintain internal synthesizer state and are not thread-safe. Concurrent access from multiple threads can lead to memory corruption. In Midiraja, the MIDI sequencing timer runs on a high-priority playback thread, while audio block generation occurs on a separate render thread.

### Event Queuing Model
To decouple these threads without relying on blocking synchronization, a queue-based approach is used:

1.  **Playback Thread:** Encodes MIDI messages into byte arrays and offers them to a non-blocking `ConcurrentLinkedQueue<byte[]>`.
2.  **Render Thread:** Before each audio block generation (`generate()`), the thread polls all pending events from the queue and dispatches them sequentially to the native C API.
3.  **Result:** State mutation and PCM generation occur on a single thread, ensuring stability.

---

## 3. Audio Level Normalization & Limiting

During integration, we observed that FM synthesis engines (OPL/OPN) produced significantly lower output levels than wavetable-based engines (GUS). 

### Root Cause
FM synthesis algorithms combine multiple operators. To prevent master bus clipping under high polyphony, native emulation libraries apply conservative internal scaling. Empirical testing (10 simultaneous channels at maximum velocity) yielded a peak float value of `0.47` and an RMS of `0.12` for the OPL engine, resulting in a low perceived volume.

### DSP Pipeline Adjustments
To normalize perceived loudness while preventing digital clipping, a gain and limiting stage was added to the FM render loops:

1.  **Makeup Gain:** A static `2.0x` (+6dB) multiplier is applied to the raw float output.
2.  **Soft Clipping:** The amplified signal is processed using a Hyperbolic Tangent function (`Math.tanh`). 
    *   For signals within the `-1.0` to `1.0` range, the transfer function is linear.
    *   For extreme peaks, the function smoothly asymptotes to `1.0`, providing harmonic saturation instead of harsh digital clicks.
    *   The final RMS energy is doubled (`0.23`), matching the GUS volume profile.

---

## 4. 1-Bit PWM Acoustic Modulation

The `--1bit pwm` flag simulates routing multi-channel FM audio through a standard 1-bit PC motherboard speaker using [Pulse Width Modulation](https://en.wikipedia.org/wiki/Pulse-width_modulation) (15.2kHz carrier).

### Pause/Silence Handling
If the render thread continues to feed silence to the PWM generator after a pause event, the generator outputs a constant 50% duty cycle square wave at the 15.2kHz carrier frequency, resulting in an audible continuous tone.

**Resolution:**
The `panic()` method in the providers now immediately asserts a `renderPaused = true` flag. This halts the render loop and stops PCM data flow to the audio driver, ensuring absolute silence during pauses.
