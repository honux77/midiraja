# PlaybackEngineFactory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `PlaybackEngine` construction behind a `@FunctionalInterface PlaybackEngineFactory` so that `PlaybackRunner.playPlaylistLoop()` can be unit-tested without real audio threads.

**Architecture:** A single `@FunctionalInterface` is added to the engine package. `PlaybackRunner` gains a 5-parameter constructor that accepts the factory; the existing 4-parameter constructor delegates to it using `PlaybackEngine::new`. The only production code change inside the loop is replacing one `new PlaybackEngine(...)` call with `engineFactory.create(...)`.

**Tech Stack:** Java 25, JUnit 5, `javax.sound.midi.Sequence`

---

## File Map

| File | Action |
|------|--------|
| `src/main/java/com/fupfin/midiraja/engine/PlaybackEngineFactory.java` | **Create** — `@FunctionalInterface` |
| `src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java` | **Modify** — add field, new constructor, replace one `new` call |
| `src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java` | **Modify** — add `MockPlaybackEngine` inner class + 5 test methods |

---

## Task 1: Failing test skeleton (TDD anchor)

**Files:**
- Modify: `src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java`

Add the `MockPlaybackEngine` inner class and one failing test that uses the not-yet-existing 5-parameter constructor. This test will fail to compile until Task 2 is complete.

- [ ] **Step 1: Add imports to `PlaybackRunnerTest.java`**

Open `src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java`. Add these imports after the existing import block:

```java
import com.fupfin.midiraja.engine.PlaybackEngine;
import com.fupfin.midiraja.engine.PlaybackEngine.PlaybackStatus;
import com.fupfin.midiraja.engine.PlaybackEngineFactory;
import com.fupfin.midiraja.engine.PlaylistContext;
import com.fupfin.midiraja.midi.MidiPort;
import com.fupfin.midiraja.ui.DumbUI;
import com.fupfin.midiraja.ui.PlaybackUI;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
```

(`ArrayList`, `AtomicInteger`, `MidiPort`, and `DumbUI` may already be imported — skip duplicates.)

- [ ] **Step 2: Add `MockPlaybackEngine` static inner class**

Add this static inner class inside `PlaybackRunnerTest`, after the existing `MockMidiProvider` class:

```java
    static class MockPlaybackEngine extends PlaybackEngine {
        private final PlaybackStatus exitStatus;

        // NOTE: super() calls sequence.getResolution() and sequence.getTracks(), so `seq`
        // must be a real non-null Sequence object. Always use createTestMidi() to obtain one.
        // Passing null will throw NullPointerException before start() is ever reached.
        MockPlaybackEngine(Sequence seq, MidiOutProvider p, PlaylistContext ctx,
                           int vol, double speed, Optional<String> start,
                           Optional<Integer> transpose, PlaybackStatus exitStatus) {
            super(seq, p, ctx, vol, speed, start, transpose);
            this.exitStatus = exitStatus;
        }

        @Override
        public PlaybackStatus start(PlaybackUI ui) throws Exception {
            return exitStatus;
        }
    }
```

- [ ] **Step 3: Add the first failing test**

Add this test method to `PlaybackRunnerTest`:

```java
    @Test
    void quitAll_exitsLoopAfterOneEngineCall(@TempDir Path tempDir) throws Exception {
        File f1 = createTestMidi(tempDir, "a.mid");
        File f2 = createTestMidi(tempDir, "b.mid");
        File f3 = createTestMidi(tempDir, "c.mid");

        AtomicInteger callCount = new AtomicInteger(0);
        PlaybackEngineFactory factory = (seq, p, ctx, vol, speed, start, transpose) -> {
            callCount.incrementAndGet();
            return new MockPlaybackEngine(seq, p, ctx, vol, speed, start, transpose,
                    PlaybackStatus.QUIT_ALL);
        };

        MockMidiProvider provider = new MockMidiProvider();
        // 5-parameter constructor — does not exist yet, will fail to compile
        PlaybackRunner runner = new PlaybackRunner(
                new PrintStream(outBytes), new PrintStream(errBytes), mockIO, true, factory);

        runner.run(provider, true, Optional.empty(), Optional.empty(),
                List.of(f1, f2, f3), common, List.of());

        assertEquals(1, callCount.get(), "Factory must be called exactly once on QUIT_ALL");
    }
```

