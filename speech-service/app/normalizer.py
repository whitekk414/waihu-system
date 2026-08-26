from dataclasses import dataclass
import math
import re

from .errors import ServiceError


TAG_PATTERN = re.compile(r"<\|([^|]+)\|>")
LANGUAGES = {"zh", "en", "yue", "ja", "ko", "auto"}
EMOTIONS = {"neutral", "happy", "sad", "angry", "fearful", "disgusted", "surprised"}


@dataclass(frozen=True)
class NormalizedResult:
    text: str
    raw_text: str
    language: str | None
    emotion: str | None


def normalize_result(raw_text: str) -> NormalizedResult:
    tags = [match.lower() for match in TAG_PATTERN.findall(raw_text)]
    language = next((tag for tag in tags if tag in LANGUAGES), None)
    emotion = next((tag for tag in tags if tag in EMOTIONS), None)
    text = TAG_PATTERN.sub("", raw_text)
    text = re.sub(r"\s+", " ", text).strip()
    if _is_pathological_repetition(text):
        raise ServiceError("ABNORMAL_REPETITION", "Recognition result contains abnormal repetition")
    return NormalizedResult(text=text, raw_text=raw_text, language=language, emotion=emotion)


def _is_pathological_repetition(text: str) -> bool:
    compact = re.sub(r"[\s，。！？、,.!?;；:：]+", "", text)
    if len(compact) <= 24:
        return False
    for unit_length in range(1, min(12, len(compact) // 2) + 1):
        unit = compact[:unit_length]
        expected = (unit * math.ceil(len(compact) / unit_length))[: len(compact)]
        matches = sum(left == right for left, right in zip(compact, expected))
        if matches / len(compact) >= 0.8:
            return True
    return False
