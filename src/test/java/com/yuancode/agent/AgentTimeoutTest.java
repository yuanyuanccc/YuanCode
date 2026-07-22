package com.yuancode.agent;

import com.yuancode.conversation.Conversation;
import com.yuancode.llm.*;
import com.yuancode.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class AgentTimeoutTest {
    @Test
    void reportsObservableTimeoutAndRollsBack() throws Exception {
        Conversation conversation = new Conversation();
        LlmClient provider = request -> new StreamHandle(new LinkedBlockingQueue<>(), () -> {});
        AgentRun run = new AgentLoop(provider, conversation, new ToolRegistry(), "anthropic", "system",
                Duration.ofMillis(10)).start("wait");

        List<AgentEvent> events = run.drainUntilComplete(Duration.ofSeconds(2));

        assertTrue(events.stream().anyMatch(event -> event instanceof AgentEvent.Error error
                && error.message().equals("Stream timeout")));
        assertEquals(AgentTermination.TIMED_OUT, ((AgentEvent.LoopCompleted) events.stream()
                .filter(AgentEvent.LoopCompleted.class::isInstance).findFirst().orElseThrow()).reason());
        assertTrue(conversation.messages().isEmpty());
    }
}
