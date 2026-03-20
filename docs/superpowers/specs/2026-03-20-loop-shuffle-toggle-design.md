# Loop/Shuffle Toggle & PLAYLIST Header Icons Design

## Overview

Three related features that improve playback control during live playback:
1. PLAYLIST header icons — loop/shuffle state always visible, right-aligned
2. Loop toggle hotkey — toggle loop on/off during playback with `L`
3. Shuffle redesign — internal play order instead of pre-shuffling; toggleable with `S`

---

## Feature 1: PLAYLIST Header Icons

### Visual Design

The PLAYLIST panel header always shows both loop (`↺` U+21BA) and shuffle (`⇆` U+21C6)
indicators, right-aligned with two `≡` characters and one trailing space after them:

```
" ≡≡[ PLAYLIST ]≡≡≡≡≡≡≡≡≡≡≡≡≡↺⇆≡≡ \n"
```

- **Active**: `Theme.COLOR_HIGHLIGHT` (amber, `\033[38;5;215m`)
- **Inactive**: `Theme.COLOR_DIM_FG` (relative dim, `\033[2m`) — safe on both dark and light terminals
- Both icons always rendered; only ANSI color changes per state

When only loop is active:
```
≡≡[ PLAYLIST ]≡≡≡≡≡≡≡≡≡≡≡≡≡[amber:↺][dim:⇆]≡≡
```

### Width Calculation

```
padding = Math.max(0, width - header.length() - visibleTagLength - 3)
// -3 = two trailing ≡ characters + one trailing space
// visibleTagLength = 2 (always: ↺ is 1 char, ⇆ is 1 char)
```

When `padding` is 0 (terminal too narrow), the tag still renders but may visually overlap
the header bracket. This is acceptable — the header is already unreadable at that width.
The `Math.max(0, ...)` guard prevents negative repeat counts.

When `rightTag` is empty, existing behavior is preserved:
```
padding = Math.max(0, width - header.length() - 1)
```

### Implementation

**`TitledPanel`**:
- Add `private String rightTag = ""`
- Add `private int rightTagVisibleLength = 0`
- Add `public void setRightTag(String tag, int visibleLength)` method
- Update `render()`: if `rightTagVisibleLength > 0`, use tag formula; else use existing formula

Render assembly when tag is present (pseudocode):
```java
String header = " ≡≡[ " + title + " ]";
int padding = Math.max(0, constraints.width() - header.length() - rightTagVisibleLength - 3);
buffer.append(header)
      .append("≡".repeat(padding))
      .append(rightTag)         // ANSI-colored icons, visibleLength = 2
      .append("≡≡")             // two fixed trailing ≡ characters
      .append(" \n");           // one trailing space
```

**`DashboardUI`**:
- Remove `static String playlistTitle(boolean loop, boolean shuffle)` helper (icon change: loop `↩`→`↺`, shuffle `⇄`→`⇆` — circular arrow for loop, bidirectional swap for shuffle)
- Remove `titledPlaylistPanel.setTitle(...)` call before render loop (title stays `"PLAYLIST"` permanently)
- Add `private static String playlistTag(boolean loopActive, boolean shuffleActive)`:

```java
private static String playlistTag(boolean loopActive, boolean shuffleActive) {
    String loopIcon    = (loopActive    ? Theme.COLOR_HIGHLIGHT : Theme.COLOR_DIM_FG) + "↺" + Theme.COLOR_RESET;
    String shuffleIcon = (shuffleActive ? Theme.COLOR_HIGHLIGHT : Theme.COLOR_DIM_FG) + "⇆" + Theme.COLOR_RESET;
    return loopIcon + shuffleIcon;
}
// visibleLength is always 2 (↺ + ⇆, each 1 column wide)
```

- Inside render loop, each frame:
```java
titledPlaylistPanel.setRightTag(
    playlistTag(engine.isLoopEnabled(), engine.isShuffleEnabled()), 2);
```

