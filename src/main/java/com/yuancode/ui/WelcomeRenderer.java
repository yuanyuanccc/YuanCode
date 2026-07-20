package com.yuancode.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WelcomeRenderer {
    private final Theme theme;

    public WelcomeRenderer(Theme theme) {
        this.theme = theme;
    }

    public String render(Context context, int width) {
        int innerWidth = Math.max(54, Math.min(70, width - 6));
        List<String> output = new ArrayList<>();
        output.add("");
        output.add(theme.accent("╭" + "─".repeat(innerWidth) + "╮"));
        output.add(line("  " + theme.badge("[ ■  ■ ]") + "  " + theme.title("Welcome to YuanCode!"), innerWidth));
        output.add(line("             " + theme.muted("Type /help for help information."), innerWidth));
        output.add(line("", innerWidth));
        output.add(line("  " + theme.title("YuanCode") + theme.muted(" v" + context.version()), innerWidth));
        output.add(line("  Model: " + context.model() + " · Provider: " + context.provider(), innerWidth));
        output.add(line("  " + theme.muted(context.workingDirectory().toAbsolutePath().normalize().toString()), innerWidth));
        output.add(theme.accent("╰" + "─".repeat(innerWidth) + "╯"));
        output.add("");
        return String.join(System.lineSeparator(), output);
    }

    private String line(String content, int width) {
        int visible = Theme.visibleLength(content);
        return theme.accent("│") + content + " ".repeat(Math.max(0, width - visible)) + theme.accent("│");
    }

    public record Context(String version, String provider, String model, Path workingDirectory) {}
}
