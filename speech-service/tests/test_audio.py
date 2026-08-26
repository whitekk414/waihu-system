from pathlib import Path
import wave

import pytest

from app.audio import AudioInspector
from app.errors import ServiceError


def write_wav(path: Path, *, seconds: float = 1.0, sample_rate: int = 8000, channels: int = 1):
    with wave.open(str(path), "wb") as output:
        output.setnchannels(channels)
        output.setsampwidth(2)
        output.setframerate(sample_rate)
        output.writeframes(b"\x00\x00" * int(seconds * sample_rate) * channels)


def test_inspects_valid_telephone_wav(tmp_path: Path):
    path = tmp_path / "call.wav"
    write_wav(path, seconds=1.25)

    metadata = AudioInspector(max_file_bytes=100_000, max_duration_seconds=10).inspect(path)

    assert metadata.duration_ms == 1250
    assert metadata.sample_rate == 8000
    assert metadata.channels == 1


def test_rejects_invalid_wav(tmp_path: Path):
    path = tmp_path / "bad.wav"
    path.write_bytes(b"not a wave")

    with pytest.raises(ServiceError) as caught:
        AudioInspector().inspect(path)

    assert caught.value.code == "INVALID_WAV"


def test_rejects_oversized_wav(tmp_path: Path):
    path = tmp_path / "large.wav"
    write_wav(path)

    with pytest.raises(ServiceError) as caught:
        AudioInspector(max_file_bytes=10).inspect(path)

    assert caught.value.code == "FILE_TOO_LARGE"


def test_rejects_excessive_duration(tmp_path: Path):
    path = tmp_path / "long.wav"
    write_wav(path, seconds=2)

    with pytest.raises(ServiceError) as caught:
        AudioInspector(max_duration_seconds=1).inspect(path)

    assert caught.value.code == "AUDIO_TOO_LONG"
