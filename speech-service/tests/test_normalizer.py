import pytest

from app.errors import ServiceError
from app.normalizer import normalize_result


def test_extracts_sensevoice_tags_and_clean_text():
    result = normalize_result("<|zh|><|NEUTRAL|><|Speech|><|woitn|> 你好，世界。 ")

    assert result.text == "你好，世界。"
    assert result.raw_text.startswith("<|zh|>")
    assert result.language == "zh"
    assert result.emotion == "neutral"


def test_accepts_text_without_tags_and_normalizes_whitespace():
    result = normalize_result("  今天   可以还款  ")

    assert result.text == "今天 可以还款"
    assert result.language is None
    assert result.emotion is None


def test_rejects_pathological_repetition():
    with pytest.raises(ServiceError) as caught:
        normalize_result("我去找你" * 10)

    assert caught.value.code == "ABNORMAL_REPETITION"


def test_does_not_reject_short_natural_repetition():
    assert normalize_result("好的好的").text == "好的好的"
