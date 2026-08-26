from pathlib import Path

import numpy as np

from app.engine import SenseVoiceOnnxEngine, SherpaOnnxFactory


def test_engine_load_uses_configured_local_paths(monkeypatch, tmp_path: Path):
    captured = {}

    def create(**kwargs):
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(SherpaOnnxFactory, "create", staticmethod(create))
    engine = SenseVoiceOnnxEngine(tmp_path / "model.int8.onnx", tmp_path / "tokens.txt", num_threads=3)

    engine.load()

    assert captured == {
        "model": str(tmp_path / "model.int8.onnx"),
        "tokens": str(tmp_path / "tokens.txt"),
        "num_threads": 3,
        "use_itn": True,
        "debug": False,
    }
    assert engine.state == "ready"
    assert engine.ready is True
    assert engine.load_error is None


def test_engine_exposes_load_failure(monkeypatch, tmp_path: Path):
    def fail(**_kwargs):
        raise RuntimeError("bad model")

    monkeypatch.setattr(SherpaOnnxFactory, "create", staticmethod(fail))
    engine = SenseVoiceOnnxEngine(tmp_path / "model.int8.onnx", tmp_path / "tokens.txt")

    engine.load()

    assert engine.state == "failed"
    assert engine.ready is False
    assert engine.load_error == "bad model"


def test_engine_transcribes_audio_with_sherpa_stream(monkeypatch, tmp_path: Path):
    wav_path = tmp_path / "answer.wav"
    samples = np.array([0.1, -0.1, 0.2], dtype=np.float32)
    accepted = {}

    class Result:
        text = "我是本人"

    class Stream:
        result = Result()

        def accept_waveform(self, sample_rate, audio):
            accepted["sample_rate"] = sample_rate
            accepted["audio"] = audio

    class Recognizer:
        def create_stream(self):
            return Stream()

        def decode_stream(self, stream):
            accepted["decoded"] = stream

    monkeypatch.setattr(SherpaOnnxFactory, "create", staticmethod(lambda **_kwargs: Recognizer()))
    monkeypatch.setattr("app.engine.soundfile.read", lambda *_args, **_kwargs: (samples, 8000))
    engine = SenseVoiceOnnxEngine(tmp_path / "model.int8.onnx", tmp_path / "tokens.txt")
    engine.load()

    result = engine.transcribe(wav_path)

    assert result.raw_text == "我是本人"
    assert accepted["sample_rate"] == 8000
    np.testing.assert_array_equal(accepted["audio"], samples)
    assert accepted["decoded"] is not None
