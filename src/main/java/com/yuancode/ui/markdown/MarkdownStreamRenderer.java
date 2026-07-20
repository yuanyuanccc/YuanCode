package com.yuancode.ui.markdown;

import com.yuancode.ui.Theme;

import java.io.PrintWriter;

public final class MarkdownStreamRenderer {
    private final PrintWriter output;
    private final Theme theme;
    private final MarkdownState state = new MarkdownState();
    private final StringBuilder fenceProbe = new StringBuilder();

    public MarkdownStreamRenderer(PrintWriter output, Theme theme) {
        this.output = output;
        this.theme = theme;
    }

    public synchronized void accept(String delta) {
        if (delta == null || delta.isEmpty()) return;
        if (!theme.colorEnabled()) {
            output.print(delta);
            updateState(delta);
            output.flush();
            return;
        }
        String[] lines = delta.split("(?<=\\n)", -1);
        for (String line : lines) {
            if (state.codeFence()) output.print(theme.code(line));
            else if (state.lineStart() && line.startsWith("#")) output.print(theme.heading(line));
            else output.print(styleInline(line));
            updateState(line);
        }
        output.flush();
    }

    public synchronized void finish() { output.flush(); }

    private String styleInline(String line) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < line.length()) {
            int codeStart = line.indexOf('`', index);
            int strongStart = line.indexOf("**", index);
            if (codeStart < 0 && strongStart < 0) { result.append(line, index, line.length()); break; }
            boolean codeFirst = codeStart >= 0 && (strongStart < 0 || codeStart < strongStart);
            int start = codeFirst ? codeStart : strongStart;
            String delimiter = codeFirst ? "`" : "**";
            int end = line.indexOf(delimiter, start + delimiter.length());
            if (end < 0) { result.append(line, index, line.length()); break; }
            result.append(line, index, start);
            String token = line.substring(start, end + delimiter.length());
            result.append(codeFirst ? theme.code(token) : theme.strong(token));
            index = end + delimiter.length();
        }
        return result.toString();
    }

    private void updateState(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '`') {
                fenceProbe.append(ch);
                if (fenceProbe.length() == 3) {
                    state.toggleFence();
                    fenceProbe.setLength(0);
                }
            } else {
                fenceProbe.setLength(0);
            }
            state.lineStart(ch == '\n');
        }
    }
}
