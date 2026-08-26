from collections.abc import Callable
from contextlib import asynccontextmanager
from pathlib import Path
import tempfile
import threading
import uuid

from fastapi import FastAPI, Request, UploadFile
from fastapi.responses import JSONResponse

from .audio import AudioInspector
from .config import load_settings
from .engine import SenseVoiceOnnxEngine
from .errors import ServiceError
from .path_guard import PathGuard
from .schemas import ErrorResponse, LocalTranscriptionRequest, TranscriptionResponse
from .service import TranscriptionService


ERROR_STATUS = {
    "INVALID_FILENAME": 400,
    "INVALID_WAV": 400,
    "RECORDING_NOT_FOUND": 404,
    "FILE_TOO_LARGE": 413,
    "AUDIO_TOO_LONG": 413,
    "ABNORMAL_REPETITION": 422,
    "NO_SPEECH": 422,
    "MODEL_NOT_READY": 503,
}


def create_app(
    service: TranscriptionService | None = None,
    engine_ready: Callable[[], bool] | None = None,
    engine_status: Callable[[], tuple[str, str | None]] | None = None,
) -> FastAPI:
    settings = load_settings()
    engine = None
    if service is None:
        engine = SenseVoiceOnnxEngine(
            settings.onnx_model_path,
            settings.tokens_path,
            settings.num_threads,
        )
        service = TranscriptionService(
            engine,
            AudioInspector(settings.max_file_bytes, settings.max_duration_seconds),
            PathGuard(settings.recordings_root),
        )
    ready = engine_ready or (lambda: bool(engine and engine.ready))
    status = engine_status or (
        lambda: (engine.state, engine.load_error) if engine else
        ("ready" if ready() else "loading", None)
    )

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if engine is not None:
            threading.Thread(target=engine.load, name="sensevoice-loader", daemon=True).start()
        yield

    app = FastAPI(
        title="Local SenseVoice Transcription API",
        version="1.0.0",
        lifespan=lifespan,
    )

    @app.exception_handler(ServiceError)
    async def handle_service_error(_: Request, error: ServiceError):
        trace_id = str(uuid.uuid4())
        body = ErrorResponse(code=error.code, message=error.message, traceId=trace_id)
        return JSONResponse(status_code=ERROR_STATUS.get(error.code, 500), content=body.model_dump())

    @app.get("/health")
    def health() -> dict[str, str | bool | None]:
        model_state, load_error = status()
        return {
            "status": "ok",
            "modelState": model_state,
            "modelReady": model_state == "ready",
            "model": "SenseVoiceSmall",
            "loadError": load_error,
        }

    @app.post("/api/v1/transcriptions/local", response_model=TranscriptionResponse)
    def transcribe_local(request: LocalTranscriptionRequest):
        return service.transcribe_local(request.filename)

    @app.post("/api/v1/transcriptions/upload", response_model=TranscriptionResponse)
    async def transcribe_upload(file: UploadFile):
        if not file.filename or Path(file.filename).suffix.lower() != ".wav":
            raise ServiceError("INVALID_FILENAME", "Only WAV uploads are allowed")
        temporary_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temporary:
                temporary_path = Path(temporary.name)
                size = 0
                while chunk := await file.read(1024 * 1024):
                    size += len(chunk)
                    if size > settings.max_file_bytes:
                        raise ServiceError("FILE_TOO_LARGE", "Audio file exceeds the configured size limit")
                    temporary.write(chunk)
            return service.transcribe_upload(temporary_path)
        finally:
            await file.close()
            if temporary_path is not None:
                temporary_path.unlink(missing_ok=True)

    return app


app = create_app()
