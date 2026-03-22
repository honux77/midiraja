# PlaylistParser Return-Value Refactor — Design Spec

## Goal

Eliminate the hidden side-effect of `PlaylistParser.parse()` mutating `CommonOptions` fields
(`shuffle`, `loop`, `recursive`, `volume`, `speed`) as a consequence of M3U `#MIDRA:` directives.
Replace the mutation with an explicit `ParseResult` return value so that directive behaviour is
fully testable without inspecting a mutable input object.

## Context

`PlaylistParser.parse(List<File>, CommonOptions)` currently writes M3U directive values directly
into the `CommonOptions` parameter. The return type is `List<File>`, which reveals only the file
list — callers have no way to see *what changed* without inspecting the same object they passed in.
This couples tests tightly to `CommonOptions` and makes the parser untestable as a pure function.

## Architecture

### New types: `PlaylistDirectives` and `ParseResult` (cli package, package-private)

Both types are **package-private** records (no `public` modifier) placed in the
`com.fupfin.midiraja.cli` package alongside `PlaylistParser` and `CommonOptions`.
`PlaylistParser.parse()` itself **remains `public`** — only the new record types are
package-private.

```java
/**
 * Playback options set by #MIDRA: directives inside M3U playlists.
 * Only fields explicitly named in an M3U directive are non-empty / true;
 * unset fields have empty Optionals or false booleans.
 *
 * When multiple M3U files are parsed in one call, directives accumulate:
 * volume/speed use last-wins, boolean flags use OR (once true, stay true).
 */
record PlaylistDirectives(
        OptionalInt volume,
        OptionalDouble speed,
        boolean shuffle,
        boolean loop,
        boolean recursive) {

    /** Sentinel value: no M3U directives were found. */
    static final PlaylistDirectives NONE =
            new PlaylistDirectives(OptionalInt.empty(), OptionalDouble.empty(),
                    false, false, false);

    /**
     * Applies directive overrides to {@code common}.
     *
     * <ul>
     *   <li>volume and speed: applied only when present in the M3U; when present the
     *       M3U value overrides whatever the CLI supplied.</li>
     *   <li>boolean flags: can only assert true; a CLI-supplied {@code true} is never
     *       cleared by an absent directive.</li>
     * </ul>
     */
    void applyTo(CommonOptions common) {
        volume.ifPresent(v -> common.volume = v);
        speed.ifPresent(s -> common.speed = s);
        if (shuffle)   common.shuffle   = true;
        if (loop)      common.loop      = true;
        if (recursive) common.recursive = true;
    }
}

/** Result of a playlist parse: the ordered file list and any M3U directives found. */
record ParseResult(List<File> files, PlaylistDirectives directives) {}
```

### Changes to `PlaylistParser`

**Signature change** (`parse()` remains `public`):
```java
// before
public List<File> parse(List<File> rawFiles, CommonOptions common)

// after
public ParseResult parse(List<File> rawFiles, CommonOptions common)
```

`common` is still accepted as input for two read-only uses — **neither is removed by this
refactor**:

1. **Top-level directory branch** in `parse()`: `parseDirectory(f, playlist, common.recursive)`
   — raw directory inputs (not inside an M3U) still seed their recursive depth from
   `common.recursive`.
2. **M3U-internal directory entries**: when `parsePlaylistFile` encounters a directory path
   inside an M3U, it uses the locally accumulated recursive state (see below), which was
   itself seeded from `common.recursive` at the start of `parsePlaylistFile`.

`common` is **never written to** inside `parse()`.

**Directive state accumulation across the outer `parse()` loop:**

A single mutable state bundle is maintained across all input files:

```java
// Inside parse() — outer-loop accumulator
boolean[] dShuffle   = {false};
boolean[] dLoop      = {false};
boolean[] dRecursive = {false};     // tracks directives only; top-level scan uses common.recursive
int[]     dVolume    = {-1};        // -1 = no directive set
double[]  dSpeed     = {Double.NaN}; // NaN = no directive set
```

