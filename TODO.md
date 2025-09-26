# Midiraja Future Improvements

This document outlines the roadmap for future enhancements to the Midiraja project.

## ~~1. 🚀 Migrate to FFM API (Project Panama)~~ (✅ DONE)
**Goal:** Replace JNA with the modern `java.lang.foreign` API.
**Benefits:**
- **Zero JNA Dependency:** Removes the bulky JNA library, reducing the Native Image binary size.
- **Improved Performance:** Direct memory access and native downcalls without JNI overhead.
- **Modern Java Showcase:** Demonstrate how to use the latest Java 22+ standard features for C-interop in a cross-platform CLI tool.
**Status/Feasibility:** 
GraalVM Native Image fully supports FFM API (Downcalls and Upcalls). For the current Java 25 target, FFM will compile to native binaries beautifully. We'll need to use the `native-image-agent` (or `ffi-config.json`) to register the ALSA, WinMM, and CoreMIDI C-functions at build time.

## ~~2. 🎨 Full-Screen TUI & Visualizer~~ (✅ DONE)
**Goal:** Upgrade the UI from a single line to a rich terminal dashboard.
**Benefits:**
- **VU Meters:** Real-time 16-channel volume animations.
- **Piano Roll:** Terminal-based visualization of MIDI notes being played.
- **Playlist UI:** Clear view of current, next, and previous tracks, along with shuffle/loop states.

## 3. 🎹 Built-in Software Synthesizer (SoundFont Fallback)
**Goal:** Allow playback even if the OS doesn't have a configured MIDI output port.
**Benefits:**
- Accept `.sf2` (SoundFont) files.
- Automatically render MIDI to audio using Java's `javax.sound.sampled` API or a lightweight audio library.
- Drastically lowers the barrier to entry for end users who just want to hear the music instantly.

## 4. 🌍 Automated Cross-Platform CI/CD
**Goal:** Automate native binary compilation for Mac, Linux, and Windows via GitHub Actions.
**Benefits:**
- Automatic release generation on tag pushes.
- Integration with Package Managers: `brew install midiraja` (Homebrew) and `scoop install midra` (Windows).
- Instant, JRE-free downloads for anyone, anywhere.
