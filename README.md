# YuanCode

YuanCode 是一个 Java 21 终端 AI 助手，支持 Claude Code 风格的内联界面、JLine 多行编辑、Markdown 流式输出、Anthropic Messages API、OpenAI Responses API、DeepSeek、当前进程内多轮对话和 Claude Extended Thinking。

## 构建与运行

```bash
./mvnw test
./mvnw package
java -jar target/yuancode-v0.4.0.jar
```

Windows PowerShell 使用 `.\\mvnw.cmd test` 和 `.\\mvnw.cmd package`。构建前请确保 `JAVA_HOME` 指向 Java 21。

首次启动会创建 `~/.yuancode/config.yaml` 示例并退出。设置对应环境变量后重新运行。

也可指定配置路径：

```bash
java -jar target/yuancode-v0.2.0.jar --config ./config.yaml
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

生成期间按 `Ctrl+C` 会取消当前回复；空闲时按 `Ctrl+C` 会退出。

交互输入中使用 `Enter` 发送，使用 `Alt+Enter` 插入换行。方向键可浏览本次运行的输入历史。

## 手工端到端验证

1. 设置 `ANTHROPIC_API_KEY` 或 `OPENAI_API_KEY`。
2. 启动 YuanCode 并输入 `请分三段介绍 Java 21`，确认文本在请求完成前逐段出现。
3. 继续询问 `总结你刚才的第二段`，确认模型能引用第一轮内容。
4. Claude 配置开启 `thinking` 时，默认不展示摘要；将 `show_thinking` 改为 `true` 后可观察 thinking 流。

本阶段不包含工具调用、文件操作、代码编辑或会话持久化。
