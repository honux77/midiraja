# Midiraja (midra) User Guide

Welcome to the official user guide for **Midiraja**! This manual will walk you through everything from playing your first song to mastering the cycle-accurate retro synthesizers built into the engine.

---

## Chapter 1. Introduction

Midiraja (`midra`) is the ultimate **Terminal-Native** MIDI player. It is designed to bring a deeply interactive, visually rich audio experience directly into your command line—without ever touching a mouse.

**The Core Features:**
* **Built for Fun, Not Just Work:** Midiraja isn't a complex, sterile DAW for professionals. It’s built for hobbyists, retro-gamers, and CLI lovers who want a convenient, fun, and interactive way to explore MIDI files.
* **The Terminal as a Canvas:** We believe CLI tools can be beautiful. Midiraja features a lightning-fast Terminal UI (TUI) with real-time 16-channel VU meters, progress bars, and live keystroke controls for tempo and pitch.
* **The 3 Ways to View:** Choose how you see your music: a glorious full-screen dashboard, a single-line mini widget for background listening, or classic pipe-friendly logs.
* **The 3 Ways to Play:** Route your music anywhere. Send it to an external hardware keyboard, link it to modern C-libraries like FluidSynth, or synthesize it internally using built-in, zero-dependency retro engines.

---

## Chapter 2. Getting Started

### 2.1. Installation
Midiraja is distributed as a single, standalone native program. You do not need to install Java or any complex audio drivers to get started.

**For macOS (The Easiest Way):**
If you use Homebrew, simply run:
```bash
brew tap YOUR_GITHUB_USERNAME/tap
brew install midra
```

**For Linux & macOS (Quick Script):**
You can also use this one-liner to download the app directly:
```bash
curl -sL https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/midiraja/master/install.sh | bash
```

### 2.2. The 10-Second Magic Command
The biggest hurdle with playing MIDI files is usually hunting down "Patch Banks" or configuring ports. Midiraja solves this by including built-in mathematical sound generators!

To hear music immediately without any setup, find any `.mid` file on your computer and type:
```bash
midra beep song.mid
```
*(This instantly fires up the Terminal UI alongside our built-in "1-Bit Digital Cluster" engine, generating classic retro sound using pure mathematics!)*

---

## Chapter 3. Basic Usage & UI Controls

### 3.1. How to Play Files and Playlists
Midiraja is designed to handle your entire music collection. The basic grammar is always:
`midra [engine_name] [options] <files>`

Here are some ways to feed music to the player:
```bash
# Play a single file
midra song.mid

# Play an entire folder! Midiraja will find all the MIDI files inside.
midra ~/my_game_music/

# Make a random, endless jukebox by adding shuffle and loop flags
midra --loop --shuffle ~/my_game_music/

# Play a standard M3U playlist file
midra my_playlist.m3u
```

### 3.2. Launch Options (Presetting the Player)
You can tell Midiraja exactly how to start playing before the UI even opens:
* **`--start 01:30`**: Skip the boring intro and start exactly at 1 minute and 30 seconds.
* **`--speed 1.5`**: Play the track 1.5x faster (great for practicing!).
* **`--transpose 12`**: Shift the entire song up one full octave (12 semitones).
* **`--volume 50`**: Start quietly at 50% master volume.

### 3.3. Live Terminal Controls (TUI)
Once the music starts, your terminal becomes an interactive dashboard. You don't need to quit the app to change the music—just press these keys:

| Key | What it does |
|-----|--------|
| **`Up` / `Down`** | Instantly skip to the Next or Previous Track in your playlist. |
| **`Left` / `Right`** | Fast-forward or Rewind the current song by 10 seconds. |
| **`+` / `-`** | Turn the master volume Up or Down. |
| **`>` / `<`** | Make the song play Faster or Slower in real-time. |
| **`'` / `/`** | Transpose the musical key Up or Down (great for singers!). |
| **`Space`** | Pause or Resume the music. |
| **`1`, `2`, `3`**| Change how the app looks! `3` is the full dashboard, `2` is a tiny mini-bar, and `1` is classic scrolling text. |
| **`q`** | Stop the music and safely quit the program. |

---

## Chapter 4. The 3 Ways to Play (Synthesizer Engines)

What makes Midiraja a "Museum of Computer Audio" is its ability to radically change how a song sounds. You choose the "Engine" by typing its name right after `midra`.

### Method A: OS MIDI Ports (No Subcommand)
If you don't type an engine name, Midiraja acts as a traffic director. It sends the raw sheet music (MIDI data) to your computer's built-in synthesizer or out through a USB cable to a real physical piano!
* **Usage:** `midra song.mid`
* **What happens:** A friendly menu will pop up asking you to select which port or device you want to send the music to.

