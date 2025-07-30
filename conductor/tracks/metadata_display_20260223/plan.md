# Implementation Plan: MIDI Metadata Extraction & Display

## Phase 1: Meta Event Parsing
- [ ] Task: Create a private method `extractAndPrintMetadata(Sequence sequence)` in `MidirajaCommand.java`.
- [ ] Task: Inside `extractAndPrintMetadata`, iterate over all tracks and events in the `Sequence`.
- [ ] Task: Intercept `MetaMessage` events (status `0xFF`).
- [ ] Task: Decode byte arrays to Strings for specific types: `0x03` (Sequence/Track Name), `0x02` (Copyright), and `0x01` (Text).

## Phase 2: Formatted Output & Integration
- [ ] Task: Filter and deduplicate extracted strings (e.g., storing the first valid title, copyright, and up to 3 info texts).
- [ ] Task: Print the collected metadata neatly to `System.out` with an indentation (e.g., `  Title: ...`) right after the `Started playing: ...` line in `playMidiWithProvider`.
- [ ] Task: Ensure the `uiThread` for the SC-55 display starts *after* the metadata has been fully printed so the `` cursor reset doesn't overwrite the metadata.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Formatted Output & Integration' (Protocol in workflow.md)