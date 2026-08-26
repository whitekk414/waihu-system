from dataclasses import dataclass, field
from pathlib import Path
import threading
from typing import Protocol

import numpy as np
import soundfile


@dataclass(frozen=True)
class EngineResult:
    raw_text: str
    segments: list[dict] = field(default_factory=list)
    speech_duration_ms: int | None = None


class Engine(Protocol):
    ready: bool

    def transcribe(self, path: Path) -> EngineResult: ...


class SherpaOnnxFactory:
    @staticmethod
    def create(**kwargs):
        import sherpa_onnx

        return sherpa_onnx.OfflineRecognizer.from_sense_voice(**kwargs)


class SenseVoiceOnnxEngine:
    def __init__(
        self,
        model_path: str | Path,
        tokens_path: str | Path,
        num_threads: int = 2,
    ):
        self.ready = False
        self.state = "loading"
        self.load_error: str | None = None
        self._model_path = str(model_path)
        self._tokens_path = str(tokens_path)
        self._num_threads = num_threads
        self._recognizer = None
        self._lock = threading.Lock()

    def load(self) -> None:
        if self.ready:
            return
        with self._lock:
            if self.ready:
                return
            try:
                self._recognizer = SherpaOnnxFactory.create(
                    model=self._model_path,
                    tokens=self._tokens_path,
                    num_threads=self._num_threads,
                    use_itn=True,
                    debug=False,
                )
                self.ready = True
                self.state = "ready"
                self.load_error = None
            except Exception as error:
                self.ready = False
                self.state = "failed"
                self.load_error = str(error)

    def transcribe(self, path: Path) -> EngineResult:
        if not self.ready or self._recognizer is None:
            raise RuntimeError("SenseVoice ONNX model is not loaded")
        samples, sample_rate = soundfile.read(str(path), dtype="float32", always_2d=False)
        if samples.ndim > 1:
            samples = np.mean(samples, axis=1, dtype=np.float32)
        samples = np.ascontiguousarray(samples, dtype=np.float32)
        stream = self._recognizer.create_stream()
        stream.accept_waveform(sample_rate, samples)
        self._recognizer.decode_stream(stream)
        return EngineResult(raw_text=str(stream.result.text).strip())
