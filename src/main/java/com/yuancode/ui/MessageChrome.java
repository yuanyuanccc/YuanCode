package com.yuancode.ui;

public final class MessageChrome {
    private final Theme theme;

    public MessageChrome(Theme theme) {
        this.theme = theme;
    }

    public String thinkingPrefix() {
        return theme.warning("● ");
    }

    public String answerPrefix() {
        return theme.accent("● ");
    }
}
