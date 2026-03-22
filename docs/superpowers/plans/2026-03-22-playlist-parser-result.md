# PlaylistParser Return-Value Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `PlaylistParser.parse()` side-effects on `CommonOptions` with an explicit `ParseResult` return value so that M3U directive behaviour is testable without inspecting a mutated input object.

**Architecture:** Two new package-private record types (`PlaylistDirectives`, `ParseResult`) are added to the `cli` package. `PlaylistParser.parse()` return type changes from `List<File>` to `ParseResult`. A private `DirectiveAccumulator` inner class collects directive state during parsing instead of writing to `CommonOptions`. Two production callers (`PlaybackRunner`, `DemoCommand`) and all directive-checking tests are updated.

**Tech Stack:** Java 25, JUnit 5, `java.util.OptionalInt`, `java.util.OptionalDouble`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/com/fupfin/midiraja/cli/PlaylistDirectives.java` | Create | Record capturing what M3U directives set; `applyTo(CommonOptions)` to apply overrides |
| `src/main/java/com/fupfin/midiraja/cli/ParseResult.java` | Create | Record bundling `List<File> files` + `PlaylistDirectives directives` |
| `src/main/java/com/fupfin/midiraja/cli/PlaylistParser.java` | Modify | Return `ParseResult`; `DirectiveAccumulator` inner class; no `CommonOptions` mutation |
| `src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java` | Modify | Call `result.directives().applyTo(common)` after parse; update Javadoc |
| `src/main/java/com/fupfin/midiraja/cli/DemoCommand.java` | Modify | Append `.files()` to parse call |
| `src/test/java/com/fupfin/midiraja/cli/PlaylistParserTest.java` | Modify | Directive assertions on `result.directives()`; file-list tests use `.files()` |

---

### Task 1: New record types

**Files:**
- Create: `src/main/java/com/fupfin/midiraja/cli/PlaylistDirectives.java`
- Create: `src/main/java/com/fupfin/midiraja/cli/ParseResult.java`

These two records compile independently and don't touch any existing code. Commit them first so Task 2 can reference them.

- [ ] **Step 1: Create `PlaylistDirectives.java`**

```java
/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.cli;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Playback options set by {@code #MIDRA:} directives inside M3U playlists.
 * Only fields explicitly named in a directive are non-empty / true;
 * unset fields have empty Optionals or {@code false} booleans.
 *
 * <p>When multiple M3U files are parsed in one call, directives accumulate:
 * volume/speed use last-wins; boolean flags use OR (once {@code true}, stay {@code true}).
 */
record PlaylistDirectives(
        OptionalInt volume,
        OptionalDouble speed,
        boolean shuffle,
        boolean loop,
        boolean recursive)
{
    /** Sentinel: no M3U directives were found in this parse call. */
    static final PlaylistDirectives NONE =
            new PlaylistDirectives(OptionalInt.empty(), OptionalDouble.empty(),
                    false, false, false);

    /**
     * Applies directive overrides to {@code common}.
     *
     * <ul>
     *   <li>volume and speed: applied only when present; when present, the M3U value
     *       overrides whatever the CLI supplied.</li>
     *   <li>boolean flags: can only assert {@code true}; a CLI-supplied {@code true}
     *       is never cleared by an absent directive.</li>
     * </ul>
     */
    void applyTo(CommonOptions common)
    {
        volume.ifPresent(v -> common.volume = v);
        speed.ifPresent(s -> common.speed = s);
        if (shuffle)   common.shuffle   = true;
        if (loop)      common.loop      = true;
        if (recursive) common.recursive = true;
    }
}
```

- [ ] **Step 2: Create `ParseResult.java`**

```java
/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.cli;

import java.io.File;
import java.util.List;

/** Result of a playlist parse: the ordered file list and any M3U directives found. */
record ParseResult(List<File> files, PlaylistDirectives directives) {}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (no errors; new files compile, nothing else changes)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/cli/PlaylistDirectives.java \
        src/main/java/com/fupfin/midiraja/cli/ParseResult.java
git commit -m "feat: add PlaylistDirectives and ParseResult records"
```

---

### Task 2: Refactor `PlaylistParser` + update production callers

**Files:**
- Modify: `src/main/java/com/fupfin/midiraja/cli/PlaylistParser.java`
- Modify: `src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java`
- Modify: `src/main/java/com/fupfin/midiraja/cli/DemoCommand.java`

Changing `parse()` return type breaks callers immediately. All three files must be committed together so the code stays compile-clean. The existing `PlaylistParserTest` will fail to compile after this step (it still uses the old `List<File>` return) — that is expected and fixed in Task 3.

- [ ] **Step 1: Add `DirectiveAccumulator` inner class to `PlaylistParser.java`**

Add the following private static inner class at the **end** of `PlaylistParser.java`, just before the closing `}`:

```java
    private static final class DirectiveAccumulator
    {
        boolean shuffle;
        boolean loop;
        /** Set to {@code true} only when a {@code --recursive} directive was parsed. */
        boolean directiveRecursive;
        /**
         * Working recursive flag used for mid-parse directory scanning inside an M3U.
         * Seeded from {@code common.recursive} at parse start; set to {@code true} by
         * the {@code --recursive} directive so that subsequent directory entries in the
         * same M3U are scanned recursively.
         */
        boolean effectiveRecursive;
        int     volume = -1;         // -1 = no directive
        double  speed  = Double.NaN; // NaN = no directive

        DirectiveAccumulator(boolean initialRecursive)
        {
            this.effectiveRecursive = initialRecursive;
        }

        PlaylistDirectives build()
        {
            if (volume == -1 && Double.isNaN(speed) && !shuffle && !loop && !directiveRecursive)
                return PlaylistDirectives.NONE;
            return new PlaylistDirectives(
                    volume == -1 ? OptionalInt.empty() : OptionalInt.of(volume),
                    Double.isNaN(speed) ? OptionalDouble.empty() : OptionalDouble.of(speed),
                    shuffle, loop, directiveRecursive);
        }
    }
