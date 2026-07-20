package com.yuancode.cli;

import com.yuancode.config.AppConfig;
import com.yuancode.config.ProviderConfig;
import com.yuancode.llm.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveShellTest {
    @Test
    void streamsAReplyKeepsHistoryAndSupportsCommands() throws Exception {
        String input = "first\nfollow-up\n/clear\n/provider openai\n/help\n/exit\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicReference<LlmRequest> secondRequest = new AtomicReference<>();
        int[] calls = {0};
        LlmClient fake = request -> {
            if (++calls[0] == 2) secondRequest.set(request);
            var queue = new LinkedBlockingQueue<StreamEvent>();
            queue.add(new StreamEvent.TextDelta("part-"));
            queue.add(new StreamEvent.TextDelta("done"));
            queue.add(new StreamEvent.Completed("done", TokenUsage.EMPTY));
            return new StreamHandle(queue, () -> {});
        };
        ProviderConfig a = provider("claude", "anthropic");
        ProviderConfig b = provider("openai", "openai");
        AppConfig config = new AppConfig("claude", Map.of("claude", a, "openai", b));
        var shell = new InteractiveShell(new BufferedReader(new StringReader(input)),
                new PrintWriter(bytes, true, StandardCharsets.UTF_8), config, ignored -> fake);

        shell.run();

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("part-done"));
        assertTrue(output.contains("对话已清空"));
        assertTrue(output.contains("已切换 Provider: openai"));
        assertTrue(output.contains("/provider <name>"));
        assertEquals(3, secondRequest.get().messages().size());
        assertEquals("first", secondRequest.get().messages().getFirst().text());
    }

    @Test
    void emptyInputDoesNotCallProvider() throws Exception {
        int[] calls = {0};
        ProviderConfig provider = provider("claude", "anthropic");
        AppConfig config = new AppConfig("claude", Map.of("claude", provider));
        var shell = new InteractiveShell(new BufferedReader(new StringReader("  \n/exit\n")),
                new PrintWriter(new StringWriter()), config, ignored -> request -> {
                    calls[0]++;
                    return null;
                });
        shell.run();
        assertEquals(0, calls[0]);
    }

    private static ProviderConfig provider(String name, String protocol) {
        return new ProviderConfig(name, protocol, "model", "http://localhost:1", "secret", 4096, false, false);
    }
}
