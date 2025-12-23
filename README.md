# Midiraja (midra) 🎵

**Midiraja** (command: `midra`) is a lightweight, interactive MIDI player for your terminal. 

Whether you want to quickly preview a `.mid` file, practice an instrument by changing the key and tempo on the fly, or just listen to a folder full of retro game soundtracks, `midra` makes it incredibly easy.

## ✨ Features
* **Rich Terminal UI**: Experience MIDI like never before with three adaptive display modes:
  * `--full` (`-3`): A glorious full-screen dashboard with 16-channel VU meters, progress bars, and a dynamic playlist.
  * `--mini` (`-2`): A compact, single-line status widget perfect for background listening.
  * `--classic` (`-1`): Standard, pipe-friendly console output.
* **Zero Config**: Automatically detects and plays through your native OS MIDI synthesizers (CoreMIDI, ALSA, WinMM).
* **Live Controls**: Change playback speed, transpose the key, or tweak the volume *while* the music is playing—and keep those settings across the entire playlist!
* **Playlists**: Toss in a folder full of files. Shuffle, loop, and skip tracks seamlessly.

---

## 🚀 Installation (macOS & Linux)

### 📦 Which version should I use?
Midiraja is distributed in three distinct flavors to guarantee it runs everywhere:

1. **`midra` (Standard Native)**: The default. Built with GraalVM Native Image. It's a single, standalone binary with instant startup time. Use this unless you encounter OS compatibility issues (like a "missing glibc" error on older Linux distributions).
2. **`midrac` (Compatible Bundle)**: A highly compatible distribution that bundles a minimal Java Runtime (JRE) optimized with Project Leyden (AppCDS) for near-native startup speeds. It is slightly larger but mathematically guaranteed to run on almost any Linux/macOS environment, regardless of OS age.
3. **`midrax` (Cross-Platform Fat JAR)**: The ultimate "write once, run anywhere" version. It requires you to have **Java 25+** already installed on your system.

### 🍺 Option 1: macOS via Homebrew (Recommended for Mac)
```bash
brew tap YOUR_GITHUB_USERNAME/tap
brew install midra
```

### ⚡ Option 2: Quick Install via Curl (Mac & Linux)
Run the following script in your terminal to automatically download and install the latest version:
```bash
curl -sL https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/midiraja/master/install.sh | bash
```
*(Note for Linux users: `midra` uses ALSA under the hood, which is pre-installed on almost all Linux distributions.)*

### 📦 Option 3: Manual Download
1. Go to the [Releases](https://github.com/YOUR_GITHUB_USERNAME/midiraja/releases) page.
2. Download the `.tar.gz` file for your computer (e.g., `darwin-arm64` for Apple Silicon Mac, `linux-amd64` for Intel/AMD Linux).
3. Extract it and move the `midra` file to `/usr/local/bin/`.

---

## 🎮 How to Use

### Quick Start
Just type `midra` followed by the file you want to play. If you have multiple MIDI devices, a menu will pop up asking you to choose one.
```bash
midra PASSPORT.MID
```

### Playlists & Folders
You can pass multiple files at once. `midra` will play them one after another.
```bash
# Play all MIDI files in the current folder
midra *.mid

# Play a playlist endlessly in a random order
midra --loop --shuffle *.mid
```

### Starting with Specific Settings
You can set the starting volume, tempo, key, or even jump to a specific time right from the command line:
```bash
# Start at 1 minute and 30 seconds
midra --start 01:30 song.mid

# Play at 1.5x speed
midra --speed 1.5 song.mid

# Transpose the song one octave up (+12 semitones) and lower volume
midra --transpose 12 --volume 50 song.mid
```

---

## 🎛️ Live Playback Controls

While a song is playing, you don't need to restart the app to make changes. Just press these keys:

* **`↑` / `↓`** : Next / Previous Track (or `n` / `p`)
* **`←` / `→`** : Skip backward / forward 10 seconds (or `f` / `b`)
* **`+` / `-`** : Volume Up / Down (or `u` / `d`)
* **`>` / `<`** : Speed Up / Down
* **`'` / `/`** : Transpose Key Up / Down (vertical placement)
* **`Spc`** : Pause / Resume
* **`q`** : Stop playback and Quit

---

## 🎹 Soft Synthesizers

`midra` includes five built-in software synthesizers alongside native OS MIDI output. Each is a subcommand — put it before the files:

| Synthesizer | Command | Sound |
|-------------|---------|-------|
| **FluidSynth** | `fluid <soundfont.sf2>` | General MIDI · SF2 SoundFont |
| **GUS / Java** | `gus` | Gravis Ultrasound · Zero-dependency pure Java DSP |
| **1-Bit Digital Cluster** | `beep` | Pure math 1-bit logic synth (PM, XOR, DSD) with AI-tuned DSP |
| **MT-32 / Munt** | `munt <rom-dir>` | Roland MT-32 · authentic DOS-era game music |
| **OPL / libADLMIDI** | `opl` | AdLib / Sound Blaster FM · no install required |
| **OPN2 / libOPNMIDI** | `opn` | Sega Genesis / PC-98 FM · no install required |

```bash
# 1-Bit Digital Cluster (Zero-dependency pure math synthesizer)
midra beep song.mid                               # Default: Modern Phase Modulation + DSD
midra beep --synth square --mux xor --voices 2    # 1981 Apple II Hardcore Hacker Mode
midra beep --synth xor --mux dsd --voices 4       # Tim Follin 1-bit Ring Modulation Style

# Gravis Ultrasound (no setup required — uses bundled Freepats)
midra gus song.mid
midra gus --bits 6 song.mid               # 6-bit Bitcrusher effect
midra gus --realsound song.mid            # 1980s RealSound PC Speaker simulation
midra gus -p /path/to/eawpats/ song.mid   # or use your own patch set

# FluidSynth (requires: brew install fluid-synth + a .sf2 file)
midra fluid /path/to/soundfont.sf2 song.mid

# MT-32 emulation (requires: brew install munt + ROM files)
midra munt ~/mt32-roms monkey_island.mid

# OPL FM synthesis (no extra install — built into the binary)
midra opl song.mid
midra opl -b 14 song.mid                  # Doom built-in bank
midra opl -b /path/to/bank.wopl song.mid  # external bank file

# OPN2 FM synthesis (Sega Genesis / PC-98 sound — built into the binary)
midra opn song.mid
midra opn -b /path/to/bank.wopn song.mid  # external bank file
```

For installation instructions, bank listings, and emulator options, see the **[Soft Synth Guide](docs/soft-synth-guide.md)**.

---

## ⚖️ License & Credits
* **Midiraja** is licensed under the [BSD 3-Clause License](LICENSE).
* This project uses several open-source libraries. Please see [NOTICES.md](NOTICES.md) for full third-party license information and attributions.
