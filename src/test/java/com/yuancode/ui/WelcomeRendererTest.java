package com.yuancode.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WelcomeRendererTest {
    @Test
    void rendersBlueRoundedWelcomeCardWithCompactYuanBadge() {
        String rendered = new WelcomeRenderer(Theme.plain()).render(
                new WelcomeRenderer.Context("0.4.2", "deepseek", "deepseek-v4-flash",
                        Path.of("C:/Users/Yuanc")), 100);

        assertTrue(rendered.contains("╭" + "─".repeat(70) + "╮"));
        assertTrue(rendered.contains("│  [ ■  ■ ]  Welcome to YuanCode!"));
        assertTrue(rendered.contains("Type /help for help information."));
        assertTrue(rendered.contains("YuanCode v0.4.2"));
        assertTrue(rendered.contains("Model: deepseek-v4-flash · Provider: deepseek"));
        assertTrue(rendered.contains(Path.of("C:/Users/Yuanc").toAbsolutePath().normalize().toString()));
        assertTrue(rendered.contains("╰" + "─".repeat(70) + "╯"));
        assertFalse(rendered.contains("\u001B["));
    }
}
