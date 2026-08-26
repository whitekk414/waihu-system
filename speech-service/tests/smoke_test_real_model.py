"""Manual real-model smoke test; excluded from normal pytest collection."""

import json
from pathlib import Path
import sys

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

from app.audio import AudioInspector
from app.engine import FunAsrEngine
from app.path_guard import PathGuard
from app.service import TranscriptionService


def main() -> int:
    recordings = SERVICE_ROOT.parent / "recordings"
    engine = FunAsrEngine()
    engine.load()
    service = TranscriptionService(engine, AudioInspector(), PathGuard(recordings))
    for filename in ("1784619067.2.wav", "1784620809.0.wav"):
        try:
            result = service.transcribe_local(filename)
            print(json.dumps(result.model_dump(), ensure_ascii=False, indent=2))
        except Exception as error:
            print(json.dumps({"filename": filename, "error": str(error)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
