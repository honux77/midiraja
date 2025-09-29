# Midiraja Future Improvements

This document outlines the roadmap for future enhancements to the Midiraja project.

## 1. 🎹 Built-in Software Synthesizer (SoundFont Fallback)
**Goal:** Allow playback even if the OS doesn't have a configured MIDI output port.
**Current Status:** An experimental `--synth` flag is implemented using Java's built-in `Gervill` synthesizer, but it **only works in JVM mode**, not in the compiled GraalVM Native Image.
**Technical Challenges & Future Work:**
- **GraalVM & `java.desktop` compatibility:** Java's built-in audio output (`javax.sound.sampled`) relies heavily on JNI (`libjsound`), dynamic class loading, and system properties (`java.home`), which cause critical runtime errors (`Can't find java.home`) in AOT compiled binaries.
- **The "FFM Audio" Barrier:** Bypassing `javax.sound` requires writing a completely new cross-platform digital audio engine. While we successfully used FFM for MIDI (which involves sending tiny, low-bandwidth event signals asynchronously), real-time PCM audio playback requires managing high-bandwidth data streams (e.g., 44.1kHz, 16bit stereo) using complex, OS-specific asynchronous callbacks (CoreAudio, WASAPI, ALSA PCM). FFM Upcalls for real-time audio callbacks carry significant performance and architectural overhead.
- **Next Steps:** Keep `--synth` as a JVM-only developer tool for now. True AOT-compatible software synthesis might require integrating a C-based synthesizer (like `libfluidsynth`) or waiting for GraalVM to better support `javax.sound`.

## 2. 🌍 Automated Cross-Platform CI/CD
**Goal:** Automate native binary compilation for Mac, Linux, and Windows via GitHub Actions.
**Benefits:**
- Automatic release generation on tag pushes.
- Integration with Package Managers: `brew install midiraja` (Homebrew) and `scoop install midra` (Windows).
- Instant, JRE-free downloads for anyone, anywhere.
