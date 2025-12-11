# 1-Bit Audio Engineering (The 'Beep' Synth)

This document outlines the architecture of the `midra beep` command, a pure mathematical software synthesizer designed to recreate the extreme limitations of 1980s 1-bit audio hardware. Internally dubbed the **'Apple II 1-Bit FM Cluster'**, this engine extends the software-based 1-bit polyphony techniques originally developed for the Apple II, fusing them with modern FM synthesis, high-speed Time-Division Multiplexing (TDM), and wait-free concurrency to create the ultimate 1-bit digital instrument.

---

## 1. Historical Context: The Apple II "Bit-Banging" Legacy

The internal speaker of early 8-bit computers was a primitive 1-bit device, physically capable of only two voltage states (On/Off). However, there was a profound architectural difference between platforms:

*   **IBM PC:** Utilized the **Intel 8253 PIT** hardware timer, which could automatically generate square waves at a specified frequency without constant CPU intervention.
*   **Apple II:** Featured no dedicated sound hardware. To produce sound, the CPU had to manually toggle the speaker's memory-mapped I/O port at precise intervals using cycle-counted machine code. This "bit-banging" approach required 100% of the CPU's attention just to maintain a single steady pitch.

Because of this extreme hardware poverty, the Apple II became the ultimate laboratory for software-driven audio innovation. In 1981, Paul Lutus released **"Electric Duet,"** utilizing interleaved execution and logical mixing to multiplex two distinct voices onto a single 1-bit speaker pin.

The `midra beep` engine pushes this philosophy to its absolute mathematical limit, simulating a dynamic cluster of Apple II units capable of generating polyphonic, 2-Operator FM synthesis through pure 1-bit pins.

---

## 2. Technical Architecture & Engineering Evolution

The engine's architecture is the result of rigorous acoustic engineering, overcoming severe mathematical constraints to achieve perfectly synchronized, polyphonic 1-bit audio.

### 2.1. True Yamaha-Style Phase Modulation (PM)
Initial iterations of the engine utilized Direct Frequency Modulation (FM). However, at high frequencies or high modulation indices, Direct FM produced severe inharmonic aliasing because the frequency accumulator could swing into negative values, fundamentally detuning the pitch.

To solve this, the engine employs **True Phase Modulation (PM)**:
1.  The carrier frequency strictly accumulates phase at a constant, mathematically perfect rate.
2.  The modulator wave is added directly to the *lookup phase* (not the frequency) just before querying the Sine LUT.
This guarantees absolute pitch stability at any frequency. Furthermore, a bulletproof mathematical wrapper (`phase - Math.floor(phase)`) ensures the accumulator never escapes the [0.0, 1.0) boundary, preventing permanent state corruption across notes.

### 2.2. The Multiplexing Breakthrough: PWM to TDM
The most critical engineering breakthrough occurred in how multiple notes are mixed within a single 1-bit unit.

Originally, the engine used boolean XOR logic to combine notes. While this worked for 2 voices (mimicking the Electric Duet), scaling to 3 or 4 voices caused **Catastrophic Phase Cancellation**. XORing four high-frequency PWM streams meant that peaks often destroyed each other ($1 \oplus 1 \oplus 1 \oplus 1 = 0$), obliterating the fundamental pitch and leaving only boiling white noise.

The final solution completely abandons XOR in favor of ultra-high-speed **Time-Division Multiplexing (TDM)**:
1.  **Analog PM:** Calculate the pure analog PM sine wave for each assigned note.
2.  **Sequential Micro-Ticking:** During the hardware oversampling loop (e.g., 32x per audio frame), the unit does not try to mix all notes at once. Instead, it sequentially cycles through its assigned notes.
3.  **1-Bit Conversion:** At any given microsecond tick, the speaker outputs the Pulse Width Modulated (PWM) state of only **ONE** specific note.
This TDM approach guarantees zero intermodulation distortion and zero phase cancellation, allowing up to 4 dense FM voices to share a single 1-bit pin while maintaining flawless psychoacoustic blending.

### 2.3. Psychoacoustic Routing: Bass Isolation
Even with TDM, routing multiple deep bass notes into the same physical Apple II unit caused muddy, low-frequency beat frequencies (beating). 

To solve this, the engine implements a **Frequency-Weighted 'Bass Isolation' Allocator**:
*   The router actively analyzes the frequency content of each virtual unit. 
*   If a unit is already holding a bass note (<150Hz), it receives an extreme routing penalty, preventing it from accepting a second bass note.
*   Conversely, high-frequency treble notes are actively encouraged to pair with existing bass notes.
This acts as a dynamic crossover network, guaranteeing perfect bandwidth separation within each physical core and completely eradicating low-frequency muddiness during massive chords.

### 2.4. Wait-Free Concurrency
To prevent the JVM Garbage Collector from stuttering the audio thread during dense MIDI playback, the engine uses a **Wait-Free, Lock-Free Object Pool**:
*   A flat array of 128 `ActiveNote` slots is allocated once at startup.
*   Note events simply toggle a `volatile boolean active` flag in an $O(1)$ operation.
*   The audio render loop performs zero allocations (zero garbage) and requires zero thread locks, ensuring rock-solid, sample-accurate tempo synchronization.

---

## 3. Global Mixing Pipeline

Once each virtual Apple II unit has generated its TDM 1-bit signal, the master bus finalizes the sound:

1.  **Per-Unit DC Blocking:** Before leaving the unit, each 1-bit signal passes through a High-Pass Filter ($R=0.995$, with an anti-blowup clamp). This isolates asymmetric duty cycles, ensuring no unit can leak DC offset into the master mix.
2.  **Analog Summing:** The clean, discrete voltages from all active units (dynamically scaled to guarantee at least 16 total polyphony) are added together mathematically, simulating an analog console.
3.  **Acoustic Filtering:** A 2-pole Low-Pass Filter ($\alpha=0.25$) simulates the physical characteristics of a 2.25-inch paper cone speaker, rolling off the harsh 22kHz PWM carrier frequencies.

---

## 4. Conclusion

The `midra beep` engine is a testament to the power of constraint-driven engineering. By combining the brutal physical limitations of 1-bit audio with advanced DSP concepts—True Phase Modulation, high-speed Time-Division Multiplexing, and Frequency-Weighted Bass Isolation—it successfully resurrects the electrifying, gritty sound of 1980s computer audio without sacrificing modern tempo stability or orchestral polyphonic clarity.