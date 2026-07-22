package com.yuancode.e2e;

import com.yuancode.agent.*;
import com.yuancode.conversation.Conversation;
import com.yuancode.llm.*;
import com.yuancode.tool.ToolRegistry;
import com.yuancode.tool.impl.AskUserTool;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AskUserDeclineE2eTest {
    @Test
    void decliningClarificationStopsTheRunBeforeTheModelCanTakeAction() throws Exception {
        ToolRegistry registry = new ToolRegistry().register(new AskUserTool(
                questions -> Map.of("_declined", "true")));
        AtomicInteger providerRequests = new AtomicInteger();
        LlmClient provider = request -> {
            providerRequests.incrementAndGet();
            LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
            queue.add(new StreamEvent.ToolCallComplete(0, "ask-1", "AskUserQuestion", Map.of(
                    "questions", List.of(Map.of(
                            "header", "Scope", "question", "Choose scope",
                            "options", List.of(Map.of("label", "MVP"), Map.of("label", "Full")))))));
            queue.add(new StreamEvent.Completed("tool_use", TokenUsage.EMPTY));
            return new StreamHandle(queue, () -> {});
        };
        Conversation conversation = new Conversation();

        List<AgentEvent> events = new AgentLoop(provider, conversation, registry, "anthropic", "system",
                Duration.ofSeconds(1)).start("build a shop").drainUntilComplete(Duration.ofSeconds(2));

        assertEquals(1, providerRequests.get());
        assertTrue(conversation.messages().isEmpty());
        AgentEvent.LoopCompleted terminal = (AgentEvent.LoopCompleted) events.stream()
                .filter(AgentEvent.LoopCompleted.class::isInstance).findFirst().orElseThrow();
        assertEquals(AgentTermination.CANCELLED, terminal.reason());
    }
}
