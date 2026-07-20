package com.yuancode;

import com.yuancode.cli.InteractiveShell;
import com.yuancode.config.ConfigException;
import com.yuancode.config.ConfigLoader;
import com.yuancode.input.InteractiveInput;
import com.yuancode.llm.LlmClientFactory;
import com.yuancode.terminal.TerminalSession;
import com.yuancode.ui.Theme;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;

public final class YuanCodeApplication {
    private YuanCodeApplication() {}

    public static void main(String[] args) {
        PrintWriter fallbackOutput = new PrintWriter(System.out, true,
                System.console() == null ? Charset.defaultCharset() : System.console().charset());
        try {
            Path configPath = configPath(args);
            ConfigLoader.LoadResult loaded = new ConfigLoader().load(configPath);
            if (loaded.createdExample()) {
                fallbackOutput.println("已创建示例配置: " + configPath);
                fallbackOutput.println("请设置 API Key 后重新启动 YuanCode。");
                return;
            }
            try (TerminalSession terminal = TerminalSession.open()) {
                var capabilities = terminal.capabilities();
                Theme theme = Theme.detect(capabilities.ansi());
                var shell = new InteractiveShell(new InteractiveInput(terminal.terminal(), theme), terminal.writer(),
                        loaded.config(), LlmClientFactory::create, theme,
                        capabilities.cursorMovement(), capabilities.width(), configPath,
                        Path.of("").toAbsolutePath());
                terminal.terminal().handle(Terminal.Signal.INT, signal -> shell.handleInterrupt());
                shell.run();
            }
        } catch (ConfigException error) {
            fallbackOutput.println("配置错误: " + error.getMessage());
        } catch (Exception error) {
            fallbackOutput.println("启动失败: "
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
    }

    private static Path configPath(String[] args) {
        if (args.length == 0) return ConfigLoader.defaultPath();
        if (args.length == 2 && args[0].equals("--config")) return Path.of(args[1]);
        throw new ConfigException("用法: yuancode [--config <path>]");
    }
}
