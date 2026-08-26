# SenseVoiceSmall + VAD Transcription API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a CPU-only local FastAPI service that transcribes uploaded or allow-listed local WAV recordings with SenseVoiceSmall and FSMN-VAD.

**Architecture:** A standalone `speech-service` owns HTTP validation, safe local-path resolution, audio inspection, one-time model loading, result normalization, and error mapping. FastAPI handlers depend on a small transcription service, while the real FunASR engine is isolated behind a protocol so unit and API tests do not load model weights.

**Tech Stack:** Python 3.11, FastAPI, Uvicorn, FunASR, SenseVoiceSmall, FSMN-VAD, soundfile, pytest, FastAPI TestClient

---

## File map

- `speech-service/requirements.txt`: pinned runtime and test dependencies.
- `speech-service/app/main.py`: FastAPI construction and exception handlers.
- `speech-service/app/config.py`: environment-backed size, duration, and recordings-root settings.
- `speech-service/app/schemas.py`: request and response models.
- `speech-service/app/errors.py`: stable service error codes.
- `speech-service/app/audio.py`: RIFF/WAVE validation and metadata inspection.
- `speech-service/app/path_guard.py`: recordings-root confinement.
- `speech-service/app/normalizer.py`: SenseVoice tags and repetition handling.
- `speech-service/app/engine.py`: engine protocol and lazy FunASR implementation.
- `speech-service/app/service.py`: shared upload/local transcription workflow.
- `speech-service/tests/`: focused unit and API tests.
- `speech-service/start.ps1`: reproducible local start command.
- `speech-service/README.md`: setup, endpoints, and smoke-test commands.

### Task 1: Service skeleton and health endpoint

**Files:**
- Create: `speech-service/requirements.txt`
- Create: `speech-service/app/__init__.py`
- Create: `speech-service/app/config.py`
- Create: `speech-service/app/main.py`
- Create: `speech-service/tests/test_health.py`

- [ ] **Step 1: Write a failing health test**

```python
from fastapi.testclient import TestClient
from app.main import create_app

def test_health_reports_model_state():
    client = TestClient(create_app(engine_ready=lambda: False))
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {
        "status": "ok", "modelReady": False, "model": "SenseVoiceSmall"
    }
```

- [ ] **Step 2: Verify RED**

Run: `python -m pytest speech-service/tests/test_health.py -q`
Expected: FAIL because `app.main` does not exist.

- [ ] **Step 3: Implement the application factory**

Create a FastAPI factory with an injectable `engine_ready` callable and `GET /health`. Configure recordings root from `SPEECH_RECORDINGS_ROOT`, defaulting to `../recordings`, plus 20 MB and 600-second limits.

- [ ] **Step 4: Verify GREEN**

Run: `python -m pytest speech-service/tests/test_health.py -q`
Expected: `1 passed`.

### Task 2: Safe local filenames and WAV validation

**Files:**
- Create: `speech-service/app/errors.py`
- Create: `speech-service/app/path_guard.py`
- Create: `speech-service/app/audio.py`
- Create: `speech-service/tests/test_path_guard.py`
- Create: `speech-service/tests/test_audio.py`

- [ ] **Step 1: Write failing path-confinement tests**

Test that `resolve_recording("call.wav")` resolves inside a temporary recordings root, while `../secret.wav`, absolute paths, slash/backslash-containing names, and missing files raise `ServiceError` with stable codes `INVALID_FILENAME` or `RECORDING_NOT_FOUND`.

- [ ] **Step 2: Verify path tests fail**

Run: `python -m pytest speech-service/tests/test_path_guard.py -q`
Expected: FAIL because `PathGuard` is missing.

- [ ] **Step 3: Implement `PathGuard`**

Use `Path(filename).name == filename`, reject `/`, `\\`, `..`, and non-`.wav` suffixes, call `resolve(strict=True)`, then verify `resolved.is_relative_to(root.resolve())`.

- [ ] **Step 4: Write failing WAV tests**

Generate RIFF/WAVE fixtures with Python's `wave` module. Assert valid 8 kHz mono PCM returns duration/sample rate/channel metadata; invalid headers raise `INVALID_WAV`; oversized input raises `FILE_TOO_LARGE`; excessive duration raises `AUDIO_TOO_LONG`.

- [ ] **Step 5: Implement WAV inspection and run tests**

Use `wave.open`, file size, frame count, frame rate, and channel count without decoding the whole file. Run:

`python -m pytest speech-service/tests/test_path_guard.py speech-service/tests/test_audio.py -q`

Expected: all pass.

### Task 3: SenseVoice result normalization

**Files:**
- Create: `speech-service/app/normalizer.py`
- Create: `speech-service/app/schemas.py`
- Create: `speech-service/tests/test_normalizer.py`

- [ ] **Step 1: Write failing normalization tests**

Cover output such as `<|zh|><|NEUTRAL|><|Speech|><|woitn|>你好`, whitespace cleanup, missing tags, segment conversion, and pathological repetition such as `我去找你` repeated ten times.

- [ ] **Step 2: Verify RED**

Run: `python -m pytest speech-service/tests/test_normalizer.py -q`
Expected: FAIL because normalization is missing.

- [ ] **Step 3: Implement normalization**

