# ch04：异步 Agent Loop Tasks

> 每个任务应能在一次专注会话内完成。实现包名统一使用 `com.yuancode`，构建与测试统一使用 Maven Wrapper。

## T1：定义 AgentEvent 与运行终态

- 影响文件：`src/main/java/com/yuancode/agent/AgentEvent.java`、`AgentTermination.java`；对应测试文件。
- 依赖任务：无。
- 参考资料定位：现有 LLM 事件 `src/main/java/com/yuancode/llm/StreamEvent.java`；当前 Listener 位于 `AgentLoop.java` 的末尾。
- 交付：使用 sealed interface + record 定义用户消息、思考增量、文本增量、工具开始、工具结果、用量、轮次完成、最终回复、循环完成、Plan 模式变化和错误；终止原因收口为可穷尽枚举。

## T2：实现 AgentRun 异步运行句柄

- 影响文件：`src/main/java/com/yuancode/agent/AgentRun.java`、`AgentLoop.java`；`AgentRunTest.java`。
- 依赖任务：T1。
- 参考资料定位：`StreamHandle.next/cancel/close`；当前同步入口 `AgentLoop.run`；Java 21 `Thread.startVirtualThread`。
- 交付：返回容量固定的事件队列、取消入口和完成状态；后台虚拟线程执行循环；异常统一转成 AgentEvent；队列写入在中断时恢复中断标志。

## T3：实现 StreamEvent 到 AgentEvent 的轮内翻译

- 影响文件：`src/main/java/com/yuancode/agent/AgentLoop.java`、`AgentEvent.java`；`AgentStreamTranslationTest.java`。
- 依赖任务：T1～T2。
- 参考资料定位：`AgentLoop.run` 中当前 StreamEvent switch；Anthropic/OpenAI 映射测试 `ProviderStreamingTest`。
- 交付：消费文本、思考、工具开始/参数/完成、用量、结束和失败事件；收集轮内文本与工具调用；流结束时只产生一次轮次终态。

## T4：实现分组 StreamingToolExecutor

- 影响文件：`src/main/java/com/yuancode/agent/StreamingToolExecutor.java`，替代或深化 `ToolExecutor.java`；对应并发测试。
- 依赖任务：T1。
- 参考资料定位：工具类别 `ToolCategory.java`；注册表查找 `ToolRegistry.get`；现有截断逻辑 `ToolExecutor.execute`。
- 交付：READ 调用多于一个时用虚拟线程并发；WRITE/COMMAND 串行；结果事件按实际完成顺序输出；返回值按原始调用索引排序；未知工具、异常和输出截断保持统一行为。

## T5：实现 Plan-only 状态与执行限制

- 影响文件：`src/main/java/com/yuancode/agent/AgentMode.java`、`AgentLoop.java`、`StreamingToolExecutor.java`；模式与执行策略测试。
- 依赖任务：T2、T4。
- 参考资料定位：`ToolCategory.READ/WRITE/COMMAND`；当前 `effectiveSystemPrompt` 的 deferred reminder 拼接点。
- 交付：运行模式可切换；Plan-only 时 system prompt 加入最小计划提醒；READ 正常执行，WRITE/COMMAND 不执行并返回工具错误；事件流可观察模式变化。

## T6：实现取消、超时与会话检查点

- 影响文件：`src/main/java/com/yuancode/agent/AgentRun.java`、`AgentLoop.java`、`src/main/java/com/yuancode/conversation/Conversation.java`；取消与回滚测试。
- 依赖任务：T2～T4。
- 参考资料定位：`InteractiveShell.handleInterrupt`、`StreamHandle.cancel`、`Conversation` 的私有 history 与追加方法。
- 交付：取消活动流、后台线程和未完成工具任务；流静默超时终止运行；运行开始记录检查点；取消或错误时回滚本次新增消息；完成、取消、错误只产生一个循环终态。

## T7：完成 ReAct 状态机与原子写回

- 影响文件：`src/main/java/com/yuancode/agent/AgentLoop.java`、`Conversation.java`；状态机测试。
- 依赖任务：T3～T6。
- 参考资料定位：现有 `Conversation.addAssistantTools/addToolResults`；当前 `AgentLoop` 的迭代上限与 deferred reminder。
- 交付：完整实现“模型请求→事件消费→工具批次→有序结果回填→下一轮”；无工具、显式结束、取消、错误和达到上限均按定义终止；只有完整工具批次可以写入 Conversation。

## T8：补齐 Agent 公共行为测试

- 影响文件：`src/test/java/com/yuancode/agent/AgentRunTest.java`、`AgentStreamTranslationTest.java`、`StreamingToolExecutorTest.java`、`AgentLoopStateTest.java`。
- 依赖任务：T1～T7。
- 参考资料定位：现有 `AgentCoreTest`、`ToolAgentLoopE2eTest`、`StreamHandle` 队列测试方式。
- 交付：覆盖事件顺序、队列容量、并发读取、串行副作用、结果重排、Plan-only、取消回滚、静默超时、循环上限和唯一终态；测试通过公共接口观察行为。

## T9：接入主流程

- 影响文件：`src/main/java/com/yuancode/cli/CommandParser.java`、`InteractiveShell.java`、相关 UI renderer；更新 CLI 测试。
- 依赖任务：T1～T8。
- 参考资料定位：`InteractiveShell.chat` 当前 Listener 消费、`handleInterrupt`、`dispatch`、`/status`；`CommandParser.parse` 的 sealed Command。
- 交付：TUI 改为消费 AgentEvent 队列；`/plan`、`/plan on`、`/plan off` 可切换模式；`/status` 展示模式；Ctrl+C 调用 AgentRun 取消；保留现有 Markdown 流、Provider 切换和输入界面。

## T10：端到端验证

- 影响文件：`src/test/java/com/yuancode/e2e/AsyncAgentLoopE2eTest.java`、`PlanModeE2eTest.java`、`README.md`、根目录 `checklist.md`；必要时增加测试工具。
- 依赖任务：T1～T9。
- 参考资料定位：现有 `ToolAgentLoopE2eTest`、`TerminalConversationE2eTest`、Maven Wrapper。
- 交付：假 Provider 驱动异步事件完整链路；可控工具验证 READ 并发和副作用串行；TUI 验证 Plan-only 拦截与 Ctrl+C；执行全量测试、打包和 JAR 启动冒烟。

## 进度

- [x] T1 AgentEvent 与运行终态
- [x] T2 AgentRun 异步句柄
- [x] T3 StreamEvent 翻译
- [x] T4 分组 StreamingToolExecutor
- [x] T5 Plan-only
- [x] T6 取消、超时与检查点
- [x] T7 ReAct 状态机
- [x] T8 公共行为测试
- [x] T9 接入主流程
- [x] T10 端到端验证
