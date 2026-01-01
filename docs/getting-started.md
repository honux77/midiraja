# Midiraja User Manual: Getting Started

Welcome to **Midiraja** (`midra`)! This guide will take you from installation to playing your first retro MIDI tunes in under 10 seconds.

---

## 1. Installation

Midiraja is distributed as a highly optimized, standalone native binary.

### macOS (Recommended)
```bash
brew tap YOUR_GITHUB_USERNAME/tap
brew install midra
```

### Linux & macOS (Quick Script)
```bash
curl -sL https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/midiraja/master/install.sh | bash
```

---

## 2. The Magic Command (Listen in 10 seconds)

You don't need to hunt down external SoundFonts or ROMs to start enjoying Midiraja. The binary comes packed with mathematical, zero-dependency synthesizers.

Just find any `.mid` file on your computer and run:
```bash
midra beep song.mid
```
This instantly fires up the **1-Bit Digital Cluster**, a pure math synthesizer that flawlessly recreates the gritty, square-wave aesthetic of 1980s microcomputers.

*(Pro tip: Try `midra opl song.mid` for instant 1990s Sound Blaster nostalgia!)*

---

## 3. Basic Playback & TUI Controls

### Playing Multiple Files
You can throw entire folders or playlists at Midiraja:
```bash
midra *.mid
midra --loop --shuffle *.mid
```

### Live Controls (Terminal UI)
Midiraja features an interactive dashboard. You don't need to quit the app to change the music; just press these keys while it's playing:

| Key | Action |
|-----|--------|
| **Up / Down** | Next / Previous Track |
| **Left / Right** | Skip backward / forward 10 seconds |
| **+ / -** | Volume Up / Down |
| **> / <** | Speed Up / Down |
| **' / /** | Transpose Key Up / Down |
| **Space** | Pause / Resume |
| **1, 2, 3**| Switch UI mode (Classic, Mini, Full) |
| **q** | Stop playback and Quit |

---

## 4. The Synthesizer Engines (Subcommands)

Midiraja's true power lies in its built-in software synthesizers. You activate them by typing their subcommand name before the file.

### 1-Bit Digital Cluster (`beep`)
A mathematical emulator of early 1980s boolean logic audio hardware (like the Apple II).
* **Requirements:** None (Built-in)
* **Usage:** `midra beep song.mid`
* **FM Synthesis Mode:** `midra beep --synth fm --fm-ratio 2.0 --fm-index 2.0 song.mid`

### AdLib / Sound Blaster FM (`opl` & `opn`)
Instant DOS-era and Sega Genesis FM synthesis using embedded emulation cores.
* **Requirements:** None (Built-in)
* **Usage (OPL):** `midra opl song.mid`
* **Usage (OPN):** `midra opn song.mid`
* **Change Bank:** `midra opl -b 14 song.mid` (Plays using the DOOM patch bank)

### Gravis Ultrasound (`gus`)
Classic 1990s wavetable synthesis. On first run, it automatically downloads a lightweight `freepats` bank.
* **Requirements:** Internet connection on first run.
* **Usage:** `midra gus song.mid`
* **Retro Bitcrusher:** `midra gus --bits 6 --realsound song.mid`

### FluidSynth (`fluidsynth`)
Modern, high-fidelity General MIDI playback.
* **Requirements:** A standard `.sf2` SoundFont file.
* **Usage:** `midra fluidsynth /path/to/soundfont.sf2 song.mid`

### Roland MT-32 (`mt32`)
Cycle-accurate emulation of the legendary Roland MT-32 module.
* **Requirements:** MT-32 ROM files (User must provide).
* **Usage:** `midra mt32 /path/to/rom_directory/ monkey_island.mid`
* **Aliases:** `munt`, `lapc1`, `cm32l`

---

## 5. Advanced Launch Options

You can set playback states right from the command line, bypassing the need to press keys later:
```bash
# Start at 1 minute and 30 seconds
midra --start 01:30 song.mid

# Play at 1.5x speed
midra --speed 1.5 song.mid

# Transpose the song one octave up (+12 semitones) and lower the starting volume
midra --transpose 12 --volume 50 song.mid
```
