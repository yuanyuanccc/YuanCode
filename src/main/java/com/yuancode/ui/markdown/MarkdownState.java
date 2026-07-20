package com.yuancode.ui.markdown;

public final class MarkdownState {
    private boolean codeFence;
    private boolean lineStart = true;
    public boolean codeFence() { return codeFence; }
    public boolean lineStart() { return lineStart; }
    void toggleFence() { codeFence = !codeFence; }
    void lineStart(boolean value) { lineStart = value; }
}
