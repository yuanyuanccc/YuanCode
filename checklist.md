# ch03：工具系统与最小 Agent Loop Checklist

> 每一项都必须通过测试、命令输出或终端行为直接观察。

## 1. 工具契约与注册表

- [ ] `Tool` 暴露名称、描述、类别、schema、执行和 Deferred 开关，`ToolCategory` 恰好包含 `READ`、`WRITE`、`COMMAND`。（运行 `rg "READ|WRITE|COMMAND" src/main/java/com/yuancode/tool` 并执行对应单测）
- [ ] `ToolResult.success("ok")` 得到 `output="ok", isError=false`，`ToolResult.error("bad")` 得到 `output="bad", isError=true`。（单元测试断言）
- [ ] 注册 `A`、`B` 后列表和 schema 仍按 `A`、`B` 顺序返回；重复名称的行为有测试锁定。（`ToolRegistryTest`）
- [ ] Anthropic schema 含 `name/description/input_schema`；OpenAI schema 含 `type=function/name/description/parameters`。（`ToolRegistryTest` 精确 Map 断言）
- [ ] 未发现的 Deferred 工具不在默认 schema 中，调用发现标记后下一次 schema 中出现。（`ToolRegistryTest`）
- [ ] 任一工具结果超过 `10_000` 字符时，执行器只保留前 `10_000` 字符并追加 `... (truncated)`。（`ToolExecutorTest`）

## 2. 文件工具

- [ ] 所有文件参数在规范化后必须位于 YuanCode 启动工作目录内；输入 `../outside.txt` 返回错误且外部文件未被读取或修改。（路径边界测试）
- [ ] ReadFile 默认从第 `0` 行偏移读取最多 `2_000` 行，输出采用 `1\t第一行` 格式。（`ReadFileToolTest`）
- [ ] ReadFile 输入不存在路径或目录时返回 `isError=true`。（`ReadFileToolTest`）
- [ ] WriteFile 写入 `nested/a.txt` 后父目录自动创建，`Files.readString` 得到完全相同的 UTF-8 内容。（`WriteFileToolTest`）
- [ ] 在支持 POSIX 权限的平台，WriteFile 创建目录权限为 `rwxr-xr-x`、文件权限为 `rw-r--r--`；Windows 上该断言明确跳过。（条件测试）
- [ ] EditFile 在 `old_string` 出现恰好 `1` 次时只替换该处；出现 `0` 次和 `2` 次时分别返回不同错误且文件保持不变。（`EditFileToolTest`）

## 3. Bash、Glob 与 Grep

- [x] Bash 固定使用 `bash -c <command>`，Windows 优先定位 Git Bash，不回退到 PowerShell 或 cmd。（`CommandAndSearchToolsTest` 真实进程验证）
- [ ] Bash 默认超时为 `120` 秒，用户 timeout 最大为 `600` 秒，超过上限时按 `600` 秒执行。（参数测试）
- [x] 执行 `printf out; printf err >&2; exit 7` 的结果同时包含 `$ command`、`out`、`STDERR: err` 和 `(exit code 7)`，并且 `isError=true`。（`CommandAndSearchToolsTest`）
- [ ] 超时命令被强制终止并返回 `isError=true`；等待线程不会永久挂起。（短超时测试）
- [ ] Glob 与 Grep 都跳过 `.git`、`.venv`、`node_modules`、`__pycache__`、`.tox`、`.mypy_cache` 共 `6` 个目录。（共享常量测试）
- [ ] Glob 对相同目录连续执行两次，返回完全相同的字典序结果。（`GlobToolTest`）
- [ ] Grep 输入非法正则返回错误；输入合法正则输出 `相对路径:行号:行内容`。（`GrepToolTest`）
- [ ] Grep 对前 `512` 字节含 `\0` 的文件无命中输出，`include="*.java"` 时不搜索非 Java 文件。（`GrepToolTest`）

## 4. Deferred 与 AskUser

