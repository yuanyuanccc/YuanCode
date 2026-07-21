package com.yuancode.agent;

import com.yuancode.conversation.*;
import com.yuancode.llm.*;
import com.yuancode.tool.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentCoreTest {
    @Test
    void unknownToolsBecomeErrorsAndLongResultsAreTruncated() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            public String name() { return "Long"; }
            public String description() { return "long"; }
            public ToolCategory category() { return ToolCategory.READ; }
            public Map<String, Object> schema() { return Map.of("type", "object"); }
            public ToolResult execute(Map<String, Object> arguments) { return ToolResult.success("x".repeat(10_001)); }
        });
        ToolExecutor executor = new ToolExecutor(registry);

        ToolResultBlock unknown = executor.execute(new ToolCallBlock("1", "Missing", Map.of()));
        ToolResultBlock truncated = executor.execute(new ToolCallBlock("2", "Long", Map.of()));

        assertTrue(unknown.isError());
        assertTrue(unknown.output().contains("Unknown tool"));
        assertTrue(truncated.output().endsWith("... (truncated)"));
        assertEquals(10_000 + "... (truncated)".length(), truncated.output().length());
    }

    @Test
    void advertisesDeferredToolsInTheSystemPrompt() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new DeferredTool());
        AtomicReference<LlmRequest> captured = new AtomicReference<>();
        LlmClient client = request -> {
            captured.set(request);
            return stream(new StreamEvent.TextDelta("done"),
                    new StreamEvent.Completed("end_turn", new TokenUsage(1, 1)));
        };

        new AgentLoop(client, new Conversation(), registry, "anthropic", "base", Duration.ofSeconds(1))
                .run("hello", new AgentLoop.Listener() {});

        assertTrue(captured.get().systemPrompt().contains("AskUser"));
        assertTrue(captured.get().systemPrompt().contains("ToolSearch"));
    }

    private static StreamHandle stream(StreamEvent... events) {
        LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
        queue.addAll(List.of(events));
        return new StreamHandle(queue, () -> {});
    }

    private static final class DeferredTool implements Tool {
        public String name() { return "AskUser"; }
        public String description() { return "ask"; }
        public ToolCategory category() { return ToolCategory.READ; }
        public Map<String, Object> schema() { return Map.of("type", "object"); }
        public ToolResult execute(Map<String, Object> arguments) { return ToolResult.success(""); }
        public boolean shouldDefer() { return true; }
    }
}
