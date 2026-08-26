from pathlib import Path
import time

from .audio import AudioInspector
from .engine import Engine
from .errors import ServiceError
from .normalizer import normalize_result
from .path_guard import PathGuard
from .schemas import AudioResponse, SegmentResponse, TranscriptionResponse


class TranscriptionService:
    def __init__(self, engine: Engine, inspector: AudioInspector, path_guard: PathGuard):
        self.engine = engine
        self.inspector = inspector
        self.path_guard = path_guard

    def transcribe_local(self, filename: str) -> TranscriptionResponse:
        return self._transcribe(self.path_guard.resolve_recording(filename), delete_after=False)

    def transcribe_upload(self, temporary_path: Path) -> TranscriptionResponse:
        return self._transcribe(temporary_path, delete_after=True)

    def _transcribe(self, path: Path, *, delete_after: bool) -> TranscriptionResponse:
        started = time.perf_counter()
        try:
            if not self.engine.ready:
                raise ServiceError("MODEL_NOT_READY", "SenseVoice model is not ready")
            metadata = self.inspector.inspect(path)
            engine_result = self.engine.transcribe(path)
            normalized = normalize_result(engine_result.raw_text)
            segments = [
                SegmentResponse(
                    startMs=int(segment["start_ms"]),
                    endMs=int(segment["end_ms"]),
                    text=str(segment.get("text", "")),
                )
                for segment in engine_result.segments
            ]
            return TranscriptionResponse(
                text=normalized.text,
                rawText=normalized.raw_text,
                language=normalized.language,
                emotion=normalized.emotion,
                segments=segments,
                audio=AudioResponse(
                    durationMs=metadata.duration_ms,
                    speechDurationMs=engine_result.speech_duration_ms,
                    sampleRate=metadata.sample_rate,
                    channels=metadata.channels,
                ),
                elapsedMs=round((time.perf_counter() - started) * 1000),
            )
        finally:
            if delete_after:
                path.unlink(missing_ok=True)
