# ch03：工具系统与最小 Agent Loop Tasks

> 每个任务应能在一次专注会话内完成。实现包名统一使用 `com.yuancode`，构建与测试统一使用 Maven Wrapper。

## T1：定义工具契约与执行结果

- 影响文件：`src/main/java/com/yuancode/tool/Tool.java`、`ToolCategory.java`、`ToolResult.java`；对应测试文件。
- 依赖任务：无。
- 参考资料定位：附件 ch03 Spec F1～F3；现有 Java 21 record/sealed 风格可参考 `src/main/java/com/yuancode/llm/StreamEvent.java`。
- 交付：统一工具接口、READ/WRITE/COMMAND 分类、成功与失败结果工厂；明确 schema 为稳定顺序 Map。

## T2：实现工具注册表与 Deferred 发现

- 影响文件：`src/main/java/com/yuancode/tool/ToolRegistry.java`、`src/test/java/com/yuancode/tool/ToolRegistryTest.java`。
- 依赖任务：T1。
- 参考资料定位：附件 ch03 Spec F4～F7、N1～N2；OpenAI 请求格式入口 `OpenAiClient.body`，Anthropic 请求格式入口 `AnthropicClient.body`。
- 交付：注册、查找、只读列表、默认隐藏 Deferred 工具、发现标记、模糊搜索、精确选择、Anthropic/OpenAI schema 转换及统一输出上限。

## T3：实现安全路径解析与 ReadFile

- 影响文件：`src/main/java/com/yuancode/tool/WorkspacePaths.java`、`src/main/java/com/yuancode/tool/impl/ReadFileTool.java`；对应测试文件。
- 依赖任务：T1。
- 参考资料定位：工作目录来源 `InteractiveShell.workingDirectory`；附件 ch03 Spec F8 ReadFile。
- 交付：所有文件工具共享的工作区边界解析；按 offset/limit 输出带 1 基行号的 UTF-8 文本；拒绝不存在、目录和越界路径。

## T4：实现 WriteFile 与 EditFile

- 影响文件：`src/main/java/com/yuancode/tool/impl/WriteFileTool.java`、`EditFileTool.java`；对应测试文件。
- 依赖任务：T1、T3。
- 参考资料定位：附件 ch03 Spec F8 WriteFile/EditFile、N4；`WorkspacePaths` 的规范化与边界校验。
- 交付：UTF-8 写入、父目录创建、可用时设置 POSIX 权限；编辑操作要求目标存在且旧文本恰好出现一次。

## T5：实现 Bash 命令工具

- 影响文件：`src/main/java/com/yuancode/tool/impl/BashTool.java`、`src/test/java/com/yuancode/tool/impl/BashToolTest.java`。
- 依赖任务：T1、T3。
- 参考资料定位：附件 ch03 Spec F8 Bash、N3；工作目录取 `InteractiveShell.workingDirectory`。
- 交付：固定通过 `bash -c` 执行；独立消费 stdout/stderr；支持默认超时、硬上限、强制终止、中断恢复和稳定输出格式。

## T6：实现 Glob 与 Grep

- 影响文件：`src/main/java/com/yuancode/tool/FileScanPolicy.java`、`src/main/java/com/yuancode/tool/impl/GlobTool.java`、`GrepTool.java`；对应测试文件。
- 依赖任务：T1～T3。
- 参考资料定位：附件 ch03 Spec F8 Glob/Grep、N5；统一输出上限来自 `ToolRegistry`。
- 交付：共享六项目录跳过策略；Glob 路径匹配与排序；Grep 正则、include 过滤、二进制检测、相对路径行号输出和截断。

## T7：实现 ToolSearch

- 影响文件：`src/main/java/com/yuancode/tool/impl/ToolSearchTool.java`、`src/test/java/com/yuancode/tool/ToolSearchToolTest.java`。
- 依赖任务：T2。
- 参考资料定位：附件 ch03 Spec F9；项目现有 Jackson 配置 `src/main/java/com/yuancode/llm/JsonSupport.java`。
- 交付：始终可见的工具搜索；支持普通查询和 `select:` 精确选择；限制结果数；返回 Provider 对应 schema 并激活命中工具。

## T8：实现终端版 AskUser