**`DashboardUITest`**:
- Remove existing `playlistTitle_*` tests (method is gone)
- Add tests for `playlistTag()`: verify ANSI sequences contain `COLOR_HIGHLIGHT` when active, `COLOR_DIM_FG` when inactive

---

## Feature 2: Loop Toggle Hotkey

### Behavior
- Press `L` / `l` during playback → loop toggles on/off immediately
- Header icon color updates within 50ms (next render frame)
- After current track ends, `PlaybackRunner` uses the new loop state for playlist navigation
- Initial value comes from `context.loop()` (set by CLI `--loop` flag)

### Key Binding
- `TerminalKey.TOGGLE_LOOP` added to `TerminalIO.TerminalKey` enum
- `l` / `L` bound in `JLineTerminalIO.buildKeyMap()`

### Engine Changes (`PlaybackEngine`)
- Add `private volatile boolean loopEnabled` initialized from `context.loop()` in constructor
- Add `public void toggleLoop() { loopEnabled = !loopEnabled; }`
- Add `public boolean isLoopEnabled() { return loopEnabled; }`

### Input Handling
- `InputHandler.handleCommonInput()`: `case TOGGLE_LOOP -> engine.toggleLoop()`

### PlaybackRunner
- After `engine.start()` returns: `common.loop = engine.isLoopEnabled()`
- `handlePlaybackStatus()` uses `common.loop` — no change required here

### DashboardUI
- Render loop reads `engine.isLoopEnabled()` for the tag (see Feature 1)

---

## Feature 3: Shuffle Toggle Hotkey

### Behavior
- Press `S` / `s` during playback → shuffle toggles on/off
- Header icon color updates within 50ms (next render frame)
- Remaining unplayed tracks in current loop are **immediately** reordered:
  - Toggle ON → remaining tracks (after current) are Fisher-Yates shuffled
  - Toggle OFF → remaining tracks restored to ascending original-file-index order

### Key Binding
- `TerminalKey.TOGGLE_SHUFFLE` added to `TerminalIO.TerminalKey` enum
- `s` / `S` bound in `JLineTerminalIO.buildKeyMap()`

### Engine Changes (`PlaybackEngine`)
- Add `private volatile boolean shuffleEnabled` initialized from `context.shuffle()`
- Add `private volatile Consumer<Boolean> shuffleCallback = null` (suppress NullAway with `@SuppressWarnings`)
- Add `public void toggleShuffle() { shuffleEnabled = !shuffleEnabled; if (shuffleCallback != null) shuffleCallback.accept(shuffleEnabled); }`
- Add `public boolean isShuffleEnabled() { return shuffleEnabled; }`
- Add `public void setShuffleCallback(Consumer<Boolean> cb) { shuffleCallback = cb; }`

### Input Handling
- `InputHandler.handleCommonInput()`: `case TOGGLE_SHUFFLE -> engine.toggleShuffle()`

### PlaybackRunner: Play Order Redesign

**Remove** `Collections.shuffle(playlist)` at startup and in `handlePlaybackStatus`.

**Add** play-order management in `playPlaylistLoop`:

```java
// Use int[] wrapper so the lambda can capture it (effectively final reference)
int[] playOrder = buildPlayOrder(playlist.size(), common.shuffle);
int[] currentIdxHolder = { 0 };  // wrapper for lambda capture

// ... inside the loop, use playOrder[currentIdxHolder[0]] as the actual track index
File file = playlist.get(playOrder[currentIdxHolder[0]]);

engine.setShuffleCallback(newState ->
    reshuffleRemaining(playOrder, currentIdxHolder[0], newState));
```

The `int[] currentIdxHolder` wrapper is updated each iteration:
```java
currentIdxHolder[0] = handlePlaybackStatus(...);
```

`playOrder` is a shared reference captured by the lambda. `reshuffleRemaining` mutates
only indices `[currentIdxHolder[0]+1 .. size-1]`. The playlist thread reads only
`playOrder[currentIdxHolder[0]]` at track-start time (engine is mid-playback when the
callback fires). This narrow race window is safe: by the time the playlist thread
advances to `currentIdxHolder[0]+1`, the mutation is complete.

