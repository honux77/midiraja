# Midiraja Future Improvements

This document outlines the roadmap for future enhancements to the Midiraja project.

## 1. 🎹 Alternative Soft Synth Integrations via FFM
**Goal:** Expand the successful FFM dynamic linking architecture beyond FluidSynth to support different MIDI standards and retro gaming aesthetics.
**Ideas to Explore:**
- **Munt (libmunt):** The ultimate Roland MT-32/CM-32L emulator. Essential for correctly playing early 90s DOS game MIDI files that sound incorrect on General MIDI/FluidSynth. Requires investigating its C API or writing a small C wrapper since Munt is primarily C++. (Target: `--munt /path/to/roms/`)
- **TiMidity++ (libtimidity):** A classic alternative to FluidSynth that supports Gravis Ultrasound (`.pat`) patch sets for a different flavor of 90s nostalgia.
- **OPL3 / AdLib Emulator:** FFM bindings for a Yamaha OPL3 FM synthesis emulator (like Nuked OPL3). Allows playing MIDI files using the iconic, gritty FM synth sound of early Sound Blaster cards without needing any external soundbank files. (Target: `--opl3`)
- **TinySoundFont (TSF):** A single-header C library for SoundFont rendering. If compiled into a tiny shared library and bundled inside the jar, we could extract and link it at runtime to achieve true "Zero-Dependency" AOT software synthesis without requiring users to `brew install fluidsynth`.

## 2. 🌍 Automated Cross-Platform CI/CD
**Goal:** Automate native binary compilation for Mac, Linux, and Windows via GitHub Actions.
**Benefits:**
- Automatic release generation on tag pushes.
- Integration with Package Managers: `brew install midiraja` (Homebrew) and `scoop install midra` (Windows).
- Instant, JRE-free downloads for anyone, anywhere.
