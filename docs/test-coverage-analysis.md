# Test Coverage Analysis

**Date:** 2026-03-23
**Scope:** Full codebase — `src/main/java` vs `src/test/java`

---

## Overview

| Metric | Value |
|--------|-------|
| Total source files | 153 Java files |
| Total source lines | ~20,357 lines |
| Total test files | 51 files |
| Total test lines | ~7,650 lines |
| Overall test-to-source ratio | **0.38** |

The project has solid coverage in core playlist and input-handling logic, moderate coverage in the engine and DSP layers, and critical gaps in the UI rendering pipeline and FFM native bridges.

---

## Package-Level Summary

| Package | Source Lines | Test Lines | Ratio | Status |
|---------|-------------|-----------|-------|--------|
| `engine` | ~1,045 | ~840 | **0.80** | Good |
| `io` | ~621 | ~380 | **0.61** | Good |
| `dsp` | ~2,742 | ~1,500 | **0.54** | Moderate |
| `cli` | ~4,553 | ~1,783 | **0.39** | Weak |
| `midi` | ~4,792 | ~1,791 | **0.37** | Weak |
| `ui` | ~2,278 | ~449 | **0.19** | Critical |

---

## Files With No Test Coverage

These production files have **zero** direct unit tests. Some are exercised indirectly via provider-level integration tests; others are entirely untested.

### FFM Native Bridges — indirectly covered only

Each bridge is exercised through its corresponding provider test, but the FFM downcall layer itself (parameter marshalling, return value handling, error codes, lifecycle) is never tested directly.

| File | Lines | Indirect coverage via |
|------|-------|----------------------|
| `FFMMuntNativeBridge.java` | 668 | `MuntSynthProviderTest` |
| `FFMAdlMidiNativeBridge.java` | 411 | `AdlMidiSynthProviderTest` |
| `FFMOpnMidiNativeBridge.java` | 409 | `OpnMidiSynthProviderTest` |
| `FFMTsfNativeBridge.java` | 375 | `TsfSynthProviderTest` |
| `AbstractFFMBridge.java` | 274 | Shared base, no direct tests |

**Risk:** FFM `invokeExact` bugs (see memory doc on the silent no-op pitfall), lifecycle ordering errors, and wrong parameter types fail silently or at native boundary with no Java stack trace.

### DSP Filters — integration-tested only

21 of 23 DSP filter classes have no dedicated unit tests. They are verified only by `RetroFiltersTest` and `DspPipelineTest`, which instantiate filters and pass audio through the full chain — these tests verify the pipeline doesn't crash, not that the filter math is correct.

| File | Lines | Notes |
|------|-------|-------|
| `OneBitHardwareFilter.java` | 328 | 7-pole speaker model, PWM quantization |
| `EqFilter.java` | 236 | 3-band Biquad IIR; boost/cut untested |
| `ReverbFilter.java` | 207 | Schroeder/Moorer; 6 presets untested |
| `CompactMacSimulatorFilter.java` | 185 | |
| `AmigaPaulaFilter.java` | 175 | |
| `AcousticSpeakerFilter.java` | 172 | |
| `CovoxDacFilter.java` | 146 | |
| `CovoxFilter.java` | 143 | |
| `OneBitAcousticSimulator.java` | 141 | |
| `FloatToShortSink.java` | 136 | |
| `ChorusFilter.java` | 131 | |
| `SpectrumBeeperFilter.java` | 109 | |
| `MasterGainFilter.java` | 94 | |
| `TubeSaturationFilter.java` | 79 | |
| `EightBitQuantizerFilter.java` | 63 | |
| `ShortToFloatFilter.java` | 62 | |
| *(others)* | | |

**Exceptions:** `DynamicsCompressor` (ratio 0.82) and `OneBitCovox` (via `OneBitCovoxTest`) have dedicated unit tests.

**Risk:** Filter coefficient calculations, gain staging, and frequency response are unverified. Retrocomputer audio accuracy depends entirely on these filters being correct.

### Synthesis Providers — base classes untested

| File | Lines | Notes |
|------|-------|-------|
| `AbstractSoftSynthProvider.java` | 237 | Base for all soft synths; render thread, event queue, gain |
| `AbstractTrackerChip.java` | 273 | Base for PSG and SCC chips |

### Other untested files

| File | Lines | Notes |
|------|-------|-------|
| `AlsaProvider.java` | 382 | Linux-only; platform isolation makes testing harder |
| `CoreMidiProvider.java` | 257 | macOS-only; same constraint |
| `DemoCommand.java` | 288 | Demo mode transitions |
| `FmCommand.java` | 208 | FM synthesis CLI command |

---

## Severely Undertested Files (ratio < 0.3, > 150 lines)

### `MidirajaCommand.java` — ratio 0.08

