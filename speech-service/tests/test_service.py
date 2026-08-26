from pathlib import Path
import wave

import pytest

from app.audio import AudioInspector
from app.engine import EngineResult
from app.errors import ServiceError
from app.path_guard import PathGuard
from app.service import TranscriptionService


def write_wav(path: Path):
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(8000)
        output.writeframes(b"\x00\x00" * 8000)


class FakeEngine:
    def __init__(self, ready: bool = True):
        self.ready = ready
        self.paths: list[Path] = []

    def transcribe(self, path: Path) -> EngineResult:
        self.paths.append(path)
        return EngineResult(
            raw_text="<|zh|><|NEUTRAL|><|Speech|>你好",
            segments=[{"start_ms": 100, "end_ms": 800, "text": "你好"}],
            speech_duration_ms=700,
        )


def build_service(root: Path, engine: FakeEngine) -> TranscriptionService:
    return TranscriptionService(engine, AudioInspector(), PathGuard(root))


def test_local_and_upload_use_the_same_engine_and_metadata(tmp_path: Path):
    local = tmp_path / "local.wav"
    upload = tmp_path / "upload.wav"
    write_wav(local)
    write_wav(upload)
    engine = FakeEngine()
    service = build_service(tmp_path, engine)

    local_result = service.transcribe_local("local.wav")
    upload_result = service.transcribe_upload(upload)

    assert local_result.text == upload_result.text == "你好"
    assert local_result.audio.durationMs == 1000
    assert local_result.audio.speechDurationMs == 700
    assert local_result.segments[0].startMs == 100
    assert len(engine.paths) == 2
    assert not upload.exists()
    assert local.exists()


def test_upload_is_deleted_when_engine_fails(tmp_path: Path):
    upload = tmp_path / "upload.wav"
    write_wav(upload)

    class BrokenEngine(FakeEngine):
        def transcribe(self, path: Path) -> EngineResult:
            raise RuntimeError("boom")

    with pytest.raises(RuntimeError):
        build_service(tmp_path, BrokenEngine()).transcribe_upload(upload)

    assert not upload.exists()


def test_rejects_request_when_model_is_not_ready(tmp_path: Path):
    recording = tmp_path / "call.wav"
    write_wav(recording)

    with pytest.raises(ServiceError) as caught:
        build_service(tmp_path, FakeEngine(ready=False)).transcribe_local("call.wav")

    assert caught.value.code == "MODEL_NOT_READY"
