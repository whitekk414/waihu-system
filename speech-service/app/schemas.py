from pydantic import BaseModel, Field


class LocalTranscriptionRequest(BaseModel):
    filename: str = Field(min_length=5, max_length=255)


class SegmentResponse(BaseModel):
    startMs: int
    endMs: int
    text: str


class AudioResponse(BaseModel):
    durationMs: int
    speechDurationMs: int | None = None
    sampleRate: int
    channels: int


class TranscriptionResponse(BaseModel):
    text: str
    rawText: str
    language: str | None = None
    emotion: str | None = None
    segments: list[SegmentResponse] = Field(default_factory=list)
    audio: AudioResponse
    model: str = "SenseVoiceSmall"
    elapsedMs: int


class ErrorResponse(BaseModel):
    code: str
    message: str
    traceId: str