Note: `createTestMidi` currently takes only `(Path tempDir)`. In Step 3 we call it with a filename argument. We will update the helper signature in Step 4 so tests can create distinct files.

- [ ] **Step 4: Update `createTestMidi` to accept a filename**

Replace the existing `createTestMidi(Path tempDir)` method with:

```java
    private File createTestMidi(Path tempDir, String name) throws Exception {
        File midiFile = tempDir.resolve(name).toFile();
        Sequence seq = new Sequence(Sequence.PPQ, 24);
        Track t = seq.createTrack();
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 64), 0));
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 2400));
        try (FileOutputStream fos = new FileOutputStream(midiFile)) {
            MidiSystem.write(seq, 1, fos);
        }
        return midiFile;
    }

    /** Backward-compat overload used by existing tests. */
    private File createTestMidi(Path tempDir) throws Exception {
        return createTestMidi(tempDir, "test.mid");
    }
```

- [ ] **Step 5: Verify compilation fails as expected**

```bash
./gradlew compileTestJava 2>&1 | grep -E "error:|cannot find symbol"
```

Expected: error mentioning `PlaybackRunner` constructor or `PlaybackEngineFactory` not found. Build must fail to compile, not crash at runtime.

If **no error appears**, stop: check whether `PlaybackEngineFactory.java` already exists in the engine package, or whether a prior partial implementation is present. Do not proceed to Task 2 until the compile error is confirmed.

---

## Task 2: Create `PlaybackEngineFactory` and wire into `PlaybackRunner`

**Files:**
- Create: `src/main/java/com/fupfin/midiraja/engine/PlaybackEngineFactory.java`
- Modify: `src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java`

- [ ] **Step 1: Create `PlaybackEngineFactory.java`**

Create `src/main/java/com/fupfin/midiraja/engine/PlaybackEngineFactory.java` with this exact content:

```java
/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.engine;

import com.fupfin.midiraja.midi.MidiOutProvider;
import java.util.Optional;
import javax.sound.midi.Sequence;

/**
 * Factory for constructing a {@link PlaybackEngine} instance per track.
 *
 * <p>The default production implementation is the constructor reference
 * {@code PlaybackEngine::new}. Tests inject a lambda that returns a mock engine whose
 * {@code start()} returns a predetermined {@link PlaybackEngine.PlaybackStatus} immediately,
 * allowing the playlist loop in {@code PlaybackRunner} to be exercised without real audio threads.
 */
@FunctionalInterface
public interface PlaybackEngineFactory
{
    /**
     * @throws Exception propagated from the engine constructor or provider
     */
    PlaybackEngine create(Sequence sequence, MidiOutProvider provider,
            PlaylistContext context,
            int initialVolumePercent, double initialSpeed,
            Optional<String> startTimeStr,
            Optional<Integer> initialTranspose) throws Exception;
}
```

- [ ] **Step 2: Add import and field to `PlaybackRunner`**

In `src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java`:

After the line `import com.fupfin.midiraja.engine.PlaybackEngine;`, add:

```java
import com.fupfin.midiraja.engine.PlaybackEngineFactory;
```

After the existing field declarations (around line 60, after `includeRetroInSuffix`), add:

```java
    private final PlaybackEngineFactory engineFactory;
```

- [ ] **Step 3: Refactor the existing 4-parameter constructor to delegate**

Replace the existing constructor body:

```java
    // BEFORE
    public PlaybackRunner(PrintStream out, PrintStream err, @Nullable TerminalIO terminalIO,
            boolean isTestMode)
    {
        this.out = out;
        this.err = err;
        this.terminalIO = terminalIO;
        this.isTestMode = isTestMode;
    }
```

with a delegation to the new 5-parameter constructor:

```java
    // AFTER
    public PlaybackRunner(PrintStream out, PrintStream err, @Nullable TerminalIO terminalIO,
            boolean isTestMode)
    {
        this(out, err, terminalIO, isTestMode, PlaybackEngine::new);
    }
```

- [ ] **Step 4: Add the new 5-parameter constructor**

