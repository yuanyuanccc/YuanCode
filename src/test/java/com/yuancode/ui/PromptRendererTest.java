package com.yuancode.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptRendererTest {
    @Test
    void rendersCompactPrimaryContinuationAndShortcutHint() {
        PromptRenderer prompt = new PromptRenderer(Theme.plain());

        assertEquals("Yuanc@YuanCode 🚀", prompt.identity("Yuanc"));
        assertEquals("╭" + "─".repeat(38) + "╮", prompt.topBorder(40));
        assertEquals("│ ❯ ", prompt.primary());
        assertEquals("│ ", prompt.continuation());
        assertEquals("? for shortcuts │", prompt.rightHint());
        assertEquals("╰" + "─".repeat(38) + "╯", prompt.bottomBorder(40));
    }
}
