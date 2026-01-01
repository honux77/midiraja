# Midiraja User Manual: Getting Started

Welcome to **Midiraja** (`midra`)! This guide will take you from installation to playing your first retro MIDI tunes in under 10 seconds. Midiraja is designed to be a bridge between modern terminals and the golden age of computer music.

---

## 🚀 1. Installation

Midiraja is distributed as a single, standalone native binary. It does not require a Java Runtime (JRE) to be pre-installed on your system.

### macOS (Recommended)
The easiest way to install and keep Midiraja up to date is via Homebrew:
```bash
brew tap YOUR_GITHUB_USERNAME/tap
brew install midra
```

### Linux & macOS (Quick Script)
Alternatively, you can use this one-liner to download the latest binary directly to your local bin directory:
```bash
curl -sL https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/midiraja/master/install.sh | bash
```

---

## 🎧 2. The Magic Command (Listen in 10 seconds)

The biggest hurdle with MIDI players is often finding and configuring "SoundFonts" or "Patch Banks." Midiraja solves this by embedding several pure-mathematical synthesizers directly into the app.

To hear music immediately without any setup, find any `.mid` file and run:
```bash
midra beep song.mid
```
This uses the **1-Bit Digital Cluster** engine. It doesn't use samples; it calculates every audio pulse in real-time using the same boolean logic found in 1980s home computers.

*(Pro tip: If you want that classic DOS gaming sound, try `midra opl song.mid` instead!)*

---

## 🎮 3. Basic Playback & TUI Controls

### Playing Multiple Files
Midiraja handles large collections with ease. You can pass a single file, a list of files, or entire directories. It will automatically scan for MIDI files inside folders.
```bash
midra *.mid
midra --loop --shuffle ~/my_midi_collection/
```

### Live Controls (Terminal UI)
While the music is playing, Midiraja transforms your terminal into an interactive dashboard. Use these keys to control the experience in real-time:

| Key | Action |
|-----|--------|
| **Up / Down** | Next / Previous Track in the playlist |
| **Left / Right** | Seek backward / forward 10 seconds |
| **+ / -** | Increase / Decrease master volume |
| **> / <** | Speed up / Slow down playback (Tempo) |
| **' / /** | Transpose the key up or down |
| **Space** | Pause or Resume playback |
| **1, 2, 3**| Toggle UI modes: 1 (Classic), 2 (Mini), 3 (Full Dashboard) |
| **q** | Stop everything and return to the prompt |

---

## 🎹 4. The Synthesizer Engines (Subcommands)

Midiraja is a "Museum of Computer Audio." By changing the subcommand, you change the entire acoustic character of the music.

### 🤖 1-Bit Digital Cluster (`beep`)
This is our flagship engine. It emulates the raw, gritty sound of the Apple II speaker. It uses 100% fixed-point integer math and AI-tuned filters to make 1-bit audio sound musical.
* **Best for:** 8-bit chiptune vibes and experimental electronic sounds.
* **Usage:** `midra beep --synth fm song.mid` (Classic FM synthesis)

### 📻 AdLib / Sound Blaster FM (`opl` & `opn`)
Perfectly replicates the FM synthesis chips found in 90s sound cards and Sega consoles. No external files are needed as we include dozens of legendary "Bank" files built-in.
* **Best for:** 90s DOS games, DOOM soundtracks, and Mega Drive/Genesis vibes.
* **Usage:** `midra opl -b 14 song.mid` (The "DOOM" soundbank)

### 🎻 Gravis Ultrasound (`gus`)
The high-end wavetable alternative to Sound Blaster. On its first run, Midiraja will offer to download a lightweight (27MB) patch set automatically.
* **Best for:** High-quality orchestral or acoustic MIDI files.
* **Usage:** `midra gus --bits 6 --realsound song.mid` (Simulates the lower-fidelity PWM output)

### 🎹 FluidSynth (`fluidsynth`)
The industry standard for modern MIDI playback. This engine requires you to provide your own SoundFont file (`.sf2`).
* **Best for:** Modern, realistic instrument reproduction.
* **Usage:** `midra fluidsynth /path/to/my_piano.sf2 song.mid`

### 📟 Roland MT-32 (`mt32`)
The "Holy Grail" of early PC gaming audio. Due to copyright, you must provide your own MT-32 ROM files. Simply point Midiraja to the folder containing them.
* **Best for:** LucasArts (Monkey Island) and Sierra (King's Quest) adventures.
* **Usage:** `midra mt32 ~/roms/ monkey_island.mid`

---

## 🛠️ 5. Advanced Launch Options

Want to start a song at a specific moment or at a specific volume? You can set these state flags directly in the command line:

* **Jump to time:** `midra --start 01:30 song.mid`
* **Custom Speed:** `midra --speed 1.5 song.mid` (1.5x faster)
* **Key Transpose:** `midra --transpose -12 song.mid` (One octave lower)

If you run Midiraja without a engine subcommand (e.g., `midra song.mid`), it will simply send the MIDI data to your OS's default synthesizer.
