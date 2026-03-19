# Design: `midra resume` — Session History & Bookmarks

**Date:** 2026-03-19

---

## Overview

`midra resume` lets users re-launch a previous playback session without retyping the full command. Sessions are saved automatically (last 10) and can be explicitly bookmarked (up to 50) for reuse.

---

## 1. Data Model

### Storage location

| Platform | Path |
|----------|------|
| macOS / Linux | `~/.config/midiraja/history.json` |
| Windows | `%APPDATA%\midiraja\history.json` |

`SessionHistory` detects the OS and resolves the path at runtime.

### `history.json` structure

```json
{
  "auto": [
    { "args": ["opl", "--retro", "amiga", "/Users/user/midi/"], "savedAt": "2026-03-19T14:30:00" },
    { "args": ["soundfont", "/Users/user/song.mid"],            "savedAt": "2026-03-19T13:00:00" }
  ],
  "bookmarks": [
    { "args": ["opl", "--retro", "amiga", "/Users/user/midi/"], "savedAt": "2026-03-19T14:31:00" }
  ]
}
```

### Rules

- **`auto`**: capped at 10 entries, newest first. On overflow the oldest entry is dropped.
- **`bookmarks`**: capped at 50 entries. On overflow the oldest entry is dropped.
- File/directory path arguments are converted to absolute paths at save time.
- Non-path arguments (`--retro`, `--volume`, etc.) are stored as-is.
- Duplicate auto entries (identical arg list) replace the existing entry and move it to the top.
  Comparison must use `List.equals()` (not array identity) — hence `List<String>` is used, not `String[]`.
- If an identical entry already exists in **bookmarks**, `recordAuto()` skips adding it to auto,
  to avoid showing the same session twice in the `resume` list.
- If corrupted or missing, `history.json` is treated as empty and silently overwritten on next save.
- Concurrent write safety: use atomic write (write to a temp file, then `Files.move` with
  `ATOMIC_MOVE` / `REPLACE_EXISTING`) to prevent partial reads by a concurrent process.

### `SessionEntry` record

```java
record SessionEntry(List<String> args, Instant savedAt) {}
```

`List<String>` gives value-based `equals()` / `hashCode()` for free, avoiding the silent identity-equality trap of `String[]`.

---

## 2. `SessionHistory` Class

**Location:** `src/main/java/com/fupfin/midiraja/cli/SessionHistory.java`

| Method | Description |
|--------|-------------|
| `recordAuto(List<String> args)` | Converts path args to absolute, skips if identical entry is in bookmarks, deduplicates in auto list, prepends, trims to 10, saves. |
| `saveBookmark(List<String> args)` | Prepends to bookmarks list, trims to 50, saves. |
| `deleteAuto(int index)` | Removes entry at index from auto list, saves. |
| `deleteBookmark(int index)` | Removes entry at index from bookmarks list, saves. |
| `promoteToBookmark(int index)` | Moves auto entry at index to bookmarks list, saves. |
| `getAll()` → `List<SessionEntry>` | Returns auto entries first, then bookmarks. |

Serialization uses hand-rolled JSON via `java.nio.file` — no third-party dependency. Atomic write via temp-file-then-`Files.move`.

---

## 3. Acquiring `originalArgs` in Subcommands

Picocli does not propagate the raw `String[]` from `main()` into subcommand objects. The args are recovered at runtime using:

```java
@Spec CommandSpec spec;

// inside call():
List<String> originalArgs = spec.commandLine().getParseResult().originalArgs();
```

`getParseResult().originalArgs()` returns the full flat token list as seen by the top-level parser (including the subcommand name token and all flags). File/directory tokens are identified by checking `new File(token).exists()` and resolved to absolute paths before passing to `SessionHistory`.

Each subcommand that delegates to `PlaybackRunner` injects this list and passes it to `PlaybackRunner.run()` as a new `List<String> originalArgs` parameter.

---

## 4. Entry Points

### `midra resume` subcommand

**Location:** `src/main/java/com/fupfin/midiraja/cli/ResumeCommand.java`

1. Loads `SessionHistory.getAll()`
2. If list is empty: prints "No session history." and exits
3. Displays `TerminalSelector` with all entries

