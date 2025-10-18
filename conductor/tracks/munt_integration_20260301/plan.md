# Implementation Plan: Munt (MT-32) Emulator Integration

## Phase 1: C/C++ Wrapper Development (`libmidiraja_munt`)
- [ ] Task: Research the `libmunt` core API and determine the minimal C++ code required to initialize an MT32 context, load ROMs from a directory, and feed it MIDI bytes.
- [ ] Task: Determine the audio output strategy within the wrapper (e.g., using `RtAudio` or Munt's internal drivers if applicable) to pull PCM data from the MT32 context and send it to the system speakers.
- [ ] Task: Create the C source files (e.g., `src/main/c/munt_wrapper.cpp`) and define a clean `extern "C"` API header (e.g., `midiraja_munt_init(char* rom_dir)`, `midiraja_munt_send_midi(byte[] data, int len)`, `midiraja_munt_close()`).
- [ ] Task: Set up a basic CMake or Makefile script to compile the wrapper into a shared library, linking against the Munt source code.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: C/C++ Wrapper Development' (Protocol in workflow.md)

## Phase 2: DIP Abstraction and Test Scaffolding
- [ ] Task: Define the `MuntNativeBridge` interface in Java to represent the operations exposed by the C wrapper (init, send, close).
- [ ] Task: Create `MuntSynthProviderTest.java`.
- [ ] Task: Write spec-level, failing unit tests for `MuntSynthProvider` using a mocked `MuntNativeBridge`. Test cases must include:
    - Happy path: `openPort`, `loadSoundbank` (ROMs), and sending a variety of valid MIDI messages (Note On, CC, SysEx).
    - Edge cases: Empty byte arrays, null arrays, malformed or incomplete MIDI bytes.
    - Error cases: Exceptions thrown by the bridge during init or playback.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: DIP Abstraction and Test Scaffolding' (Protocol in workflow.md)

## Phase 3: Java FFM Bindings (`MuntSynthProvider`)
- [ ] Task: Implement `FFMMuntNativeBridge.java` implementing the interface, using `SymbolLookup.libraryLookup` and `Linker.nativeLinker()` to bind to `libmidiraja_munt`.
- [ ] Task: Implement `MuntSynthProvider.java` to satisfy all the spec-level tests defined in Phase 2, delegating native calls to the injected `MuntNativeBridge`.
- [ ] Task: Update `reachability-metadata.json` with the new FFM downcall signatures used by the `FFMMuntNativeBridge`.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Java FFM Bindings' (Protocol in workflow.md)

## Phase 4: CLI Integration and Build Automation
- [ ] Task: Update `MidirajaCommand.java` to add the `--munt <rom_dir>` option.
- [ ] Task: Wire the `--munt` option to instantiate and configure the `MuntSynthProvider` with the real `FFMMuntNativeBridge`.
- [ ] Task: Update the `build.gradle` (or a helper script) to automate the compilation of the C++ wrapper before the Java/Native Image build step.
- [ ] Task: Update GitHub Actions CI/CD workflows to install necessary C++ toolchains, Munt dependencies, and compile the wrapper for macOS, Linux, and Windows during the release process.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: CLI Integration and Build Automation' (Protocol in workflow.md)