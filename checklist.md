# YuanCode v0.4 验收清单：蓝色卡片式终端展示层

> 每项必须可重复观察。自动化测试使用 JLine 虚拟终端和本地伪 SSE 服务，不使用真实 API Key。

## 构建与范围

- [ ] PowerShell 7 中运行 `.\\mvnw.cmd clean package` 退出码为 `0`，全部测试通过。
- [ ] `pom.xml` 使用 JLine 3.x，并包含 Java 21 在 Windows 所需的 terminal provider。
- [ ] 执行 `Get-ChildItem -Recurse src/main -Filter *.java | Select-String 'ToolCall|ToolUse|SubAgent|TeamManager|MemoryManager'` 返回 `0` 条。
- [ ] 现有 Anthropic、OpenAI Responses 和 DeepSeek Provider 测试继续通过。

## 欢迎卡与 Yuan 图标

- [ ] 交互终端启动后不再出现 `you>` 或 `assistant>`。
- [ ] 宽终端启动卡首尾分别为 `╭` + `70` 个 `─` + `╮` 与 `╰` + `70` 个 `─` + `╯`。
- [ ] 启动卡包含紧凑图标 `[ ■  ■ ]`、`Welcome to YuanCode!` 和 `Type /help for help information.`。
- [ ] 卡片边框和图标使用蓝色强调样式，ANSI 复位后不污染正文颜色。
- [ ] 欢迎页包含 `YuanCode`、版本、当前模型、Provider 和绝对工作目录。
- [ ] 欢迎页模型行精确包含 `Model: <model> · Provider: <provider>`，且不出现 `Kimi Code`、`Claude Code` 或第三方品牌图标。
- [ ] 终端宽度不小于 `80` 列时字标位于信息左侧；小于 `80` 列时信息从字标下一行开始且没有负数填充或截断异常。
- [ ] `NO_COLOR` 存在时欢迎页不包含 `\u001B[`，但字标和全部文字仍存在。

## 输入编辑

- [ ] 输入框上方显示 `<系统用户>@YuanCode 🚀`。
- [ ] 输入框上下边框使用 `╭─╮` 与 `╰─╯` 结构；主提示符为 `│ ❯ `，续行提示为 `│ `，右侧出现 `? for shortcuts │`。
- [ ] `Enter` 提交当前缓冲区并只发出一次模型请求。
- [ ] `Alt+Enter` 在光标处插入换行，不发送请求。
- [ ] 粘贴包含三行的文本只产生一条用户消息和一次模型请求，换行原样保留。
- [ ] 左右方向键、Home、End、Backspace 和 Delete 在 Windows Terminal + PowerShell 7 中行为正确。
- [ ] 上下方向键可浏览本次运行中已提交输入；退出重启后历史为空。
- [ ] 空缓冲区按 `Ctrl+D` 正常退出；非空缓冲区按 `Ctrl+D` 不退出且不丢失内容。
- [ ] 输入空白或只含换行时不调用 Provider。

## 消息排版与 Markdown

- [ ] 用户消息使用 `>` 前缀和强调样式；无颜色模式仍保留 `>`。
- [ ] 助手正文直接位于用户消息下方，不出现 `assistant>`、`AI:` 或供应商名称前缀。
- [ ] Thinking 以黄色 `● ` 起始，助手回答以蓝色 `● ` 起始；`NO_COLOR` 下两者仍包含 `● `。
- [ ] 输入 `# 标题`、`- 项目`、`**强调**`、`` `code` `` 时完成态分别具有可区分样式；无颜色模式保留原始文本结构。
- [ ] 围栏代码块的语言标识存在时应用基础关键字、字符串和注释样式；未知语言按普通代码样式显示。
- [ ] 开始围栏、代码内容和结束围栏分别落在不同 SSE 增量时，输出只包含一份代码内容。
- [ ] `**`、反引号或围栏被拆到两个 SSE 增量时，渲染器完成后不丢字、不调换顺序且不重复字符。
- [ ] 未闭合代码围栏随流结束时，已有内容仍可见，终端样式已复位。
- [ ] 单行超过当前终端宽度时不抛异常，后续消息仍可输入。

