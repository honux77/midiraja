# Dependency Fan-In / Fan-Out Analysis

**Date:** 2026-03-23
**Scope:** `src/main/java` — 140 source files (136 analyzed classes)

---

## Overview

| Metric | Value |
|--------|-------|
| Total classes | 136 |
| Total dependency edges | 208 |
| Average fan-in | 1.53 |
| Average fan-out | 1.53 |
| Average instability | 0.45 |
| Zero fan-in classes | 77 (CLI entry points + DSP plugins — by design) |
| Zero fan-out classes | 84 (stable abstractions + utilities) |

**Instability** = fan-out / (fan-in + fan-out). 0 = maximally stable, 1 = maximally unstable.

Only intra-project references (`com.fupfin.midiraja.*`) are counted. JDK and third-party imports are excluded.

---

## Stable Core Abstractions (Fan-In ≥ 6, Instability < 0.2)

These classes are depended on by many others and change rarely. They define the architecture's skeleton.

| Class | Package | Fan-in | Fan-out | Instability | Notes |
|-------|---------|--------|---------|-------------|-------|
| `AudioProcessor` | `dsp` | 15 | 0 | 0.000 | All 22 DSP filters depend on this interface |
| `AppLogger` | `io` | 11 | 0 | 0.000 | Universal logging abstraction |
| `MidiPort` | `midi` | 11 | 0 | 0.000 | Port identity value object |
| `MidiOutProvider` | `midi` | 10 | 1 | 0.091 | All synth providers implement this |
| `PlaylistContext` | `engine` | 8 | 1 | 0.111 | Playback session context |
| `TerminalIO` | `io` | 8 | 0 | 0.000 | Terminal I/O abstraction |
| `NativeAudioEngine` | `midi` | 7 | 0 | 0.000 | Ring-buffer audio output abstraction |
| `PlaybackCommands` | `engine` | 6 | 0 | 0.000 | Input loop control surface (new) |
| `Theme` | `ui` | 6 | 0 | 0.000 | ANSI escape constants |
| `FloatToShortSink` | `dsp` | 6 | 1 | 0.143 | PCM format converter |

---

## All Classes Sorted by Fan-In

| Class | Package | Fan-in | Fan-out | Instability |
|-------|---------|--------|---------|-------------|
| `AudioProcessor` | dsp | 15 | 0 | 0.000 |
| `MidirajaCommand` | root | 13 | 21 | 0.618 |
| `AppLogger` | io | 11 | 0 | 0.000 |
| `MidiPort` | midi | 11 | 0 | 0.000 |
| `MidiOutProvider` | midi | 10 | 1 | 0.091 |
| `PlaylistContext` | engine | 8 | 1 | 0.111 |
| `TerminalIO` | io | 8 | 0 | 0.000 |
| `NativeAudioEngine` | midi | 7 | 0 | 0.000 |
| `FloatToShortSink` | dsp | 6 | 1 | 0.143 |
| `PlaybackCommands` | engine | 6 | 0 | 0.000 |
| `Theme` | ui | 6 | 0 | 0.000 |
| `MasterGainFilter` | dsp | 5 | 0 | 0.000 |
| `PlaybackEngine.PlaybackStatus` | engine | 5 | 0 | 0.000 |
| `Version` | root | 4 | 0 | 0.000 |
| `PlaybackState` | engine | 4 | 1 | 0.200 |
| `GusSynthProvider` | midi.gus | 4 | 4 | 0.500 |
| `PlaybackUI` | ui | 4 | 3 | 0.429 |
| `AdlMidiSynthProvider` | midi | 3 | 1 | 0.250 |
| `FFMAdlMidiNativeBridge` | midi | 3 | 0 | 0.000 |
| `FFMOpnMidiNativeBridge` | midi | 3 | 0 | 0.000 |
| `FFMTsfNativeBridge` | midi | 3 | 0 | 0.000 |
| `MidiProviderFactory` | midi | 3 | 3 | 0.500 |
| `OpnMidiSynthProvider` | midi | 3 | 1 | 0.250 |
| `TsfSynthProvider` | midi | 3 | 1 | 0.250 |
| `BeepSynthProvider` | midi.beep | 3 | 3 | 0.500 |
| `PsgSynthProvider` | midi.psg | 3 | 4 | 0.571 |
| `Logo` | ui | 3 | 0 | 0.000 |
| `LibraryPaths` | root | 2 | 0 | 0.000 |
| `ShortToFloatFilter` | dsp | 2 | 0 | 0.000 |
| `PlaybackEngine` | engine | 2 | 1 | 0.333 |
| `PlaybackEngineFactory` | engine | 2 | 1 | 0.333 |
| `AbstractOneBitSynthProvider` | midi | 2 | 2 | 0.500 |
| `FFMMuntNativeBridge` | midi | 2 | 0 | 0.000 |
| `FluidSynthProvider` | midi | 2 | 0 | 0.000 |
| `MidiUtils` | midi | 2 | 0 | 0.000 |
| `MuntSynthProvider` | midi | 2 | 1 | 0.333 |
| `SoftSynthProvider` | midi | 2 | 0 | 0.000 |
| `DashboardUI` | ui | 2 | 5 | 0.714 |
| `PlaybackEventListener` | ui | 2 | 1 | 0.333 |
| `ScreenBuffer` | ui | 2 | 0 | 0.000 |
| *(remaining ~96 classes)* | various | ≤1 | varies | — |

---

## Top 15 Fan-Out Classes (Most Coupled Outward)