- 影响文件：`src/main/java/com/yuancode/tool/impl/AskUserTool.java`、`src/main/java/com/yuancode/tool/UserQuestionHandler.java`；对应测试文件。
- 依赖任务：T1～T2。
- 参考资料定位：终端输入入口 `InteractiveShell.readInput`、`InteractiveInput.read`；附件 ch03 Spec F10。
- 交付：Deferred 的结构化提问工具；通过注入的终端处理器展示 1～4 个问题并等待回答；超时、取消和拒绝返回可观察错误结果。

## T9：扩展会话消息以保存工具往返

- 影响文件：`src/main/java/com/yuancode/conversation/ConversationMessage.java`、`Conversation.java`、新增工具调用/结果 block；更新 `ConversationTest.java`。
- 依赖任务：T1。
- 参考资料定位：`Conversation.addUser`、`Conversation.addAssistant`、`ConversationMessage` 当前 record；附件 ch03 设计概要“主流程”。
- 交付：保存调用 ID、工具名、完整参数、输出与错误状态；不可变历史视图；文本、思考、工具调用和工具结果均可跨下一次请求保留。

## T10：扩展两个 Provider 的工具协议

- 影响文件：`src/main/java/com/yuancode/llm/LlmRequest.java`、`StreamEvent.java`、`llm/anthropic/AnthropicClient.java`、`llm/openai/OpenAiClient.java`；Provider 流测试与 SSE fixtures。
- 依赖任务：T2、T9。
- 参考资料定位：`AnthropicClient.body/message/StreamMapper.map`、`OpenAiClient.body/message/mapEvent`、`ProviderStreamingTest`。
- 交付：请求携带协议对应工具 schema；序列化工具调用与结果；流式累计工具参数；统一发出工具开始、参数增量、工具完成和普通完成事件。

## T11：实现工具执行器与最小 Agent Loop

- 影响文件：`src/main/java/com/yuancode/agent/ToolExecutor.java`、`AgentLoop.java`；新增执行器与循环测试。
- 依赖任务：T2～T10。
- 参考资料定位：当前单次流消费逻辑 `InteractiveShell.chat`；取消逻辑 `InteractiveShell.handleInterrupt`；`StreamHandle.next`。
- 交付：执行器查找并串行执行工具、统一截断输出；循环持续请求模型并回灌结果；支持多个工具调用、最大迭代、流超时、取消和不可恢复错误退出。

## T12：接入主流程

- 影响文件：`src/main/java/com/yuancode/cli/InteractiveShell.java`、相关 UI renderer、`YuanCodeApplication.java`；更新 shell 测试。
- 依赖任务：T3～T11。
- 参考资料定位：`InteractiveShell` 构造器、`chat`、`dispatch`、`workingDirectory`；`MessageChrome` 与 `ResponseSummaryRenderer`。
- 交付：以当前工作目录创建默认六工具、ToolSearch 和 AskUser；用 Agent Loop 替换单次 LLM 请求；终端展示工具调用与结果摘要；保留现有 Provider 切换、取消、Markdown 输出和欢迎界面。

## T13：端到端验证

- 影响文件：`src/test/java/com/yuancode/e2e/ToolAgentLoopE2eTest.java`、`README.md`、根目录 `checklist.md`；必要时增加测试资源。
- 依赖任务：T1～T12。
- 参考资料定位：现有 `ConversationE2eTest`、`TerminalConversationE2eTest`、`src/test/resources/smoke-config.yaml`；Maven Wrapper `mvnw.cmd`。
- 交付：无网络假 Provider 驱动 ReadFile/Bash/最终回答完整链路；执行全量测试与打包；按 checklist 留存命令输出并更新使用说明。

## 进度

- [x] T1 工具契约与执行结果
- [x] T2 注册表与 Deferred 发现
- [x] T3 安全路径与 ReadFile
- [x] T4 WriteFile / EditFile
- [x] T5 Bash
- [x] T6 Glob / Grep
- [x] T7 ToolSearch
- [x] T8 AskUser
- [x] T9 会话工具消息
- [x] T10 Provider 工具协议
- [x] T11 工具执行器与 Agent Loop
- [x] T12 接入主流程
- [x] T13 端到端验证
