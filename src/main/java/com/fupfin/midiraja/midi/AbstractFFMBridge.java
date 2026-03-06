package com.fupfin.midiraja.midi;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractFFMBridge implements AutoCloseable
{
    protected static final FunctionDescriptor DESC_INIT =
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
    protected static final FunctionDescriptor DESC_VOID_PTR =
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
    protected static final FunctionDescriptor DESC_PTR_INT =
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
    protected static final FunctionDescriptor DESC_PTR_STR =
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    protected static final FunctionDescriptor DESC_PTR_PTR_LONG = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
    protected static final FunctionDescriptor DESC_GENERATE = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
    protected static final FunctionDescriptor DESC_NOTE_ON =
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE,
                    ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE);
    protected static final FunctionDescriptor DESC_NOTE_OFF = FunctionDescriptor
            .ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE);
    protected static final FunctionDescriptor DESC_CTRL_CHANGE =
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE,
                    ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE);
    protected static final FunctionDescriptor DESC_PITCH_BEND = FunctionDescriptor
            .ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_SHORT);
    protected static final FunctionDescriptor DESC_SYS_EX = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
    protected static final FunctionDescriptor DESC_ERROR_INFO =
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    protected static final FunctionDescriptor DESC_NO_ARGS_INT =
            FunctionDescriptor.of(ValueLayout.JAVA_INT);

    protected final Arena arena;
    protected final SymbolLookup lib;
    protected final Linker linker;

    protected AbstractFFMBridge(Arena arena, SymbolLookup lib)
    {
        this.arena = arena;
        this.lib = lib;
        this.linker = Linker.nativeLinker();
    }

    protected final MethodHandle downcall(String symbol, FunctionDescriptor descriptor)
    {
        return linker.downcallHandle(
                lib.find(symbol).orElseThrow(
                        () -> new IllegalArgumentException("Symbol not found: " + symbol)),
                descriptor);
    }

    public static SymbolLookup tryLoadLibrary(Arena arena, String fallbackDevDir, String... paths)
            throws RuntimeException
    {
        if (paths.length == 0) throw new IllegalArgumentException("No library paths provided");
        List<String> failedPaths = new ArrayList<>();
        String projectRoot = new File("").getAbsolutePath();

        List<String> allPaths = new ArrayList<>(List.of(paths));
        if (fallbackDevDir != null && !fallbackDevDir.isEmpty())
        {
            // Assume paths are given like "libmt32emu.dylib", "libmt32emu.so", "libmt32emu.dll"
            for (String path : paths)
            {
                allPaths.add(projectRoot + "/src/main/c/" + fallbackDevDir + "/" + path);
            }
        }

        for (String path : allPaths)
        {
            try
            {
                if (path.startsWith("/"))
                {
                    File f = new File(path);
                    if (f.exists())
                    {
                        return SymbolLookup.libraryLookup(f.toPath(), arena);
                    }
                    else
                    {
                        failedPaths.add(path + " (not found)");
                    }
                }
                else
                {
                    return SymbolLookup.libraryLookup(path, arena);
                }
            }
            catch (IllegalArgumentException e)
            {
                failedPaths.add(path);
            }
        }

        throw new RuntimeException("Failed to load native library. Paths tried:\n  - "
                + String.join("\n  - ", failedPaths));
    }

    @Override
    public void close() throws RuntimeException
    {
        if (arena != null && arena.scope().isAlive())
        {
            arena.close();
        }
    }
}
