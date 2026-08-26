from dataclasses import dataclass
from pathlib import Path
import wave

from .errors import ServiceError


@dataclass(frozen=True)
class AudioMetadata:
    duration_ms: int
    sample_rate: int
    channels: int


class AudioInspector:
    def __init__(self, max_file_bytes: int = 20 * 1024 * 1024, max_duration_seconds: int = 600):
        self.max_file_bytes = max_file_bytes
        self.max_duration_seconds = max_duration_seconds

    def inspect(self, path: Path) -> AudioMetadata:
        if path.stat().st_size > self.max_file_bytes:
            raise ServiceError("FILE_TOO_LARGE", "Audio file exceeds the configured size limit")
        try:
            with wave.open(str(path), "rb") as audio:
                sample_rate = audio.getframerate()
                channels = audio.getnchannels()
                frame_count = audio.getnframes()
                sample_width = audio.getsampwidth()
        except (wave.Error, EOFError, OSError) as error:
            raise ServiceError("INVALID_WAV", "File is not a valid WAV recording") from error
        if sample_rate <= 0 or channels <= 0 or sample_width not in (1, 2, 3, 4):
            raise ServiceError("INVALID_WAV", "WAV audio properties are invalid")
        duration_seconds = frame_count / sample_rate
        if duration_seconds > self.max_duration_seconds:
            raise ServiceError("AUDIO_TOO_LONG", "Audio duration exceeds the configured limit")
        return AudioMetadata(
            duration_ms=round(duration_seconds * 1000),
            sample_rate=sample_rate,
            channels=channels,
        )
