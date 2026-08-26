# GPT-5.4 决策与本地规则降级设计

## 目标

SenseVoice 完成单轮转写后，优先调用模型审计平台 UAT 的 `openai.gpt-5.4` 做受约束意图判断；请求超时、接口异常或结果不合法时，立即使用现有本地规则继续通话。

## 接口

- 地址：`http://uat-front.mstar.mx/model-audit-platform/api/v1/chat/completions`
- 请求头：`X-LLM-Env: UAT`、每次唯一的 `X-Request-Id`、`tenant_id: 10001`、`Content-Type: application/json`
- 模型：`openai.gpt-5.4`
- 超时：15 秒，不重试
- 不使用 Authorization 请求头

## 决策约束

模型只返回 `intent`、`confidence`、`nextNode`、`needHuman`、`summary`。Java 必须校验枚举、置信度范围以及状态机允许的下一节点。任何 HTTP、超时、空内容、JSON 或业务校验异常均降级到 `RuleDecisionAdapter`。

## 安全与可观测性

请求不包含手机号，只包含当前节点、允许意图和转写文本。日志记录请求 ID、耗时及降级原因，不输出完整转写、手机号或模型响应体。

## 验收

接口契约测试验证三个 UAT 请求头和模型字段；降级测试覆盖超时/HTTP 异常/非法 JSON；使用历史转写做一次真实 UAT 决策测试，不拨打电话。
