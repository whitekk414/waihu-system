# Dialog Recording Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure a completed call recording is ready before transcription, retry transient SenseVoice 422 responses, and persist a terminal failure instead of leaving a dialog task pending.

**Architecture:** Keep retry policy inside `SenseVoiceTranscriptionAdapter`, where HTTP failure semantics are known. Treat HTTP 422 as transient for a bounded three-attempt sequence with a short delay. At the dialog boundary, convert unrecoverable processing exceptions into a persisted failed task before idempotently hanging up.

**Tech Stack:** Java 17, Spring Boot, Java HTTP Client, JUnit 5, AssertJ, Mockito.

---

### Task 1: SenseVoice transient 422 retry

**Files:**
- Modify: `src/main/java/com/company/outbound/processing/SenseVoiceTranscriptionAdapter.java`
- Test: `src/test/java/com/company/outbound/processing/SenseVoiceTranscriptionAdapterTest.java`

- [ ] Add a test server sequence returning 422, 422, then a successful transcription.
- [ ] Run the focused test and confirm it fails because 422 is not retried.
- [ ] Implement three bounded attempts with a short delay for HTTP 422 and existing transient failures.
- [ ] Verify the focused adapter test passes and HTTP 400 remains non-retryable.

### Task 2: Persist dialog failure state

**Files:**
- Modify: `src/main/java/com/company/outbound/dialog/DialogOrchestrator.java`
- Modify as required: `src/main/java/com/company/outbound/dialog/DialogSessionService.java`
- Test: `src/test/java/com/company/outbound/dialog/DialogOrchestratorTest.java`

- [ ] Add a failing test proving a transcription exception moves the task to a terminal failure state.
- [ ] Run the focused test and confirm it fails while the task remains pending.
- [ ] Persist the failure before idempotent hangup and session removal.
- [ ] Verify processing and hangup failure tests pass.

### Task 3: Full verification

**Files:**
- Verify all modified production and test files.

- [ ] Run the full Java test suite and require zero failures.
- [ ] Run the package build and require exit code 0.
- [ ] Run `git diff --check` and inspect the final diff without committing.
- [ ] Restart the local Java service with real SenseVoice and GPT mode, then confirm HTTP 8081, ASR health, and `honor Connected/Free` without placing a call.
