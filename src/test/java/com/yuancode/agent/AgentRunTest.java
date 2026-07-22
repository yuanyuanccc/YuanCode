package com.yuancode.agent;

import com.yuancode.conversation.Conversation;
import com.yuancode.llm.*;
import com.yuancode.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AgentRunTest {
    @Test
    void startReturnsBeforeTheProviderFinishesAndPublishesACompleteEventStream() throws Exception {
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        LlmClient client = request -> {
            providerStarted.countDown();
            try {
                assertTrue(releaseProvider.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(error);
            }
            LinkedBlockingQueue<StreamEvent> events = new LinkedBlockingQueue<>();
            events.add(new StreamEvent.TextDelta("完成"));
            events.add(new StreamEvent.Completed("end_turn", new TokenUsage(3, 2)));
            return new StreamHandle(events, () -> {});
        };
        AgentLoop loop = new AgentLoop(client, new Conversation(), new ToolRegistry(),
                "anthropic", "system", Duration.ofSeconds(1));

        AgentRun run = loop.start("请处理");

        assertTrue(providerStarted.await(1, TimeUnit.SECONDS));
        assertFalse(run.isDone());
        assertInstanceOf(AgentEvent.UserMessage.class, run.next(Duration.ofSeconds(1)));
        releaseProvider.countDown();
        List<AgentEvent> remaining = run.drainUntilComplete(Duration.ofSeconds(2));
        assertTrue(remaining.stream().anyMatch(AgentEvent.TextDelta.class::isInstance));
        assertEquals(1, remaining.stream().filter(AgentEvent.LoopCompleted.class::isInstance).count());
        AgentEvent.LoopCompleted completed = (AgentEvent.LoopCompleted) remaining.stream()
                .filter(AgentEvent.LoopCompleted.class::isInstance).findFirst().orElseThrow();
        assertEquals(AgentTermination.COMPLETED, completed.reason());
        assertTrue(run.isDone());
    }

    @Test
    void interruptedProducerRestoresItsInterruptFlagAndCanStillTerminate() throws Exception {
        AgentRun run = new AgentRun();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread producer = Thread.startVirtualThread(() -> {
            Thread.currentThread().interrupt();
            run.publish(new AgentEvent.TextDelta("ignored"));
            interruptRestored.set(Thread.currentThread().isInterrupted());
            run.complete(AgentTermination.CANCELLED, 0);
        });
        producer.join();

        assertTrue(interruptRestored.get());
        List<AgentEvent> events = run.drainUntilComplete(Duration.ofSeconds(1));
        assertEquals(1, events.stream().filter(AgentEvent.LoopCompleted.class::isInstance).count());
    }
}
