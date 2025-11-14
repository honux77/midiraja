/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE
 * file in the root directory of this source tree.
 */

package com.midiraja.midi.timidity;

import com.midiraja.midi.MidiPort;
import com.midiraja.midi.NativeAudioEngine;
import com.midiraja.midi.SoftSynthProvider;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("ThreadPriorityCheck")
public class JavaTimiditySynthProvider implements SoftSynthProvider {
  private final NativeAudioEngine audio;
  private final @Nullable String patchDir;
  private final TimidityJavaEngine engine;

  private @Nullable Thread renderThread;
  private volatile boolean running = false;
  private volatile boolean renderPaused = false;

  public JavaTimiditySynthProvider(NativeAudioEngine audio,
                                   @Nullable String patchDir) {
    this.audio = audio;
    this.patchDir = patchDir;
    this.engine = new TimidityJavaEngine(44100);
  }

  @SuppressWarnings("EmptyCatch")
  @Override
  public List<MidiPort> getOutputPorts() {
    return List.of(new MidiPort(0, "Midiraja Pure Java TiMidity (GUS)"));
  }

  @SuppressWarnings("EmptyCatch")
  @Override
  public void openPort(int portIndex) throws Exception {
    if (patchDir != null) {
      File pianoPat = new File(patchDir, "acpiano.pat");
      if (pianoPat.exists()) {
        try (FileInputStream in = new FileInputStream(pianoPat)) {
          GusPatch patch = GusPatchReader.read(in);
          // Map it to program 0 (Acoustic Grand Piano)
          engine.loadPatch(0, patch);
        }
      }
    }

    if (audio != null) {
      audio.init(44100, 2, 4096);
      startRenderThread();
    }
  }

  @SuppressWarnings("EmptyCatch")
  @Override
  public void loadSoundbank(String path) throws Exception {
    // For TiMidity, the path is already set as patchDir. This handles the
    // explicit .sf2 equivalent. We could load a .cfg file here in the future.
  }

  private void startRenderThread() {
    running = true;
    renderThread = new Thread(() -> {
      final int framesToRender = 512;
      short[] pcmBuffer = new short[framesToRender * 2]; // Stereo
      float[] left = new float[framesToRender];
      float[] right = new float[framesToRender];

      while (running) {
        if (renderPaused) {
          try {
            Thread.sleep(1);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
          continue;
        }

        // Clear buffers
        for (int i = 0; i < framesToRender; i++) {
          left[i] = 0;
          right[i] = 0;
        }

        engine.render(left, right, framesToRender);

        for (int i = 0; i < framesToRender; i++) {
          float l = Math.max(-1.0f, Math.min(1.0f, left[i] / 128.0f));
          float r = Math.max(-1.0f, Math.min(1.0f, right[i] / 128.0f));
          pcmBuffer[i * 2] = (short)(l * 32767);
          pcmBuffer[i * 2 + 1] = (short)(r * 32767);
        }

        if (audio != null) {
          audio.push(pcmBuffer);
        }
      }
    });
    renderThread.setPriority(Thread.MAX_PRIORITY);
    renderThread.setDaemon(true);
    renderThread.start();
  }

  @SuppressWarnings("EmptyCatch")
  @Override
  public void prepareForNewTrack() {
    if (audio == null)
      return;
    renderPaused = true;
    try {
      Thread.sleep(20);
    } catch (InterruptedException ignored) {
    }
    audio.flush();
    engine.getActiveVoices().clear(); // Fast drain equivalent
  }

  @SuppressWarnings("EmptyCatch")
  @Override
  public void onPlaybackStarted() {
    renderPaused = false;
  }

  @SuppressWarnings("EmptyCatch")
  @Override
  public void sendMessage(byte[] data) {
    if (data == null || data.length < 1)
      return;

    int status = data[0] & 0xFF;
    int cmd = status & 0xF0;
    int ch = status & 0x0F;

    if (cmd == 0x90 && data.length >= 3) {
      int note = data[1] & 0xFF;
      int velocity = data[2] & 0xFF;
      if (velocity > 0) {
        engine.noteOn(ch, note, velocity);
      } else {
        engine.noteOff(ch, note);
      }
    } else if (cmd == 0x80 && data.length >= 3) {
      int note = data[1] & 0xFF;
      engine.noteOff(ch, note);
    }
  }

  @SuppressWarnings("EmptyCatch")
  @Override
  public void panic() {
    engine.getActiveVoices().clear();
    if (audio != null)
      audio.flush();
  }

  @SuppressWarnings("EmptyCatch")
  @Override
  public void closePort() {
    running = false;
    if (renderThread != null) {
      renderThread.interrupt();
      try {
        renderThread.join(500);
      } catch (InterruptedException ignored) {
      }
    }
    if (audio != null)
      audio.close();
    engine.getActiveVoices().clear();
  }
}