```

Also add these imports at the top of `PlaylistParser.java` (after the existing imports):
```java
import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
```

- [ ] **Step 2: Replace `parse()` method in `PlaylistParser.java`**

Replace the existing `parse()` method (lines 44–66) with:

```java
    /**
     * Expands {@code rawFiles} into an ordered list of MIDI files. Directories are scanned
     * according to {@code common.recursive}. M3U {@code #MIDRA:} directives are collected and
     * returned via {@link ParseResult#directives()}; {@code common} is never modified by this
     * method.
     */
    public ParseResult parse(List<File> rawFiles, CommonOptions common)
    {
        List<File> playlist = new ArrayList<>();
        var acc = new DirectiveAccumulator(common.recursive);
        for (File f : rawFiles)
        {
            f = normalize(f);
            String nameLower = f.getName().toLowerCase(Locale.ROOT);
            if (Files.isDirectory(f.toPath()))
            {
                parseDirectory(f, playlist, common.recursive);
            }
            else if (nameLower.endsWith(".m3u") || nameLower.endsWith(".m3u8")
                    || nameLower.endsWith(".txt"))
            {
                parsePlaylistFile(f, playlist, acc);
            }
            else
            {
                playlist.add(f);
            }
        }
        return new ParseResult(Collections.unmodifiableList(playlist), acc.build());
    }
