# 本地 SenseVoice 转写服务

该服务在本机 CPU 上运行 SenseVoiceSmall + FSMN-VAD，不依赖外部 API Key。默认仅监听 `127.0.0.1:8090`。

## 安装

```powershell
python -m venv speech-service/.venv
speech-service/.venv/Scripts/python -m pip install -r speech-service/requirements.txt
```

服务使用 sherpa-onnx 版 SenseVoiceSmall。请将已校验的 `model.int8.onnx` 和 `tokens.txt` 放到：

```text
speech-service/.models/verified/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/
```

模型不提交到 Git。模型加载完成前 `/health` 的 `modelReady` 为 `false`，转写接口返回 `MODEL_NOT_READY`。

## 启动

```powershell
powershell -ExecutionPolicy Bypass -File speech-service/start.ps1
```

## 接口

健康检查：

```powershell
curl.exe http://127.0.0.1:8090/health
```

上传 WAV：

```powershell
curl.exe -F "file=@recordings/1784620809.0.wav" http://127.0.0.1:8090/api/v1/transcriptions/upload
```

读取项目 `recordings` 中的文件：

```powershell
curl.exe -H "Content-Type: application/json" -d '{"filename":"1784620809.0.wav"}' http://127.0.0.1:8090/api/v1/transcriptions/local
```

本地模式只接受一个 `.wav` 文件名，不接受绝对路径、子目录或 `..`。默认限制为 20 MB、10 分钟。

## 测试

```powershell
speech-service/.venv/Scripts/python -m pytest speech-service/tests -q
speech-service/.venv/Scripts/python speech-service/tests/smoke_test_real_model.py
```

## 当前限制

Asterisk 当前保存的是机器人与客户混合的单声道录音。VAD 能去除静音，但不能区分双方；后续需要 Asterisk 分轨录音才能稳定只识别客户回复。