**`buildPlayOrder(int size, boolean shuffle)`**:
- Returns `int[]` of length `size`
- If not shuffle: `[0, 1, 2, ..., size-1]`
- If shuffle: Fisher-Yates shuffle of above
- If `size == 0`: returns `int[0]` (empty array — playlist loop guard handles this)
- If `size == 1`: returns `[0]` — shuffle is a no-op on one element

**`reshuffleRemaining(int[] playOrder, int currentIdx, boolean shuffleOn)`**:
- Operates on slice `[currentIdx+1 .. playOrder.length-1]`
- If `shuffleOn`: Fisher-Yates shuffle the slice in-place
- If `shuffleOff`: sort the slice ascending (restores original file-index order)
- If slice is empty (currentIdx == size-1 or size == 0): no-op

**After `engine.start()` returns**:
```java
common.shuffle = engine.isShuffleEnabled();
```

**On loop wrap-around** (inside `playPlaylistLoop`):

After `handlePlaybackStatus` returns the next index, detect loop wrap-around by tracking
the previous status:

```java
PlaybackStatus prevStatus = PlaybackStatus.FINISHED; // initialized before the loop

// At top of each iteration, after updating currentIdxHolder[0]:
if (prevStatus == PlaybackStatus.FINISHED && currentIdxHolder[0] == 0) {
    // Loop wrapped around — rebuild play order for the new loop
    playOrder = buildPlayOrder(playlist.size(), engine.isShuffleEnabled());
}
prevStatus = status; // update after engine.start() returns
```

`handlePlaybackStatus` remains a pure function. The condition
`prevStatus == FINISHED && currentIdx == 0` uniquely identifies loop wrap-around:
PREVIOUS from track 0 yields `playlist.size()-1` (not 0), and NEXT past the last track
with loop off yields `playlist.size()` (exits the loop). Only `FINISHED` at the last track
with `common.loop == true` returns 0.

**`PlaylistContext` construction** — built once per iteration at track-load time (before `engine.start()`), not updated mid-playback:
```java
// Snapshot playOrder at track-start time
List<File> orderedFiles = IntStream.of(playOrder).mapToObj(playlist::get).toList();
var context = new PlaylistContext(orderedFiles, currentIdxHolder[0], port, title,
        engine.isLoopEnabled(), engine.isShuffleEnabled());
```

`orderedFiles.get(i)` equals `playlist.get(playOrder[i])`, so `currentIndex` correctly
points to the current track within the ordered view. If `reshuffleRemaining` fires mid-track
(via the callback), the already-constructed `context` is unaffected — the PlaylistPanel
reflects the new play order at the start of the next track. This is intentional (see Non-Goals).

---

## Controls Panel Update

Add `[L]Loop [S]Shuf` to `ControlsPanel` displays:

- `height >= 3` (2nd line): `[L]Loop  [S]Shuf  [*]Save  [R]Resume Session  [Q]Quit`
- `height == 2` (compact): include `[L]Loop [S]Shuf` in the single compressed line
- `height == 1` (minLine): append `[L]Loop [S]Shuf` (truncated if terminal too narrow)

---

## Key Binding Confirmations

`s`/`S` and `l`/`L` are currently unbound in `JLineTerminalIO.buildKeyMap()`. No conflict
resolution or rebinding is needed — both keys can be added directly.

---

## Native Image Metadata

No FFM/downcall changes. The shuffle callback (`Consumer<Boolean>`) is used as a field
in `PlaybackEngine` — no reflection required. `reachability-metadata.json` does not
need updating.

---

## Edge Cases

| Case | Behavior |
|------|----------|
| Empty playlist (`size=0`) | `buildPlayOrder(0, ...)` returns `int[0]`; loop guard exits immediately |
| Single-track playlist | `reshuffleRemaining` operates on empty slice — no-op |
| Toggle shuffle ON at last track | Remaining slice is empty — no-op |
| Toggle shuffle OFF when already sorted | Sort is idempotent — no-op effectively |
| Terminal too narrow for tag | `Math.max(0, padding)` clamps to 0; tag may overlap bracket text |

