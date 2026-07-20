package com.yuancode.input;

import com.yuancode.ui.PromptRenderer;
import com.yuancode.ui.Theme;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveInputTest {
    @Test
    void paintsCompleteBoxBeforeMovingCursorIntoEditableRow() {
        StringWriter text = new StringWriter();
        InputFrame frame = new InputFrame(new PrintWriter(text),
                new PromptRenderer(Theme.plain()), 40, true);

        frame.open();

        String rendered = text.toString();
        int bottom = rendered.indexOf("╰" + "─".repeat(38) + "╯");
        int moveIntoEditor = rendered.indexOf("\u001B[1A");
        assertTrue(bottom >= 0 && bottom < moveIntoEditor, rendered);
    }

    @Test
    void repaintsBottomBorderWhenSubmittedBeforeResponseStarts() {
        StringWriter text = new StringWriter();
        InputFrame frame = new InputFrame(new PrintWriter(text),
                new PromptRenderer(Theme.plain()), 40, true);

        frame.open();
        frame.close();

        String border = "╰" + "─".repeat(38) + "╯";
        assertTrue(text.toString().indexOf(border) != text.toString().lastIndexOf(border), text.toString());
    }
}
