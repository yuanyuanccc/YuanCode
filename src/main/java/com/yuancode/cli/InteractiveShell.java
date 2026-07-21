package com.yuancode.cli;

import com.yuancode.config.AppConfig;
import com.yuancode.config.ConfigException;
import com.yuancode.config.ProviderConfig;
import com.yuancode.agent.AgentLoop;
import com.yuancode.conversation.Conversation;
import com.yuancode.conversation.ToolResultBlock;
import com.yuancode.input.InteractiveInput;
import com.yuancode.llm.*;
import com.yuancode.ui.*;
import com.yuancode.ui.markdown.MarkdownStreamRenderer;
import com.yuancode.tool.ToolRegistry;
import com.yuancode.tool.impl.AskUserTool;
import com.yuancode.tool.impl.ToolSearchTool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class InteractiveShell {
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(90);
    private static final String VERSION = "0.5.0";
    private final BufferedReader plainInput;
    private final InteractiveInput interactiveInput;
    private final PrintWriter output;
    private final AppConfig config;
    private final Function<ProviderConfig, LlmClient> clientFactory;
    private final Conversation conversation = new Conversation();
    private final CommandParser parser = new CommandParser();
    private final AtomicReference<StreamHandle> activeStream = new AtomicReference<>();
    private final Theme theme;
    private final boolean dynamic;
    private final int width;
    private final Path configPath;
    private final Path workingDirectory;
    private volatile boolean exitRequested;
    private volatile Thread runThread;
    private String providerName;
    private LlmClient client;
    private ToolRegistry registry;

    public InteractiveShell(BufferedReader input, PrintWriter output, AppConfig config,
                            Function<ProviderConfig, LlmClient> clientFactory) {
        this(input, null, output, config, clientFactory, Theme.plain(), false, 80,
                Path.of(System.getProperty("user.home"), ".yuancode", "config.yaml"),
                Path.of("").toAbsolutePath());
    }

    public InteractiveShell(InteractiveInput input, PrintWriter output, AppConfig config,
                            Function<ProviderConfig, LlmClient> clientFactory, Theme theme,
                            boolean dynamic, int width, Path configPath, Path workingDirectory) {
        this(null, input, output, config, clientFactory, theme, dynamic, width, configPath, workingDirectory);
    }

    private InteractiveShell(BufferedReader plainInput, InteractiveInput interactiveInput, PrintWriter output,
                             AppConfig config, Function<ProviderConfig, LlmClient> clientFactory,
                             Theme theme, boolean dynamic, int width, Path configPath, Path workingDirectory) {
        this.plainInput = plainInput;
        this.interactiveInput = interactiveInput;
        this.output = output;
        this.config = config;
        this.clientFactory = clientFactory;
        this.theme = theme;
        this.dynamic = dynamic;
        this.width = width;
        this.configPath = configPath.toAbsolutePath().normalize();
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        switchProvider(config.activeProviderName(), false);
    }

    public void run() throws IOException {
        runThread = Thread.currentThread();
        renderWelcome();
        while (!exitRequested) {
            switch (readInput()) {
                case InputResult.Message message -> dispatch(parser.parse(message.text()));
                case InputResult.Interrupted ignored -> exitRequested = true;
                case InputResult.EndOfFile ignored -> exitRequested = true;
            }
        }
    }

    public boolean handleInterrupt() {
        StreamHandle stream = activeStream.get();
        if (stream == null) {
            exitRequested = true;
            return true;
        }
        stream.cancel();
        Thread thread = runThread;
        if (thread != null) thread.interrupt();
        return false;
    }

    private InputResult readInput() throws IOException {
        if (interactiveInput != null) {
            return switch (interactiveInput.read()) {
                case InteractiveInput.ReadResult.Message message -> new InputResult.Message(message.text());
                case InteractiveInput.ReadResult.Interrupted ignored -> new InputResult.Interrupted();
                case InteractiveInput.ReadResult.EndOfFile ignored -> new InputResult.EndOfFile();
            };
        }
        output.print("› ");
        output.flush();
        String line = plainInput.readLine();
        return line == null ? new InputResult.EndOfFile() : new InputResult.Message(line);
    }

    private void renderWelcome() {
        ProviderConfig provider = config.provider(providerName);
        output.print(new WelcomeRenderer(theme).render(
                new WelcomeRenderer.Context(VERSION, providerName, provider.model(), workingDirectory), width));
        output.flush();
    }

    private void dispatch(CommandParser.Command command) {
        switch (command) {
            case CommandParser.Command.Message message -> {
                if (!message.text().isBlank()) chat(message.text());
            }
            case CommandParser.Command.Help ignored -> output.println(
                    "/help  /clear  /provider <name>  /model  /status  /config  /exit  /quit");
            case CommandParser.Command.Clear ignored -> {
                conversation.clear();
                output.println(theme.muted("对话已清空"));
            }
            case CommandParser.Command.Exit ignored -> exitRequested = true;
            case CommandParser.Command.SwitchProvider change -> switchProvider(change.name(), true);
            case CommandParser.Command.Model ignored -> showModel();
            case CommandParser.Command.Status ignored -> output.println(theme.muted(
                    "工作目录: " + workingDirectory + " · 会话轮数: " + conversation.completedTurns()));
            case CommandParser.Command.Config ignored -> output.println(theme.muted("配置文件: " + configPath));
            case CommandParser.Command.Invalid invalid -> output.println(theme.error(invalid.message()));
        }
        output.flush();
    }

    private void showModel() {
        ProviderConfig provider = config.provider(providerName);
        output.println(theme.muted("Provider: " + providerName + " · protocol: "
                + provider.protocol() + " · model: " + provider.model()));
    }

    private void switchProvider(String name, boolean announce) {
        try {
            ProviderConfig selected = config.provider(name);
            LlmClient replacement = clientFactory.apply(selected);
            providerName = name;
            client = replacement;
            registry = ToolRegistry.createDefault(workingDirectory);
            registry.register(new ToolSearchTool(registry, selected.protocol()));
            registry.register(new AskUserTool(this::askQuestions));
            if (announce) output.println(theme.muted("已切换 Provider: " + providerName));
        } catch (ConfigException error) {
            output.println(theme.error(error.getMessage()));
        }
    }

    private void chat(String prompt) {
        if (interactiveInput == null) output.println(theme.user("> " + prompt));
        boolean[] thinkingStarted = {false};
        boolean[] textStarted = {false};
        LiveStatus status = new LiveStatus(output, theme, dynamic);
        MarkdownStreamRenderer markdown = new MarkdownStreamRenderer(output, theme);
        MessageChrome chrome = new MessageChrome(theme);
        status.start();
        try {
            ProviderConfig provider = config.provider(providerName);
            AgentLoop loop = new AgentLoop(client, conversation, registry, provider.protocol(),
                    "You are YuanCode, a helpful terminal AI coding assistant. Use tools when needed.",
                    STREAM_IDLE_TIMEOUT);
            loop.run(prompt, new AgentLoop.Listener() {
                    @Override public void streamOpened(StreamHandle stream) { activeStream.set(stream); }
                    @Override public void streamClosed() { activeStream.set(null); }
                    @Override public void text(String delta) {
                        if (!textStarted[0]) {
                            status.firstToken();
                            if (thinkingStarted[0]) output.println(theme.muted("Thinking complete"));
                            output.print(chrome.answerPrefix());
                            textStarted[0] = true;
                        }
                        markdown.accept(delta);
                    }
                    @Override public void thinking(String delta) {
                        if (!thinkingStarted[0]) {
                            status.firstToken();
                            output.print(chrome.thinkingPrefix() + theme.muted("Thinking… "));
                            thinkingStarted[0] = true;
                        }
                        output.print(theme.muted(delta));
                        output.flush();
                    }
                    @Override public void toolCall(String name) {
                        status.firstToken();
                        if (thinkingStarted[0] && !textStarted[0]) output.println();
                        output.println(theme.muted("◆ Running " + name + "…"));
                    }
                    @Override public void toolResult(String name, ToolResultBlock result) {
                        String marker = result.isError() ? "✗" : "✓";
                        output.println(theme.muted(marker + " " + name));
                        if (!result.output().isBlank()) output.println(theme.muted(result.output()));
                    }
                    @Override public void completed(TokenUsage usage) {
                        status.firstToken();
                        markdown.finish();
                        output.println();
                        output.println(new ResponseSummaryRenderer(theme).render(status.elapsed(), usage));
                    }
                });
        } catch (InterruptedException cancelled) {
            Thread.interrupted();
            status.firstToken();
            output.println(theme.muted("已取消"));
        } catch (RuntimeException error) {
            status.firstToken();
            output.println(theme.error("错误: " + safeMessage(error)));
        } finally {
            activeStream.set(null);
            status.close();
            output.flush();
        }
    }

    private Map<String, String> askQuestions(List<Map<String, Object>> questions) throws IOException {
        Map<String, String> answers = new LinkedHashMap<>();
        for (Map<String, Object> question : questions) {
            String header = String.valueOf(question.getOrDefault("header", "Question"));
            output.println(theme.user("? " + question.getOrDefault("question", header)));
            Object rawOptions = question.get("options");
            if (rawOptions instanceof List<?> options) {
                for (int index = 0; index < options.size(); index++) {
                    Object option = options.get(index);
                    Object label = option instanceof Map<?, ?> map ? map.get("label") : option;
                    output.println(theme.muted("  " + (index + 1) + ". " + label));
                }
            }
            output.flush();
            InputResult input = readInput();
            if (!(input instanceof InputResult.Message message) || message.text().isBlank()) {
                return Map.of("_declined", "true");
            }
            answers.put(header, message.text());
        }
        return answers;
    }

    private static String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? "模型请求失败" : error.getMessage();
    }

    private sealed interface InputResult permits InputResult.Message, InputResult.Interrupted, InputResult.EndOfFile {
        record Message(String text) implements InputResult {}
        record Interrupted() implements InputResult {}
        record EndOfFile() implements InputResult {}
    }
}
