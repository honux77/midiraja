# Midiraja Future Improvements

---

## User Experience

### "Just Works" Default Playback
**Goal:** Let new users run `midra song.mid` with no engine flag and hear audio instantly.
- Implement `EngineAutoSelector`: detect available engines in priority order (soundfont → 1bit)
- Auto-discover SF2 files from common system paths (Homebrew, apt, MuseScore)
- Fallback to `1bit` when no SF2 is found — always produces sound, zero dependencies

### `midra doctor`
**Goal:** Reduce "why isn't this working?" support burden and improve onboarding.
- Check audio output, ALSA/CoreAudio availability, optional library links, ROM paths

### `midra compare`
**Goal:** Let users hear the same MIDI file across multiple engines side-by-side.
- `midra compare song.mid` — cycles through all available engines, pausing between each
- Useful for evaluating SF2 files or choosing a retro aesthetic

### Config / Preset System
**Goal:** Persist per-user defaults so common flags are not retyped.
- `midra config set default-engine soundfont`
- `midra config set default-sf2 ~/soundfonts/FluidR3_GM.sf2`
- TOML config file at `~/.config/midra/config.toml`

---

## Distribution

### Package Manager
**Goal:** `brew install midra` / `scoop install midra` / `aur` one-liner.
- Homebrew tap (`fupfin/tap`)
- Scoop bucket
- AUR package (community or official)

### Windows
**Goal:** First-class Windows experience on par with macOS/Linux.
- CI smoke test on Windows (GitHub Actions)
- Chocolatey package / `winget` manifest

---

## Synthesis Engines

### Pure Java Audio & `midrax` Revival
**Goal:** Replace `libmidiraja_audio` (miniaudio) with a pure Java audio sink so that
`midrax` becomes a truly cross-platform distribution requiring only Java 25+, with no native libraries.
- Implement a `javax.sound.sampled`-based `AudioEngine` as an alternative to `NativeAudioEngine`
- Once complete, revive `midrax` as a standalone ZIP release — no `build-native-libs.sh` needed

### C64 SID Synthesizer (`midra sid`)
**Goal:** Add cycle-accurate C64 SID chip emulation.
- **Option A — Pure Java:** Zero-dependency implementation like `beep`/`psg`
- **Option B — libsidplayfp:** Dynamically link to `libsidplayfp` for maximum accuracy

### Amiga Paula Simulation
**Goal:** Explore extreme retro audio constraints.
- Add `--paula` option to the GUS engine
- 4-voice polyphony limit with aggressive voice stealing
- Hard-panned stereo (voices 0/3 → 100% Left, 1/2 → 100% Right)
- 8-bit forced resolution via existing noise-shaped quantizer
