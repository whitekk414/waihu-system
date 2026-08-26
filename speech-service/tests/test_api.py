from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.errors import ServiceError
from app.main import create_app
from app.schemas import AudioResponse, TranscriptionResponse


def response() -> TranscriptionResponse:
    return TranscriptionResponse(
        text="你好",
        rawText="<|zh|>你好",
        language="zh",
        audio=AudioResponse(durationMs=1000, sampleRate=8000, channels=1),
        elapsedMs=12,
    )


class FakeService:
    def __init__(self):
        self.filename = None
        self.upload_bytes = None

    def transcribe_local(self, filename: str):
        self.filename = filename
        return response()

    def transcribe_upload(self, path: Path):
        self.upload_bytes = path.read_bytes()
        path.unlink(missing_ok=True)
        return response()


def test_local_endpoint_accepts_filename():
    service = FakeService()
    client = TestClient(create_app(service=service, engine_ready=lambda: True))

    result = client.post("/api/v1/transcriptions/local", json={"filename": "call.wav"})

    assert result.status_code == 200
    assert result.json()["text"] == "你好"
    assert service.filename == "call.wav"


def test_upload_endpoint_passes_file_to_service():
    service = FakeService()
    client = TestClient(create_app(service=service, engine_ready=lambda: True))

    result = client.post(
        "/api/v1/transcriptions/upload",
        files={"file": ("call.wav", b"RIFF-test", "audio/wav")},
    )

    assert result.status_code == 200
    assert service.upload_bytes == b"RIFF-test"


@pytest.mark.parametrize(
    ("code", "status"),
    [
        ("INVALID_FILENAME", 400),
        ("INVALID_WAV", 400),
        ("RECORDING_NOT_FOUND", 404),
        ("FILE_TOO_LARGE", 413),
        ("AUDIO_TOO_LONG", 413),
        ("ABNORMAL_REPETITION", 422),
        ("MODEL_NOT_READY", 503),
    ],
)
def test_maps_service_error_codes(code: str, status: int):
    class FailingService(FakeService):
        def transcribe_local(self, filename: str):
            raise ServiceError(code, "safe message")

    client = TestClient(create_app(service=FailingService(), engine_ready=lambda: True))

    result = client.post("/api/v1/transcriptions/local", json={"filename": "call.wav"})

    assert result.status_code == status
    assert result.json()["code"] == code
    assert result.json()["message"] == "safe message"
    assert result.json()["traceId"]
