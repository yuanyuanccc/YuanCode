package com.yuancode.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageChromeTest {
    @Test
    void keepsThinkingAndAnswerBulletsObservableWithoutColor() {
        MessageChrome chrome = new MessageChrome(Theme.plain());

        assertEquals("● ", chrome.thinkingPrefix());
        assertEquals("● ", chrome.answerPrefix());
    }
}