Main CLI entry point (461 lines, 37 test lines). The command wires subcommands, legacy aliases, and audio library resolution but is almost entirely untested. Changes to flag names, defaults, or subcommand dispatch are not caught by the test suite.

### `GusSynthProvider.java` — ratio 0.10

Gravis Ultrasound soft-synth provider (393 lines). Complex instrument mapping, envelope control, and multi-chip rendering are exercised by only 39 test lines — effectively just instantiation and a smoke test.

### `ResumeCommand.java` — ratio 0.14

History-based session resume (222 lines). Only 30 test lines cover the basic session selection path; error cases (empty history, invalid file, missing port) are not tested.

### `DashboardUI.java` — ratio 0.15

Main TUI dashboard (215 lines). 32 test lines verify panel structure only; the rendering pipeline, layout calculation, and update cycle are untested.

### `BeepSynthProvider.java` — ratio 0.16

1-bit PC speaker synthesizer (809 lines). Complex PWM quantization and fixed-point math are verified by only 129 test lines — limited to basic initialization and that audio is non-silent.

### `PsgChip.java` — ratio 0.17

AY-3-8910 / YM2149F chip emulator (336 lines). Tone channel math, noise generator, and hardware envelope are covered by only 57 test lines.

### `NativeAudioEngine.java` — ratio 0.21

Native audio ring buffer management (276 lines). Push/flush/latency interactions with the render thread are only partially verified.

### `TerminalSelector.java` — ratio 0.23

Interactive MIDI port selection menu (661 lines). Three display modes (full-screen, mini, classic) are exercised by 155 test lines; keyboard navigation edge cases and terminal resize are not covered.

### `PlaybackRunner.java` — ratio 0.23

Playback orchestrator — port selection, terminal setup, and playlist delegation (441 lines). The remaining test coverage focuses on port selection; terminal lifecycle and error paths are not tested.

### `SccChip.java` — ratio 0.25

Konami SCC chip emulator (291 lines, 72 test lines). Custom waveform buffer management and 5-channel mixing untested.

### `FluidSynthProvider.java` — ratio 0.26

FluidSynth wrapper (460 lines). 120 test lines cover initialization and basic event routing; audio driver setup and SoundFont loading edge cases are not tested.

---

## Large and Complex Files

Files over 400 lines are listed with method counts and notes on structural complexity.

### `MidiPlaybackEngine.java` — 848 lines, 39 public methods

The largest single class. It currently handles:
- Real-time MIDI event scheduling (tick-to-nanosecond conversion, spin-wait)
- Seek (fast-forward chase, position recalculation)
- State management (play/pause/stop, loop, shuffle, bookmark)
- MIDI filter pipeline integration (volume, transpose, sysex)
- UI notification (listeners, `notificationScheduler`, latency compensation)
- Provider lifecycle (startup delay, `prepareForNewTrack`, `onPlaybackStarted`)

Despite 39 public methods, `PlaybackEngineTest.java` tests only through the `start()` integration boundary. Individual concerns (timing math, seek accuracy, filter application) are exercised only indirectly. The existing integration tests are valuable but make root-cause diagnosis difficult when failures occur.

**Note:** The `MidiPlaybackEngine` is the single most important candidate for targeted unit test additions. Key private methods (`getTickForTime`, `processChaseEvent`, `playLoop` timing logic) are not reachable without refactoring.

### `BeepSynthProvider.java` — 809 lines, 3 public methods

Large file but single responsibility (1-bit synthesis). Low method count means unit testing is harder because most logic is internal. The main opportunity is audio output verification (RMS, frequency accuracy, click artifacts).

### `TerminalSelector.java` — 661 lines, 12 public methods

Three UI modes in one class. Navigation state, keyboard handling, and rendering logic are interleaved. A natural split would be: separate renderer per mode + shared selection state machine.

### `FFMMuntNativeBridge.java` — 668 lines, 18 public methods

Complex but inherently difficult to unit-test (requires Munt native library). The `NativeMetadataConsistencyTest` verifies FFM registration; the `FFMMuntNativeBridgeIntegrationTest` (skipped when ROMs absent) covers behavior. No intermediate unit-test layer exists.

---

## Well-Tested Areas

These components have strong coverage and serve as reference for test patterns.

| File | Lines | Test Lines | Ratio | Notes |
|------|-------|-----------|-------|-------|
| `PlaylistParser.java` | 283 | 451 | **1.59** | M3U parsing, `#MIDRA:` directives, edge cases |
| `PlaylistPlayer.java` | 291 | 264 | **0.91** | Playlist loop, navigation, shuffle/loop |
| `AdlMidiSynthProvider.java` | 87 | 221 | **2.72** | OPL synth provider — event queue, render thread |
| `OpnMidiSynthProvider.java` | 87 | 218 | **2.50** | OPN synth provider |
| `InputHandler.java` | 55 | 156 | **2.84** | All 16 key→command routes tested with exact deltas |
| `InputLoopRunner.java` | 43 | 96 | **2.23** | Normal loop, early exit, IOException path |
| `DynamicsCompressor.java` | 176 | 145 | **0.82** | DSP math with signal analysis (`DspAnalyzer`) |
| `GusEngine.java` | 137 | 171 | **1.25** | GUS wavetable core logic |
| `CommonOptions.java` | 221 | 173 | **0.78** | CLI option parsing, boundaries |
| `AltScreenScope.java` | 76 | 100 | **1.32** | Terminal alt-screen lifecycle |

