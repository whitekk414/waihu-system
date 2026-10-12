# macOS 微信聊天记录 MCP 部署参考

本文根据用户提供的文章整理，去除了网页导航、评论区和重复目录，保留部署顺序与常用命令。本文是参考资料，不代表外呼系统已接入微信，也不代表相关工具已经在本仓库测试通过。

## 来源与适用范围

- 原作者：Myluzh Blog / myluzh。
- 原文：[《wechat-mac-reader + chatlog》](https://itho.cn/ai-agent/597.html)。
- 原文标注发布时间：2026-05-11 17:21。
- 整理日期：2026-10-12。
- 原文环境：Apple Silicon / arm64、macOS WeChat 4.1.8、Claude Code 2.1.138。
- 以下版本、兼容性及命令来自所提供的文章，未重新验证；实际部署以各项目当前说明为准。

相关项目：

- [wechat-mac-reader](https://github.com/xingfanxia/wechat-mac-reader)：生成读取配置。
- [wechat-db-decrypt-macos](https://github.com/Thearas/wechat-db-decrypt-macos)：提取数据库解密所需信息。
- [chatlog-bot](https://github.com/TE0dollary/chatlog-bot)：解密、查询数据库，提供 HTTP API / MCP。

## 工作方式与数据边界

处理顺序为：本机微信数据库 → 提取 key → 生成配置 → chatlog-bot 解密 → 本地 HTTP / MCP → 客户端查询与总结。

原文使用 `127.0.0.1:5030` 作为服务地址。数据库和解密服务在本机，不等于整个 AI 总结过程都在本机：如果客户端使用云端模型，发送给模型的聊天文本仍可能离开设备。需要完全本地处理时，还必须使用本地模型并确认客户端的数据流。

以下运行产物不应提交到 GitHub：

| 路径 | 内容 |
| --- | --- |
| `wechat_keys.json` | 数据库解密 key |
| `data/config.yaml` | 账户及解密配置 |
| `data/` | 数据库和解密产物 |
| `logs/` | 可能含账户、消息或运行信息的日志 |

本文只整理命令和示例，不包含实际 key、数据库或聊天记录。

## 1. 检查环境

以下命令在目标 Mac 的终端执行：

```bash
uname -m
csrutil status
pgrep -x WeChat
defaults read /Applications/WeChat.app/Contents/Info.plist CFBundleShortVersionString
xcode-select -p
lldb -P
go version
sqlcipher --version
```

原文使用 `arm64` 架构，并要求先打开微信完成登录。缺少构建工具时：

```bash
xcode-select --install
brew install go sqlcipher
```

原文的 key 提取方式涉及 LLDB 附加进程，可能需要关闭 SIP。关闭 SIP 会改变系统保护设置，本文不将其作为普通安装命令自动执行；需要采用该路径时，应先检查工具说明和实际兼容性。原文给出的 Apple Silicon 路径是进入恢复模式，在终端执行 `csrutil disable` 后重启。

## 2. 下载工具并生成配置

```bash
cd ~/Desktop
git clone https://github.com/xingfanxia/wechat-mac-reader.git
cd wechat-mac-reader
mkdir -p lib
git clone https://github.com/Thearas/wechat-db-decrypt-macos.git lib/wechat-db-decrypt-macos

cd lib/wechat-db-decrypt-macos
PYTHONPATH="$(lldb -P)" /usr/bin/python3 find_key_memscan.py
mv wechat_keys.json ~/Desktop/wechat-mac-reader/wechat_keys.json

cd ~/Desktop/wechat-mac-reader
python3 -m venv .venv
.venv/bin/python -m pip install pyyaml
.venv/bin/python scripts/generate-config.py wechat_keys.json data/config.yaml
```

扫描前检查脚本是否向终端打印真实 key；原文建议将 key 的打印内容替换为 `<redacted>`，但保留写入配置文件的逻辑。

检查配置时只打印数量和监听地址：

```bash
.venv/bin/python - <<'PY'
import yaml
with open("data/config.yaml", encoding="utf-8") as stream:
    config = yaml.safe_load(stream) or {}
account = config.get("last_account")
keys = config.get("accounts", {}).get(account, {}).get("derived_key_map", {})
print("derived_key_count:", len(keys))
print("http_addr:", config.get("http_addr"))
print("auto_decrypt:", config.get("auto_decrypt"))
PY
```

原文记录了 23 个数据库中找到 22 个 key，缺失的是 `migrate/unspportmsg.db`。这是原作者的单次运行结果，不能作为所有设备的验收条件。重点是主要记录能否查询，以及实际使用数据库是否解密成功。

## 3. 编译并启动 chatlog-bot

```bash
cd ~/Desktop
git clone https://github.com/TE0dollary/chatlog-bot.git
cd chatlog-bot
CGO_ENABLED=1 go build -trimpath -o bin/chatlog main.go
mkdir -p ~/go/bin
cp bin/chatlog ~/go/bin/chatlog
~/go/bin/chatlog version

cd ~/Desktop/wechat-mac-reader
~/go/bin/chatlog --config ~/Desktop/wechat-mac-reader/data server
```

首次启动按原文会进行自动解密。先确认日志显示服务监听 `127.0.0.1:5030`，再测试接口：

```bash
curl http://127.0.0.1:5030/health
curl 'http://127.0.0.1:5030/api/v1/session?limit=3'
curl 'http://127.0.0.1:5030/api/v1/contact?limit=10'
curl 'http://127.0.0.1:5030/api/v1/chatroom?limit=10'
```

查询聊天记录时，使用 URL 编码传递中文名称：

```bash
curl -G 'http://127.0.0.1:5030/api/v1/chatlog' \
  --data-urlencode 'talker=群名' \
  --data-urlencode 'time=2026-05-01~2026-05-11' \
  --data-urlencode 'limit=100' \
  --data-urlencode 'format=json'
```

| 参数 | 原文说明 |
| --- | --- |
| `talker` | 群名、联系人名、wxid 或群聊 ID |
| `time` | 单日 `YYYY-MM-DD` 或日期范围 |
| `limit` | 返回数量 |
| `offset` | 分页偏移 |
| `format` | `json`、`csv`、`text` |

## 4. 接入 Claude Code

优先尝试原文使用的 HTTP 地址：

```bash
claude --version
claude mcp add --scope user --transport http chatlog http://127.0.0.1:5030/mcp
claude mcp get chatlog
claude mcp list
```

如果该工具版本不支持此 HTTP 路径，再按原文切换 SSE：

```bash
claude mcp remove chatlog -s user
claude mcp add --scope user --transport sse chatlog http://127.0.0.1:5030/sse
claude mcp get chatlog
```

可以用 `curl -i -m 3` 检查 `/mcp` 或 `/sse` 的响应，但 SSE 长连接超时本身不说明服务故障，HTTP 200 本身也不证明完整 MCP 协商成功。最终以客户端连接状态和实际查询结果为准。

适合的请求示例：

- 查询指定群当天的消息，按重点、待办和提到我的事项整理。
- 查询指定日期范围，按决策、争议和链接整理，并保留消息时间。
- 查找最近三天涉及“合同”的消息，按群或联系人归类。

每次明确联系人或群、日期范围、是否引用原文和输出结构，可减少无关读取。

## 5. 设置 launchd 常驻

先通过前台启动完成验证，再创建 `~/Library/LaunchAgents/com.chatlog-bot.plist`。原文配置的主要字段如下，实际文件必须把用户名替换为本机绝对路径；plist 中的路径不会自动展开 `~`：

| 字段 | 配置要点 |
| --- | --- |
| `Label` | `com.chatlog-bot` |
| `ProgramArguments` | chatlog 绝对路径、`--config`、配置目录绝对路径、`server` |
| `WorkingDirectory` | wechat-mac-reader 绝对路径 |
| `RunAtLoad` | `true` |
| `KeepAlive.SuccessfulExit` | `false` |
| `StandardOutPath` / `StandardErrorPath` | 本机日志文件绝对路径 |
| `EnvironmentVariables.PATH` | 包含用户 bin 和 Homebrew 路径 |

限制运行文件权限并启动：

```bash
cd ~/Desktop/wechat-mac-reader
mkdir -p logs
chmod 700 data logs
chmod 600 wechat_keys.json data/config.yaml ~/Library/LaunchAgents/com.chatlog-bot.plist
find data -type d -exec chmod 700 {} +
find data -type f -exec chmod 600 {} +

launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.chatlog-bot.plist
launchctl enable gui/$(id -u)/com.chatlog-bot
launchctl print gui/$(id -u)/com.chatlog-bot
lsof -nP -iTCP:5030 -sTCP:LISTEN
```

停止与重启：

```bash
launchctl bootout gui/$(id -u) ~/Library/LaunchAgents/com.chatlog-bot.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.chatlog-bot.plist
```

## 6. 更新与排障

| 现象 | 检查顺序 |
| --- | --- |
| 无法附加进程或未找到 key | 微信是否登录、工具与微信版本是否兼容、LLDB 路径、权限和 SIP 状态 |
| PyYAML 导入失败 | 使用 `.venv/bin/python` 安装并运行配置生成脚本 |
| `dataKey or derivedKeyMap is required` | 检查 `last_account` 对应的 `derived_key_map` 数量是否为 0 |
| `/health` 不通 | 前台运行确认错误，再检查 launchd、监听端口和错误日志 |
| MCP 连接失败 | 先验证底层服务，再检查客户端传输方式和 MCP 配置 |
| 消息不完整 | 确认 Mac 微信已有相应历史记录，必要时先从手机迁移 |
| 升级后解密失败或无新消息 | 检查项目兼容性，必要时重新提取 key、生成配置并重启 |

原文将微信重启、微信升级、macOS 更新等列为重新提取 key 的时机；整理后以实际解密失败或记录更新异常为触发依据，不假定每次重启都一定需要重新提取。

常用诊断：

```bash
curl http://127.0.0.1:5030/health
launchctl print gui/$(id -u)/com.chatlog-bot
lsof -nP -iTCP:5030 -sTCP:LISTEN
tail -n 100 ~/Desktop/wechat-mac-reader/logs/chatlog-bot.err
claude mcp get chatlog
```

需要重新生成时，重复第 2 节的提取与配置命令，再停止并启动 launchd 服务。历史记录迁移入口以当前手机微信界面为准，原文路径为“我 → 设置 → 通用 → 聊天记录迁移与备份 → 迁移到电脑”。

## 与外呼系统的关系

这份资料作为独立技术参考归档。当前仓库的电话、录音、SenseVoice 和 ARI 链路没有因此增加微信读取功能，也没有将微信数据接入任务判断。后续如需关联通知或沟通记录，需要单独定义数据接口及业务流程。

版权归原作者所有；本整理保留原文标题、作者与链接，属于部署要点的归纳和补充说明。
