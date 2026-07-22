# ch04：异步 Agent Loop Checklist

> 所有条目必须通过测试、命令输出或终端行为直接观察。

## 1. AgentEvent 与异步运行

- [x] `AgentEvent` 是 sealed interface，恰好覆盖 `UserMessage`、`ThinkingDelta`、`TextDelta`、`ToolCallStarted`、`ToolResultCompleted`、`UsageUpdated`、`TurnCompleted`、`FinalReply`、`LoopCompleted`、`PlanModeChanged`、`Error` 共 `11` 类事件。（`AgentEventTest`）
- [x] `AgentTermination` 恰好包含 `COMPLETED`、`CANCELLED`、`TIMED_OUT`、`MAX_ITERATIONS`、`ERROR`。（枚举精确断言）
- [x] 每次运行使用 `LinkedBlockingQueue<AgentEvent>(64)`，后台线程为虚拟线程，调用入口在模型响应前返回。（`AgentRunTest`）
- [x] 事件生产线程中断时不抛出未捕获 `InterruptedException`，中断标志得到恢复，且仍能产生唯一终态。（中断测试）
- [x] 每次运行最终恰好出现 `1` 个 `LoopCompleted`；错误细节可额外通过 `Error` 事件观察，但不重复终止。（完成、取消、超时和上限测试）

## 2. ReAct 与事件翻译

- [x] 用户输入后第一条业务事件为 `UserMessage`，内容与输入完全一致。（`AgentRunTest`）
- [x] LLM 的 thinking 和 text 增量按接收顺序分别转成 `ThinkingDelta` 和 `TextDelta`。（`AsyncAgentLoopE2eTest`）
- [x] 工具真正开始本地执行前产生 `ToolCallStarted`，包含调用 ID、工具名和完整参数。（事件内容断言）
- [x] 每个工具调用产生 `1` 个 `ToolResultCompleted`，包含调用 ID、工具名、输出、`isError` 和耗时。（事件内容断言）
- [x] 每轮结束产生 `UsageUpdated` 和 `TurnCompleted`；无工具调用时再产生 `FinalReply` 与 `LoopCompleted(COMPLETED)`。（完整事件序列断言）
- [x] 假 Provider 返回“工具调用→工具结果后最终文本”时，会话顺序为 user、assistant tool call、user tool result、assistant text。（`AsyncAgentLoopE2eTest`）
- [x] 第 `20` 次模型迭代后仍要求工具时停止，不发起第 `21` 次请求，并产生 `LoopCompleted(MAX_ITERATIONS)`。（`AgentIterationLimitTest`）

## 3. 工具分组与顺序

- [x] 同轮 `2` 个 READ 工具使用不同虚拟线程重叠执行。（闩锁测试，不依赖 sleep）
- [x] 同轮 WRITE 与 COMMAND 工具从不重叠执行，开始顺序与模型调用顺序一致。（并发计数器最大值为 `1`）
- [x] READ 的 `ToolResultCompleted` 可按实际完成顺序出现，返回给 Conversation 的结果按模型原始调用索引排序。（完成顺序与回填顺序双断言）
- [x] 未知工具返回 `isError=true`，工具抛出的运行时异常被转成错误结果，不终止 JVM。（`StreamingToolExecutorTest`）
- [x] 工具输出超过 `10_000` 字符时保留前 `10_000` 字符并追加 `... (truncated)`。（精确长度与后缀断言）

## 4. Plan-only

- [x] `/plan` 在 NORMAL 与 PLAN_ONLY 间切换，`/plan on` 和 `/plan off` 可幂等设置；其他参数输出 `用法: /plan [on|off]`。（`CommandParserTest`、`InteractiveShellTest`）
- [x] `/status` 在 Plan-only 时显示 `模式: PLAN_ONLY`，普通模式显示 `模式: NORMAL`。（Shell 输出断言）
- [x] Plan-only 请求的 system prompt 包含最小提醒：只分析、只使用读取工具、输出待审批计划。（请求捕获测试）
- [x] Plan-only 下 READ 工具正常执行；WRITE 和 COMMAND 的执行计数均为 `0`，结果为 `isError=true`。（执行策略测试）
- [x] WRITE/COMMAND 被拦截后错误结果明确提示关闭 Plan-only，随后模型仍可生成最终计划。（`PlanModeE2eTest`）
- [x] 关闭 Plan-only 后，相同 WRITE/COMMAND 调用恢复正常执行。（模式切换测试）

## 5. 取消、超时与一致性

- [x] Agent 流默认等待上限为 `30` 秒；可控短超时产生 `Error("Stream timeout")` 和 `LoopCompleted(TIMED_OUT)`。（`AgentTimeoutTest`）
- [x] Ctrl+C 调用当前 `AgentRun.cancel()`，活动 `StreamHandle.cancel()` 恰好执行 `1` 次。（取消测试）
- [x] 取消时后台虚拟线程和未完成 READ future 均收到中断，运行在限定时间内结束。（闩锁测试）
- [x] 在 LLM 流中途取消后，Conversation 恢复到运行前消息数量，不保留部分 assistant 文本。（检查点测试）
- [x] 在多工具批次中途取消后，不写入部分 assistant tool call 或 tool result。（检查点测试）
- [x] 正常完成时不回滚，会话保留完整 user、assistant 和 tool result。（正常路径对照测试）

## 6. 接入与回归

- [x] `rg -n "AgentRun|AgentEvent" src/main/java/com/yuancode/cli/InteractiveShell.java` 返回真实主流程引用。
- [x] `rg -n "new StreamingToolExecutor" src/main/java/com/yuancode/agent` 返回真实调用。
- [x] TUI 消费事件时 thinking、文本、工具状态和最终摘要仍使用现有 Theme 与 Markdown renderer。（`InteractiveShellTest`）
- [x] 生成期间 Ctrl+C 只取消当前运行；空闲时 Ctrl+C 仍退出 YuanCode。（终端行为与取消测试）
- [x] Provider 切换、`/clear`、多行输入、Extended Thinking 和 Deferred 提醒的现有测试继续通过。（全量回归）

## 7. 构建与端到端验收

- [x] 设置 `JAVA_HOME` 为 JDK 21 后，`.\mvnw.cmd test` 退出码为 `0`，共 `51` 个测试，失败数和跳过数均为 `0`。
- [x] `.\mvnw.cmd package` 退出码为 `0`，当前版本可执行 JAR 已生成且本地启动退出码为 `0`。
- [x] 隔离目录真实 DeepSeek 验收：`/plan on` 后 Bash 被拦截、只输出待审批计划，`proof.txt` 保持不存在。
- [x] 隔离目录真实 DeepSeek 验收：`/plan off` 并清空会话后，同轮看到两个 `ReadFile` 启动并得到 `alpha`、`beta` 综合回答。
- [x] `AsyncAgentLoopE2eTest` 完整覆盖“用户输入→异步事件→Provider 工具调用→并发工具执行→有序结果回传→最终回复→唯一循环终态”。

## 8. 文档与安全

- [x] 根目录 `spec.md`、`tasks.md`、`checklist.md` 均描述 YuanCode、Java 21、Maven 和 `com.yuancode`，命名扫描返回 `0` 条旧项目引用。
- [x] README 说明 `/plan`、事件驱动 Agent Loop、取消行为和 READ 并发策略。
- [x] API Key 扫描不返回仓库中的真实凭据；真实验收密钥仅注入一次性 Java 子进程，未写入文件。