**Patterns worth replicating:**
- `RecordingCommands` (interface mock that records calls as strings) — clean, no framework needed
- `FakeClock` injection for deterministic timing tests
- `WavRmsMeter` + `DspAnalyzer` for verifying audio output quality
- `RecordingMidiProvider` for capturing MIDI byte sequences

---

## UI Package Detail

The `ui` package (0.19 overall ratio) contains 24 classes of which 13 have no test coverage at all.

| Class | Lines | Test Lines | Ratio | Notes |
|-------|-------|-----------|-------|-------|
| `InputHandler` | 55 | 156 | 2.84 | ✅ |
| `InputLoopRunner` | 43 | 96 | 2.23 | ✅ |
| `NowPlayingPanel` | 154 | 73 | 0.47 | Partial |
| `DashboardUI` | 215 | 32 | 0.15 | Weak |
| `LineUI` | 178 | 0 | 0.00 | Untested — channel level listener added but no test |
| `DumbUI` | 109 | 0 | 0.00 | |
| `ChannelActivityPanel` | 155 | 0 | 0.00 | Level decay, MIDI activity display |
| `PlaylistPanel` | 173 | 0 | 0.00 | |
| `ScreenBuffer` | 102 | 0 | 0.00 | Terminal string buffer |
| `DashboardLayoutManager` | 133 | 0 | 0.00 | |
| `StatusPanel` | 135 | 0 | 0.00 | |
| `CompositePanel` | 72 | 0 | 0.00 | |
| `MetadataPanel` | 70 | 0 | 0.00 | |
| `ControlsPanel` | 60 | 0 | 0.00 | |
| `ProgressBar` | 80 | 0 | 0.00 | |
| `TitledPanel` | ~50 | 0 | 0.00 | |
| `Theme` | ~40 | 0 | 0.00 | Constants only — testing not applicable |

`ScreenBuffer` and `ProgressBar` are the most accessible entry points for UI unit tests since they are pure string-transformation utilities with no terminal I/O dependency.

---

## Recommended Next Steps

Ranked by expected value / effort ratio.

### 1. DSP filter unit tests — high value, low effort

`EqFilter` and `ReverbFilter` are pure signal-processing classes with no I/O dependencies. Tests can use `DspAnalyzer` (already in test infrastructure) to verify frequency response and impulse response. These tests would directly confirm retrocomputer audio accuracy.

Target: 300–400 lines of tests, EqFilter and ReverbFilter minimum.

### 2. `ScreenBuffer` and `ProgressBar` unit tests — high value, low effort

Both are pure string-transformation utilities. Tests require no mocking and verify the terminal rendering primitives that everything else depends on. Small scope, high confidence gain.

Target: 150–200 lines.

### 3. `MidiPlaybackEngine` — targeted additions for untested behaviors

The existing integration tests in `PlaybackEngineTest` cover the happy path well. Remaining gaps:
- `getTickForTime` accuracy across BPM-change events (currently private; testable via constructor `startTimeStr`)
- `processChaseEvent` vs `processEvent` — NoteOn excluded during chase
- Seek while paused, then unpause (timing reference reset)
- Rapid consecutive seeks (atomicity of `seekTarget`)

Target: 100–150 additional lines.

### 4. `AbstractSoftSynthProvider` — shared behavior tests

Four providers inherit from this base. Testing the base class's render thread management, event queue draining, and `panic()` behavior in one place would replace duplicated testing across providers.

Target: 200 lines.

### 5. `TerminalSelector` — mode-specific navigation

Full-screen, mini, and classic modes each have distinct keyboard handling. Tests using `MockTerminalIO` key injection can verify navigation without a real terminal.

Target: 150 lines per mode.

---

## Test Infrastructure Available

| Utility | Location | Purpose |
|---------|----------|---------|
| `MockTerminalIO` | `test/.../io/` | Key injection, output capture |
| `FakeClock` | `test/.../engine/` | Deterministic timing |
| `RecordingMidiProvider` | `PlaybackEngineTest` inner | Captures raw MIDI bytes |
| `RecordingCommands` | `test/.../ui/` | Records `PlaybackCommands` calls |
| `WavRmsMeter` | `test/.../` | Audio RMS analysis |
| `DspAnalyzer` | `test/.../` | Frequency domain analysis |
| `HeadlessRenderer` | `test/.../` | Render loop without terminal |
