# Java + Asterisk ARI Guided Dialog MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a two-round Java-controlled outbound dialog over the existing HONOR SIM/BlueZ/`chan_mobile` channel, with prompt playback, per-turn recording, SenseVoice transcription, constrained model decisions, and a local test UI.

**Architecture:** The Spring Boot application owns a deterministic dialog state machine and drives native WSL2 Asterisk through ARI. Asterisk owns only call media operations. SenseVoice and the decision model are replaceable HTTP ports; their outputs are validated before the state machine accepts a transition.

**Tech Stack:** Java 17, Spring Boot 3.4, JPA/H2, Java HTTP/WebSocket client, Asterisk 18 ARI/Stasis, `chan_mobile`, Python 3.11, FastAPI, FunASR SenseVoiceSmall + FSMN-VAD, vanilla HTML/CSS/JavaScript.

**Repository rule:** The user requested local changes only. Do not commit, push, or open a pull request. Where the standard workflow would commit, record the checkpoint with `git diff --check` and `git status --short` instead.

---

## File map

### Speech service

- Modify `speech-service/app/config.py`: expose optional local SenseVoice and VAD model paths.
- Modify `speech-service/app/engine.py`: construct `AutoModel` from explicit local paths and expose load failures.
- Modify `speech-service/app/main.py`: report `loading`, `ready`, or `failed` health state without hanging.
- Modify `speech-service/start.ps1`: set local cached model paths when present and start one process only.
- Create `speech-service/tests/test_engine_loading.py`: verify local paths are passed to FunASR and failures are observable.
- Modify `speech-service/tests/test_health.py`: verify health states.
- Modify `speech-service/README.md`: document startup and the verified Bluetooth recording smoke test.

### Native WSL2 Asterisk

- Keep `infra/asterisk-wsl/chan_mobile.conf`: existing CSR8510 and HONOR HFP configuration.
- Create `infra/asterisk-wsl/http.conf`: ARI HTTP listener on port 8088.
- Create `infra/asterisk-wsl/ari.conf`: local ARI user template.
- Create `infra/asterisk-wsl/extensions.conf`: `Stasis(waihu-dialog)` entry point and safe hangup context.
- Create `scripts/install-wsl-asterisk-config.ps1`: copy versioned config and prompts into Ubuntu, restart services, and verify readiness.
- Create `scripts/check-wsl-mobile.ps1`: non-mutating readiness check for usbipd, BlueZ, ARI, and `mobile show devices`.

### Java dialog domain

- Create `src/main/java/com/company/outbound/dialog/DialogNode.java`: finite node enumeration.
- Create `src/main/java/com/company/outbound/dialog/DialogIntent.java`: allowed model intents.
- Create `src/main/java/com/company/outbound/dialog/DialogDecision.java`: validated decision value.
- Create `src/main/java/com/company/outbound/dialog/DialogTurn.java`: persisted per-turn entity.
- Create `src/main/java/com/company/outbound/dialog/DialogTurnRepository.java`: turn persistence.
- Create `src/main/java/com/company/outbound/dialog/DialogStateMachine.java`: pure transition logic.
- Create `src/main/java/com/company/outbound/dialog/DialogSessionService.java`: persistence-facing session operations.
- Modify `src/main/java/com/company/outbound/task/CallStatus.java`: add the detailed dialog statuses from the design.
- Modify `src/main/java/com/company/outbound/task/CallTask.java`: persist current node, human flag, and terminal outcome.
- Modify `src/main/java/com/company/outbound/task/CallTaskView.java`: expose dialog fields and ordered turns.

### Java integration ports