```

- [ ] **Step 3: Replace `parsePlaylistFile()` method in `PlaylistParser.java`**

Replace the existing `parsePlaylistFile()` method (lines 68–199) with the version below.
The only changes from the original are:
- Signature: `CommonOptions common` → `DirectiveAccumulator acc`
- All `common.shuffle = true` → `acc.shuffle = true` (and similar for `loop`)
- `--recursive` block: sets **both** `acc.directiveRecursive = true` and `acc.effectiveRecursive = true`
- All `common.volume = ...` → `acc.volume = ...`
- All `common.speed = ...` → `acc.speed = ...`
- The verbose log for volume/speed now reads `acc.volume` / `acc.speed`
- `parseDirectory(track, playlist, common.recursive)` inside the M3U body → `parseDirectory(track, playlist, acc.effectiveRecursive)`

```java
    @SuppressWarnings({"StringSplitter", "EmptyCatch"})
    private void parsePlaylistFile(File playlistFile, List<File> playlist, DirectiveAccumulator acc)
    {
        try
        {
            List<String> lines = Files.readAllLines(playlistFile.toPath());
            File parentDir = playlistFile.getParentFile();

            for (String rawLine : lines)
            {
                String line = rawLine.trim();

                // Parse Custom M3U Directives: #MIDRA: --option
                if (line.toUpperCase(Locale.ROOT).startsWith("#MIDRA:"))
                {
                    String directive = line.substring(7).trim();

                    // Parse all tokens in one pass; use exact equality to avoid
                    // substring false-positives (e.g. -s inside --speed, -r inside --reset).
                    String[] tokens = directive.split("\\s+");
                    for (int i = 0; i < tokens.length; i++)
                    {
                        String token = tokens[i];

                        // Boolean flags
                        if (token.equals("--shuffle") || token.equals("-s"))
                        {
                            acc.shuffle = true;
                            logVerbose("Applied directive from playlist: --shuffle");
                        }
                        else if (token.equals("--loop") || token.equals("-r"))
                        {
                            acc.loop = true;
                            logVerbose("Applied directive from playlist: --loop");
                        }
                        else if (token.equals("--recursive") || token.equals("-R"))
                        {
                            acc.directiveRecursive = true;
                            acc.effectiveRecursive = true;
                            logVerbose("Applied directive from playlist: --recursive");
                        }

                        // Key-value directives
                        if (token.startsWith("--volume=") || token.startsWith("-v="))
                        {
                            try
                            {
                                acc.volume =
                                        Integer.parseInt(token.substring(token.indexOf('=') + 1));
                                logVerbose("Applied directive from playlist: " + token);
                            }
                            catch (NumberFormatException ignored)
                            {
                            }
                        }
                        else if ((token.equals("--volume") || token.equals("-v"))
                                && i + 1 < tokens.length)
                        {
                            try
                            {
                                acc.volume = Integer.parseInt(tokens[++i]);
                                logVerbose("Applied directive from playlist: --volume "
                                        + acc.volume);
                            }
                            catch (NumberFormatException ignored)
                            {
                            }
                        }

                        if (token.startsWith("--speed=") || token.startsWith("-x="))
                        {
                            try
                            {
                                acc.speed =
                                        Double.parseDouble(token.substring(token.indexOf('=') + 1));
                                logVerbose("Applied directive from playlist: " + token);
                            }
                            catch (NumberFormatException ignored)
                            {
                            }
                        }
                        else if ((token.equals("--speed") || token.equals("-x"))
                                && i + 1 < tokens.length)
                        {
                            try
                            {
                                acc.speed = Double.parseDouble(tokens[++i]);
                                logVerbose("Applied directive from playlist: --speed "
                                        + acc.speed);
                            }
                            catch (NumberFormatException ignored)
                            {
                            }
                        }
                    }
                    continue;
                }

                if (line.isEmpty() || line.startsWith("#"))
                {
                    continue;
                }

                File track = new File(line);
                if (!track.isAbsolute() && parentDir != null)
                {
                    track = new File(parentDir, line);
                }

                if (Files.isDirectory(track.toPath()))
                {
                    parseDirectory(track, playlist, acc.effectiveRecursive);
                }
                else if (track.exists())
                {
                    playlist.add(track);
                }
                else
                {
                    logVerbose("Playlist track not found: " + track.getAbsolutePath());
                }
            }
            logVerbose("Loaded playlist: " + playlistFile.getName() + " (" + lines.size()
                    + " lines parsed)");
        }
        catch (Exception e)
        {
            log.warning("Error reading playlist file '" + playlistFile.getName() + "': " + e.getMessage());
            err.println("Error reading playlist file '" + playlistFile.getName() + "': "
                    + e.getMessage());
            if (verbose) e.printStackTrace(err);
        }
    }
```

- [ ] **Step 4: Update `PlaybackRunner.java` — parse call-site and Javadoc**

In `PlaybackRunner.java`, find the block (around line 143):
```java
        PlaylistParser parser = new PlaylistParser(err, common.isVerbose());
        List<File> playlist = parser.parse(rawFiles, common);
```

Replace with:
```java
        PlaylistParser parser = new PlaylistParser(err, common.isVerbose());
        var parseResult = parser.parse(rawFiles, common);
        parseResult.directives().applyTo(common);
        List<File> playlist = parseResult.files();
