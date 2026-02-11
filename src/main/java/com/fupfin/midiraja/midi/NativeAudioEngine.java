/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.midi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("EmptyCatch") public class NativeAudioEngine extends AbstractFFMBridge implements AudioEngine
{
    private MemorySegment ctx = MemorySegment.NULL;

    private final MethodHandle midiraja_audio_init;
    private final MethodHandle midiraja_audio_push;
    private final MethodHandle midiraja_audio_get_buffer_capacity_frames;
    private final MethodHandle midiraja_audio_get_queued_frames;
    private final MethodHandle midiraja_audio_get_device_latency_frames;
    private final MethodHandle midiraja_audio_flush;
    private final MethodHandle midiraja_audio_close;

    public NativeAudioEngine(String libPath) throws Exception
    {
        this(Arena.ofShared(), libPath);
    }

    private NativeAudioEngine(Arena arena, String libPath) throws Exception
    {
        super(arena, loadLib(libPath));

        midiraja_audio_init = downcall("midiraja_audio_init",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT));

        midiraja_audio_get_buffer_capacity_frames = downcall("midiraja_audio_get_buffer_capacity_frames", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        midiraja_audio_push = downcall("midiraja_audio_push", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        midiraja_audio_get_queued_frames =
            downcall("midiraja_audio_get_queued_frames", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        midiraja_audio_get_device_latency_frames = downcall("midiraja_audio_get_device_latency_frames", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        midiraja_audio_flush = downcall("midiraja_audio_flush", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        midiraja_audio_close = downcall("midiraja_audio_close", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    }


    private static SymbolLookup loadLib(String libPath) {
        System.load(new java.io.File(libPath).getAbsolutePath());
        return SymbolLookup.loaderLookup();
    }
    @Override public void init(int sampleRate, int channels, int bufferFrames) throws Exception
    {
        try
        {
            ctx =
                (MemorySegment) midiraja_audio_init.invokeExact(sampleRate, channels, bufferFrames);
            if (ctx.equals(MemorySegment.NULL))
            {
                throw new Exception("Failed to initialize miniaudio engine.");
            }
        }
        catch (Throwable t) {
            System.err.println("[NativeBridge Error] " + t.getMessage());
            throw new Exception("Error invoking midiraja_audio_init", t);
        }
    }

    private MemorySegment pushBuffer = MemorySegment.NULL;
    private int currentPushBufferSize = 0;

    @Override public int getQueuedFrames()
    {
        if (ctx.equals(MemorySegment.NULL))
            return 0;
        try
        {
            return (int) midiraja_audio_get_queued_frames.invokeExact(ctx);
        }
        catch (Throwable ignored) {
            System.err.println("[NativeBridge Error] " + ignored.getMessage());
            return 0;
        }
    }

    @Override public int getDeviceLatencyFrames()
    {
        if (ctx.equals(MemorySegment.NULL))
            return 0;
        try
        {
            return (int) midiraja_audio_get_device_latency_frames.invokeExact(ctx);
        }
        catch (Throwable ignored) {
            System.err.println("[NativeBridge Error] " + ignored.getMessage());
            return 0;
        }
    }

    @Override public int push(short[] pcmData)
    {
        if (ctx.equals(MemorySegment.NULL) || pcmData == null || pcmData.length == 0)
            return 0;

        try
        {
            int requiredBytes = pcmData.length * 2;
            if (currentPushBufferSize < requiredBytes)
            {
                pushBuffer = arena.allocate(requiredBytes);
                currentPushBufferSize = requiredBytes;
            }
            MemorySegment.copy(pcmData, 0, pushBuffer, ValueLayout.JAVA_SHORT, 0, pcmData.length);
            return (int) midiraja_audio_push.invokeExact(ctx, pushBuffer, pcmData.length);
        }
        catch (Throwable ignored) {
            System.err.println("[NativeBridge Error] " + ignored.getMessage());
            return 0;
        }
    }
    
    @Override public int getBufferCapacityFrames() {
        if (ctx.equals(MemorySegment.NULL)) return 0;
        try {
            return (int) midiraja_audio_get_buffer_capacity_frames.invokeExact(ctx);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override public void flush()
    {
        if (ctx.equals(MemorySegment.NULL))
            return;
        try
        {
            midiraja_audio_flush.invokeExact(ctx);
        }
        catch (Throwable ignored) {
            System.err.println("[NativeBridge Error] " + ignored.getMessage());
        }
    }

    @Override public void close()
    {
        if (!ctx.equals(MemorySegment.NULL))
        {
            try
            {
                midiraja_audio_close.invokeExact(ctx);
            }
            catch (Throwable ignored) {
            System.err.println("[NativeBridge Error] " + ignored.getMessage());
            }
            ctx = MemorySegment.NULL;
        }
        try { super.close(); } catch(Exception e) {
            System.err.println("[NativeBridge Error] " + e.getMessage());}
    }
}