- Extend `src/main/java/com/company/outbound/telephony/TelephonyPort.java`: originate, play, record, hang up, and event listener operations.
- Replace `src/main/java/com/company/outbound/telephony/AriTelephonyAdapter.java`: use `Mobile/honor/{number}`, ARI Stasis, media REST calls, and an events WebSocket.
- Extend `src/main/java/com/company/outbound/telephony/TelephonyEvent.java`: typed ARI event plus operation ID.
- Modify `src/main/java/com/company/outbound/telephony/MockTelephonyAdapter.java`: deterministic fake media lifecycle.
- Create `src/main/java/com/company/outbound/processing/SenseVoiceTranscriptionAdapter.java`: multipart/local transcription HTTP client.
- Create `src/main/java/com/company/outbound/processing/DecisionPort.java`: constrained decision interface.
- Create `src/main/java/com/company/outbound/processing/RuleDecisionAdapter.java`: deterministic MVP fallback for tests.
- Create `src/main/java/com/company/outbound/processing/GptDecisionAdapter.java`: configured GPT-compatible JSON decision client.
- Create `src/main/java/com/company/outbound/dialog/DialogOrchestrator.java`: event-driven two-round coordinator.
- Modify `src/main/java/com/company/outbound/task/CallTaskController.java`: confirmation-gated dialog start endpoint.
- Modify `src/main/resources/application.yml`: WSL ARI, prompt, recording, SenseVoice, and model settings.

### UI and tests

- Modify `src/main/resources/static/index.html`: single-number confirmed dialog form and turn detail area.
- Modify `src/main/resources/static/app.js`: safe JSON parsing, confirmation request, SSE refresh, and turn rendering.
- Modify `src/main/resources/static/styles.css`: state/intent/recording presentation.
- Create `src/test/java/com/company/outbound/dialog/DialogStateMachineTest.java`.
- Create `src/test/java/com/company/outbound/dialog/DialogOrchestratorTest.java`.
- Extend `src/test/java/com/company/outbound/telephony/AriTelephonyAdapterTest.java`.
- Create `src/test/java/com/company/outbound/processing/SenseVoiceTranscriptionAdapterTest.java`.
- Extend `src/test/java/com/company/outbound/task/CallTaskControllerTest.java`.
- Modify `src/test/java/com/company/outbound/system/StaticPageTest.java`.

---

### Task 1: Make SenseVoice startup deterministic and observable

**Files:**
- Modify: `speech-service/app/config.py`
- Modify: `speech-service/app/engine.py`
- Modify: `speech-service/app/main.py`
- Modify: `speech-service/start.ps1`
- Test: `speech-service/tests/test_engine_loading.py`
- Test: `speech-service/tests/test_health.py`

- [ ] **Step 1: Write failing tests for explicit local model paths**

Create tests around a factory seam instead of loading the 936 MB model:

```python
def test_engine_load_uses_configured_local_paths(monkeypatch, tmp_path):
    captured = {}
    monkeypatch.setattr("app.engine.AutoModelFactory.create", lambda **kw: captured.update(kw) or object())
    engine = FunAsrEngine(model_path=tmp_path / "sense", vad_model_path=tmp_path / "vad")
    engine.load()
    assert captured["model"] == str(tmp_path / "sense")
    assert captured["vad_model"] == str(tmp_path / "vad")
    assert engine.state == "ready"

def test_engine_exposes_load_failure(monkeypatch, tmp_path):
    monkeypatch.setattr("app.engine.AutoModelFactory.create", lambda **_: (_ for _ in ()).throw(RuntimeError("bad model")))
    engine = FunAsrEngine(tmp_path / "sense", tmp_path / "vad")
    engine.load()
    assert engine.state == "failed"
    assert engine.load_error == "bad model"
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
speech-service\.venv\Scripts\python -m pytest speech-service/tests/test_engine_loading.py speech-service/tests/test_health.py -q
```

Expected: failures because `AutoModelFactory`, `state`, `load_error`, and local path settings do not exist.

- [ ] **Step 3: Add explicit settings and model lifecycle**

Implement settings with `SENSEVOICE_MODEL_PATH` and `SENSEVOICE_VAD_MODEL_PATH`. Add an `AutoModelFactory.create(**kwargs)` wrapper and make `FunAsrEngine` maintain `loading/ready/failed` plus a safe error string. The factory call must use:

```python
AutoModel(
    model=str(model_path),
    vad_model=str(vad_model_path),
    vad_kwargs={"max_single_segment_time": 30000},
    trust_remote_code=True,
    device="cpu",
    disable_update=True,
)
```

Health response must become:

```json
{"status":"ok","modelState":"ready","modelReady":true,"model":"SenseVoiceSmall","loadError":null}
```

- [ ] **Step 4: Run focused and full Python tests**

Run:

```powershell
speech-service\.venv\Scripts\python -m pytest speech-service/tests -q
```

Expected: all tests pass with no traceback.

- [ ] **Step 5: Run the real recording smoke test**

Start one service process, wait for `modelState=ready`, then call:

```powershell
curl.exe -H "Content-Type: application/json" `
  -d '{"filename":"bt-response-20260804-111605.wav"}' `
  http://127.0.0.1:8090/api/v1/transcriptions/local
```

Expected: HTTP 200 with non-empty Chinese `text`. Capture elapsed time and confirm no duplicate FunASR Python processes remain.

- [ ] **Step 6: Local checkpoint**

Run `git diff --check` and `git status --short`. Do not commit.

### Task 2: Configure native WSL2 Asterisk ARI safely

**Files:**
- Create: `infra/asterisk-wsl/http.conf`
- Create: `infra/asterisk-wsl/ari.conf`
- Create: `infra/asterisk-wsl/extensions.conf`
- Create: `scripts/install-wsl-asterisk-config.ps1`
- Create: `scripts/check-wsl-mobile.ps1`

- [ ] **Step 1: Write a failing readiness script**

The script must return nonzero unless all checks pass:

```powershell
usbipd list | Select-String '0a12:0001.*Attached' | Out-Null
wsl -d Ubuntu-22.04 -u root -- systemctl is-active --quiet bluetooth
wsl -d Ubuntu-22.04 -u root -- asterisk -rx 'module show like mobile' | Select-String 'Running' | Out-Null
wsl -d Ubuntu-22.04 -u root -- asterisk -rx 'mobile show devices' | Select-String 'honor.*Yes.*Free' | Out-Null
curl.exe -fsS -u "$env:ASTERISK_ARI_USER`:$env:ASTERISK_ARI_PASSWORD" http://localhost:8088/ari/asterisk/info | Out-Null
```

- [ ] **Step 2: Run it and verify RED**

Expected: fail only on ARI because native WSL Asterisk does not yet have the project ARI configuration.

- [ ] **Step 3: Add minimal ARI/Stasis configuration**

Use `bindaddr=0.0.0.0`, port `8088`, a non-read-only ARI user sourced by the installer from environment variables, and this dialplan entry:

```asterisk
[waihu-mobile]
exten => _X.,1,NoOp(Waihu outbound dialog ${EXTEN})
 same => n,Stasis(waihu-dialog,${EXTEN})
 same => n,Hangup()
```

The installer must back up `/etc/asterisk/{http,ari,extensions}.conf` once, install files with `asterisk:asterisk` ownership and `0640` permissions, restart Asterisk, and never alter `chan_mobile.conf` unless `-InstallMobileConfig` is explicitly passed.

- [ ] **Step 4: Install and verify GREEN**

Run the installer, then the readiness script. Expected: five checks pass; `mobile show devices` remains `Connected Yes / Free`.

- [ ] **Step 5: Verify ARI can originate without calling a real number**

Create a Local test channel into `Stasis(waihu-dialog)` and delete it. Expected: WebSocket emits `StasisStart` and `StasisEnd`; no `Mobile/honor` channel is created.

- [ ] **Step 6: Local checkpoint**

Run `git diff --check` and do not commit.

### Task 3: Implement the pure dialog state machine and persistence model

**Files:**
- Create: `src/main/java/com/company/outbound/dialog/DialogNode.java`
- Create: `src/main/java/com/company/outbound/dialog/DialogIntent.java`
- Create: `src/main/java/com/company/outbound/dialog/DialogDecision.java`
- Create: `src/main/java/com/company/outbound/dialog/DialogTurn.java`
- Create: `src/main/java/com/company/outbound/dialog/DialogTurnRepository.java`
- Create: `src/main/java/com/company/outbound/dialog/DialogStateMachine.java`
- Create: `src/main/java/com/company/outbound/dialog/DialogSessionService.java`
- Modify: `src/main/java/com/company/outbound/task/CallStatus.java`
- Modify: `src/main/java/com/company/outbound/task/CallTask.java`
- Modify: `src/main/java/com/company/outbound/task/CallTaskView.java`
- Test: `src/test/java/com/company/outbound/dialog/DialogStateMachineTest.java`

- [ ] **Step 1: Write transition tests first**

Cover at minimum:

```java
assertThat(machine.decide(DialogNode.ASK_IDENTITY, SELF_CONFIRMED, 0).nextNode())
    .isEqualTo(DialogNode.ASK_PAYMENT_PLAN);
