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

## 🎮 3. Basic Playback & The 3 Ways to View

### Playing Multiple Files
Midiraja handles large collections with ease. You can pass a single file, a list of files, or entire directories. It will automatically scan for MIDI files inside folders.
```bash
midra *.mid
midra --loop --shuffle ~/my_midi_collection/
```

### Live Controls & Display Modes (Terminal UI)
While the music is playing, Midiraja transforms your terminal into an interactive dashboard. You can instantly change how the app looks by pressing **`1` (Classic)**, **`2` (Mini Widget)**, or **`3` (Full Dashboard)**. 

Use these keys to control the experience in real-time:

| Key | Action |
|-----|--------|
| **Up / Down** | Next / Previous Track in the playlist |
| **Left / Right** | Seek backward / forward 10 seconds |
| **+ / -** | Increase / Decrease master volume |
| **> / <** | Speed up / Slow down playback (Tempo) |
| **' / /** | Transpose the key up or down |
| **Space** | Pause or Resume playback |
| **1, 2, 3**| Toggle UI display modes |
| **q** | Stop everything and return to the prompt |

---

## 🎹 4. The 3 Ways to Play (Engines & Subcommands)

Midiraja's architecture allows it to output audio in three fundamentally different ways. You choose the method by adding a subcommand.

### Option A: OS MIDI Ports (No subcommand)
If you just type `midra song.mid`, Midiraja acts as a sequencer. It sends raw MIDI signals directly to your operating system.
* **Best for:** Using Apple CoreMIDI, Windows GS Wavetable, or routing to a physical external synthesizer (like a real Yamaha Motif) plugged in via USB.
* **Usage:** `midra song.mid` (A menu will pop up asking you to select a port).

### Option B: Built-in Retro Engines (Zero-Dependency)
These engines are compiled directly into the Midiraja binary. They require absolutely no external installations or downloads to work.

* **🤖 1-Bit Digital Cluster (`beep`):** Emulates the raw, gritty, boolean-logic sound of the Apple II speaker. 
  * *Usage:* `midra beep --synth fm song.mid`
* **📻 AdLib / Sound Blaster FM (`opl` & `opn`):** Perfect FM synthesis replicating 90s DOS sound cards and Sega consoles. Includes DOOM and Duke Nukem banks built-in.
  * *Usage:* `midra opl -b 14 song.mid`
* **🎻 Gravis Ultrasound (`gus`):** 1990s wavetable synthesis. On its first run, it will automatically download a tiny 27MB patch set for you.
  * *Usage:* `midra gus --bits 6 --realsound song.mid`

### Option C: Shared Library Linking (External Engines)
If you want industry-standard modern rendering or cycle-accurate emulation, Midiraja can dynamically link to popular C-libraries if you have them installed via Homebrew or apt.

* **🎹 FluidSynth (`fluidsynth`):** The industry standard for SoundFont rendering. Requires you to provide your own `.sf2` file.
  * *Usage:* `midra fluidsynth /path/to/my_piano.sf2 song.mid`
* **📟 Roland MT-32 (`mt32`):** The "Holy Grail" of early PC gaming audio. Requires the `munt` emulator and original Roland ROM files.
  * *Usage:* `midra mt32 ~/roms/ monkey_island.mid`

---

## 🛠️ 5. Advanced Launch Options

Want to start a song at a specific moment or at a specific volume? You can set these state flags directly in the command line:

* **Jump to time:** `midra --start 01:30 song.mid`
* **Custom Speed:** `midra --speed 1.5 song.mid` (1.5x faster)
* **Key Transpose:** `midra --transpose -12 song.mid` (One octave lower)