Parse tags with a bounded regular expression, map language and emotion tags to lowercase response values, remove non-content tags, normalize punctuation/whitespace, and reject text when a repeated unit of 1-12 characters occupies at least 80% of a result longer than 24 characters. Return `ABNORMAL_REPETITION` rather than misleading clean text.

- [ ] **Step 4: Verify GREEN**

Run: `python -m pytest speech-service/tests/test_normalizer.py -q`
Expected: all pass.

### Task 4: Lazy FunASR engine and shared transcription workflow

**Files:**
- Create: `speech-service/app/engine.py`
- Create: `speech-service/app/service.py`
- Create: `speech-service/tests/test_service.py`

- [ ] **Step 1: Write failing service tests with a fake engine**

Assert both `transcribe_upload` and `transcribe_local` run the same validator and engine, include audio metadata and elapsed time, delete upload temporary files in success and failure cases, and map an unloaded engine to `MODEL_NOT_READY`.

- [ ] **Step 2: Verify RED**

Run: `python -m pytest speech-service/tests/test_service.py -q`
Expected: FAIL because service/engine modules are missing.

- [ ] **Step 3: Implement the engine boundary**

Define an `Engine` protocol with `ready` and `transcribe(path)`. Implement `FunAsrEngine` with a lock and one-time lazy initialization:

```python
AutoModel(
    model="iic/SenseVoiceSmall",
    vad_model="fsmn-vad",
    vad_kwargs={"max_single_segment_time": 30000},
    trust_remote_code=True,
    device="cpu",
    disable_update=True,
)
```

Generate with Chinese language, ITN enabled, VAD merging enabled, and a 15-second merge length. Keep FunASR imports inside the lazy loader so API/unit tests stay lightweight.

- [ ] **Step 4: Implement the shared workflow**

Validate audio, invoke the engine, normalize output, populate metadata and timing, and clean uploads in `finally`. Do not modify local source recordings.

- [ ] **Step 5: Verify GREEN**

Run: `python -m pytest speech-service/tests/test_service.py -q`
Expected: all pass.

### Task 5: Upload and local HTTP endpoints

**Files:**
- Modify: `speech-service/app/main.py`
- Modify: `speech-service/app/schemas.py`
- Create: `speech-service/tests/test_api.py`

- [ ] **Step 1: Write failing API tests**

Use an injected fake service to verify `POST /api/v1/transcriptions/upload` accepts multipart field `file`, `POST /api/v1/transcriptions/local` accepts `{ "filename": "call.wav" }`, and service codes map to HTTP 400/404/413/422/503/500 with `{code,message,traceId}`.

- [ ] **Step 2: Verify RED**

Run: `python -m pytest speech-service/tests/test_api.py -q`
Expected: route tests fail with 404.

- [ ] **Step 3: Implement endpoints and safe error mapping**

Read uploads into a `NamedTemporaryFile` in bounded chunks, reject non-WAV filename/content, return Pydantic response models, and log internal exceptions with trace IDs without returning absolute paths.

- [ ] **Step 4: Verify GREEN and full unit suite**

Run: `python -m pytest speech-service/tests -q`
Expected: all pass.

### Task 6: Install, real-model smoke test, and documentation

**Files:**
- Create: `speech-service/start.ps1`
- Create: `speech-service/README.md`
- Create: `speech-service/tests/smoke_test_real_model.py`

- [ ] **Step 1: Create an isolated virtual environment and install dependencies**

Run:

```powershell
python -m venv speech-service/.venv
speech-service/.venv/Scripts/python -m pip install -r speech-service/requirements.txt
```

Expected: installation completes without dependency conflicts.

- [ ] **Step 2: Run the full suite inside the environment**

Run: `speech-service/.venv/Scripts/python -m pytest speech-service/tests -q`
Expected: all pass.

- [ ] **Step 3: Start the local service**

`start.ps1` must set `SPEECH_RECORDINGS_ROOT` to the absolute project recordings directory and launch:

```powershell
python -m uvicorn app.main:app --app-dir speech-service --host 127.0.0.1 --port 8090
```

- [ ] **Step 4: Run real-model smoke tests**

Call `/health`, then submit `1784619067.2.wav` and `1784620809.0.wav` through the local endpoint. Record text, language/emotion when present, VAD segments, elapsed time, and whether repetition protection triggered. Do not claim accuracy without listening-based ground truth.

- [ ] **Step 5: Verify API manually**

```powershell
curl.exe -F "file=@recordings/1784620809.0.wav" http://127.0.0.1:8090/api/v1/transcriptions/upload
curl.exe -H "Content-Type: application/json" -d '{"filename":"1784620809.0.wav"}' http://127.0.0.1:8090/api/v1/transcriptions/local
```

Expected: both return matching structured JSON or the same stable diagnostic error.

- [ ] **Step 6: Document operation and limitations**

Document first-run model download, CPU warm-up latency, endpoint examples, recordings-root confinement, 20 MB/10-minute limits, and the mixed-channel limitation. Do not add Git commit steps because the user explicitly requested local-only changes.

## Final verification

- [ ] Run `python -m pytest speech-service/tests -q`.
- [ ] Confirm `GET http://127.0.0.1:8090/health` returns 200.
- [ ] Confirm upload and local modes handle the same WAV.
- [ ] Confirm `../` local filenames are rejected.
- [ ] Confirm the service binds only to `127.0.0.1`.
- [ ] Confirm no secrets or customer recordings were added to Git.
