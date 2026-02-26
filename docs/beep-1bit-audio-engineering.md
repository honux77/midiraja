# 1-Bit Audio Engineering (The 'Beep' Synth)

This document outlines the architecture of the `midra beep` command, a pure mathematical software synthesizer designed to explore the extreme physical limitations of 1-bit audio logic. Internally dubbed the **'1-Bit Digital Cluster'**, this engine extends the software-based bit-banging techniques of the 1980s (e.g., Apple II, ZX Spectrum), fusing them with True [Phase Modulation](https://en.wikipedia.org/wiki/Phase_modulation), Delta-Sigma noise shaping, and Heuristic AI Optimization to create a definitive, zero-dependency 1-bit digital instrument.

---

## 1. Historical Context: The Apple II "Bit-Banging" Legacy

The internal speaker of early 8-bit computers was a primitive 1-bit device, physically capable of only two voltage states (On/Off). However, there was a profound architectural difference between platforms:

*   **IBM PC:** Utilized the **Intel 8253 PIT** hardware timer, which could automatically generate square waves at a specified frequency without constant CPU intervention.
*   **Apple II:** Featured no dedicated sound hardware. To produce sound, the CPU had to manually toggle the speaker's memory-mapped I/O port at precise intervals using cycle-counted machine code. This "bit-banging" approach required 100% of the CPU's attention just to maintain a single steady pitch.

Because of this extreme hardware poverty, the Apple II became the ultimate laboratory for software-driven audio innovation. In 1981, Paul Lutus released **"Electric Duet."** He achieved the impossible by utilizing strict cycle-counted [Time-Division Multiplexing](https://en.wikipedia.org/wiki/Time-division_multiplexing) and `EOR` (Exclusive-OR) logic to rapidly switch the speaker between two virtual square waves at ~8 kHz, successfully multiplexing two distinct voices onto a single 1-bit speaker pin.

The `midra beep` engine pushes this philosophy to its absolute mathematical limit, simulating a dynamic cluster of 1-bit units capable of generating polyphonic 2-Operator FM synthesis through pure 1-bit pins.

---

## 2. Technical Architecture & Engineering Evolution

The engine's architecture is the result of rigorous acoustic engineering, overcoming severe mathematical constraints to achieve perfectly synchronized, polyphonic 1-bit audio.

### 2.1. The Synthesis Triad (Timbre Generation)
Before any notes can be multiplexed together, the engine must generate their fundamental timbre. The engine provides three distinct synthesis algorithms (`--synth`), representing different eras of digital audio engineering:

**1. Modern: Yamaha-Like [Phase Modulation](https://en.wikipedia.org/wiki/Phase_modulation) (`--synth pm`)**
*   Provides a clean, bell-like, pseudo-analog sound.
*   Instead of mathematically unstable Direct Frequency Modulation, it adds the modulator wave directly to the *lookup phase* of a mathematically perfect carrier accumulator. This guarantees absolute pitch stability at any frequency and prevents high-frequency chords from collapsing into dissonant noise.

**2. Hardcore 1980s: Timbral [Ring Modulation](https://en.wikipedia.org/wiki/Ring_modulation) (`--synth xor`)**
*   Mimics the brilliant '[Ring Modulation](https://en.wikipedia.org/wiki/Ring_modulation)' hacks used by 1-bit legends like Tim Follin on the ZX Spectrum.
*   It generates two raw 1-bit square waves (a Carrier and a slightly detuned Modulator) and logically crushes them together using an Exclusive-OR (`^`) gate. This carves out a gritty, aggressively buzzing, chainsaw-like chiptune timbre before the signal ever reaches the master multiplexer.

**3. Classic Apple II: Pure Square Wave & Duty Modulation (`--synth square` - Default)**
*   The original workhorse of 1980s computer games (e.g., *Karateka*, *Ultima*).
*   Because a 1-bit speaker cannot natively output volume changes, developers relied on **Duty Cycle Modulation (PWM)** to alter timbre and perceived energy. As the duty cycle narrows (e.g., 50% down to 10%), the fundamental harmonic dominates less, creating a "thinner" or "tinny" sound.
*   This engine keeps the rigid square wave "alive" by continuously wobbling its pitch via a 6Hz LFO (Vibrato) and mathematically sweeping its pulse width between 10% and 90% (Wah-Wah Duty Sweep). Extremely narrow pulses physically deform into triangular shapes due to the analog capacitance and inertia of a real speaker cone, an acoustic phenomenon we simulate via our output LPF stage.

### 2.2. The Multiplexing Evolution: From XOR to DSD
The most critical engineering challenge in 1-bit audio is mixing multiple polyphonic notes within a single, binary (On/Off) speaker pin. The engine documents the historical and mathematical evolution of this problem by providing four distinct multiplexing algorithms (`--mux`):

**1. The 1981 Hack: XOR Logic Gate (`--mux xor` - Default)**
*   Inspired by Paul Lutus's original "Electric Duet" (which used the 6502's `EOR` instruction to manage speaker phase toggling), this mode converts each analog sine wave into a discrete 1-bit [Pulse Width Modulated (PWM)](https://en.wikipedia.org/wiki/Pulse-width_modulation) stream, then crushes them together through a boolean Exclusive-OR (`^`) logic gate.
*   **Acoustic Result:** Severe, authentic [Ring Modulation](https://en.wikipedia.org/wiki/Ring_modulation). Creates a highly gritty, buzzing chiptune texture.
*   **Limitation:** XORing more than 2 voices causes catastrophic phase cancellation ($1 \oplus 1 = 0$), obliterating the fundamental pitch into white noise.

**2. The High-Speed Switch: [Time-Division Multiplexing](https://en.wikipedia.org/wiki/Time-division_multiplexing) (`--mux tdm`)**
*   An impossible feat for a 1MHz CPU, this algorithm switches between notes at over 1.4MHz. Instead of logically mixing them, the pin simply outputs the state of only **ONE** specific note per micro-tick.
*   **Acoustic Result:** Zero intermodulation distortion, allowing 4 voices per pin, but results in a slightly "thin" or "sliced" acoustic texture.

**3. The Analog Cheat: Pure PWM Multiplexing (`--mux pwm`)**
*   Abandoning logical mixing entirely, this mode mathematically sums all analog [Phase Modulation](https://en.wikipedia.org/wiki/Phase_modulation) sine waves together *first*, and then compares that massive combined chord against a 22kHz sawtooth carrier.
*   **Acoustic Result:** Flawless phase mixing and 0% intermodulation. However, it leaves a distinct, retro 22kHz "carrier whine" (a faint, high-pitched background hum) characteristic of early Class-D amplifiers.

**4. The Modern Pinnacle: [Delta-Sigma Modulation](https://en.wikipedia.org/wiki/Delta-sigma_modulation) (`--mux dsd`)**
*   The ultimate conclusion of 1-bit audio. It transitions from varying pulse *widths* (PWM) to varying pulse *density* (PDM - Pulse Density Modulation). It sums the analog waves perfectly and replaces the fixed 22kHz PWM carrier with a 1st-order Delta-Sigma error accumulator running at 1.4MHz.
*   **The TPDF Breakthrough:** To prevent the deadly "Idle Tones" (limit cycles) inherent to 1st-order DSD, a mathematically precise Triangular Probability Density Function (TPDF) [Dither](https://en.wikipedia.org/wiki/Dither) is injected directly into the error loop, effectively randomizing and decorrelating the quantization noise.
*   **Acoustic Result:** It pushes all quantization noise completely out of the human hearing range, replacing harsh carrier whines with warm analog hiss. It yields breathtaking, studio-grade Hi-Fi sound while technically remaining a pure 1-bit logic stream.

### 2.3. Psychoacoustic Routing: Bass Isolation
Even with advanced multiplexing, routing multiple deep bass notes into the same physical unit caused muddy, low-frequency beat frequencies (beating). 

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

### 2.5. Advanced DSP Pipeline & Physical Realism

To transform the harsh, mathematically pure 1-bit logic outputs into a warm, listenable audio stream, the engine employs critical Digital Signal Processing (DSP) techniques grounded in physical measurements of original hardware.

#### 2.5.1. The "Mechanical LPF" Discovery (PC Speaker Analysis)
In March 2026, spectral analysis of an authentic IBM PC Speaker PCM recording ("RealSound" playback) revealed a crucial physical truth: **the speaker cone itself is the primary filter.**

Contrary to the theory that raw 1-bit logic pulses (18.6kHz) reach the air unfiltered, measurements showed:
- **Steep Mechanical Roll-off:** The response is relatively flat up to 7kHz, then hits a **steep cliff**, dropping to **-66.6 dB at 8kHz**.
- **Resonant peaks:** High energy around 2.5kHz and 6.7kHz, characteristic of small, unshielded plastic/paper cones and their internal air cavities.
- **Carrier Absence:** High-frequency PWM switching noise (18kHz+) was virtually absent from the air, proving that the **mechanical inertia of the cone** acts as a perfect physical integrator.

#### 2.5.2. Analytical Area Integration (BLIT)
To simulate this physical inertia within a 44.1kHz digital system without creating painful digital aliasing (Nyquist folding), the engine utilizes **Analytical Area Integration**. Instead of outputting raw +1/-1 samples, it calculates the exact time-weighted average of the PWM pulse within each 44.1kHz frame. This mathematically represents the speaker cone's inability to follow high-frequency transitions, effectively acting as a Band-Limited (BLIT) reconstruction filter.

#### 2.5.3. Pipeline Safety
1.  **Exponential Oversampling:** Logic runs at up to 1.4MHz to push quantization noise far beyond human hearing.
2.  **Anti-Blowup DC Blocking:** A strict High-Pass Filter ($R=0.995$) drains DC accumulation caused by asymmetric XOR/PWM logic, preventing "Integral Windup."

---

### 2.6. The Apple II Hardware Reality Check
While the `midra beep` engine provides a vast matrix of 1-bit audio possibilities, it is important to distinguish between historical fact and modern over-engineering. A stock 1980s Apple II, running a MOS Technology 6502 CPU at exactly 1.023 MHz, had severe physical limits.

**What was ACTUALLY possible on the Apple II?**
Only two distinct acoustic paths were possible within the constraints of a 1MHz processor:
1.  **The Synthesizer Path:** Generated via real-time CPU cycle-counting.
    *   **Generation:** Only pure Square Waves (`--synth square` - Default) were possible. LFO Vibrato and Duty Sweep were achievable by mathematically altering the delay loops.
    *   **Multiplexing:** Only Boolean XOR (`--mux xor` - Default) was possible, and strictly limited to 2 voices (e.g., *Electric Duet*). 
2.  **The Sampler Path (The "Real Sound" Hack):** 
    *   Pre-rendered 1-bit audio (essentially `--mux pwm` or PCM) could be played back. As documented by Michael J. Mahon in his seminal KansasFest presentations ("Real Sound for 8-bit Apple IIs"), by cycle-counting the 1.023 MHz 6502 CPU to drive the $C030 speaker pin at an ~11 kHz sample rate, the CPU effectively acted as a **Time-Domain DAC**.
    *   $1.023 \text{ MHz (CPU)} \div 11 \text{ kHz (Sample Rate)} \approx \mathbf{93 \text{ Steps}}$. This brilliant hack squeezed roughly 6.5 bits of dynamic range out of a 1-bit pin.
    *   *Constraint:* Real-time **polyphonic** synthesis using PWM was mathematically impossible because the 6502 CPU lacked the speed to perform real-time analog summing of multiple channels before driving the high-frequency comparator logic. As Mahon demonstrated in his later *RTSynth* project (a Real-Time Synthesizer), achieving complex wavetable synthesis and dynamic envelopes via PWM consumed 100% of the CPU's 92-cycle execution window, strictly limiting it to a **monophonic (single-voice)** instrument.

---

## 3. Global Mixing Pipeline

Once each virtual unit has generated its 1-bit signal, the master bus finalizes the sound:

1.  **Per-Unit DC Blocking:** Each signal passes through a High-Pass Filter ($R=0.995$) to isolate asymmetric duty cycles.
2.  **Analog Summing:** Discrete voltages from all active units are added mathematically.
3.  **Acoustic Filtering:** A 2-pole Low-Pass Filter ($\alpha=0.25$) or a measured hardware curve simulates the physical characteristics of the target hardware speaker.

---

## 4. Conclusion

The `midra beep` engine is a testament to the power of constraint-driven engineering. By combining the brutal physical limitations of 1-bit audio with measured spectral data from original hardware, it successfully resurrects the electrifying, gritty sound of 1980s computer audio while maintaining modern stability and clarity.