- [ ] ToolSearch 默认出现在 schema 中，AskUser 默认不出现。（`ToolSearchToolTest`）
- [ ] `query="select:AskUser"` 返回 AskUser schema，并使它在下一轮 schema 中出现。（`ToolSearchToolTest`）
- [ ] ToolSearch 的 `max_results` 默认值为 `5`，输入小于 `1` 或大于 `20` 时分别夹紧为 `1` 和 `20`。（参数化测试）
- [ ] AskUser 接受 `1～4` 个问题，每个问题接受 `2～4` 个选项；越界输入返回错误。（`AskUserToolTest`）
- [ ] AskUser 等待上限为 `5` 分钟；超时、用户取消或答案含 `_declined` 时返回 `isError=true`。（使用可控 future/clock 的单测）
- [ ] 在 TUI 触发 AskUser 时可输入选择并让同一轮 Agent 继续，而不是开始一轮新的普通聊天。（端到端测试）

## 5. Provider 与会话往返

- [ ] Anthropic 请求同时包含工具 schema、assistant `tool_use` 和 user `tool_result`，调用 ID、参数、结果与 `is_error` 原样保留。（请求体测试）
- [ ] OpenAI Responses 请求包含 function tools、function call 和 function call output，调用 ID 与 JSON 参数原样保留。（请求体测试）
- [ ] Anthropic 的工具开始、JSON 参数增量和 block 完成被还原为统一工具调用事件。（SSE fixture 测试）
- [ ] OpenAI 的 function call 参数增量和完成事件被还原为相同的统一工具调用事件。（SSE fixture 测试）
- [ ] `/clear` 后文本、thinking、tool call 和 tool result 历史数量都为 `0`。（`ConversationTest`/`InteractiveShellTest`）

## 6. Agent Loop 与接入

- [ ] 假 Provider 依次返回“ReadFile 调用 → 工具结果后最终文本”，最终会话顺序为 user、assistant tool call、user tool result、assistant text。（`ToolAgentLoopE2eTest`）
- [ ] 一次响应包含多个工具调用时按返回顺序逐个执行并全部回传。（`AgentLoopTest`）
- [ ] 未知工具名不会抛出未捕获异常，而是生成 `isError=true` 的工具结果供模型修正。（`ToolExecutorTest`）
- [ ] 单轮最多执行 `20` 次模型迭代，第 `21` 次前终止并显示明确错误。（`AgentLoopTest`）
- [ ] 工具业务错误不会立即终止循环；网络错误、协议错误或用户取消会终止本轮。（`AgentLoopTest`）
- [ ] `rg -n "new AgentLoop|ToolRegistry" src/main/java/com/yuancode/cli/InteractiveShell.java` 至少各返回 `1` 个主流程引用。
- [ ] TUI 中工具调用与结果各有一条可见状态输出，最终文本仍由现有 Markdown renderer 流式显示。（`InteractiveShellTest`）

## 7. 构建与端到端验收

- [x] 设置 `JAVA_HOME` 为 JDK 21 后，`./mvnw.cmd test` 退出码为 `0`，测试失败数为 `0`。（2026-07-21：36 项，0 失败，Bash 环境测试跳过 1 项）
- [x] `./mvnw.cmd package` 退出码为 `0`，`target/` 下生成可执行 YuanCode JAR。（`target/yuancode-v0.5.0.jar`）
- [ ] 本地启动 JAR，要求 YuanCode 读取工作区内一个文本文件，可看到带行号的工具结果及基于该内容生成的最终回答。
- [x] Git Bash 集成测试执行命令并观察 `$ command`、stdout、stderr 与退出码；当前 Git Bash 定位为 `D:\Git\bin\bash.exe`。
- [x] 至少一条自动化端到端测试完整覆盖“用户输入 → Provider 工具调用 → 本地执行 → 工具结果回传 → Provider 最终文本 → 会话落盘到内存”。（`ToolAgentLoopE2eTest`）

## 8. 文档与安全

- [ ] 根目录 `spec.md`、`tasks.md`、`checklist.md` 均描述 YuanCode、Java 21、Maven 和 `com.yuancode`，执行 `rg "M[e]wCode|com[.]mewcode|gradle[w]" spec.md tasks.md checklist.md` 返回 `0` 条。
- [x] `README.md` 说明本机必须可执行 `bash`，并给出 ReadFile、Bash 与 AskUser 示例。
- [ ] `rg -n "sk-[A-Za-z0-9]" . -g "!target/**" -g "!.git/**"` 不返回真实 API key。
