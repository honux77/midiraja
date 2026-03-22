# PlaybackEngineFactory Refactor — Design Spec

## Goal

Extract `PlaybackEngine` construction out of `PlaybackRunner.playPlaylistLoop()` behind a
`@FunctionalInterface` so that the playlist loop can be exercised in tests without starting
real audio threads or requiring MIDI hardware.

## Context

`PlaybackRunner.playPlaylistLoop()` currently contains a single `new PlaybackEngine(...)` call
deep inside its loop. Because `PlaybackEngine.start()` blocks until playback finishes (running
MIDI timing, audio rendering, and UI threads), the loop cannot be tested without a real MIDI
provider and a real audio pipeline.

Extracting engine creation behind a factory allows tests to inject a mock engine that returns
a predetermined `PlaybackStatus` immediately, making the full loop testable as a pure
control-flow unit.

## Architecture

### New file: `PlaybackEngineFactory.java` (engine package)

```java
@FunctionalInterface
public interface PlaybackEngineFactory {
    PlaybackEngine create(Sequence sequence, MidiOutProvider provider,
                          PlaylistContext context,
                          int initialVolumePercent, double initialSpeed,
                          Optional<String> startTimeStr,
                          Optional<Integer> initialTranspose);
}
```

The default production implementation is the constructor reference `PlaybackEngine::new`,
which satisfies this signature exactly.

### Changes to `PlaybackRunner`

**Field:**
```java
private final PlaybackEngineFactory engineFactory;
```

**Existing constructor (unchanged signature — all callers stay the same):**
```java
public PlaybackRunner(PrintStream out, PrintStream err, TerminalIO io, boolean testMode) {
    this(out, err, io, testMode, PlaybackEngine::new);
}
```

**New constructor (test injection point):**
```java
public PlaybackRunner(PrintStream out, PrintStream err, TerminalIO io, boolean testMode,
                      PlaybackEngineFactory engineFactory) {
    // existing field initialisation ...
    this.engineFactory = engineFactory;
}
```

**Single call-site change in `playPlaylistLoop()`:**
```java
// before
var engine = new PlaybackEngine(sequence, provider, context,
        common.volume, common.speed, currentStartTime, common.transpose);

// after
var engine = engineFactory.create(sequence, provider, context,
        common.volume, common.speed, currentStartTime, common.transpose);
```

No other files change. All `*Command` classes construct `PlaybackRunner` via the existing
4-parameter constructor and continue to use the real engine.

## Test Design

### `MockPlaybackEngine` (inner class in test file)

Extends `PlaybackEngine` and overrides `start()` to return a predetermined
`PlaybackStatus` without starting any threads:

```java
class MockPlaybackEngine extends PlaybackEngine {
    private final PlaybackStatus exitStatus;

    MockPlaybackEngine(Sequence seq, MidiOutProvider p, PlaylistContext ctx,
                       int vol, double speed, Optional<String> start,
                       Optional<Integer> transpose, PlaybackStatus exitStatus) {
        super(seq, p, ctx, vol, speed, start, transpose);
        this.exitStatus = exitStatus;
    }

    @Override
    public PlaybackStatus start(PlaybackUI ui) {
        return exitStatus;
    }
}
```

The factory lambda used in tests:

```java
PlaybackEngineFactory factory = (seq, p, ctx, vol, speed, start, transpose) ->
        new MockPlaybackEngine(seq, p, ctx, vol, speed, start, transpose, exitStatus);
```

### Test scenarios (added to `PlaybackRunnerTest.java`)

| Test | Factory exit status | Track index | Loop | Expected outcome |
|------|--------------------:|------------:|-----:|-----------------|
| `finishedStatus_advancesToNextTrack` | `FINISHED` | 0 of 3 | false | loop runs engine twice (track 0 → 1), exits at track 2 |
| `nextStatus_advancesToNextTrack` | `NEXT` | 0 of 3 | false | same as above |
| `quitAll_exitsLoopImmediately` | `QUIT_ALL` | 0 of 3 | false | loop exits after first engine call |
| `finishedAtLastTrack_withLoop_wrapsToFirst` | `FINISHED` (last) | 2 of 3 | true | wraps to track 0 on next iteration (limit iterations to avoid infinite loop) |
| `previousStatus_goesBackOneTrack` | `PREVIOUS` | 1 of 3 | false | engine called for track 0 next |

Each test verifies the **sequence** passed to the factory matches the expected track file, and
the **call count** matches expectations.

## Error Handling

No new error-handling paths are introduced. The factory can throw `Exception` (matching the
existing `throws Exception` on `playPlaylistLoop()`). Mock factories in tests will not throw.

## Scope

- **In scope:** `PlaybackEngineFactory` interface, `PlaybackRunner` constructor + loop change,
  5 new tests.
- **Out of scope:** Command class refactoring (separate sub-project), `PlaylistParser` mutation
  cleanup (separate sub-project), `PlaybackEngine` internal timing abstraction.

## Files Changed

| File | Change |
|------|--------|
| `src/main/java/com/fupfin/midiraja/engine/PlaybackEngineFactory.java` | New — `@FunctionalInterface` |
| `src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java` | Add field, new constructor, replace one `new` call |
| `src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java` | Add `MockPlaybackEngine` + 5 test methods |
