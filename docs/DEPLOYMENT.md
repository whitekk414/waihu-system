# 部署手册

本手册描述已实机验证的 Windows 11 + WSL2 Ubuntu 22.04 + Asterisk 18 + USB 蓝牙 + Android 手机 SIM 方案。Docker Compose 配置主要用于 SIP/ARI 软件链路测试；真实 SIM 拨号使用 WSL2 中的 `chan_mobile`。

## 1. 组件与端口

| 组件 | 默认地址 | 作用 |
| --- | --- | --- |
| Spring Boot | `http://localhost:8081` | 任务、状态机、Web 页面、ARI 客户端 |
| Asterisk ARI | `http://localhost:8088/ari` | 拨号、播放、录音和通话事件 |
| SenseVoice | `http://127.0.0.1:8090` | 本地 WAV 语音转写 |
| Asterisk SIP | UDP `5060` | 可选的 Linphone/SIP 链路测试 |

## 2. 前置条件

- Windows 11，已启用 WSL2，Ubuntu 22.04 可正常运行 `systemd`。
- JDK 17、Maven 3.9+、Git、Python 3.10+。
- WSL 内安装 Asterisk 18、`chan_mobile`、BlueZ 和 FFmpeg。
- Windows 安装 `usbipd-win`。
- CSR8510 或已验证兼容的 USB 蓝牙适配器。
- 一台可与适配器配对、已插入 SIM 卡且可正常通话的 Android 手机。
- 首次安装 SenseVoice 模型时需网络；模型文件不进入 Git。

## 3. 获取项目与验证基础环境

```powershell
git clone https://github.com/whitekk414/waihu-system.git
cd waihu-system
java -version
mvn -version
python --version
wsl -d Ubuntu-22.04 -- uname -a
```

## 4. 准备 Asterisk ARI 与手机蓝牙

### 4.1 配对与记录地址

1. 将 USB 蓝牙适配器共享并附加到 WSL，具体 BUSID 以 `usbipd list` 输出为准。
2. 在 WSL 中启动 BlueZ，用 `bluetoothctl` 与手机配对、信任并连接。
3. 记录蓝牙适配器地址和手机蓝牙地址，只放入当前终端的环境变量。

```powershell
$env:ASTERISK_ARI_USER='outbound'
$env:ASTERISK_ARI_PASSWORD='<strong-random-password>'
$env:BLUETOOTH_ADAPTER_ADDRESS='<adapter-mac>'
$env:PHONE_BLUETOOTH_ADDRESS='<phone-mac>'
```

### 4.2 安装配置模板

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-wsl-asterisk-config.ps1 -InstallMobileConfig
```

脚本会保留首次覆盖前的 `*.waihu-backup`，将 ARI 密码与设备地址渲染到 WSL 内，设置蓝牙语音模式并重启 Asterisk。

引导流程需要 `prompts/` 中的全部 WAV。将它们安装到 Asterisk 自定义语音目录：

```powershell
wsl -d Ubuntu-22.04 -u root -- mkdir -p /var/lib/asterisk/sounds/custom
wsl -d Ubuntu-22.04 -u root -- bash -lc "cp /mnt/c/<project-path>/prompts/*.wav /var/lib/asterisk/sounds/custom/ && chown asterisk:asterisk /var/lib/asterisk/sounds/custom/*.wav && chmod 0644 /var/lib/asterisk/sounds/custom/*.wav"
```

`<project-path>` 替换为当前项目目录的 WSL 路径。也可以用 `wslpath -a` 查看 Windows 路径对应值。

### 4.3 健康检查

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-wsl-mobile.ps1
wsl -d Ubuntu-22.04 -- asterisk -rx "mobile show devices"
```

目标设备状态应为 `Connected: Yes` 且 `State: Free`。

## 5. 安装与启动 SenseVoice

```powershell
python -m venv speech-service/.venv
speech-service/.venv/Scripts/python -m pip install --upgrade pip
speech-service/.venv/Scripts/python -m pip install -r speech-service/requirements.txt
```

`speech-service/start.ps1` 默认从以下本地目录读取 ONNX 模型：

```text
speech-service/.models/verified/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/
  model.int8.onnx
  tokens.txt
```

按 `speech-service/README.md` 准备已校验模型后启动：

```powershell
powershell -ExecutionPolicy Bypass -File speech-service/start.ps1
curl.exe http://127.0.0.1:8090/health
```

只有返回 `"modelReady":true` 后才能执行真实转写。

## 6. 构建并启动 Java 服务

```powershell
mvn clean package
java -jar target/waihu-system-0.0.1-SNAPSHOT.jar `
  --server.port=8081 `
  --outbound.dialog.enabled=true `
  --outbound.dialog.fixed-prompt.enabled=true `
  --outbound.dialog.fixed-prompt.playlist=identity-question,visit-consent-question,contact-time-question,closing `
  --outbound.telephony.mode=ari `
  --outbound.processing.mode=real `
  --outbound.processing.transcription=sensevoice `
  --outbound.processing.decision=rule `
  --outbound.telephony.ari.base-url=http://127.0.0.1:8088 `
  --outbound.telephony.ari.username=$env:ASTERISK_ARI_USER `
  --outbound.telephony.ari.password=$env:ASTERISK_ARI_PASSWORD
```

打开 [http://localhost:8081](http://localhost:8081)。页面只允许一个 11 位测试号码，并要求在拨打前勾选授权确认。

## 7. 可选：Docker SIP 软件链路

该方式适合 Linphone/SIP 测试，不代表手机 SIM 链路：

```powershell
Copy-Item .env.example .env
# 先编辑 .env 中的密码和地址
docker compose up --build
```

## 8. 安全和生产前要求

- 密码和设备地址只通过环境变量注入，不要写回模板或提交到 Git。
- `recordings/`、`logs/`、`data/` 必须限制访问，并根据合规保留周期自动删除。
- 正式外呼前完成号码授权、录音告知、拨打时段、拒呼名单和人工申诉流程评审。
- 当前 Web 页面无登录，只允许在受控内网测试，不得直接暴露到互联网。
- 单手机单通道仅用于 PoC；生产化需增加调度、并发限制、监控、审计、失败重试和人工接管。