assertThat(machine.decide(DialogNode.ASK_IDENTITY, NOT_SELF, 0).terminalOutcome())
    .isEqualTo("NOT_SELF");
assertThat(machine.decide(DialogNode.ASK_IDENTITY, UNCLEAR, 0).retry()).isTrue();
assertThat(machine.decide(DialogNode.ASK_IDENTITY, UNCLEAR, 1).needHuman()).isTrue();
```

Also cover all payment intents and reject an intent that is invalid for the current node.

- [ ] **Step 2: Run Java test and verify RED**

Run `mvn -Dtest=DialogStateMachineTest test`. Expected: compilation failure because dialog types do not exist.

- [ ] **Step 3: Implement minimal pure transition logic**

Use exhaustive `switch` expressions. Do not call repositories, ARI, HTTP, or clocks from `DialogStateMachine`.

- [ ] **Step 4: Add per-turn persistence**

Persist `taskId`, `node`, `attempt`, `promptId`, `recordingName`, `transcript`, `intent`, `confidence`, `summary`, `createdAt`, and `completedAt`. Enforce uniqueness on `(taskId,node,attempt)`.

- [ ] **Step 5: Run domain and repository tests**

Run `mvn -Dtest='DialogStateMachineTest,*CallTask*Test' test`. Expected: pass.

- [ ] **Step 6: Local checkpoint**

Run `git diff --check`; do not commit.

### Task 4: Extend the telephony port and implement ARI media events

**Files:**
- Modify: `src/main/java/com/company/outbound/telephony/TelephonyPort.java`
- Modify: `src/main/java/com/company/outbound/telephony/TelephonyEvent.java`
- Modify: `src/main/java/com/company/outbound/telephony/AriTelephonyAdapter.java`
- Modify: `src/main/java/com/company/outbound/telephony/MockTelephonyAdapter.java`
- Test: `src/test/java/com/company/outbound/telephony/AriTelephonyAdapterTest.java`
- Test: `src/test/java/com/company/outbound/telephony/MockTelephonyLifecycleTest.java`

- [ ] **Step 1: Define the desired port in tests**

The port API must be:

```java
String originate(CallCommand command);
String play(UUID taskId, String channelId, String promptId);
String record(UUID taskId, String channelId, String recordingName,
              int maxDurationSeconds, int maxSilenceSeconds);
void hangup(UUID taskId, String channelId);
void setEventListener(Consumer<TelephonyEvent> listener);
```

Typed events: `CHANNEL_ANSWERED`, `PLAYBACK_FINISHED`, `RECORDING_FINISHED`, `CHANNEL_ENDED`, `OPERATION_FAILED`.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest='AriTelephonyAdapterTest,MockTelephonyLifecycleTest' test`. Expected: compilation failure for the new operations and event types.

- [ ] **Step 3: Implement HTTP request formation**

Use these ARI operations:

```text
POST /ari/channels?endpoint=Mobile%2Fhonor%2F{number}&app=waihu-dialog&appArgs={taskId}
POST /ari/channels/{channelId}/play/{playbackId}?media=sound:custom/{promptId}
POST /ari/channels/{channelId}/record?name={recordingName}&format=wav&maxDurationSeconds=20&maxSilenceSeconds=3&ifExists=fail&beep=false
DELETE /ari/channels/{channelId}
GET ws://.../ari/events?app=waihu-dialog&api_key=user:password
```

Map `StasisStart`, `ChannelStateChange(state=Up)`, `PlaybackFinished`, `RecordingFinished`, `StasisEnd`, and REST failures into typed events. Correlate task IDs from `appArgs`; correlate play/record IDs from generated operation IDs.

- [ ] **Step 4: Add event idempotency and reconnect behavior**

Generate a stable event key from ARI event type plus resource ID and timestamp. WebSocket reconnect uses capped backoff but must not originate or replay a media operation after reconnect.

- [ ] **Step 5: Run focused and full Java tests**

Run `mvn -Dtest='AriTelephonyAdapterTest,MockTelephonyLifecycleTest' test`, then `mvn test`. Expected: all pass.

- [ ] **Step 6: Local checkpoint**

Run `git diff --check`; do not commit.

### Task 5: Implement transcription and constrained decision adapters

**Files:**
- Create: `src/main/java/com/company/outbound/processing/SenseVoiceTranscriptionAdapter.java`
- Create: `src/main/java/com/company/outbound/processing/DecisionPort.java`
- Create: `src/main/java/com/company/outbound/processing/RuleDecisionAdapter.java`
- Create: `src/main/java/com/company/outbound/processing/GptDecisionAdapter.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/company/outbound/processing/SenseVoiceTranscriptionAdapterTest.java`
- Test: `src/test/java/com/company/outbound/processing/GptDecisionAdapterTest.java`

- [ ] **Step 1: Write HTTP contract tests**

SenseVoice must POST `{"filename":"...wav"}` and reject empty text. GPT-compatible decisions must be deserialized into `DialogDecision` and reject unknown `intent`, unknown `nextNode`, confidence outside `0..1`, or a next node not allowed by the state machine.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest='SenseVoiceTranscriptionAdapterTest,GptDecisionAdapterTest' test`. Expected: compilation failure because adapters do not exist.

- [ ] **Step 3: Implement SenseVoice adapter**

Read base URL from `outbound.processing.sensevoice.base-url`, use a 30-second request timeout, and map non-2xx responses to safe errors containing status and service error code but never full response bodies or phone numbers.

- [ ] **Step 4: Implement decision ports**

`RuleDecisionAdapter` recognizes deterministic test phrases. `GptDecisionAdapter` sends node, allowed intents, and transcript; configure temperature `0`, require JSON output, and validate the result before returning.

- [ ] **Step 5: Run tests**

Run focused tests and then `mvn test`. Expected: all pass.

- [ ] **Step 6: Local checkpoint**

Run `git diff --check`; do not commit.

### Task 6: Build the event-driven two-round orchestrator

**Files:**
- Create: `src/main/java/com/company/outbound/dialog/DialogOrchestrator.java`
- Modify: `src/main/java/com/company/outbound/telephony/CallOrchestrator.java`
- Modify: `src/main/java/com/company/outbound/processing/ProcessingOrchestrator.java`
- Test: `src/test/java/com/company/outbound/dialog/DialogOrchestratorTest.java`

- [ ] **Step 1: Write a complete happy-path test**

Drive fake events in this exact order:

```text
start -> CHANNEL_ANSWERED -> play q1 -> PLAYBACK_FINISHED
-> record a1 -> RECORDING_FINISHED -> transcribe "是本人"
-> decision SELF_CONFIRMED -> play q2 -> PLAYBACK_FINISHED
-> record a2 -> RECORDING_FINISHED -> transcribe "月底还款"
-> decision HAS_PAYMENT_PLAN -> play closing -> PLAYBACK_FINISHED -> hangup
```

Assert each command happens exactly once and both turns persist independently.

- [ ] **Step 2: Add failing error-path tests**

Cover duplicate ARI events, Q1/Q2 unclear retry once, second unclear to human, transcription retry once, invalid decision to human, channel ending mid-recording, and operation timeout cleanup.

- [ ] **Step 3: Verify RED**

Run `mvn -Dtest=DialogOrchestratorTest test`. Expected: failures because orchestration is not implemented.

- [ ] **Step 4: Implement serialized per-task event handling**

Use a per-task lock or single-thread executor so only one state transition/media operation can run at a time. Before every event action, verify `taskId`, `channelId`, current node, expected event type, and operation ID.

- [ ] **Step 5: Add timeouts and terminal cleanup**

On any terminal outcome, call `hangup` only if the channel is still active, mark the task terminal, cancel pending timers, and ignore later events.

- [ ] **Step 6: Run all Java tests**

Run `mvn test`. Expected: zero failures and no background-thread leakage warnings.

- [ ] **Step 7: Local checkpoint**

Run `git diff --check`; do not commit.

### Task 7: Add the confirmation-gated test API and UI

**Files:**
- Modify: `src/main/java/com/company/outbound/task/CallTaskController.java`
- Modify: `src/main/java/com/company/outbound/task/CallTaskView.java`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`
- Test: `src/test/java/com/company/outbound/task/CallTaskControllerTest.java`
- Test: `src/test/java/com/company/outbound/system/StaticPageTest.java`

