package com.yuancode.input;

import com.yuancode.ui.PromptRenderer;

import java.io.PrintWriter;

final class InputFrame {
    private final PrintWriter output;
    private final PromptRenderer prompt;
    private final int width;
    private final boolean dynamic;

    InputFrame(PrintWriter output, PromptRenderer prompt, int width, boolean dynamic) {
        this.output = output;
        this.prompt = prompt;
        this.width = width;
        this.dynamic = dynamic;
    }

    void open() {
        output.println(prompt.topBorder(width));
        if (dynamic) {
            output.println(prompt.emptyRow(width));
            output.print(prompt.bottomBorder(width));
            output.print("\r\u001B[1A");
        }
        output.flush();
    }

    void close() {
        output.print('\r');
        output.println(prompt.bottomBorder(width));
        output.flush();
    }
}
