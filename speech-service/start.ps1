$ErrorActionPreference = 'Stop'
$serviceRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $serviceRoot
$python = Join-Path $serviceRoot '.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $python)) {
    throw 'Virtual environment is missing. Run: python -m venv speech-service/.venv'
}

$env:SPEECH_RECORDINGS_ROOT = Join-Path $projectRoot 'recordings'
$modelRoot = Join-Path $serviceRoot '.models\verified\sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17'
$env:SENSEVOICE_ONNX_MODEL_PATH = Join-Path $modelRoot 'model.int8.onnx'
$env:SENSEVOICE_TOKENS_PATH = Join-Path $modelRoot 'tokens.txt'
& $python -m uvicorn app.main:app --app-dir $serviceRoot --host 127.0.0.1 --port 8090
