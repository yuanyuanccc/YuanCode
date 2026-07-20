# YuanCode v0.4 实施任务：蓝色卡片式终端展示层

> 每个任务可在一次专注会话内完成。现有 Provider、配置和会话代码保持可用，本阶段只重构终端交互层。

## T1：接入 JLine 并建立终端会话抽象

- 影响文件：`pom.xml`、`src/main/java/com/yuancode/terminal/TerminalSession.java`、`TerminalCapabilities.java`、`src/test/java/com/yuancode/terminal/TerminalSessionTest.java`
- 依赖任务：无
- 工作内容：引入 JLine reader、terminal 与 Java 21/Windows 所需 provider，封装 system terminal 创建、尺寸、信号、样式能力、资源恢复和 dumb-terminal 降级。
- 参考资料定位：[JLine Terminal](https://jline.org/docs/terminal/) 的 `TerminalBuilder`、signals、capabilities 和 size；[Terminal Providers](https://jline.org/docs/modules/terminal-providers/) 的 JNI 与 dumb fallback。

## T2：实现主题、紧凑 Yuan 图标、蓝色启动卡片与提示区

- 影响文件：`src/main/java/com/yuancode/ui/Theme.java`、`WelcomeRenderer.java`、`PromptRenderer.java`、`MessageChrome.java`、`src/test/java/com/yuancode/ui/WelcomeRendererTest.java`、`PromptRendererTest.java`、`MessageChromeTest.java`
- 依赖任务：T1
- 工作内容：建立蓝色强调色与黄色思考色，渲染圆角欢迎卡、紧凑图标、用户身份行和独立输入框，展示版本、模型、Provider、工作目录与快捷提示，并用圆点区分思考与回答。
- 参考资料定位：`checklist.md` 的字标字符矩阵、颜色与宽窄布局快照；JLine attributed text/style API。

## T3：实现多行输入编辑、历史与按键绑定

- 影响文件：`src/main/java/com/yuancode/input/InteractiveInput.java`、`InputBindings.java`、`src/test/java/com/yuancode/input/InteractiveInputTest.java`
- 依赖任务：T1
- 工作内容：使用 LineReader 提供行编辑和进程内历史，绑定发送与插入换行操作，启用 bracketed paste，处理用户中断和 EOF。
- 参考资料定位：[JLine Line Reading](https://jline.org/docs/line-reader/) 的 history、multi-line input、widgets 和 exceptions；[Widgets](https://jline.org/docs/advanced/widgets/) 的自定义 widget 与 key map。

## T4：实现增量 Markdown 子集渲染

- 影响文件：`src/main/java/com/yuancode/ui/markdown/MarkdownStreamRenderer.java`、`MarkdownState.java`、`CodeHighlighter.java`、`src/test/java/com/yuancode/ui/markdown/MarkdownStreamRendererTest.java`
- 依赖任务：T1
- 工作内容：支持标题、列表、强调、行内代码和围栏代码块，维护跨增量状态，保证未闭合语法可稳定输出且完成时不重复全文。
- 参考资料定位：`MarkdownStreamRenderer` 的逐增量输入与结束方法；`MarkdownState` 的行、围栏和行内状态；`checklist.md` 的分片边界测试样例。

## T5：实现动态等待状态与完成摘要

- 影响文件：`src/main/java/com/yuancode/ui/LiveStatus.java`、`ResponseSummaryRenderer.java`、`src/test/java/com/yuancode/ui/LiveStatusTest.java`
- 依赖任务：T1
- 工作内容：在首个正文前定时刷新等待状态，正文到达时清理状态行，完成后展示耗时和可用 token，用能力检测决定是否原地刷新。
- 参考资料定位：JLine terminal cursor capabilities；`LiveStatus` 的启动、首 token、完成、取消生命周期。

## T6：重构消息、Thinking、错误与取消展示

- 影响文件：`src/main/java/com/yuancode/ui/ConversationRenderer.java`、`src/main/java/com/yuancode/cli/InteractiveShell.java`、`src/test/java/com/yuancode/ui/ConversationRendererTest.java`、`src/test/java/com/yuancode/cli/InteractiveShellTest.java`
- 依赖任务：T2、T4、T5
- 工作内容：替换 `you>` 与 `assistant>` 排版，整合流式 Markdown 和状态行，生成中展示 thinking 摘要并在正文开始后收起，取消时保留屏幕文本但不写入会话。
- 参考资料定位：现有 `InteractiveShell.chat` 的事件消费分支；`ConversationRenderer` 的 user/system/error/thinking/assistant 输出方法。

## T7：扩展状态类内置命令

- 影响文件：`src/main/java/com/yuancode/cli/CommandParser.java`、`InteractiveShell.java`、`src/test/java/com/yuancode/cli/CommandParserTest.java`
- 依赖任务：T2、T3
- 工作内容：增加模型、状态、配置路径与退出别名命令，保留原有命令，确保配置命令不读取或展示文件内容。
- 参考资料定位：`CommandParser` 的 sealed command 分支；`InteractiveShell` 的命令 dispatch；`checklist.md` 的精确输出字段。

## T8：实现无颜色与非交互降级路径

- 影响文件：`src/main/java/com/yuancode/terminal/TerminalSession.java`、`src/main/java/com/yuancode/ui/Theme.java`、`src/main/java/com/yuancode/cli/PlainTextShell.java`、`src/test/java/com/yuancode/cli/PlainTextShellTest.java`
- 依赖任务：T1、T4、T5
- 工作内容：检测 dumb terminal、重定向和 `NO_COLOR`，关闭 ANSI、动态重绘和高级编辑，同时保留逐行输入、流式输出及命令功能。
- 参考资料定位：[JLine troubleshooting](https://jline.org/docs/troubleshooting/) 的 non-interactive、dumb terminal 与 ANSI detection；`TerminalCapabilities` 的降级判定。

## T9：接入主流程

- 影响文件：`src/main/java/com/yuancode/YuanCodeApplication.java`、`src/main/java/com/yuancode/cli/InteractiveShell.java`、`src/main/java/com/yuancode/cli/SignalSupport.java`、`src/test/java/com/yuancode/YuanCodeApplicationTest.java`
- 依赖任务：T1-T8
- 工作内容：用 TerminalSession 和新输入/展示组件替换原始 BufferedReader/PrintWriter 装配，移除重复信号处理，保证配置缺失、启动失败、正常退出和异常退出均恢复终端。
- 参考资料定位：现有 `YuanCodeApplication.main` 装配点；`SignalSupport` 的替代路径；JLine `UserInterruptException` 与 `EndOfFileException`。

## T10：端到端验证

- 影响文件：`src/test/java/com/yuancode/e2e/TerminalConversationE2eTest.java`、`README.md`
- 依赖任务：T9
- 工作内容：使用虚拟终端和本地伪 SSE 服务验证欢迎页、多行输入、增量 Markdown、状态生命周期、多轮历史、取消、命令、Provider 切换和纯文本降级，并记录 Windows Terminal 手工验证步骤。
- 参考资料定位：`checklist.md` 的全部可观测项；现有 `ConversationE2eTest` 的伪服务与请求捕获方式。