- [ ] **Step 1: Write failing controller tests**

Require creation first and explicit confirmation on start:

```json
POST /api/tasks/{id}/start-dialog
{"confirmed":true}
```

`confirmed=false` or missing must return 400. Starting when SenseVoice is not ready or mobile is not `Free` must return 409 without originating.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest='CallTaskControllerTest,StaticPageTest' test` and confirm failures.

- [ ] **Step 3: Implement API and safe readiness checks**

Keep single-number validation (`^1\d{10}$`). Return task detail with ordered turns, masked number, current node, terminal outcome, and `needHuman`.

- [ ] **Step 4: Implement UI**

The page must require a visible confirmation step, disable start while a task is active, render every turn, link to recordings through a controlled download endpoint, and parse empty/non-JSON HTTP responses without throwing `Unexpected end of JSON input`.

- [ ] **Step 5: Run UI/controller and full tests**

Run focused tests, then `mvn test`. Expected: all pass.

- [ ] **Step 6: Local checkpoint**

Run `git diff --check`; do not commit.

### Task 8: Local integration and authorized real-SIM acceptance

**Files:**
- Modify: `README.md` if present, otherwise create it
- Modify: `speech-service/README.md`
- Create: `docs/runbooks/wsl-mobile-dialog-poc.md`

- [ ] **Step 1: Verify all automated suites fresh**

Run:

```powershell
speech-service\.venv\Scripts\python -m pytest speech-service/tests -q
mvn test
```

Expected: all tests pass.

- [ ] **Step 2: Run environment readiness**

Run `scripts/check-wsl-mobile.ps1`. Expected: USB attached, BlueZ active, `honor` connected/free, ARI authenticated, SenseVoice ready.

- [ ] **Step 3: Run a mock two-round browser test**

Use mock telephony, fixed WAV fixtures, and rule decisions. Expected: UI reaches `COMPLETED`, shows two distinct recordings/transcripts, and no real phone call occurs.

- [ ] **Step 4: Obtain explicit authorization for one real call**

Require the user to provide the exact test number and confirmation in the current conversation. Never infer authorization from an older call.

- [ ] **Step 5: Run one real two-round call**

Verify clear Q1/Q2 playback, separate answer WAV files, non-empty transcription, valid decisions, correct closing prompt, automatic hangup, and `honor` returning to `Free`.

- [ ] **Step 6: Verify artifacts and no orphan resources**

Check Asterisk channels, mobile state, Java task state, recording files, and Python process count. Expected: no active orphan channel and exactly one speech-service model process.

- [ ] **Step 7: Final local review**

Run `git diff --check`, `git status --short`, and summarize modified/untracked files. Do not commit or push.