### Method B: Built-in Retro Engines (Zero-Dependency)
These engines are baked directly into the Midiraja app. They require **absolutely zero external files or downloads**.

#### 1. 1-Bit Digital Cluster (`beep`)
* **What is it?** Back in 1981, computers like the Apple II didn't have sound cards; they just had a tiny beeper that could only be turned ON or OFF (1-Bit). This engine mathematically simulates that incredibly harsh, gritty, and charming sound constraint.
* **How to use it:** 
  * `midra beep song.mid` (Plays with a modern, smooth FM synthesis flavor)
  * `midra beep --synth square --mux xor --voices 2 song.mid` (The hardcore 1981 Apple II emulation mode. Brace your ears!)

#### 2. AdLib / Sound Blaster FM (`opl` & `opn`)
* **What is it?** This perfectly replicates the famous Yamaha chips used in 1990s PC sound cards and Sega Genesis consoles. It gives everything that classic, twangy "DOOM" or "Sonic the Hedgehog" vibe.
* **How to use it:** 
  * `midra opl song.mid` (PC DOS style)
  * `midra opn song.mid` (Sega Genesis style)
  * `midra opl -b 14 song.mid` (Plays the song using the legendary built-in DOOM instrument bank!)

#### 3. Gravis Ultrasound (`gus`)
* **What is it?** In the mid-90s, the GUS card revolutionized PC audio by playing back actual recorded audio samples (wavetables) instead of synthesizing them.
* **How to use it:** `midra gus song.mid` 
* *(Note: The very first time you run this, Midiraja will automatically download a tiny 27MB patch set for you. After that, it works completely offline!)*

### Method C: Shared Library Linking (External Engines)
If you want ultra-realistic modern audio or perfect emulation of specific high-end retro gear, Midiraja can "link" to popular tools you may have already installed on your Mac or Linux machine.

#### 1. FluidSynth (`fluidsynth`)
* **What is it?** The industry standard for playing `.sf2` (SoundFont) files. SoundFonts are massive libraries of professionally recorded real instruments (like grand pianos or orchestras).
* **Requirements:** You must download your own `.sf2` file from the internet.
* **How to use it:** `midra fluidsynth /path/to/my_piano.sf2 song.mid`

#### 2. Roland MT-32 (`mt32`)
* **What is it?** The "Holy Grail" of early 90s adventure game audio. If you ever played Monkey Island or King's Quest, this is the magical synthesizer that originally powered them.
* **Requirements:** Because of copyright laws, you must legally acquire your own "MT-32 ROM" files and place them in a folder. You also need to install the `munt` emulator via Homebrew.
* **How to use it:** `midra mt32 ~/my_rom_folder/ monkey_island.mid`

---

## Chapter 5. Powered by Open Source (Appendices)

Midiraja stands on the shoulders of giants. While our UI, rendering pipelines, and the 1-Bit engine are custom-built, we proudly integrate several legendary open-source emulation cores to bring you the best retro sound possible. We highly encourage you to check out and support these amazing projects!

### The Emulation Cores
* **[libADLMIDI](https://github.com/Wohlstand/libADLMIDI):** An incredible C++ library that emulates the Yamaha OPL3 (YMF262) chip. This is the heart of our `midra opl` engine.
* **[libOPNMIDI](https://github.com/Wohlstand/libOPNMIDI):** A sister project that emulates the Yamaha OPN2 (YM2612) chip found in the Sega Genesis. This powers our `midra opn` engine.
* **[Munt (MT-32 Emulator)](https://github.com/munt/munt):** The definitive, cycle-accurate software synthesizer replicating the Roland MT-32 and CM-32L hardware. We dynamically link to this library for the `midra mt32` command.
* **[FluidSynth](https://www.fluidsynth.org/):** The world's leading real-time software synthesizer based on the SoundFont 2 specifications. We link to this for the `midra fluidsynth` command.

### Technical Documentation
If you are an audio engineer or a hardcore retro programming enthusiast, you might enjoy reading our deep-dive technical papers on how we built our custom engines:
* **[🤖 The 1-Bit Audio Engineering Whitepaper](beep-1bit-audio-engineering.md)**: Discover the strict integer mathematics, fixed-point logic, and AI-driven DSP algorithms that power our zero-dependency `beep` engine.
* **[🎹 Soft Synth Setup Guide](soft-synth-guide.md)**: Detailed configuration instructions and environment variable settings for integrating external libraries like FluidSynth and Munt.