Insert the following constructor immediately after the 4-parameter constructor:

```java
    /** Test constructor: injects a custom engine factory. */
    public PlaybackRunner(PrintStream out, PrintStream err, @Nullable TerminalIO terminalIO,
            boolean isTestMode, PlaybackEngineFactory engineFactory)
    {
        this.out = out;
        this.err = err;
        this.terminalIO = terminalIO;
        this.isTestMode = isTestMode;
        this.engineFactory = engineFactory;
    }
```

- [ ] **Step 5: Replace `new PlaybackEngine(...)` with `engineFactory.create(...)`**

In `playPlaylistLoop()`, find the single `new PlaybackEngine(...)` call (currently around line 461):

```java
                // BEFORE
                var engine = new PlaybackEngine(sequence, provider, context, common.volume,
                        common.speed, currentStartTime, common.transpose);
```

Replace with:

```java
                // AFTER
                var engine = engineFactory.create(sequence, provider, context, common.volume,
                        common.speed, currentStartTime, common.transpose);
```

The `engine.start(ui)` call at line 501 (`ScopedValue.where(...).call(() -> engine.start(ui))`) is **unchanged**.

- [ ] **Step 6: Run the first test to verify it passes**

```bash
./gradlew test --tests "com.fupfin.midiraja.cli.PlaybackRunnerTest.quitAll_exitsLoopAfterOneEngineCall" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`, test passes.

- [ ] **Step 7: Run the full test suite to check for regressions**

```bash
./gradlew test -x jacocoTestReport 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/engine/PlaybackEngineFactory.java \
        src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java \
        src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java
git commit -m "feat: extract PlaybackEngineFactory; test playlist loop with mock engine"
```

---

## Task 3: Remaining playlist-loop tests

**Files:**
- Modify: `src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java`

Add four more test methods. All use `shuffle=false` (the default in `CommonOptions`) so that `playOrderHolder` is the identity mapping and factory call N receives the sequence for `playlist.get(N % size)`.

- [ ] **Step 1: Add `finishedStatus_advancesThroughAllTracks`**

```java
    @Test
    void finishedStatus_advancesThroughAllTracks(@TempDir Path tempDir) throws Exception {
        File f1 = createTestMidi(tempDir, "a.mid");
        File f2 = createTestMidi(tempDir, "b.mid");
        File f3 = createTestMidi(tempDir, "c.mid");

        AtomicInteger callCount = new AtomicInteger(0);
        PlaybackEngineFactory factory = (seq, p, ctx, vol, speed, start, transpose) -> {
            callCount.incrementAndGet();
            return new MockPlaybackEngine(seq, p, ctx, vol, speed, start, transpose,
                    PlaybackStatus.FINISHED);
        };

        MockMidiProvider provider = new MockMidiProvider();
        PlaybackRunner runner = new PlaybackRunner(
                new PrintStream(outBytes), new PrintStream(errBytes), mockIO, true, factory);

        runner.run(provider, true, Optional.empty(), Optional.empty(),
                List.of(f1, f2, f3), common, List.of());

        // The spec table describes this as a 2-call scenario, but that is incorrect.
        // Tracing the loop: track 0 → FINISHED → nextIdx=1 (engine called, call 1);
        // track 1 → FINISHED → nextIdx=2 (call 2); track 2 → FINISHED → nextIdx=3 (call 3);
        // while condition (3 < 3) is false → loop exits. Engine is called for all 3 tracks.
        assertEquals(3, callCount.get(), "Engine must be created for every track");
    }
```

- [ ] **Step 2: Add `nextStatus_exitsAtNavBoundary`**