```

Also update the `@param common` Javadoc on `run()` (around line 125):
```java
     * @param common shared playback options (may be mutated by M3U directives)
```
Replace with:
```java
     * @param common shared playback options; M3U playlist directives are applied to this
     *        object via {@link PlaylistDirectives#applyTo} after parsing
```

- [ ] **Step 5: Update `DemoCommand.java` — append `.files()`**

Find (around line 70):
```java
        List<File> allFiles = new PlaylistParser(p.getErr(), common.isVerbose()).parse(List.of(demoDir), common);
```

Replace with:
```java
        List<File> allFiles = new PlaylistParser(p.getErr(), common.isVerbose())
                .parse(List.of(demoDir), common).files();
```

- [ ] **Step 6: Verify compilation (tests will fail — expected)**

Run: `./gradlew compileJava compileTestJava`
Expected: `compileJava` — BUILD SUCCESSFUL; `compileTestJava` — COMPILE ERROR in `PlaylistParserTest` (references to `List<File>` from `parse()` and `common.shuffle` etc.). This is expected — Task 3 fixes the tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fupfin/midiraja/cli/PlaylistParser.java \
        src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java \
        src/main/java/com/fupfin/midiraja/cli/DemoCommand.java
git commit -m "refactor: PlaylistParser.parse returns ParseResult; no CommonOptions mutation"
```

---

### Task 3: Update `PlaylistParserTest`

**Files:**
- Modify: `src/test/java/com/fupfin/midiraja/cli/PlaylistParserTest.java`

All changes fall into three mechanical patterns. Work through each test method in order.

- [ ] **Step 1: Understand the three patterns**

**Pattern A — file-list tests** (the test only checks the returned list, no directive assertions):
```java
// before
List<File> result = parser.parse(List.of(...), common);
assertEquals(2, result.size());

// after — just append .files()
List<File> result = parser.parse(List.of(...), common).files();
assertEquals(2, result.size());
```

**Pattern B — boolean directive tests** (checks `common.shuffle` / `common.loop` / `common.recursive`):
```java
// before
parser.parse(List.of(m3u), common);
assertTrue(common.shuffle, "...");

// after
var result = parser.parse(List.of(m3u), common);
assertTrue(result.directives().shuffle(), "...");
```
(Same for `loop()` and `recursive()`)

**Pattern C — numeric directive tests** (checks `common.volume` or `common.speed`):
```java
// before
parser.parse(List.of(m3u), common);
assertEquals(75, common.volume, "...");

// after
var result = parser.parse(List.of(m3u), common);
assertEquals(75, result.directives().volume().getAsInt(), "...");

// for speed:
assertEquals(1.5, result.directives().speed().getAsDouble(), 0.001, "...");
```

For "not-set" assertions (invalid number, missing token):
```java
// before: assertEquals(100, common.volume, "invalid number should be silently ignored");
// after:
assertTrue(result.directives().volume().isEmpty(), "invalid number should leave volume directive absent");
```

- [ ] **Step 2: Apply Pattern A to file-list tests**

Update these methods (add `.files()` to the `parser.parse(...)` call):
- `testParseMidiFile` (line 43)
- `testParseDirectory` (line 56)
- `testParseDirectoryNonRecursive` (line 69)
- `testParseDirectoryRecursive` (line 82)
- `testM3uIgnoresComments` (line 244)
- `testM3uRelativePaths` (line 255)
- `testM3uMissingTrackSkipped` (line 266)
- `parseDirectory_resultIsSortedAlphabetically` (line 400)
- `testM3u8Extension` line 389 specifically: `parser.parse(List.of(m3u8), new CommonOptions()).size()` → `parser.parse(List.of(m3u8), new CommonOptions()).files().size()`

> **Note:** The five `normalize_*` tests (`normalize_noTrailingQuote_returnsSameInstance`, `normalize_oneTrailingQuote_stripped`, `normalize_multipleTrailingQuotes_allStripped`, `normalize_quoteInMiddle_notStripped`, `normalize_onlyQuotes_becomesEmptyPath`) call `PlaylistParser.normalize(f)` directly and do not call `parse()`. They require **no changes** and will pass unmodified.

- [ ] **Step 3: Apply Pattern B to boolean directive tests**

The test `testM3uShuffleDirectiveLongFlag` also checks file list size — handle both:
```java
// before
assertFalse(common.shuffle);
List<File> result = parser.parse(List.of(m3u), common);
assertTrue(common.shuffle, "#MIDRA: --shuffle should set shuffle=true");
assertEquals(1, result.size());

// after
assertFalse(common.shuffle);  // pre-condition on initial common state — keep as-is
var result = parser.parse(List.of(m3u), common);
assertTrue(result.directives().shuffle(), "#MIDRA: --shuffle should set shuffle=true");
assertEquals(1, result.files().size());
```

Apply Pattern B to these tests (capturing result, asserting on `result.directives()`):
- `testM3uShuffleDirectiveLongFlag`
- `testM3uShuffleDirectiveShortFlag`
- `testM3uLoopDirectiveLongFlag`
- `testM3uLoopDirectiveShortFlag`
- `testM3uRecursiveDirectiveLongFlag`
- `testM3uRecursiveDirectiveShortFlag`
- `testTxtPlaylistExtension` — assert on `result.directives().shuffle()`
- `testM3uCaseInsensitivePrefix` — assert on `result.directives().shuffle()`
- `testM3uDirectiveNoSpaceAfterColon` — assert on `result.directives().loop()`
- `testM3u8Extension` — assert on `result.directives().shuffle()` (line 389 fix is covered under Pattern A above)

- [ ] **Step 4: Apply Pattern C to numeric directive tests**

Update these tests using `result.directives().volume().getAsInt()` or `.speed().getAsDouble()`:
- `testM3uVolumeDirective` — remove pre-assertion `assertEquals(100, common.volume)` (it tests initial state of `common`, which is already covered by `setUp()`); assert `result.directives().volume().getAsInt() == 75`
- `testM3uVolumeDirectiveEqualsForm` — assert `result.directives().volume().getAsInt() == 60`
- `testM3uVolumeDirectiveShortFlag` — assert `result.directives().volume().getAsInt() == 80`
- `testM3uSpeedDirective` — remove pre-assertion `assertEquals(1.0, common.speed)` (same reason); assert `result.directives().speed().getAsDouble() == 1.5`
- `testM3uSpeedDirectiveEqualsForm` — assert `result.directives().speed().getAsDouble() == 2.0`
- `testM3uVolumeDirectiveShortEquals` — assert `result.directives().volume().getAsInt() == 80`
- `testM3uSpeedDirectiveShortSpace` — assert `result.directives().speed().getAsDouble() == 1.5`

- [ ] **Step 5: Fix "not-set" tests**

`testM3uInvalidVolumeIgnored`:
```java
// before
parser.parse(List.of(m3u), common);
assertEquals(100, common.volume, "invalid number should be silently ignored");

// after
var result = parser.parse(List.of(m3u), common);
assertTrue(result.directives().volume().isEmpty(),
        "invalid number should leave volume directive absent");
```

`testM3uVolumeAtEndOfTokens`:
```java
// before
parser.parse(List.of(m3u), common);
assertEquals(100, common.volume, "missing value token should leave volume unchanged");

// after
var result = parser.parse(List.of(m3u), common);
assertTrue(result.directives().volume().isEmpty(),
        "missing value token should leave volume directive absent");
```

- [ ] **Step 6: Fix multi-directive test**

`testM3uMultipleDirectives`:
```java
// before
parser.parse(List.of(m3u), common);
assertTrue(common.shuffle, "shuffle should be set");
assertTrue(common.loop, "loop should be set");
assertEquals(50, common.volume, "volume should be 50");

// after
var result = parser.parse(List.of(m3u), common);
assertTrue(result.directives().shuffle(), "shuffle should be set");
assertTrue(result.directives().loop(), "loop should be set");
assertEquals(50, result.directives().volume().getAsInt(), "volume should be 50");
```

- [ ] **Step 7: Run all tests**

Run: `./gradlew test --tests "com.fupfin.midiraja.cli.PlaylistParserTest"`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Run full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 9: Commit**

```bash
git add src/test/java/com/fupfin/midiraja/cli/PlaylistParserTest.java
git commit -m "test: update PlaylistParserTest to assert on ParseResult.directives()"
```