`parsePlaylistFile` is given a reference to this accumulator (or the local arrays) and updates it
when directives are found. Because multiple M3U files can appear in `rawFiles`, directives
accumulate across files: volume/speed use last-wins (each new directive overwrites the previous),
boolean flags use OR (once true, stay true). When `rawFiles` contains only plain `.mid` files or
a directory (no M3U), the accumulator is never touched and `PlaylistDirectives.NONE` is returned.

**Mid-parse `recursive` in `parsePlaylistFile`:**

`parsePlaylistFile` keeps a local `effectiveRecursive` variable seeded from `common.recursive`
at its call-site. When a `--recursive` directive is encountered, both `effectiveRecursive` and
`dRecursive[0]` are set to `true`. Subsequent directory entries inside the same M3U use
`effectiveRecursive` — preserving the existing mid-parse behaviour.

**Return value construction:**

```java
// at the end of parse()
PlaylistDirectives directives = (dVolume[0] == -1 && Double.isNaN(dSpeed[0])
        && !dShuffle[0] && !dLoop[0] && !dRecursive[0])
    ? PlaylistDirectives.NONE
    : new PlaylistDirectives(
          dVolume[0] == -1 ? OptionalInt.empty() : OptionalInt.of(dVolume[0]),
          Double.isNaN(dSpeed[0]) ? OptionalDouble.empty() : OptionalDouble.of(dSpeed[0]),
          dShuffle[0], dLoop[0], dRecursive[0]);
return new ParseResult(Collections.unmodifiableList(playlist), directives);
```

`Collections.unmodifiableList` is an intentional tightening over the current mutable `ArrayList`
return — both production callers and all tests read the list without mutating it, so this is safe.

### Changes to callers

| File | Change |
|------|--------|
| `PlaybackRunner.java` | `var result = parser.parse(rawFiles, common);` then `result.directives().applyTo(common);` then use `result.files()` |
| `DemoCommand.java` | `new PlaylistParser(...).parse(List.of(demoDir), common).files()` — `demoDir` is always a directory, so `parsePlaylistFile` is never called and no directives are ever populated; calling `applyTo` would be a no-op but is omitted for clarity |

No other production files call `parse()`.

### Changes to `PlaylistParserTest`

All tests that currently assert on `common.shuffle`, `common.loop`, etc. after calling `parse()`
are updated to assert on `result.directives()` instead:

```java
// before
parser.parse(List.of(m3u), common);
assertTrue(common.shuffle);

// after
var result = parser.parse(List.of(m3u), common);
assertTrue(result.directives().shuffle());
```

Tests that only care about the file list use `.files()`:
```java
List<File> files = parser.parse(List.of(midiFile), common).files();
```

Directive tests no longer need to assert on `common` at all. A `new CommonOptions()` is still
passed in to supply the initial `recursive` seed value, but the test never reads it back.

## Error Handling

No new error-handling paths are introduced. The `parse()` method retains its existing
try/catch behaviour for missing files and unreadable playlists.

## Scope

- **In scope:** `PlaylistDirectives` record, `ParseResult` record, `PlaylistParser.parse()`
  return type and internal state refactor, two production callers, existing `PlaylistParserTest`
  test assertions.
- **Out of scope:** `CommonOptions` field visibility changes, M3U directive grammar changes,
  new directive types.

## Files Changed

| File | Change |
|------|--------|
| `src/main/java/com/fupfin/midiraja/cli/PlaylistDirectives.java` | New package-private record |
| `src/main/java/com/fupfin/midiraja/cli/ParseResult.java` | New package-private record |
| `src/main/java/com/fupfin/midiraja/cli/PlaylistParser.java` | Return type change; internal directive accumulation refactored to local state; `parse()` remains `public`; **Javadoc on `parse()` updated** — remove the sentence "M3U directives may mutate `common.*`" and replace with "M3U directives found in playlists are returned via `ParseResult.directives()`; `common` is never modified" |
| `src/main/java/com/fupfin/midiraja/cli/PlaybackRunner.java` | Apply directives explicitly after parse; update `@param common` Javadoc on `run()` to note that directives are applied via `result.directives().applyTo(common)` after parsing |
| `src/main/java/com/fupfin/midiraja/cli/DemoCommand.java` | `.files()` suffix on parse call |
| `src/test/java/com/fupfin/midiraja/cli/PlaylistParserTest.java` | Directive assertions on `result.directives()` |
