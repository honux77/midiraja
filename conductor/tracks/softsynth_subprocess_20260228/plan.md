# Implementation Plan: External Soft Synth Subprocess Integration

## Phase 1: Test Scaffolding and Edge Case Definitions
- [ ] Task: Create `SoftSynthProviderTest.java` and write failing unit tests for basic command parsing and process execution (TDD Red Phase).
- [ ] Task: Write failing tests for exception handling and boundary conditions (e.g., malformed commands, missing executables, process crashing unexpectedly) (TDD Red Phase).
- [ ] Task: Write failing tests for lifecycle management, ensuring `closePort()` forcefully terminates hanging or zombie processes (TDD Red Phase).
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Test Scaffolding and Edge Case Definitions' (Protocol in workflow.md)

## Phase 2: Core Provider Implementation & Review
- [ ] Task: Implement `SoftSynthProvider.java` using `ProcessBuilder` to make the initialization and basic execution tests pass (TDD Green Phase).
- [ ] Task: Implement robust error handling in `openPort()` to deal with "command not found" or permission denied OS errors.
- [ ] Task: Implement `sendMessage()` and `closePort()` to successfully pipe raw MIDI bytes, handling `IOException` if the pipe breaks prematurely (TDD Green Phase).
- [ ] Task: Implement the daemon thread to securely consume `stdout` and `stderr` to prevent OS pipe buffers from blocking and protect the TUI.
- [ ] Task: Perform a self-code-review against the codebase standards and refactor the provider for simplicity and safety.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Core Provider Implementation & Review' (Protocol in workflow.md)

## Phase 3: CLI Integration and Validation
- [ ] Task: Update `MidirajaCommand.java` to add the `--soft-synth` CLI option.
- [ ] Task: Wire the CLI option to instantiate `SoftSynthProvider` instead of the default native OS providers.
- [ ] Task: Perform a final code review and refactoring pass across all modified files (`SoftSynthProvider.java` and `MidirajaCommand.java`).
- [ ] Task: Run full integration tests to verify a MIDI file plays through an external synth without JVM/AOT conflicts, even when gracefully interrupted via `Ctrl+C`.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: CLI Integration and Validation' (Protocol in workflow.md)