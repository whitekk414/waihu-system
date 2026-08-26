from dataclasses import dataclass
import os
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    recordings_root: Path
    onnx_model_path: Path
    tokens_path: Path
    num_threads: int = 2
    max_file_bytes: int = 20 * 1024 * 1024
    max_duration_seconds: int = 600


def load_settings() -> Settings:
    default_root = Path(__file__).resolve().parents[2] / "recordings"
    default_model_root = (
        Path(__file__).resolve().parents[1]
        / ".models"
        / "verified"
        / "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"
    )
    configured_root = Path(os.environ.get("SPEECH_RECORDINGS_ROOT", default_root))
    return Settings(
        recordings_root=configured_root.resolve(),
        onnx_model_path=Path(os.environ.get(
            "SENSEVOICE_ONNX_MODEL_PATH", default_model_root / "model.int8.onnx"
        )).resolve(),
        tokens_path=Path(os.environ.get(
            "SENSEVOICE_TOKENS_PATH", default_model_root / "tokens.txt"
        )).resolve(),
        num_threads=int(os.environ.get("SENSEVOICE_NUM_THREADS", "2")),
    )
