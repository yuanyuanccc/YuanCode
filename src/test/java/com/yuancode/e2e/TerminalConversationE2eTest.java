package com.yuancode.e2e;

import com.yuancode.cli.InteractiveShell;
import com.yuancode.config.AppConfig;
import com.yuancode.config.ProviderConfig;
import com.yuancode.input.InteractiveInput;
import com.yuancode.llm.*;
import com.yuancode.ui.Theme;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TerminalConversationE2eTest {
    @Test
    void virtualTerminalSubmitsAltEnterMultilineAsOneRequest() throws Exception {
        byte[] keys = "first line\u001b\rsecond line\r/quit\r".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicReference<LlmRequest> captured = new AtomicReference<>();
        ProviderConfig provider = new ProviderConfig("deepseek", "anthropic", "deepseek-v4-flash",
                "http://localhost:1", "secret", 4096, false, false);
        AppConfig config = new AppConfig("deepseek", Map.of("deepseek", provider));
        LlmClient fake = request -> {
            captured.set(request);
            var events = new LinkedBlockingQueue<StreamEvent>();
            events.add(new StreamEvent.TextDelta("```java\nint value = 1;\n```"));
            events.add(new StreamEvent.Completed("done", new TokenUsage(3, 4)));
            return new StreamHandle(events, () -> {});
        };

        try (Terminal terminal = TerminalBuilder.builder().system(false)
                .streams(new ByteArrayInputStream(keys), output)
                .encoding(StandardCharsets.UTF_8).type("xterm-256color")
                .size(new Size(100, 30)).build()) {
            var shell = new InteractiveShell(new InteractiveInput(terminal), terminal.writer(), config,
                    ignored -> fake, Theme.plain(), false, 100,
                    Path.of("config.yaml"), Path.of("."));
            assertTimeoutPreemptively(Duration.ofSeconds(5), shell::run);
        }

        assertNotNull(captured.get());
        assertEquals("first line\nsecond line", captured.get().messages().getFirst().text());
        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("YuanCode v0.4.2"));
        assertTrue(rendered.contains("int value = 1;"));
        assertFalse(rendered.contains("assistant>"));
    }
}