| Class | Package | Fan-out | Fan-in | Instability |
|-------|---------|---------|--------|-------------|
| `MidirajaCommand` | root | 21 | 13 | 0.618 |
| `DemoCommand` | cli | 19 | 0 | 1.000 |
| `PlaybackRunner` | cli | 15 | 0 | 1.000 |
| `PlaylistPlayer` | cli | 9 | 0 | 1.000 |
| `MidiPlaybackEngine` | engine | 8 | 1 | 0.889 |
| `DemoTransitionScreen` | cli | 7 | 0 | 1.000 |
| `InfoCommand` | cli | 7 | 0 | 1.000 |
| `MuntCommand` | cli | 7 | 0 | 1.000 |
| `PsgCommand` | cli | 7 | 0 | 1.000 |
| `BeepCommand` | cli | 6 | 0 | 1.000 |
| `FmCommand` | cli | 6 | 0 | 1.000 |
| `TerminalSelector` | cli | 6 | 0 | 1.000 |
| `DashboardUI` | ui | 5 | 2 | 0.714 |
| `DumbUI` | ui | 5 | 1 | 0.833 |
| `LineUI` | ui | 5 | 1 | 0.833 |

---

## Package-Level Summary

| Package | Classes | Fan-in (total) | Fan-out (total) | Character |
|---------|---------|----------------|-----------------|-----------|
| `cli` | 26 | 0 | 111 | 진입점 레이어. cli를 임포트하는 클래스는 없음 |
| `dsp` | 23 | 28 | 1 | 플러그인 구조 — `AudioProcessor`에만 의존 |
| `midi` | 31 | 68 | 13 | 가장 큰 서브시스템; 핵심 구현체 집합 |
| `engine` | 7 | 23 | 12 | 균형 잡힌 중간 레이어 |
| `io` | 6 | 27 | 1 | 공통 인프라; 높은 안정성 |
| `ui` | 22 | 21 | 32 | 조합 레이어; 외향 의존이 많음 |
| root | 3 | 19 | 21 | `MidirajaCommand`가 모든 레이어 연결 |

---

## Key Findings

### 1. `MidirajaCommand` — Instability Law Tension

Fan-in 13, fan-out 21, instability 0.62. Many classes depend on it, yet it also depends on many.
According to the Stable Dependencies Principle, a class with high fan-in should have low instability.
`MidirajaCommand` violates this: changes to any of the 21 classes it imports may force changes here,
and those ripple to the 13 dependents.

**Mitigation already in place:** picocli subcommand dispatch means most changes go into individual
`*Command` classes (all fan-in=0), not `MidirajaCommand` itself. However, audio library resolution
and the legacy alias block still live here.

### 2. `AudioProcessor` — Perfect Stable Abstraction

Fan-in 15, fan-out 0, instability 0.000. All 22 DSP filters and 3 synthesis pipelines depend on this
interface; the interface depends on nothing. This is the textbook definition of a stable abstraction.
The DSP layer is a clean plugin architecture.

### 3. `MidiPlaybackEngine` — High Instability Despite Central Role

Fan-out 8, fan-in 1, instability 0.889. The implementation class is barely depended on (only
`PlaybackEngineFactory`), because everything else goes through the `PlaybackEngine` interface.
This is the intended effect of the ISP refactoring done in this session.

However, `MidiPlaybackEngine` itself (848 lines, 39 methods) depends on 8 internal classes,
making it the most internally coupled non-command class. It is the primary refactoring candidate
for further decomposition — see `test-coverage-analysis.md §3`.

### 4. ISP Refactoring Effect Confirmed

After this session's refactoring:
- `PlaybackState` fan-in = 4 (render loop, `DashboardUI`, `LineUI`, `DumbUI`)
- `PlaybackCommands` fan-in = 6 (input handler, input loop runner, all three UI classes)

Each interface is independently used by different consumers as intended.
`PlaybackEngine` (combined) fan-in = 2 (`PlaylistPlayer`, `PlaybackEngineFactory`) — only the
classes that need the full lifecycle surface.

### 5. DSP Filters — Zero Fan-In by Design

All 22 DSP filter classes have fan-in = 0. They are assembled at runtime via configuration strings
(`--tube`, `--chorus`, `--reverb`, etc.) in the CLI commands. This is an intentional plugin pattern:
filters are never statically imported by business logic, only by the factory/assembly code in the
CLI layer. The pattern is correct; direct fan-in would couple the DSP layer to specific commands.

### 6. Dependency Flow (Layering)

```
CLI Commands (fan-in=0)
    ↓
MidirajaCommand / PlaybackRunner / PlaylistPlayer
    ↓
PlaybackEngine interface → MidiPlaybackEngine (impl)
    ↓                   ↓
PlaybackState      PlaybackCommands
    ↓                   ↓
    └──────────┬─────────┘
               ↓
MidiOutProvider ← (all synth providers)
NativeAudioEngine
AudioProcessor  ← (all DSP filters)
TerminalIO / AppLogger / MidiPort  ← stable leaf abstractions
```

---

## Recommended Actions

| Priority | Class | Issue | Action |
|----------|-------|-------|--------|
| High | `MidiPlaybackEngine` | fan-out=8, 848 lines | Extract timing, seek, and state sub-objects |
| Medium | `MidirajaCommand` | instability 0.62 with fan-in 13 | Move audio lib resolution to `AudioLibResolver` (already exists but may not be fully used) |
| Low | `DashboardUI` | instability 0.71 | Already correct after ISP refactoring; no immediate action needed |
