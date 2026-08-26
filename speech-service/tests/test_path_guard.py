from pathlib import Path

import pytest

from app.errors import ServiceError
from app.path_guard import PathGuard


def test_resolves_existing_wav_inside_recordings_root(tmp_path: Path):
    recording = tmp_path / "call.wav"
    recording.write_bytes(b"RIFF")

    assert PathGuard(tmp_path).resolve_recording("call.wav") == recording.resolve()


@pytest.mark.parametrize("filename", ["../secret.wav", "sub/call.wav", "sub\\call.wav", "C:\\secret.wav", "call.mp3"])
def test_rejects_unsafe_or_non_wav_filename(tmp_path: Path, filename: str):
    with pytest.raises(ServiceError) as caught:
        PathGuard(tmp_path).resolve_recording(filename)

    assert caught.value.code == "INVALID_FILENAME"


def test_reports_missing_recording(tmp_path: Path):
    with pytest.raises(ServiceError) as caught:
        PathGuard(tmp_path).resolve_recording("missing.wav")

    assert caught.value.code == "RECORDING_NOT_FOUND"
