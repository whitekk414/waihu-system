from pathlib import Path

from .errors import ServiceError


class PathGuard:
    def __init__(self, recordings_root: Path):
        self.root = recordings_root.resolve()

    def resolve_recording(self, filename: str) -> Path:
        candidate_name = Path(filename)
        if (
            not filename
            or candidate_name.name != filename
            or "/" in filename
            or "\\" in filename
            or ".." in filename
            or candidate_name.suffix.lower() != ".wav"
        ):
            raise ServiceError("INVALID_FILENAME", "Only a WAV filename is allowed")
        candidate = self.root / filename
        try:
            resolved = candidate.resolve(strict=True)
        except FileNotFoundError as error:
            raise ServiceError("RECORDING_NOT_FOUND", "Recording does not exist") from error
        if not resolved.is_file() or not resolved.is_relative_to(self.root):
            raise ServiceError("INVALID_FILENAME", "Recording is outside the allowed directory")
        return resolved