## 流式状态、Thinking 与用量

- [ ] 发出请求后立即显示 `✻ Thinking…`，每隔 `1 秒` 更新一次已等待秒数。
- [ ] 第一个正文增量到达前状态可见；正文到达后状态行被清理且 `Thinking…` 不残留在正文中。
- [ ] 正文增量到达后立即 flush，在下一个伪 SSE 事件发出前即可从虚拟终端读取。
- [ ] 完成后显示耗时以及 `input_tokens`、`output_tokens`；用量缺失时只显示耗时。
- [ ] `show_thinking: true` 时 thinking 摘要在生成期间可见；正文开始后变为单行 `Thinking complete` 状态。
- [ ] `show_thinking: false` 时终端不出现测试字符串 `private-thought-canary`，但签名仍在下一轮请求中原样存在。

## 取消、错误与终端恢复

- [ ] 生成期间第一次按 `Ctrl+C` 会关闭 HTTP 流、显示 `已取消` 并返回输入区，进程不退出。
- [ ] 取消前已输出的部分正文仍留在终端历史，但下一次请求不包含该不完整助手回复。
- [ ] 空闲状态按 `Ctrl+C` 或输入 `/exit`、`/quit` 后进程退出码为 `0`。
- [ ] API 认证、限流、上下文过长和网络错误采用错误样式；无颜色模式仍包含 `错误:` 前缀。
- [ ] 正常退出、取消、异常和 EOF 四条路径结束后，终端 echo、光标和颜色状态均恢复。
- [ ] 错误输出、状态命令和测试报告均不包含 `secret-canary-key`。

## 内置命令

- [ ] `/help` 输出至少包含 `/clear`、`/provider <name>`、`/model`、`/status`、`/config`、`/exit` 和 `/quit`。
- [ ] `/model` 显示当前 Provider 名称、protocol 和 model，不显示 API Key。
- [ ] `/status` 显示绝对工作目录和当前已完成会话轮数。
- [ ] `/config` 只显示 `C:\Users\Yuanc\.yuancode\config.yaml` 形式的路径，不读取或打印文件内容。
- [ ] `/provider deepseek` 成功后欢迎状态与 `/model` 均反映新 Provider，已有会话历史保持不变。
- [ ] `/clear` 后会话轮数变为 `0`，下一次请求不包含清空前文本。

## 降级与窗口变化

- [ ] 标准输出重定向到文件时不包含 ANSI 控制序列、光标移动序列或动态状态重复行。
- [ ] system terminal 创建失败时自动进入逐行纯文本模式，并能完成一轮本地伪 SSE 对话。
- [ ] dumb terminal 中 `/help`、`/clear`、`/provider`、`/model`、`/status`、`/config` 和退出命令仍可用。
- [ ] 生成期间把终端从宽布局缩到窄布局不会抛异常；下一次提示符按新宽度绘制。
- [ ] 长回复不暂停等待分页输入，内容由终端自身滚动。

## 端到端验收

- [ ] `TerminalConversationE2eTest` 完成 `欢迎页 → Alt+Enter 多行输入 → 两次文本增量 → Markdown 代码块 → 完成摘要 → 追问 → /quit`，并断言第二次请求包含完整首轮上下文。
- [ ] Windows Terminal + PowerShell 7 中真实运行 YuanCode，欢迎页字标无错位，多行编辑可用，输入 `请用 Java 写一个二分查找并解释复杂度` 时代码块逐步显示且最终不重复。
- [ ] 真实生成中按 `Ctrl+C` 后能立即继续输入下一问，下一问回答不把被取消的残缺内容视为历史。
