# Plan: Linux ALSA Support & Docker Build

## Phase 1: Docker Build Environment Setup
- [ ] Task: Create `Dockerfile.linux` based on Ubuntu 24.04 with GraalVM 25, Gradle, and `libasound2-dev`.
- [ ] Task: Create a helper script `scripts/docker-build.sh` to build and test the project inside Colima.
- [ ] Task: Verify that `MidirajaCommand` can run (as a stub) inside the container.

## Phase 2: ALSA JNA Mapping
- [ ] Task: Define `AlsaLibrary` JNA interface for `libasound.so.2`.
- [ ] Task: Map core ALSA structures (e.g., `snd_seq_addr_t`, `snd_seq_ev_note_t`, `snd_seq_event_t`).
- [ ] Task: Implement `AlsaLibrary` functions: `snd_seq_open`, `snd_seq_create_simple_port`, `snd_seq_connect_to`, etc.

## Phase 3: LinuxProvider Implementation
- [ ] Task: Implement `getOutputPorts()` by querying ALSA clients and ports.
- [ ] Task: Implement `openPort()` to establish a connection between `midra`'s virtual port and the target device.
- [ ] Task: Implement `sendMessage()` using ALSA event encoding and `snd_seq_drain_output`.
- [ ] Task: Implement `panic()` using ALSA's `SND_SEQ_EVENT_CONTROLLER` (CC 120/123).

## Phase 4: Linux Native Image & Verification
- [ ] Task: Run `native-image-agent` inside Docker to generate `reflect-config.json` for JNA/ALSA structures.
- [ ] Task: Perform Linux Native Image compilation via Docker.
- [ ] Task: (Optional) Set up `snd-virmidi` in Colima to test actual MIDI message delivery if possible.
- [ ] Task: Conductor - User Manual Verification (Protocol in workflow.md).
