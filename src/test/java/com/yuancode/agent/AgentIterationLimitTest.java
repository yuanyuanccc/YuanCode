package com.yuancode.agent;

import com.yuancode.conversation.Conversation;
import com.yuancode.llm.*;
import com.yuancode.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentIterationLimitTest {
    @Test
    void stopsAfterTwentyModelRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        LlmClient client = request -> {
            int number = requests.incrementAndGet();
            LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
            queue.add(new StreamEvent.ToolCallComplete(0, "call-" + number, "missing", Map.of()));
            queue.add(new StreamEvent.Completed("tool_use", TokenUsage.EMPTY));
            return new StreamHandle(queue, () -> {});
        };
        AgentRun run = new AgentLoop(client, new Conversation(), new ToolRegistry(),
                "anthropic", "system", Duration.ofSeconds(1)).start("loop");

        List<AgentEvent> events = run.drainUntilComplete(Duration.ofSeconds(5));

        assertEquals(20, requests.get());
        AgentEvent.LoopCompleted terminal = (AgentEvent.LoopCompleted) events.stream()
                .filter(AgentEvent.LoopCompleted.class::isInstance).findFirst().orElseThrow();
        assertEquals(AgentTermination.MAX_ITERATIONS, terminal.reason());
        assertEquals(20, terminal.iterations());
    }
}
