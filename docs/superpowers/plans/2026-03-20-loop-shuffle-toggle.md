# Loop/Shuffle Toggle & PLAYLIST Header Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add live loop/shuffle toggle hotkeys (`L`/`S`) during playback, show state as colored icons (`↺`/`⇆`) in the PLAYLIST header (full mode) and status line (mini mode), and redesign shuffle to use an internal play-order array instead of mutating the file list.

**Architecture:** `PlaybackEngine` gains `volatile` loop/shuffle state with toggle methods and a shuffle callback. `TitledPanel` gains a right-aligned tag slot. `PlaybackRunner` replaces `Collections.shuffle(playlist)` with a `playOrder[]` array that can be reshuffled live via the callback. `LineUI` shows state but suppresses the hotkeys via `handleMiniInput`.

**Tech Stack:** Java 25, JUnit 5, `./gradlew test` to run tests.

**Spec:** `docs/superpowers/specs/2026-03-20-loop-shuffle-toggle-design.md`

---

## File Map

| File | Change |
|------|--------|
| `src/main/java/.../io/TerminalIO.java` | Add `TOGGLE_LOOP`, `TOGGLE_SHUFFLE` to `TerminalKey` enum |
| `src/main/java/.../io/JLineTerminalIO.java` | Bind `l`/`L` → `TOGGLE_LOOP`, `s`/`S` → `TOGGLE_SHUFFLE` |
| `src/main/java/.../engine/PlaybackEngine.java` | Add `loopEnabled`, `shuffleEnabled`, toggle methods, shuffle callback |
| `src/main/java/.../ui/InputHandler.java` | Handle new keys in `handleCommonInput`; add `handleMiniInput` |
| `src/main/java/.../ui/TitledPanel.java` | Add `setRightTag(String, int)`, update `render()` |
| `src/main/java/.../ui/DashboardUI.java` | Replace `playlistTitle()` with `playlistTag()`, update render loop |
| `src/main/java/.../ui/ControlsPanel.java` | Add `[L]Loop [S]Shuf` to all height variants |
| `src/main/java/.../ui/LineUI.java` | Add loop/shuffle indicator; switch to `handleMiniInput` |
| `src/main/java/.../cli/PlaybackRunner.java` | Add `buildPlayOrder`, `reshuffleRemaining`; remove `Collections.shuffle` |
| `src/test/java/.../ui/DashboardUITest.java` | Replace `playlistTitle_*` with `playlistTag_*` tests |
| `src/test/java/.../engine/PlaybackEngineTest.java` | Add toggle + callback tests |
| `src/test/java/.../cli/PlaybackRunnerTest.java` | Add `buildPlayOrder` + `reshuffleRemaining` tests |

Base package path: `com/fupfin/midiraja`

---

## Task 1: PlaybackEngine — loop/shuffle toggle state

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/engine/PlaybackEngine.java`
- Test: `src/test/java/com/fupfin/midiraja/engine/PlaybackEngineTest.java`

- [ ] **Step 1: Write failing tests**

Add to `PlaybackEngineTest.java`:

```java
@Test void testToggleLoop() {
    PlaybackEngine engine = new PlaybackEngine(
        mockSequence, mockProvider, ctx(), 100, 1.0, Optional.empty(), Optional.empty());

    assertTrue(engine.isLoopEnabled() == false); // default: loop off
    engine.toggleLoop();
    assertTrue(engine.isLoopEnabled());
    engine.toggleLoop();
    assertFalse(engine.isLoopEnabled());
}

@Test void testLoopInitializedFromContext() {
    var ctxWithLoop = new PlaylistContext(
        List.of(new File("test.mid")), 0, new MidiPort(0, "Mock"), null, true, false);
    PlaybackEngine engine = new PlaybackEngine(
        mockSequence, mockProvider, ctxWithLoop, 100, 1.0, Optional.empty(), Optional.empty());
    assertTrue(engine.isLoopEnabled());
}

