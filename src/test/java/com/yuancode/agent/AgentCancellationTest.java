package com.yuancode.agent;

import com.yuancode.conversation.Conversation;
import com.yuancode.llm.*;
import com.yuancode.tool.ToolRegistry;
import com.yuancode.tool.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentCancellationTest {
    @Test
    void cancelClosesStreamOnceAndRollsConversationBack() throws Exception {
        Conversation conversation = new Conversation();
        conversation.addUser("existing");
        int before = conversation.messages().size();
        CountDownLatch opened = new CountDownLatch(1);
        AtomicInteger cancelled = new AtomicInteger();
        LlmClient client = request -> {
            opened.countDown();
            return new StreamHandle(new LinkedBlockingQueue<>(), cancelled::incrementAndGet);
        };
        AgentLoop loop = new AgentLoop(client, conversation, new ToolRegistry(),
                "anthropic", "system", Duration.ofSeconds(30));

        AgentRun run = loop.start("new turn");
        assertTrue(opened.await(1, TimeUnit.SECONDS));
        run.cancel();
        List<AgentEvent> events = run.drainUntilComplete(Duration.ofSeconds(2));

        assertEquals(1, cancelled.get());
        assertEquals(before, conversation.messages().size());
        AgentEvent.LoopCompleted completed = (AgentEvent.LoopCompleted) events.stream()
                .filter(AgentEvent.LoopCompleted.class::isInstance).findFirst().orElseThrow();
        assertEquals(AgentTermination.CANCELLED, completed.reason());
    }

    @Test
    void cancelInterruptsAnActiveReadAndDoesNotCommitPartialToolBatch() throws Exception {
        Conversation conversation = new Conversation();
        CountDownLatch toolStarted = new CountDownLatch(1);
        CountDownLatch toolInterrupted = new CountDownLatch(1);
        ToolRegistry registry = new ToolRegistry().register(new Tool() {
            public String name() { return "BlockingRead"; }
            public String description() { return "blocking"; }
            public ToolCategory category() { return ToolCategory.READ; }
            public Map<String, Object> schema() { return Map.of("type", "object"); }
            public ToolResult execute(Map<String, Object> arguments) {
                toolStarted.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException error) {
                    toolInterrupted.countDown();
                    Thread.currentThread().interrupt();
                    return ToolResult.error("interrupted");
                }
                return ToolResult.success("unexpected");
            }
        });
        LlmClient client = request -> {
            LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
            queue.add(new StreamEvent.ToolCallComplete(0, "r1", "BlockingRead", Map.of()));
            queue.add(new StreamEvent.Completed("tool_use", TokenUsage.EMPTY));
            return new StreamHandle(queue, () -> {});
        };
        AgentRun run = new AgentLoop(client, conversation, registry, "anthropic", "system",
                Duration.ofSeconds(1)).start("read");
        assertTrue(toolStarted.await(1, TimeUnit.SECONDS));

        run.cancel();
        List<AgentEvent> events = run.drainUntilComplete(Duration.ofSeconds(2));

        assertTrue(toolInterrupted.await(1, TimeUnit.SECONDS));
        assertTrue(conversation.messages().isEmpty());
        assertEquals(1, events.stream().filter(AgentEvent.LoopCompleted.class::isInstance).count());
    }
}
