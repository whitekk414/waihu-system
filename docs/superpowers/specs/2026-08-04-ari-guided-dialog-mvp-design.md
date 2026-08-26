# Java + Asterisk ARI 语音引导对话 MVP 设计

## 1. 目标

在已打通的“荣耀手机 SIM + 蓝牙 HFP + WSL2 Asterisk `chan_mobile`”链路上，实现一个两轮语音引导对话：Java 通过 Asterisk ARI 控制拨号、播放预制问题、分段录音，调用 SenseVoice 转写，再调用模型做结构化判断。

本阶段只做单路 PoC，不做登录、并发调度、抢话打断、动态 TTS 和自动批量拨号。

## 2. 已验证基础

- CSR8510 USB 蓝牙适配器已透传到 Ubuntu 22.04 WSL2。
- BlueZ 已与手机配对，手机暴露 HFP Audio Gateway。
- Asterisk `chan_mobile` 设备 `honor` 显示 `Connected=Yes, State=Free`。
- Asterisk 已能通过 SIM 拨打真人号码并清晰播放 WAV。
- Asterisk 已能录制接听端回答为 8 kHz/16-bit/单声道 WAV。
- SenseVoice 接口已存在，但当前本地模型加载卡住，必须在状态机联调前修复。

## 3. 架构

### 3.1 Java 对话编排器

Java 服务是唯一的对话状态所有者，通过 ARI 订阅通道、播放和录音事件。它负责：

- 创建与启动测试任务。
- 经由 `Mobile/honor/{number}` 拨号。
- 在接通后按节点播放预制音频。
- 对每个问题单独录音。
- 调用 SenseVoice 和模型判断接口。
- 根据结构化结果转移状态。
- 记录每轮原始音频、转写、判断和异常。

### 3.2 WSL2 Asterisk

Asterisk 负责通话媒体，不承载业务决策：

- `chan_mobile` 接入手机 SIM 通话。
- ARI 接受 Java 的 originate、playback、record 和 hangup 指令。
- 录音规则：最长 20 秒，连续静音 3 秒结束，WAV 格式。
- 播放规则：只使用 Asterisk 声音目录中已验证的 8 kHz 单声道预制音频。

### 3.3 SenseVoice 转写服务

- 使用独立 HTTP API，不将 Python 模型嵌入 Java。
- 输入是单轮 WAV 文件。
- 输出包含文字、分段、语音时长和错误码。
- 模型必须使用已缓存的本地路径启动，避免启动时进行远端仓库解析。

### 3.4 模型判断接口

模型不能直接执行拨号或挂断，只返回约束 JSON：

```json
{
  "intent": "SELF_CONFIRMED",
  "confidence": 0.95,
  "nextNode": "ASK_PAYMENT_PLAN",
  "needHuman": false,
  "summary": "客户确认本人接听"
}
```

Java 校验枚举、必填字段和置信度后才允许状态转移。

## 4. 两轮对话流程

### 第一轮：是否本人

播放：“您好，请问是某某本人吗？请回答是或不是。”

判断枚举：

- `SELF_CONFIRMED`：进入第二轮。
- `NOT_SELF`：播放结束语并挂断。
- `UNCLEAR`：重试当前问题一次。
- 第二次仍不明确：标记人工处理并挂断。

### 第二轮：还款计划

播放：“请问您近期是否有还款计划？”

判断枚举：

- `HAS_PAYMENT_PLAN`
- `NO_PAYMENT_PLAN`
- `REFUSE_TO_ANSWER`
- `UNCLEAR`

完成判断后播放结束语，保存任务结果并挂断。`UNCLEAR` 重试一次，仍不明确则标记人工。

## 5. 状态模型

```text
CREATED
  -> DIALING
  -> ANSWERED
  -> PLAYING_Q1
  -> RECORDING_A1
  -> TRANSCRIBING_A1
  -> DECIDING_A1
  -> PLAYING_Q2 | RETRYING_Q1 | COMPLETED_NOT_SELF
  -> RECORDING_A2
  -> TRANSCRIBING_A2
  -> DECIDING_A2
  -> RETRYING_Q2 | COMPLETED | NEEDS_HUMAN
  -> HANGUP
```

任何时刻只允许一个正在执行的媒体操作。所有 ARI 回调通过任务 ID 和通道 ID 校验，忽略过期事件。

## 6. 数据与文件

单次任务保存：

- taskId、测试号码、创建/接通/挂断时间。
- 当前状态、最终结果、是否需要人工。
- 每轮的问题节点、重试次数、WAV 文件名、转写、意图、置信度和摘要。

建议文件名：

```text
{taskId}-q1-attempt1.wav
{taskId}-q2-attempt1.wav
```

号码在日志和页面默认脱敏；测试页面不提供批量导入。

## 7. 错误处理

- 蓝牙或 `honor` 通道不是 `Free`：拒绝启动任务。
- 无应答、忙线或呼叫失败：记录明确结果，不自动重拨。
- 播放/录音事件超时：挂断并标记技术失败。
- SenseVoice 未就绪：不发起新测试；通话中失败则重试一次。
- 模型返回非法 JSON 或非法节点：不执行该转移，标记人工。
- Java 进程异常：重启时将未结束任务标记为中断，不自动重拨。

## 8. 测试页面

保留无登录的本地测试页，提供：

- 输入单个测试号码。
- 明确的二次“确认拨打”操作。
- 实时显示状态机节点。
- 显示每轮问题、录音、转写、意图、置信度和人工标记。
- 提供录音播放和单次刷新，不提供批量拨号。

## 9. 测试策略

- 单元测试：每个意图对应的状态转移、重试上限、非法模型输出、过期 ARI 事件。
- 契约测试：Java 对 SenseVoice 和判断模型 JSON 的校验。
- ARI 适配器测试：拨号、播放、录音、挂断的请求和事件关联。
- 本地集成测试：用固定 WAV 和模型假实现完整跑两轮，不真实拨号。
- 人工端到端测试：仅在明确授权测试号码后，验证 SIM 拨号、播放、回答、分段录音和识别结果。

## 10. 验收标准

1. 不使用商业 SIP 落地或付费语音网关，继续使用荣耀手机 SIM。
2. Java 能从测试页发起一次经确认的两轮对话。
3. 接听端能清晰听到两段预制语音。
4. 两段回答分别生成可播放 WAV，不互相覆盖。
5. SenseVoice 能在可接受时间内返回两段中文转写。
6. 模型输出通过严格 JSON 校验并驱动正确状态转移。
7. 任意外部服务失败时，任务都能收敛到失败或人工状态，不留下正在通话的孤儿通道。

## 11. 实施顺序

1. 修复 SenseVoice 本地模型加载并用已有蓝牙通话 WAV 验证。
2. 在 WSL2 Asterisk 配置 ARI/Stasis，保留现有 Docker SIP PoC 不受影响。
3. 用测试先行实现 Java 对话状态机。
4. 实现 ARI 播放、分段录音和事件关联。
5. 接入 SenseVoice 和模型判断接口。
6. 扩展本地测试页。
7. 先用假通道集成测试，再在明确授权下进行一次真实 SIM 端到端验证。