@Test void testToggleShuffle() {
    PlaybackEngine engine = new PlaybackEngine(
        mockSequence, mockProvider, ctx(), 100, 1.0, Optional.empty(), Optional.empty());

    assertFalse(engine.isShuffleEnabled());
    engine.toggleShuffle();
    assertTrue(engine.isShuffleEnabled());
}

@Test void testShuffleCallbackFired() {
    PlaybackEngine engine = new PlaybackEngine(
        mockSequence, mockProvider, ctx(), 100, 1.0, Optional.empty(), Optional.empty());

    List<Boolean> received = new ArrayList<>();
    engine.setShuffleCallback(received::add);

    engine.toggleShuffle(); // false → true
    engine.toggleShuffle(); // true → false

    assertEquals(List.of(true, false), received);
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "com.fupfin.midiraja.engine.PlaybackEngineTest.testToggleLoop" 2>&1 | tail -5
```
Expected: FAIL — `isLoopEnabled()` method not found.

- [ ] **Step 3: Add fields and methods to PlaybackEngine**

In `PlaybackEngine.java`, after the existing `private volatile boolean bookmarked = false;` line (~line 57), add:

```java
private volatile boolean loopEnabled = false;
private volatile boolean shuffleEnabled = false;
@SuppressWarnings("NullAway")
private volatile java.util.function.Consumer<Boolean> shuffleCallback = null;
```

At the end of the constructor (after `this.context = context;` on ~line 120), add:

```java
this.loopEnabled = context.loop();
this.shuffleEnabled = context.shuffle();
```

After the existing `setHoldAtEnd` method (~line 85), add:

```java
public void toggleLoop() { loopEnabled = !loopEnabled; }
public boolean isLoopEnabled() { return loopEnabled; }

public void toggleShuffle()
{
    shuffleEnabled = !shuffleEnabled;
    var cb = shuffleCallback;
    if (cb != null) cb.accept(shuffleEnabled);
}
public boolean isShuffleEnabled() { return shuffleEnabled; }
public void setShuffleCallback(java.util.function.Consumer<Boolean> cb) { shuffleCallback = cb; }
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.fupfin.midiraja.engine.PlaybackEngineTest" 2>&1 | tail -10
```
Expected: All `PlaybackEngineTest` tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/engine/PlaybackEngine.java \
        src/test/java/com/fupfin/midiraja/engine/PlaybackEngineTest.java
git commit -m "feat: add loop/shuffle toggle state to PlaybackEngine"
```

---

## Task 2: Key bindings + InputHandler

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/io/TerminalIO.java`
- Modify: `src/main/java/com/fupfin/midiraja/io/JLineTerminalIO.java`
- Modify: `src/main/java/com/fupfin/midiraja/ui/InputHandler.java`

No new tests needed for this task — the enum values and key wiring are integration-level; engine tests in Task 1 already cover toggle dispatch.

- [ ] **Step 1: Add TOGGLE_LOOP and TOGGLE_SHUFFLE to TerminalKey**

In `TerminalIO.java`, the `TerminalKey` enum currently ends with `RESUME_SESSION`. Change:

```java
// Before:
NONE, SEEK_FORWARD, SEEK_BACKWARD, VOLUME_UP, VOLUME_DOWN, SPEED_UP, SPEED_DOWN,
TRANSPOSE_UP, TRANSPOSE_DOWN, NEXT_TRACK, PREV_TRACK, QUIT, PAUSE, BOOKMARK, RESUME_SESSION

// After:
NONE, SEEK_FORWARD, SEEK_BACKWARD, VOLUME_UP, VOLUME_DOWN, SPEED_UP, SPEED_DOWN,
TRANSPOSE_UP, TRANSPOSE_DOWN, NEXT_TRACK, PREV_TRACK, QUIT, PAUSE, BOOKMARK,
RESUME_SESSION, TOGGLE_LOOP, TOGGLE_SHUFFLE
```

- [ ] **Step 2: Bind l/L and s/S in JLineTerminalIO**

In `JLineTerminalIO.buildKeyMap()`, after the `km.bind(TerminalKey.RESUME_SESSION, "r", "R");` line (~line 82), add:

```java
km.bind(TerminalKey.TOGGLE_LOOP,    "l", "L");
km.bind(TerminalKey.TOGGLE_SHUFFLE, "s", "S");
```

- [ ] **Step 3: Handle new keys in InputHandler**

In `InputHandler.handleCommonInput()`, after the `case BOOKMARK -> engine.fireBookmark();` line, add:

```java
case TOGGLE_LOOP -> engine.toggleLoop();
case TOGGLE_SHUFFLE -> engine.toggleShuffle();
```

Then add a new static method after `handleCommonInput`:

```java
public static void handleMiniInput(PlaybackEngine engine, TerminalKey key)
{
    switch (key)
    {
        case TOGGLE_LOOP, TOGGLE_SHUFFLE ->
        {
        } // not available in mini mode
        default -> handleCommonInput(engine, key);
    }
}
```

- [ ] **Step 4: Run all tests to confirm nothing broke**

```bash
./gradlew test 2>&1 | tail -10
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/io/TerminalIO.java \
        src/main/java/com/fupfin/midiraja/io/JLineTerminalIO.java \
        src/main/java/com/fupfin/midiraja/ui/InputHandler.java
git commit -m "feat: add TOGGLE_LOOP/TOGGLE_SHUFFLE key bindings (l/L, s/S)"
```

---

## Task 3: TitledPanel — right tag support

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/ui/TitledPanel.java`
- Test: `src/test/java/com/fupfin/midiraja/ui/TitledPanelTest.java` (create new)

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/fupfin/midiraja/ui/TitledPanelTest.java`:

```java
package com.fupfin.midiraja.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TitledPanelTest {

    private static final Panel EMPTY_PANEL = new Panel() {
        @Override public void render(ScreenBuffer b) {}
        @Override public void onLayoutUpdated(LayoutConstraints c) {}
        @Override public void onPlaybackStateChanged() {}
        @Override public void onTick(long m) {}
        @Override public void onTempoChanged(float b) {}
        @Override public void onChannelActivity(int c, int v) {}
    };

    @Test void noTag_rendersTraditionalHeader() {
        TitledPanel panel = new TitledPanel("PLAYLIST", EMPTY_PANEL, true); // noBottomBorder
        panel.onLayoutUpdated(new LayoutConstraints(30, 1, false, false));
        ScreenBuffer buf = new ScreenBuffer();
        panel.render(buf);
        String line = buf.toString().split("\n")[0];
        // " ≡≡[ PLAYLIST ]" = 16 chars, then 13 ≡, then " "  → total 30
        assertTrue(line.startsWith(" ≡≡[ PLAYLIST ]"));
        assertTrue(line.endsWith(" "));
        assertEquals(30, line.length());
    }

    @Test void withTag_placesTagBeforeTrailing2EquivAndSpace() {
        TitledPanel panel = new TitledPanel("PLAYLIST", EMPTY_PANEL, true);
        panel.setRightTag("AB", 2); // visible length = 2
        panel.onLayoutUpdated(new LayoutConstraints(30, 1, false, false));
        ScreenBuffer buf = new ScreenBuffer();
        panel.render(buf);
        String line = buf.toString().split("\n")[0];
        // " ≡≡[ PLAYLIST ]" (16) + padding≡ + "AB" + "≡≡" + " " = 30
        // padding = 30 - 16 - 2 - 3 = 9
        assertTrue(line.startsWith(" ≡≡[ PLAYLIST ]"));
        assertTrue(line.endsWith("AB≡≡ "));
        assertEquals(30, line.length());
    }

    @Test void withTag_tooNarrow_paddingClampsToZero() {
        TitledPanel panel = new TitledPanel("PLAYLIST", EMPTY_PANEL, true);
        panel.setRightTag("AB", 2);
        // width=18: header=16, tag=2, suffix=3 → padding = 18-16-2-3 = -3 → clamped to 0
        panel.onLayoutUpdated(new LayoutConstraints(18, 1, false, false));
        ScreenBuffer buf = new ScreenBuffer();
        // Should not throw
        assertDoesNotThrow(() -> panel.render(buf));
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "com.fupfin.midiraja.ui.TitledPanelTest" 2>&1 | tail -5
```
Expected: FAIL — `setRightTag` method not found.

- [ ] **Step 3: Add right tag fields and method to TitledPanel**

In `TitledPanel.java`, after the `private final boolean noBottomBorder;` field (~line 15), add:

```java
private String rightTag = "";
private int rightTagVisibleLength = 0;
```

After the second constructor (ending around line 29), add:

```java
public void setRightTag(String tag, int visibleLength)
{
    this.rightTag = tag;
    this.rightTagVisibleLength = visibleLength;
}
```

- [ ] **Step 4: Update render() to use right tag**

In `TitledPanel.render()`, replace the header-drawing section (currently lines 77–80):

```java
// Before:
String header = " ≡≡[ " + title + " ]";
int padding = Math.max(0, constraints.width() - header.length() - 1);
buffer.append(header).append("≡".repeat(padding)).append(" \n");

// After:
String header = " ≡≡[ " + title + " ]";
if (rightTagVisibleLength > 0)
{
    int padding = Math.max(0,
            constraints.width() - header.length() - rightTagVisibleLength - 3);
    buffer.append(header).append("≡".repeat(padding))
          .append(rightTag).append("≡≡").append(" \n");
}
else
{
    int padding = Math.max(0, constraints.width() - header.length() - 1);
    buffer.append(header).append("≡".repeat(padding)).append(" \n");
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.fupfin.midiraja.ui.TitledPanelTest" 2>&1 | tail -10
```
Expected: All 3 `TitledPanelTest` tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/ui/TitledPanel.java \
        src/test/java/com/fupfin/midiraja/ui/TitledPanelTest.java
git commit -m "feat: add right tag slot to TitledPanel header line"
```

---

## Task 4: DashboardUI + ControlsPanel — full mode display

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/ui/DashboardUI.java`
- Modify: `src/main/java/com/fupfin/midiraja/ui/ControlsPanel.java`
- Modify: `src/test/java/com/fupfin/midiraja/ui/DashboardUITest.java`

- [ ] **Step 1: Update DashboardUITest — replace playlistTitle tests with playlistTag tests**

In `DashboardUITest.java`, replace all four existing `playlistTitle_*` tests with:

```java
@Test void playlistTag_bothOff_bothDim() {
    String tag = DashboardUI.playlistTag(false, false);
    assertTrue(tag.contains(Theme.COLOR_DIM_FG + "↺"));
    assertTrue(tag.contains(Theme.COLOR_DIM_FG + "⇆"));
    assertFalse(tag.contains(Theme.COLOR_HIGHLIGHT + "↺"));
    assertFalse(tag.contains(Theme.COLOR_HIGHLIGHT + "⇆"));
}

@Test void playlistTag_loopOnly_loopAmber() {
    String tag = DashboardUI.playlistTag(true, false);
    assertTrue(tag.contains(Theme.COLOR_HIGHLIGHT + "↺"));
    assertTrue(tag.contains(Theme.COLOR_DIM_FG + "⇆"));
}

@Test void playlistTag_shuffleOnly_shuffleAmber() {
    String tag = DashboardUI.playlistTag(false, true);
    assertTrue(tag.contains(Theme.COLOR_DIM_FG + "↺"));
    assertTrue(tag.contains(Theme.COLOR_HIGHLIGHT + "⇆"));
}

@Test void playlistTag_both_bothAmber() {
    String tag = DashboardUI.playlistTag(true, true);
    assertTrue(tag.contains(Theme.COLOR_HIGHLIGHT + "↺"));
    assertTrue(tag.contains(Theme.COLOR_HIGHLIGHT + "⇆"));
}
```

Also add an import at the top if not already present:
```java
import com.fupfin.midiraja.ui.Theme;
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "com.fupfin.midiraja.ui.DashboardUITest" 2>&1 | tail -5
```
Expected: FAIL — `playlistTag` method not found (and old tests may fail since the method signature is changing).

- [ ] **Step 3: Update DashboardUI**

In `DashboardUI.java`:

**a) Replace `playlistTitle` with `playlistTag`** (currently at ~line 54).
The new method must be **package-private** (no access modifier) so `DashboardUITest` can call it:

```java
// Remove:
static String playlistTitle(boolean loop, boolean shuffle)
{
    var sb = new StringBuilder("PLAYLIST");
    if (loop)    sb.append(" \u21A9");
    if (shuffle) sb.append(" \u21C4");
    return sb.toString();
}

// Add:
static String playlistTag(boolean loopActive, boolean shuffleActive)
{
    String loopIcon    = (loopActive    ? Theme.COLOR_HIGHLIGHT : Theme.COLOR_DIM_FG) + "↺" + Theme.COLOR_RESET;
    String shuffleIcon = (shuffleActive ? Theme.COLOR_HIGHLIGHT : Theme.COLOR_DIM_FG) + "⇆" + Theme.COLOR_RESET;
    return loopIcon + shuffleIcon;
}
```

**b) Remove the one-time `setTitle` call before the render loop** (~line 77):

```java
// Remove this line:
titledPlaylistPanel.setTitle(playlistTitle(ctx.loop(), ctx.shuffle()));
```

**c) Inside the render loop, add `setRightTag` call each frame** — add just before `titledNowPlayingPanel.render(buffer);` (~line 115):

```java
titledPlaylistPanel.setRightTag(playlistTag(engine.isLoopEnabled(), engine.isShuffleEnabled()), 2);
```

- [ ] **Step 4: Update ControlsPanel**

In `ControlsPanel.java`, replace the entire `render()` method body with:

```java
@Override
public void render(ScreenBuffer buffer)
{
    if (constraints.height() <= 0) return;

    String minLine = "[Spc]Pause [▲▼]Skip [◀▶]Seek [+-]Vol [<>]Tempo [/']Trans [L]Loop [S]Shuf [*]Save [Q]Quit";

    if (constraints.height() >= 3)
    {
        buffer.append("[Spc] Pause  [◀ ▶] Seek  [▲ ▼] Skip  [+-] Vol  [< >] Tempo  [/ '] Trans\n");
        buffer.append("[L] Loop  [S] Shuffle  [*] Save  [R] Resume Session  [Q] Quit\n");
    }
    else if (constraints.height() == 2)
    {
        buffer.append("[Spc]Pause [◀▶]Seek [▲▼]Skip [+-]Vol [<>]Tempo [/']Trans [L]Loop [S]Shuf [*]Bkm [R]Resume [Q]Quit\n");
    }
    else
    {
        buffer.append(truncate(minLine.trim(), constraints.width())).append("\n");
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.fupfin.midiraja.ui.DashboardUITest" 2>&1 | tail -10
```
Expected: All 4 `DashboardUITest` tests PASS.

- [ ] **Step 6: Run all tests**

```bash
./gradlew test 2>&1 | tail -10
```
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/ui/DashboardUI.java \
        src/main/java/com/fupfin/midiraja/ui/ControlsPanel.java \
        src/test/java/com/fupfin/midiraja/ui/DashboardUITest.java
git commit -m "feat: show live loop/shuffle icons in PLAYLIST header and controls"
```

---

## Task 5: LineUI — mini mode state display + input restriction

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/ui/LineUI.java`

No new tests: `LineUI` is a render loop that requires a real terminal; testing is manual.

- [ ] **Step 1: Add loop/shuffle indicator to the dynamic status line**

In `LineUI.java`, inside the `while (engine.isPlaying())` loop, after the `buffer.append(...)` call that appends the status string (~line 99–101), add:

```java
String loopIcon    = engine.isLoopEnabled()
        ? Theme.COLOR_HIGHLIGHT + "↺" + Theme.COLOR_RESET
        : Theme.COLOR_DIM_FG + "↺" + Theme.COLOR_RESET;
String shuffleIcon = engine.isShuffleEnabled()
        ? Theme.COLOR_HIGHLIGHT + "⇆" + Theme.COLOR_RESET
        : Theme.COLOR_DIM_FG + "⇆" + Theme.COLOR_RESET;
buffer.append(" ").append(loopIcon).append(shuffleIcon);
```

- [ ] **Step 2: Switch runInputLoop to use handleMiniInput**

In `LineUI.java`, change `runInputLoop`:

```java
// Before:
@Override
public void runInputLoop(PlaybackEngine engine)
{
    InputLoopRunner.run(engine, InputHandler::handleCommonInput);
}

// After:
@Override
public void runInputLoop(PlaybackEngine engine)
{
    InputLoopRunner.run(engine, InputHandler::handleMiniInput);
}
```

- [ ] **Step 3: Run all tests to confirm nothing broke**

```bash
./gradlew test 2>&1 | tail -10
```
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/ui/LineUI.java
git commit -m "feat: show loop/shuffle state in LineUI mini mode (no toggle)"
```

---

## Task 6: PlaybackRunner — play order redesign

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java`
- Test: `src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java`

- [ ] **Step 1: Write failing tests for buildPlayOrder and reshuffleRemaining**

Add to `PlaybackRunnerTest.java`:

```java
@Test void buildPlayOrder_notShuffled_isSequential() {
    int[] order = PlaybackRunner.buildPlayOrder(4, false);
    assertArrayEquals(new int[]{0, 1, 2, 3}, order);
}

@Test void buildPlayOrder_shuffled_containsAllIndices() {
    int[] order = PlaybackRunner.buildPlayOrder(5, true);
    assertEquals(5, order.length);
    // All indices 0-4 present
    int sum = 0;
    for (int v : order) sum += v;
    assertEquals(0 + 1 + 2 + 3 + 4, sum);
}

@Test void buildPlayOrder_sizeZero_returnsEmpty() {
    assertArrayEquals(new int[0], PlaybackRunner.buildPlayOrder(0, false));
    assertArrayEquals(new int[0], PlaybackRunner.buildPlayOrder(0, true));
}

@Test void buildPlayOrder_sizeOne_returnsSingleElement() {
    assertArrayEquals(new int[]{0}, PlaybackRunner.buildPlayOrder(1, true));
}

@Test void reshuffleRemaining_shuffleOn_remainingNotInOriginalOrder() {
    // Use a large enough slice that the probability of staying sorted is negligible
    int[] order = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    PlaybackRunner.reshuffleRemaining(order, 0, true);
    // The remaining slice [1..9] should not be in original order (with overwhelming probability)
    boolean stillSorted = true;
    for (int i = 1; i < order.length - 1; i++) {
        if (order[i] > order[i + 1]) { stillSorted = false; break; }
    }
    // index 0 is untouched
    assertEquals(0, order[0]);
    // All values 1-9 still present
    int sum = 0;
    for (int i = 1; i < order.length; i++) sum += order[i];
    assertEquals(1+2+3+4+5+6+7+8+9, sum);
    // At least one element is out of order (not all 9 in sequence)
    assertFalse(stillSorted, "Shuffled slice should not remain sorted");
}

@Test void reshuffleRemaining_shuffleOff_restoresAscendingOrder() {
    int[] order = {0, 4, 2, 1, 3}; // remaining [1..4] is unsorted
    PlaybackRunner.reshuffleRemaining(order, 0, false);
    assertArrayEquals(new int[]{0, 1, 2, 3, 4}, order);
}

@Test void reshuffleRemaining_atLastTrack_isNoOp() {
    int[] order = {0, 1, 2};
    int[] before = order.clone();
    PlaybackRunner.reshuffleRemaining(order, 2, true); // currentIdx = last
    assertArrayEquals(before, order);
}

@Test void reshuffleRemaining_idempotentSortOff() {
    int[] order = {0, 1, 2, 3}; // already sorted
    PlaybackRunner.reshuffleRemaining(order, 0, false);
    assertArrayEquals(new int[]{0, 1, 2, 3}, order);
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "com.fupfin.midiraja.cli.PlaybackRunnerTest.buildPlayOrder_notShuffled_isSequential" 2>&1 | tail -5
```
Expected: FAIL — `buildPlayOrder` method not found.

- [ ] **Step 3: Add buildPlayOrder and reshuffleRemaining as package-private static methods**

In `PlaybackRunner.java`, add after the `handlePlaybackStatus` method (end of file, ~line 519):

```java
static int[] buildPlayOrder(int size, boolean shuffle)
{
    int[] order = new int[size];
    for (int i = 0; i < size; i++) order[i] = i;
    if (shuffle)
    {
        var rng = new java.util.Random();
        for (int i = size - 1; i > 0; i--)
        {
            int j = rng.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
    }
    return order;
}

static void reshuffleRemaining(int[] playOrder, int currentIdx, boolean shuffleOn)
{
    int start = currentIdx + 1;
    int end = playOrder.length;
    if (start >= end) return;
    if (shuffleOn)
    {
        var rng = new java.util.Random();
        for (int i = end - 1; i > start; i--)
        {
            int j = start + rng.nextInt(i - start + 1);
            int tmp = playOrder[i];
            playOrder[i] = playOrder[j];
            playOrder[j] = tmp;
        }
    }
    else
    {
        java.util.Arrays.sort(playOrder, start, end);
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.fupfin.midiraja.cli.PlaybackRunnerTest" 2>&1 | tail -10
```
Expected: All `PlaybackRunnerTest` tests PASS (existing + new).

- [ ] **Step 5: Remove Collections.shuffle from run() and handlePlaybackStatus**

In `PlaybackRunner.run()`, remove (~line 137–140):
```java
// Remove entirely:
if (common.shuffle)
{
    Collections.shuffle(playlist);
}
```

In `PlaybackRunner.handlePlaybackStatus()`, in the `FINISHED` case, remove:
```java
// Remove:
if (common.shuffle) Collections.shuffle(playlist);
```

The `FINISHED` case becomes:
```java
case FINISHED ->
{
    int next = currentTrackIdx + 1;
    if (next >= playlist.size())
    {
        if (common.loop)
        {
            yield 0;
        }
        yield next; // exits the loop
    }
    yield next;
}
```

- [ ] **Step 6: Rewrite playPlaylistLoop to use playOrder**

In `PlaybackRunner.playPlaylistLoop()`, replace the current method body with the following (keep the method signature unchanged):

```java
private void playPlaylistLoop(List<File> playlist, MidiOutProvider provider, MidiPort port,
        CommonOptions common, PlaybackUI ui, TerminalIO activeIO,
        Optional<String> initialStartTime, List<String> originalArgs) throws Exception
{
    // Use int[][] so the lambda can capture playOrderHolder (effectively final reference)
    // while playOrderHolder[0] can be reassigned on loop wrap-around.
    int[][] playOrderHolder = { buildPlayOrder(playlist.size(), common.shuffle) };
    int[] currentIdxHolder = {0};
    Optional<String> currentStartTime = initialStartTime;
    boolean wasPaused = false;
    PlaybackStatus prevStatus = PlaybackStatus.FINISHED;

    while (currentIdxHolder[0] >= 0 && currentIdxHolder[0] < playlist.size())
    {
        var file = playlist.get(playOrderHolder[0][currentIdxHolder[0]]);
        try
        {
            var sequence = MidiUtils.loadSequence(file);
            logVerbose(common.isVerbose(),
                    String.format("Loaded '%s' - Resolution: %d PPQ, Microsecond Length: %d",
                            file.getName(), sequence.getResolution(),
                            sequence.getMicrosecondLength()));

            var title = MidiUtils.extractSequenceTitle(sequence);

            // Build ordered file view for display (snapshot at track-start time)
            List<File> orderedFiles =
                    java.util.stream.IntStream.of(playOrderHolder[0]).mapToObj(playlist::get).toList();
            var context = new PlaylistContext(orderedFiles, currentIdxHolder[0], port, title,
                    common.loop, common.shuffle);

            var engine = new PlaybackEngine(sequence, provider, context, common.volume,
                    common.speed, currentStartTime, common.transpose);

            if (common.ignoreSysex) engine.setIgnoreSysex(true);
            if (common.resetType.isPresent()) engine.setInitialResetType(common.resetType);
            if (wasPaused) engine.setInitiallyPaused();

            boolean initiallyBookmarked =
                    !originalArgs.isEmpty() && new SessionHistory().isBookmarked(originalArgs);
            engine.setBookmarked(initiallyBookmarked);
            engine.setBookmarkCallback(bookmarked -> {
                var h = new SessionHistory();
                if (bookmarked) {
                    h.saveBookmark(originalArgs);
                } else {
                    h.removeBookmarkByArgs(originalArgs);
                }
            });

            // Wire shuffle callback: mutates remaining slice of playOrderHolder[0] live
            engine.setShuffleCallback(
                    newState -> reshuffleRemaining(playOrderHolder[0], currentIdxHolder[0], newState));

            boolean isLastTrack = (currentIdxHolder[0] == playlist.size() - 1);
            if (!suppressHoldAtEnd && isLastTrack && !common.loop && (ui instanceof DashboardUI))
            {
                engine.setHoldAtEnd(true);
            }

            var status = ScopedValue.where(TerminalIO.CONTEXT, activeIO).call(() -> engine.start(ui));
            lastRawStatus = status;
            prevStatus = status;

            currentStartTime = Optional.empty();
            common.volume = (int) (engine.getVolumeScale() * 100);
            common.speed = engine.getCurrentSpeed();
            common.transpose = Optional.of(engine.getCurrentTranspose());
            common.loop = engine.isLoopEnabled();
            common.shuffle = engine.isShuffleEnabled();
            wasPaused = engine.isPaused();

            int nextIdx = handlePlaybackStatus(status, currentIdxHolder[0], playlist, common);

            // Rebuild play order when loop wraps around to the beginning
            if (prevStatus == PlaybackStatus.FINISHED && nextIdx == 0
                    && currentIdxHolder[0] == playlist.size() - 1)
            {
                playOrderHolder[0] = buildPlayOrder(playlist.size(), engine.isShuffleEnabled());
            }

            currentIdxHolder[0] = nextIdx;
        }
        catch (Exception e)
        {
            err.println("\n[Error] Failed to load MIDI file: " + file.getName());
            err.println("        Reason: " + e.getMessage());
            err.println("        Skipping to the next track...");
            currentIdxHolder[0]++;
        }
    }
}
```

- [ ] **Step 7: Remove unused Collections import if needed**

Check if `Collections` is still used elsewhere in `PlaybackRunner.java`:

```bash
grep -n "Collections" src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java
```

If the only remaining use was `Collections.shuffle`, remove the import line:
```java
// Remove if unused:
import java.util.Collections;
```

- [ ] **Step 8: Run all tests**

```bash
./gradlew test 2>&1 | tail -15
```
Expected: All tests PASS.

- [ ] **Step 9: Commit everything**

```bash
git add src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java \
        src/test/java/com/fupfin/midiraja/cli/PlaybackRunnerTest.java \
        docs/superpowers/specs/2026-03-20-loop-shuffle-toggle-design.md
git commit -m "feat: replace Collections.shuffle with live play-order for shuffle toggle"
```

---

## Final verification

- [ ] **Run full test suite**

```bash
./gradlew test 2>&1 | tail -20
```
Expected: All tests PASS, no failures.

- [ ] **Manual smoke test (if MIDI available)**

```bash
# Full mode — verify L and S keys toggle icons in PLAYLIST header
midra <file.mid>

# Mini mode — verify icons shown but L/S have no effect
midra --mini <file.mid>

# CLI flag still works as initial state
midra --loop --shuffle <file.mid>
```