```java
    @Test
    void nextStatus_exitsAtNavBoundary(@TempDir Path tempDir) throws Exception {
        File f1 = createTestMidi(tempDir, "a.mid");
        File f2 = createTestMidi(tempDir, "b.mid");
        File f3 = createTestMidi(tempDir, "c.mid");

        AtomicInteger callCount = new AtomicInteger(0);
        PlaybackEngineFactory factory = (seq, p, ctx, vol, speed, start, transpose) -> {
            callCount.incrementAndGet();
            return new MockPlaybackEngine(seq, p, ctx, vol, speed, start, transpose,
                    PlaybackStatus.NEXT);
        };

        MockMidiProvider provider = new MockMidiProvider();
        PlaybackRunner runner = new PlaybackRunner(
                new PrintStream(outBytes), new PrintStream(errBytes), mockIO, true, factory);
        // Without exitOnNavBoundary, NEXT at the last track wraps to index 0 (infinite loop).
        // With it, NEXT at the last track returns playlist.size(), exiting the loop.
        runner.setExitOnNavBoundary(true);

        runner.run(provider, true, Optional.empty(), Optional.empty(),
                List.of(f1, f2, f3), common, List.of());

        assertEquals(3, callCount.get(), "Engine called once per track; exits at last-track boundary");
    }
```

- [ ] **Step 3: Add `loopEnabled_wrapsBackToFirstTrack`**

```java
    @Test
    void loopEnabled_wrapsBackToFirstTrack(@TempDir Path tempDir) throws Exception {
        File f1 = createTestMidi(tempDir, "a.mid");
        File f2 = createTestMidi(tempDir, "b.mid");
        File f3 = createTestMidi(tempDir, "c.mid");

        // Calls 1-3: tracks 0, 1, 2 → FINISHED with loop → wraps to track 0.
        // Call 4: track 0 again → QUIT_ALL to stop the test.
        AtomicInteger callCount = new AtomicInteger(0);
        PlaybackEngineFactory factory = (seq, p, ctx, vol, speed, start, transpose) -> {
            int call = callCount.incrementAndGet();
            PlaybackStatus status = call < 4 ? PlaybackStatus.FINISHED : PlaybackStatus.QUIT_ALL;
            return new MockPlaybackEngine(seq, p, ctx, vol, speed, start, transpose, status);
        };

        MockMidiProvider provider = new MockMidiProvider();
        PlaybackRunner runner = new PlaybackRunner(
                new PrintStream(outBytes), new PrintStream(errBytes), mockIO, true, factory);
        common.loop = true;

        runner.run(provider, true, Optional.empty(), Optional.empty(),
                List.of(f1, f2, f3), common, List.of());

        assertEquals(4, callCount.get(),
                "Loop must wrap: 3 tracks then track 0 again before QUIT_ALL");
    }
```

- [ ] **Step 4: Add `previousStatus_goesBackOneTrack`**

```java
    @Test
    void previousStatus_goesBackOneTrack(@TempDir Path tempDir) throws Exception {
        File f1 = createTestMidi(tempDir, "a.mid");
        File f2 = createTestMidi(tempDir, "b.mid");
        File f3 = createTestMidi(tempDir, "c.mid");

        // Call 1 (track 0): NEXT  → advances to track 1
        // Call 2 (track 1): PREVIOUS → goes back to track 0
        // Call 3 (track 0): QUIT_ALL → exits
        AtomicInteger callCount = new AtomicInteger(0);
        PlaybackEngineFactory factory = (seq, p, ctx, vol, speed, start, transpose) -> {
            int call = callCount.incrementAndGet();
            PlaybackStatus status = switch (call) {
                case 1 -> PlaybackStatus.NEXT;
                case 2 -> PlaybackStatus.PREVIOUS;
                default -> PlaybackStatus.QUIT_ALL;
            };
            return new MockPlaybackEngine(seq, p, ctx, vol, speed, start, transpose, status);
        };

        MockMidiProvider provider = new MockMidiProvider();
        PlaybackRunner runner = new PlaybackRunner(
                new PrintStream(outBytes), new PrintStream(errBytes), mockIO, true, factory);

        runner.run(provider, true, Optional.empty(), Optional.empty(),
                List.of(f1, f2, f3), common, List.of());

        assertEquals(3, callCount.get(), "PREVIOUS must go back one track; engine called 3 times");
    }
```

- [ ] **Step 5: Run all new tests**

```bash
./gradlew test --tests "com.fupfin.midiraja.cli.PlaybackRunnerTest" 2>&1 | tail -20
```

Expected: all tests pass, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run the full suite**

```bash
./gradlew test -x jacocoTestReport 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java
git commit -m "test: add playlist loop integration tests using MockPlaybackEngine"
```
