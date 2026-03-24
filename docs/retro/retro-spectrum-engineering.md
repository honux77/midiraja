# ZX Spectrum Beeper Simulation (`--retro spectrum`)

## 1. Hardware Background

The ZX Spectrum's speaker, like the Apple II, was a 1-bit device toggled directly by the Z80
CPU (bit 4 of port $FE). Software audio on the Spectrum relied on cycle-counted Z80 routines
at a 3.5 MHz clock.

With a ~17.5 kHz carrier, the Z80 provides approximately:

$$3{,}500{,}000 \text{ Hz} \div 17{,}500 \text{ Hz} = 200 \text{ steps} \approx 7.6 \text{ bits}$$

## 2. Acoustic Characteristics

The Spectrum's tiny square speaker had a different resonance profile from the IBM PC's larger
cone. Its output was more "buzzy" due to the speaker's physical construction and the very
small 22mm diaphragm.

## 3. Direct-Toggle Model

Unlike PWM-based hardware (PC speaker, Apple II), the Spectrum does not use a carrier
frequency at all. The Z80 simply writes a bit to toggle the speaker voltage.
`SpectrumBeeperFilter` models this directly: input audio is quantized to 128 discrete Z80
amplitude steps (~7-bit), then shaped through a physical beeper model with a high-pass and
two-stage low-pass filter.

There is no carrier integration step; the aliasing problems that affect `OneBitHardwareFilter`
do not apply here.

## 4. Parameters

| Parameter | Value | Purpose |
| :--- | :--- | :--- |
| Carrier | N/A | Direct bit toggle; no PWM carrier |
| Levels | 128 | Z80 3.5 MHz / ~27 cycles per step ≈ 7-bit |
| HP α | 0.930 | ~510 Hz high-pass; 22mm beeper bass limit |
| LP α | 0.600 | 2× ~4.5 kHz low-pass; small diaphragm inertia |
