/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.midi;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"EmptyCatch", "UnusedVariable"})
public class FFMMuntNativeBridge implements MuntNativeBridge {

    private final Arena arena;
    private MemorySegment context = MemorySegment.NULL;
    
    // FFM Method Handles
    private final MethodHandle mt32emu_create_context;
    private final MethodHandle mt32emu_free_context;
    private final MethodHandle mt32emu_add_rom_file;
    private final MethodHandle mt32emu_open_synth;
    private final MethodHandle mt32emu_close_synth;
    private final MethodHandle mt32emu_play_msg;
    private final MethodHandle mt32emu_play_sysex;
    private final MethodHandle mt32emu_render_bit16s;

    public FFMMuntNativeBridge() throws Exception {
        this.arena = Arena.ofShared();
        
        SymbolLookup lib = tryLoadLibrary(arena, "libmt32emu.dylib", "libmt32emu.so", "libmt32emu.dll");
        Linker linker = Linker.nativeLinker();

        // mt32emu_context mt32emu_create_context(mt32emu_report_handler_i report_handler, void *instance_data)
        mt32emu_create_context = linker.downcallHandle(
            lib.find("mt32emu_create_context").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // void mt32emu_free_context(mt32emu_context context)
        mt32emu_free_context = linker.downcallHandle(
            lib.find("mt32emu_free_context").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );

        // mt32emu_return_code mt32emu_add_rom_file(mt32emu_context context, const char *filename)
        mt32emu_add_rom_file = linker.downcallHandle(
            lib.find("mt32emu_add_rom_file").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // mt32emu_return_code mt32emu_open_synth(mt32emu_const_context context)
        mt32emu_open_synth = linker.downcallHandle(
            lib.find("mt32emu_open_synth").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        // void mt32emu_close_synth(mt32emu_const_context context)
        mt32emu_close_synth = linker.downcallHandle(
            lib.find("mt32emu_close_synth").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );

        // mt32emu_return_code mt32emu_play_msg(mt32emu_const_context context, mt32emu_bit32u msg)
        mt32emu_play_msg = linker.downcallHandle(
            lib.find("mt32emu_play_msg").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // mt32emu_return_code mt32emu_play_sysex(mt32emu_const_context context, const mt32emu_bit8u *sysex, mt32emu_bit32u len)
        mt32emu_play_sysex = linker.downcallHandle(
            lib.find("mt32emu_play_sysex").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // void mt32emu_render_bit16s(mt32emu_const_context context, mt32emu_bit16s *stream, mt32emu_bit32u len)
        mt32emu_render_bit16s = linker.downcallHandle(
            lib.find("mt32emu_render_bit16s").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
    }
    
    private SymbolLookup tryLoadLibrary(Arena arena, String... paths) {
        List<String> failedPaths = new ArrayList<>();
        // Also look in our local build dir for development ease
        String projectRoot = new File("").getAbsolutePath();
        String devPathMac = projectRoot + "/src/main/c/munt/libmt32emu.dylib";
        String devPathLinux = projectRoot + "/src/main/c/munt/libmt32emu.so";
        
        String[] allPaths = new String[paths.length + 2];
        System.arraycopy(paths, 0, allPaths, 0, paths.length);
        allPaths[paths.length] = devPathMac;
        allPaths[paths.length + 1] = devPathLinux;

        for (String path : allPaths) {
            try {
                if (path.startsWith("/")) {
                    File f = new File(path);
                    if (f.exists()) {
                        return SymbolLookup.libraryLookup(f.toPath(), arena);
                    }
                } else {
                    return SymbolLookup.libraryLookup(path, arena);
                }
            } catch (IllegalArgumentException e) {
                failedPaths.add(path);
            }
        }
        throw new IllegalArgumentException("Cannot open libmt32emu. Searched paths: " + String.join(", ", failedPaths));
    }

    @Override
    public void createSynth() throws Exception {
        try {
            context = (MemorySegment) mt32emu_create_context.invokeExact(MemorySegment.NULL, MemorySegment.NULL);
            if (context.equals(MemorySegment.NULL)) {
                throw new Exception("Failed to create Munt context");
            }
        } catch (Throwable t) {
            throw new Exception("Error creating Munt context", t);
        }
    }

    @Override
    public void loadRoms(String romDirectory) throws Exception {
        if (context.equals(MemorySegment.NULL)) return;
        
        File dir = new File(romDirectory);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new Exception("Munt ROM directory not found: " + romDirectory);
        }
        
        File controlRom = new File(dir, "MT32_CONTROL.ROM");
        File pcmRom = new File(dir, "MT32_PCM.ROM");
        
        if (!controlRom.exists() || !pcmRom.exists()) {
             // Let's also try lowercase
             controlRom = new File(dir, "mt32_control.rom");
             pcmRom = new File(dir, "mt32_pcm.rom");
             if (!controlRom.exists() || !pcmRom.exists()) {
                 throw new Exception("Missing MT32_CONTROL.ROM or MT32_PCM.ROM in " + romDirectory);
             }
        }

        try {
            MemorySegment ctrlPathStr = arena.allocateFrom(controlRom.getAbsolutePath());
            int rc1 = (int) mt32emu_add_rom_file.invokeExact(context, ctrlPathStr);
            if (rc1 != 0) throw new Exception("Failed to load control ROM");

            MemorySegment pcmPathStr = arena.allocateFrom(pcmRom.getAbsolutePath());
            int rc2 = (int) mt32emu_add_rom_file.invokeExact(context, pcmPathStr);
            if (rc2 != 0) throw new Exception("Failed to load PCM ROM");
        } catch (Throwable t) {
            throw new Exception("Error loading Munt ROMs", t);
        }
    }

    @Override
    public void openSynth() throws Exception {
        if (context.equals(MemorySegment.NULL)) return;
        try {
            int rc = (int) mt32emu_open_synth.invokeExact(context);
            if (rc != 0) throw new Exception("Failed to open Munt synth (Check if ROMs are valid)");
        } catch (Throwable t) {
            throw new Exception("Error opening Munt synth", t);
        }
    }

    private void playMsg(int msg) {
        if (context.equals(MemorySegment.NULL)) return;
        try {
            int _dummy = (int) mt32emu_play_msg.invokeExact(context, msg);
        } catch (Throwable ignored) {}
    }

    @Override
    public void playNoteOn(int channel, int key, int velocity) {
        playMsg(0x90 | channel | (key << 8) | (velocity << 16));
    }

    @Override
    public void playNoteOff(int channel, int key) {
        playMsg(0x80 | channel | (key << 8));
    }

    @Override
    public void playControlChange(int channel, int number, int value) {
        playMsg(0xB0 | channel | (number << 8) | (value << 16));
    }

    @Override
    public void playProgramChange(int channel, int program) {
        playMsg(0xC0 | channel | (program << 8));
    }

    @Override
    public void playPitchBend(int channel, int value) {
        int lsb = value & 0x7F;
        int msb = (value >> 7) & 0x7F;
        playMsg(0xE0 | channel | (lsb << 8) | (msb << 16));
    }

    @Override
    public void playSysex(byte[] sysexData) {
        if (context.equals(MemorySegment.NULL) || sysexData == null || sysexData.length == 0) return;
        try {
            MemorySegment dataSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, sysexData);
            int _dummy = (int) mt32emu_play_sysex.invokeExact(context, dataSeg, sysexData.length);
        } catch (Throwable ignored) {}
    }

    @Override
    public void renderAudio(short[] buffer, int frames) {
        if (context.equals(MemorySegment.NULL) || buffer == null || buffer.length == 0) return;
        try {
            MemorySegment bufSeg = arena.allocateFrom(ValueLayout.JAVA_SHORT, buffer);
            mt32emu_render_bit16s.invokeExact(context, bufSeg, frames);
            // Copy back the rendered PCM data from native memory to Java array
            MemorySegment.copy(bufSeg, ValueLayout.JAVA_SHORT, 0, buffer, 0, buffer.length);
        } catch (Throwable ignored) {}
    }

    @Override
    public void close() {
        if (!context.equals(MemorySegment.NULL)) {
            try {
                mt32emu_close_synth.invokeExact(context);
                mt32emu_free_context.invokeExact(context);
            } catch (Throwable ignored) {}
            context = MemorySegment.NULL;
        }
        if (arena.scope().isAlive()) {
            arena.close();
        }
    }
}