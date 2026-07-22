# YuanCode

YuanCode 是一个 Java 21 终端 AI 编程助手，支持 Claude Code 风格的内联界面、JLine 多行编辑、Markdown 流式输出、Anthropic Messages API、OpenAI Responses API、DeepSeek、当前进程内多轮对话、Claude Extended Thinking，以及模型驱动的本地工具调用。

## 工具能力与运行要求

v0.6.1 内置 `ReadFile`、`WriteFile`、`EditFile`、`Bash`、`Glob`、`Grep` 和始终可用的 `AskUserQuestion`。对于“做一个电商系统”这类大型、宽泛且缺少关键约束的需求，YuanCode 会要求模型先通过结构化问题澄清，再制定计划或修改文件。Agent Loop 在 Java 21 虚拟线程中运行，通过有界事件流向 TUI 推送思考、文本、工具状态和最终结果。模型同轮发起多个 READ 工具时会并发执行，WRITE 与 COMMAND 始终串行，并按原调用顺序回填会话。

如果用户在结构化提问时中断、拒绝或输入结束，当前 Agent 运行会立即取消并回滚本轮会话，不会让模型绕过澄清继续修改文件。

- Java 21
- 仓库自带的 Maven Wrapper
- Windows 需要 Git Bash；Linux/macOS 需要可执行的 `bash`

Windows 上 YuanCode 会从 `git.exe` 的安装目录定位 Git Bash，避免误用 `C:\Windows\System32\bash.exe`。自定义安装可设置 `YUANCODE_BASH` 为 `bash.exe` 的绝对路径。YuanCode 不会自动回退到 PowerShell 或 cmd。

示例输入：`读取 README.md 并概括项目结构`、`运行 pwd 并解释输出`、`如果实现方式不明确，请先询问我再修改代码`。

## 构建与运行

```bash
./mvnw test
./mvnw package
java -jar target/yuancode-v0.6.1.jar
```

Windows PowerShell 使用 `.\\mvnw.cmd test` 和 `.\\mvnw.cmd package`。构建前请确保 `JAVA_HOME` 指向 Java 21。

首次启动会创建 `~/.yuancode/config.yaml` 示例并退出。设置对应环境变量后重新运行。

也可指定配置路径：

```bash
java -jar target/yuancode-v0.6.1.jar --config ./config.yaml
```

## 配置示例

```yaml
active_provider: claude
providers:
  claude:
    protocol: anthropic
    model: claude-sonnet-4-6
    base_url: https://api.anthropic.com
    api_key: ${ANTHROPIC_API_KEY}
    max_tokens: 4096
    thinking: true
    show_thinking: false
  openai:
    protocol: openai
    model: gpt-5-mini
    base_url: https://api.openai.com
    api_key: ${OPENAI_API_KEY}
    max_tokens: 4096
  deepseek:
    protocol: anthropic
    model: deepseek-v4-flash
    base_url: https://api.deepseek.com/anthropic
    api_key: ${DEEPSEEK_API_KEY}
    max_tokens: 4096
    thinking: false
    show_thinking: false
```

`base_url` 是服务根地址；YuanCode 会按协议追加 `/v1/messages` 或 `/v1/responses`。

DeepSeek 使用其官方 Anthropic 兼容入口，因此配置为 `protocol: anthropic`。设置 `DEEPSEEK_API_KEY` 后，可在运行中输入 `/provider deepseek` 切换；`deepseek-v4-flash` 与 `deepseek-v4-pro` 均可作为模型。

## 内置命令

- `/help`：查看帮助
- `/clear`：清空当前对话
- `/provider <name>`：切换到另一个已配置 Provider
- `/exit`：退出
- `/quit`：退出别名
- `/model`：查看当前 Provider、协议和模型
- `/status`：查看工作目录和会话轮数
- `/config`：查看配置文件路径（不会输出配置内容）
- `/plan`：在普通模式与 Plan-only 模式间切换
- `/plan on`、`/plan off`：幂等开启或关闭 Plan-only；开启后只允许 READ 工具

生成期间按 `Ctrl+C` 会取消当前运行、活动模型流和等待中的工具，并将对话恢复到本轮开始前；空闲时按 `Ctrl+C` 会退出。

交互输入中使用 `Enter` 发送，使用 `Alt+Enter` 插入换行。方向键可浏览本次运行的输入历史。

## 手工端到端验证

1. 设置 `ANTHROPIC_API_KEY` 或 `OPENAI_API_KEY`。
2. 启动 YuanCode 并输入 `请分三段介绍 Java 21`，确认文本在请求完成前逐段出现。
3. 继续询问 `总结你刚才的第二段`，确认模型能引用第一轮内容。
4. Claude 配置开启 `thinking` 时，默认不展示摘要；将 `show_thinking` 改为 `true` 后可观察 thinking 流。
5. 输入 `我要一个电商系统`，确认 YuanCode 在修改文件前先显示 `AskUserQuestion` 的结构化选项。
6. 在结构化问题中按 `Ctrl+C` 或结束输入，确认当前运行停止且没有继续执行写文件或命令工具。

本阶段不包含权限审批、Hook、上下文压缩、自动重试、MCP、Subagent、Worktree 或会话持久化。
