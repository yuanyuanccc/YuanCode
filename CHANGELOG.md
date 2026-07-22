# Changelog

## v0.6.1 — 2026-07-22

### 修复

- 将结构化澄清工具统一命名为 `AskUserQuestion`，并从按需发现工具调整为首次请求始终可见的核心工具。
- 对大型、宽泛、关键约束缺失且存在多种实现方向的开发请求，要求模型先澄清关键需求，再制定计划或修改文件。
- 用户拒绝、中断或结束结构化提问时，立即取消当前 Agent 运行并回滚本轮 Conversation，防止模型绕过澄清继续执行副作用工具。

### 验证

- 真实 DeepSeek 输入“我要一个电商系统”会先询问技术栈和功能范围。
- 51 项自动化测试全部通过，失败数与跳过数均为 0。
- 可执行产物为 `target/yuancode-v0.6.1.jar`。

## v0.6.0 — 2026-07-22

### 新增

- 基于 Java 21 虚拟线程和有界 `AgentEvent` 队列的异步 Agent Loop。
- READ 工具并发执行，WRITE 与 COMMAND 工具串行执行，工具结果按模型原始调用顺序回填。
- NORMAL 与 PLAN_ONLY 模式，以及 `/plan`、`/plan on`、`/plan off` 命令。
- 运行级取消、流静默超时、20 轮上限和 Conversation 检查点回滚。

## v0.5.0 — 2026-07-21

### 新增

- ReadFile、WriteFile、EditFile、Bash、Glob、Grep 等本地工具。
- Anthropic 与 OpenAI 工具调用事件解析和最小 ReAct 循环。
- Windows Git Bash 自动发现、ToolSearch 和终端结构化提问基础能力。
