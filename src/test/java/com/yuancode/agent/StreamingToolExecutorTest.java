package com.yuancode.agent;

import com.yuancode.conversation.ToolCallBlock;
import com.yuancode.conversation.ToolResultBlock;
import com.yuancode.tool.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StreamingToolExecutorTest {
    @Test
    void readToolsOverlapAndResultsRemainInCallOrder() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ToolRegistry registry = new ToolRegistry()
                .register(tool("first", ToolCategory.READ, () -> await(bothStarted, release, "one")))
                .register(tool("second", ToolCategory.READ, () -> await(bothStarted, release, "two")));
        StreamingToolExecutor executor = new StreamingToolExecutor(registry);
        Thread releaser = Thread.startVirtualThread(() -> {
            try { assertTrue(bothStarted.await(1, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new RuntimeException(error); }
            release.countDown();
        });

        List<ToolResultBlock> results = executor.execute(List.of(
                new ToolCallBlock("1", "first", Map.of()),
                new ToolCallBlock("2", "second", Map.of())), AgentMode.NORMAL, ignored -> {});

        releaser.join();
        assertEquals(List.of("one", "two"), results.stream().map(ToolResultBlock::output).toList());
    }

    @Test
    void sideEffectsAreSerialAndPlanOnlyBlocksThem() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry()
                .register(tool("write", ToolCategory.WRITE, () -> tracked(active, maxActive, executions)))
                .register(tool("command", ToolCategory.COMMAND, () -> tracked(active, maxActive, executions)));
        StreamingToolExecutor executor = new StreamingToolExecutor(registry);
        List<ToolCallBlock> calls = List.of(new ToolCallBlock("1", "write", Map.of()),
                new ToolCallBlock("2", "command", Map.of()));

        executor.execute(calls, AgentMode.NORMAL, ignored -> {});
        assertEquals(1, maxActive.get());
        assertEquals(2, executions.get());

        List<ToolResultBlock> blocked = executor.execute(calls, AgentMode.PLAN_ONLY, ignored -> {});
        assertEquals(2, executions.get());
        assertTrue(blocked.stream().allMatch(ToolResultBlock::isError));
        assertTrue(blocked.getFirst().output().contains("/plan off"));
    }

    private static String await(CountDownLatch started, CountDownLatch release, String value) {
        started.countDown();
        try { assertTrue(release.await(1, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
        return value;
    }

    private static String tracked(AtomicInteger active, AtomicInteger max, AtomicInteger executions) {
        executions.incrementAndGet();
        int now = active.incrementAndGet();
        max.accumulateAndGet(now, Math::max);
        Thread.yield();
        active.decrementAndGet();
        return "ok";
    }

    private static Tool tool(String name, ToolCategory category, java.util.function.Supplier<String> action) {
        return new Tool() {
            public String name() { return name; }
            public String description() { return name; }
            public ToolCategory category() { return category; }
            public Map<String, Object> schema() { return Map.of("type", "object"); }
            public ToolResult execute(Map<String, Object> arguments) { return ToolResult.success(action.get()); }
        };
    }
}