---

## Files Changed

| File | Change |
|------|--------|
| `io/TerminalIO.java` | Add `TOGGLE_LOOP`, `TOGGLE_SHUFFLE` to `TerminalKey` enum |
| `io/JLineTerminalIO.java` | Bind `l`/`L` → `TOGGLE_LOOP`, `s`/`S` → `TOGGLE_SHUFFLE` |
| `engine/PlaybackEngine.java` | Add `loopEnabled`, `shuffleEnabled`, toggle methods, shuffle callback |
| `ui/InputHandler.java` | Handle `TOGGLE_LOOP`, `TOGGLE_SHUFFLE`; add `handleMiniInput()` |
| `ui/LineUI.java` | Add loop/shuffle indicator to dynamic line; switch to `handleMiniInput` |
| `ui/TitledPanel.java` | Add `setRightTag(String, int)`, update `render()` width calculation |
| `ui/DashboardUI.java` | Replace `playlistTitle()` with `playlistTag()`, update render loop |
| `ui/ControlsPanel.java` | Add `[L]Loop [S]Shuf` to all height variants |
| `cli/PlaybackRunner.java` | Add `playOrder[]`, `currentIdxHolder[]`, remove `Collections.shuffle`, add callback wiring, update loop wrap-around |
| `test/.../DashboardUITest.java` | Replace `playlistTitle_*` tests with `playlistTag_*` tests |
| `test/.../PlaybackEngineTest.java` | Add tests for `toggleLoop()`, `toggleShuffle()`, `isLoopEnabled()`, `isShuffleEnabled()` |
| `test/.../PlaybackRunnerTest.java` | Add tests for `buildPlayOrder()` and `reshuffleRemaining()` covering: toggle ON shuffles remaining, toggle OFF sorts remaining, toggle at last track (no-op), single track (no-op), idempotent toggle OFF |

---

## Mini Mode (LineUI)

Mini mode shows loop/shuffle state but does **not** support live toggling.

### Display

Add loop/shuffle indicators to the dynamic single-line update (end of the status line):

```java
String loopIcon    = engine.isLoopEnabled()    ? Theme.COLOR_HIGHLIGHT + "↺" + Theme.COLOR_RESET
                                                : Theme.COLOR_DIM_FG   + "↺" + Theme.COLOR_RESET;
String shuffleIcon = engine.isShuffleEnabled() ? Theme.COLOR_HIGHLIGHT + "⇆" + Theme.COLOR_RESET
                                                : Theme.COLOR_DIM_FG   + "⇆" + Theme.COLOR_RESET;
buffer.append(" ").append(loopIcon).append(shuffleIcon);
```

This reflects live engine state each frame (even though toggling is not available in mini mode,
the state set by CLI flags is shown correctly).

### Input Handling

`LineUI.runInputLoop()` uses `InputHandler::handleMiniInput` instead of `handleCommonInput`:

```java
// InputHandler.java
public static void handleMiniInput(PlaybackEngine engine, TerminalKey key) {
    switch (key) {
        case TOGGLE_LOOP, TOGGLE_SHUFFLE -> {} // not available in mini mode
        default -> handleCommonInput(engine, key);
    }
}
```

`LineUI`:
```java
@Override
public void runInputLoop(PlaybackEngine engine) {
    InputLoopRunner.run(engine, InputHandler::handleMiniInput);
}
```

No key guide change needed in `LineUI` (the existing controls line does not list L/S).

---

## Non-Goals

- Persisting loop/shuffle toggle state across sessions (CLI flags remain the source of truth for initial state)
- Loop/shuffle toggling in `DumbUI` or `LineUI`
- Loop/shuffle display in `DumbUI`
- Reordering the playlist display in real-time mid-track (display updates at next track start)
