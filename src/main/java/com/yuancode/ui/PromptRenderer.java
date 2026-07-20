package com.yuancode.ui;

public final class PromptRenderer {
    private final Theme theme;

    public PromptRenderer(Theme theme) {
        this.theme = theme;
    }

    public String primary() {
        return theme.accent("│") + theme.user(" ❯ ");
    }

    public String identity(String username) {
        return theme.title(username + "@YuanCode") + " 🚀";
    }

    public String continuation() {
        return theme.muted("│ ");
    }

    public String rightHint() {
        return theme.muted("? for shortcuts ") + theme.accent("│");
    }

    public String topBorder(int width) {
        return border('╭', '╮', width);
    }

    public String bottomBorder(int width) {
        return border('╰', '╯', width);
    }

    public String emptyRow(int width) {
        return theme.accent("│") + " ".repeat(Math.max(1, width - 2)) + theme.accent("│");
    }

    private String border(char left, char right, int width) {
        return theme.accent(left + "─".repeat(Math.max(1, width - 2)) + right);
    }
}
