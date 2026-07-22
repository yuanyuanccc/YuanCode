package com.yuancode.e2e;

import com.yuancode.agent.*;
import com.yuancode.conversation.Conversation;
import com.yuancode.llm.*;
import com.yuancode.tool.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncAgentLoopE2eTest {
    @Test
    void runsConcurrentReadsWritesBackInCallOrderAndFinishesOnce() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ToolRegistry registry = new ToolRegistry()
                .register(readTool("SlowFirst", "first", bothStarted, release))
                .register(readTool("FastSecond", "second", bothStarted, release));
        Conversation conversation = new Conversation();
        AtomicInteger requests = new AtomicInteger();
        LlmClient provider = request -> {
            if (requests.getAndIncrement() == 0) {
                return stream(
                        new StreamEvent.ThinkingDelta("inspect"),
                        new StreamEvent.ToolCallComplete(0, "c1", "SlowFirst", Map.of("path", "a")),
                        new StreamEvent.ToolCallComplete(1, "c2", "FastSecond", Map.of("path", "b")),
                        new StreamEvent.Completed("tool_use", new TokenUsage(5, 2)));
            }
            assertEquals(List.of("first", "second"), request.messages().getLast().toolResults().stream()
                    .map(result -> result.output()).toList());
            return stream(new StreamEvent.TextDelta("combined"),
                    new StreamEvent.Completed("end_turn", new TokenUsage(8, 3)));
        };
        Thread releaser = Thread.startVirtualThread(() -> {
            try { assertTrue(bothStarted.await(1, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new RuntimeException(error); }
            release.countDown();
        });

        AgentRun run = new AgentLoop(provider, conversation, registry, "anthropic", "system",
                Duration.ofSeconds(1)).start("read both");
        List<AgentEvent> events = run.drainUntilComplete(Duration.ofSeconds(5));
        releaser.join();

        assertEquals(2, requests.get());
        assertEquals(4, conversation.messages().size());
        assertEquals("combined", conversation.messages().getLast().text());
        assertEquals(2, events.stream().filter(AgentEvent.ToolCallStarted.class::isInstance).count());
        assertEquals(2, events.stream().filter(AgentEvent.ToolResultCompleted.class::isInstance).count());
        assertEquals(1, events.stream().filter(AgentEvent.FinalReply.class::isInstance).count());
        assertEquals(1, events.stream().filter(AgentEvent.LoopCompleted.class::isInstance).count());
        assertEquals(AgentTermination.COMPLETED, ((AgentEvent.LoopCompleted) events.stream()
                .filter(AgentEvent.LoopCompleted.class::isInstance).findFirst().orElseThrow()).reason());
    }

    private static Tool readTool(String name, String output, CountDownLatch started, CountDownLatch release) {
        return new Tool() {
            public String name() { return name; }
            public String description() { return name; }
            public ToolCategory category() { return ToolCategory.READ; }
            public Map<String, Object> schema() { return Map.of("type", "object"); }
            public ToolResult execute(Map<String, Object> arguments) {
                started.countDown();
                try { assertTrue(release.await(1, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
                return ToolResult.success(output);
            }
        };
    }

    private static StreamHandle stream(StreamEvent... events) {
        LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
        queue.addAll(List.of(events));
        return new StreamHandle(queue, () -> {});
    }
}
