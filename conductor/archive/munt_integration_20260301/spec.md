# Specification: Munt (MT-32) Emulator Integration

## 1. Overview
Integrate the Munt (Roland MT-32/CM-32L) emulator to allow Midiraja to accurately playback early 90s DOS game MIDI files. Since Munt is a C++ project and Java FFM strictly requires a C ABI, we will create a minimal, custom C wrapper (`libmidiraja_munt`) that exposes the necessary Munt synthesis and audio playback capabilities to Midiraja via FFM. The integration will strictly adhere to spec-level testing, including exhaustive boundary and exception testing, and will employ Dependency Inversion Principle (DIP) to ensure high testability.

## 2. Functional Requirements
*   **Custom C Wrapper (`libmidiraja_munt`):**
    *   Develop a small C/C++ project that links against the core `munt` engine.
    *   Expose a flat C API for initializing the emulator, loading ROMs, processing MIDI events (Note On/Off, CC, SysEx), and managing an internal audio driver (or a thread that pulls PCM data and sends it to the OS using a lightweight audio library if Munt doesn't provide a reliable cross-platform driver out of the box).
*   **Java FFM Integration (`MuntSynthProvider`):**
    *   Create a new `MuntSynthProvider` implementing `SoftSynthProvider`.
    *   Use `java.lang.foreign` to load our custom `libmidiraja_munt` shared library.
    *   Map the C functions and implement `openPort()`, `loadSoundbank()` (which will act as the ROM directory loader), `sendMessage()`, and `closePort()`.
    *   **DIP & Testability:** The native C function calls must be abstracted behind an internal interface (e.g., `MuntNativeBridge`) so that the `MuntSynthProvider`'s core logic can be tested purely in Java without requiring the actual shared library or FFM linkages during unit tests.
*   **CLI Options:**
    *   Add `--munt <rom_directory_path>`: Activates the `MuntSynthProvider` and tells the C wrapper where to find the `MT32_CONTROL.ROM` and `MT32_PCM.ROM` files.
*   **Build System Updates:**
    *   Update the CI/CD pipeline (and potentially the Gradle build) to compile the C++ wrapper into a shared library (`.dylib`, `.so`, `.dll`) for the target platforms before building the Java project.

## 3. Non-Functional Requirements
*   **AOT Compatibility:** The Java side must remain fully compatible with GraalVM Native Image.
*   **Performance:** The C wrapper and audio thread must operate with low latency to ensure real-time MIDI playback.
*   **Graceful Degradation:** If the custom shared library or the ROM files are missing, Midiraja must throw a clear, informative exception instructing the user on how to resolve the issue.
*   **Code Quality:** The Java integration must be heavily refactored for readability and maintainability.

## 4. Out of Scope
*   Distributing the proprietary Roland MT-32 ROM files (users must provide their own).
*   Exposing advanced Munt configuration settings (e.g., custom DAC emulation types) in the first iteration.

## 5. Acceptance Criteria
*   The C++ wrapper compiles cleanly on Linux, macOS, and Windows.
*   Running `midra --munt /path/to/roms mt32_song.mid` successfully loads the ROMs and plays audio.
*   **Testing:** Spec-level unit tests exist for `MuntSynthProvider`, covering happy paths (successful parsing and routing of Note On, Off, SysEx) and edge cases (null inputs, empty arrays, malformed MIDI bytes, missing ROM directories, missing native library).
*   Integration tests verify the FFM routing logic using the DIP abstraction.