**List item display format:**
```
opl --retro amiga /Users/user/midi/   [2026-03-19 14:30]
opl --retro amiga /Users/user/midi/   [2026-03-19 14:31]  ★
```

Auto entries show first (no marker), bookmarks follow (★ marker).

**Key bindings in the selector (FULL and MINI modes only):**

| Key | Action |
|-----|--------|
| `Enter` | Launch selected session |
| `D` | Delete selected entry |
| `B` | Promote selected auto entry to bookmark (no-op on ★ entries) |
| `Q` / `Esc` | Cancel |

In CLASSIC mode (non-interactive / pipe), `D` and `B` actions are not available. The user enters a number to select, or `0` to cancel.

**Re-execution:** `ResumeCommand` creates a fresh `CommandLine` instance and executes the saved args:

```java
int exitCode = new CommandLine(new MidirajaCommand()).execute(
    entry.args().toArray(new String[0]));
```

This is the standard picocli re-execution pattern, matching `MidirajaCommand.main()`. It bypasses the current `ResumeCommand` instance cleanly.

### Auto-save trigger

`PlaybackRunner.run()` calls `SessionHistory.recordAuto(originalArgs)` after the empty-playlist guard (after `playlist.isEmpty()` check) and before port selection. This ensures sessions that produce no files are not recorded.

---

## 5. Bookmark via `*` Key During Playback

`b`/`B` is already bound to `SEEK_BACKWARD`. The bookmark key is `*` (asterisk) — visually matching the ★ display marker and unoccupied in the current key map.

`BOOKMARK` is **not** added to `PlaybackStatus` because `requestStop(status)` sets `isPlaying = false`, which would terminate playback — the opposite of the desired behavior.

Instead, bookmarking is wired as a side-effect callback:

- `TerminalKey.BOOKMARK` is added to the `TerminalKey` enum.
- `JLineTerminalIO` binds `*` → `TerminalKey.BOOKMARK`.
- `PlaybackEngine` accepts an optional `Runnable bookmarkCallback` (set by `PlaybackRunner`).
- `InputHandler.handleCommonInput()` gets a new `case BOOKMARK -> engine.fireBookmark()`.
- `engine.fireBookmark()` invokes the callback on the input thread without touching `isPlaying`.
- `PlaybackRunner` sets the callback to `() -> SessionHistory.saveBookmark(originalArgs)`.
- A brief `[Bookmarked]` line is printed to `err` inside the callback. Playback continues uninterrupted.

---

## 6. GraalVM / Native Image

`ResumeCommand` is a new picocli command class and requires a reflection entry in `reachability-metadata.json`, following the same pattern as all other `cli.*` command classes added previously.

`SessionHistory` and `SessionEntry` use only `java.nio.file`, `java.time`, and `java.util` — no reflection needed.

---

## 7. Files Changed

| File | Change |
|------|--------|
| `SessionEntry.java` | **New** — record (`List<String>` args, `Instant` savedAt) |
| `SessionHistory.java` | **New** — load/save/delete/promote, atomic write |
| `ResumeCommand.java` | **New** — `midra resume` subcommand |
| `MidirajaCommand.java` | Register `ResumeCommand` subcommand |
| `PlaybackRunner.java` | Add `originalArgs` param, auto-save after playlist guard, set bookmark callback |
| `PlaybackEngine.java` | Accept `bookmarkCallback`, add `fireBookmark()` |
| `InputHandler.java` | Add `case BOOKMARK -> engine.fireBookmark()` in `handleCommonInput()` |
| `TerminalIO.java` | Add `BOOKMARK` to `TerminalKey` enum |
| `JLineTerminalIO.java` | Bind `*` → `TerminalKey.BOOKMARK` |
| `TerminalSelector.java` | Add `D` and `B` key actions (FULL + MINI modes) |
| Each subcommand (`OplCommand`, etc.) | Inject `@Spec`, pass `originalArgs` to `PlaybackRunner.run()` |
| `reachability-metadata.json` | Add entry for `ResumeCommand` |

---

## 8. Out of Scope

- Editing a saved entry's args
- Named bookmarks (label field)
- Sync across machines
- Import/export of history file
- Arg-order-insensitive deduplication (two runs with same files in different order are treated as distinct sessions)
