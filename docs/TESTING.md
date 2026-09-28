# 测试与验收手册

## 1. 自动化测试

### Java

```powershell
mvn -q test
```

覆盖任务接口、状态迁移、ARI 适配器、对话状态机、中文意图规则、GPT 回退、SenseVoice HTTP 客户端和静态页面。

### SenseVoice API

```powershell
speech-service/.venv/Scripts/python -m pytest -q speech-service/tests
```

覆盖健康检查、WAV 验证、路径防护、文件大小/时长限制、模型状态、转写结果标准化和 API 错误码。

如本地已准备真实 ONNX 模型，可额外执行：

```powershell
speech-service/.venv/Scripts/python speech-service/tests/smoke_test_real_model.py
```

## 2. 不拨号健康检查

以下检查不会创建真实外呼：

```powershell
curl.exe http://127.0.0.1:8090/health
curl.exe http://127.0.0.1:8081/
curl.exe -u "$($env:ASTERISK_ARI_USER):$($env:ASTERISK_ARI_PASSWORD)" http://127.0.0.1:8088/ari/asterisk/info
powershell -ExecutionPolicy Bypass -File scripts/check-wsl-mobile.ps1
```

预期：

- SenseVoice 返回 `status=ok` 且 `modelReady=true`。
- Java 首页返回 HTTP 200。
- ARI 返回 Asterisk 信息，无 401/404。
- 蓝牙适配器已附加到 WSL，`chan_mobile` 模块运行，手机为 `Connected / Free`。

## 3. 话术文件检查

```powershell
Get-ChildItem prompts/*.wav | Select-Object Name,Length
wsl -d Ubuntu-22.04 -- bash -lc "ls -l /var/lib/asterisk/sounds/custom/*.wav"
```

引导流程至少需要：

- `identity-question.wav`
- `processing.wav`
- `identity-confirmed.wav`
- `identity-retry.wav`
- `not-self-closing.wav`
- `visit-consent-question.wav`
- `visit-accepted.wav`
- `visit-declined.wav`
- `visit-retry.wav`
- `contact-time-question.wav`
- `time-retry.wav`
- `manual-closing.wav`
- `closing.wav`

所有话术应为 Asterisk 可直接播放的 8 kHz、单声道、PCM WAV。

## 4. 真人通话验收

### 拨打前必做

- [ ] 测试号码属于项目人员或已获得明确授权。
- [ ] 操作人已核对完整号码，且本次只拨打一个号码。
- [ ] 已确认当地通话录音与自动外呼要求。
- [ ] Asterisk、SenseVoice 和 Java 均已通过健康检查。
- [ ] 手机有信号、SIM 可呼出、蓝牙状态为 `Free`。
- [ ] 页面的“我已获得授权，确认拨打该号码”已由操作人主动勾选。

### 标准通话脚本

1. 创建任务并确认拨打。
2. 接通后听到本人确认问题，回答“是的，我是本人”。
3. 确认听到过渡语，随后进入家访配合问题。
4. 回答“可以，我配合”，确认进入联系时间问题。
5. 回答“周五下午三点”，确认播放结束语并正常挂断。
6. 在 Web 页查看任务状态、时间线、各轮转写和意图。

### 分支验收

| 回答 | 预期行为 |
| --- | --- |
| “不是本人” | 播放信息保护结束语，不再透露业务内容 |
| “不可以，我不同意” | 播放已记录意见的回复，标记后续人工联系 |
| “你是谁”等无法判断内容 | 播放对应追问语并重新录音一次 |
| 连续两次无法判断 | 播放人工跟进结束语，`needManualFollowUp=true` |
| “周五下午三点” | 保存联系时间并正常结束 |

## 5. 结果与数据检查

```powershell
curl.exe http://127.0.0.1:8081/api/tasks
curl.exe http://127.0.0.1:8081/api/tasks/<task-id>
curl.exe http://127.0.0.1:8081/api/tasks/<task-id>/turns
curl.exe http://127.0.0.1:8081/api/events/tasks/<task-id>
Get-ChildItem recordings -Filter "<task-id>*.wav"
```

验收时确认：

- 任务最终为 `COMPLETED`，或在技术异常时为可追溯的 `FAILED`。
- 每次录音的文件名包含任务 ID、问题序号和尝试次数。
- 转写内容与实际回答一致，意图分支正确。
- 结构化结果含本人、家访意愿、联系时间和人工跟进标记。

## 6. 常见故障

| 现象 | 检查方向 |
| --- | --- |
| 页面点击无反应 | 查看浏览器 Network、Java 日志及 `/api/tasks/{id}/start-dialog` 返回码 |
| ARI 401 | 确认 Java 与 `/etc/asterisk/ari.conf` 使用同一组环境变量 |
| ARI 404 | 确认 Asterisk HTTP/ARI 已启用，访问路径含 `/ari` |
| `mobile show devices` 不是 Free | 解除占用通话，重连手机，检查 USB 是否仍附加到 WSL |
| 接通后无声音 | 检查全部 WAV 是否已复制到 Asterisk `custom` 目录且为 8 kHz 单声道 |
| 没有录音 | 检查 Asterisk stored recording、ARI 下载接口和 Java `recordings/` 写权限 |
| SenseVoice 503 | 查看 `/health` 的 `modelState` 与 `loadError`，确认 ONNX 模型和 `tokens.txt` 路径 |
| 回答始终不清楚 | 检查录音是否有声、通话音量和环境噪声；第二次仍不清属预期人工跟进分支 |
| 接通后立即挂断 | 查看 Java `DialogOrchestrator` 错误和 Asterisk CLI 事件，确认首段话术可读 |
