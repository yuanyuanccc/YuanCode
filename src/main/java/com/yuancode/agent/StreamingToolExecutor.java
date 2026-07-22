package com.yuancode.agent;

import com.yuancode.conversation.ToolCallBlock;
import com.yuancode.conversation.ToolResultBlock;
import com.yuancode.tool.ToolCategory;
import com.yuancode.tool.ToolRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class StreamingToolExecutor {
    private static final String PLAN_ONLY_ERROR =
            "Plan-only 模式只允许读取工具；请先使用 /plan off 关闭 Plan-only 后再执行。";
    private final ToolRegistry registry;
    private final ToolExecutor executor;

    public StreamingToolExecutor(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
        this.executor = new ToolExecutor(registry);
    }

    public List<ToolResultBlock> execute(List<ToolCallBlock> calls, AgentMode mode,
                                         Consumer<AgentEvent> events) throws InterruptedException {
        if (calls.isEmpty()) return List.of();
        List<ToolResultBlock> ordered = new ArrayList<>(java.util.Collections.nCopies(calls.size(), null));
        try (ExecutorService reads = Executors.newVirtualThreadPerTaskExecutor();
             ExecutorService sideEffects = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())) {
            List<Future<IndexedResult>> futures = new ArrayList<>();
            for (int index = 0; index < calls.size(); index++) {
                int callIndex = index;
                ToolCallBlock call = calls.get(index);
                boolean read = registry.get(call.name()).map(tool -> tool.category() == ToolCategory.READ).orElse(false);
                ExecutorService target = read ? reads : sideEffects;
                futures.add(target.submit(() -> executeOne(callIndex, call, mode, read, events)));
            }
            try {
                for (Future<IndexedResult> future : futures) {
                    IndexedResult result = future.get();
                    ordered.set(result.index(), result.result());
                }
            } catch (InterruptedException error) {
                futures.forEach(future -> future.cancel(true));
                reads.shutdownNow();
                sideEffects.shutdownNow();
                throw error;
            } catch (ExecutionException error) {
                throw new IllegalStateException("Tool execution failed", error.getCause());
            }
        }
        return List.copyOf(ordered);
    }

    private IndexedResult executeOne(int index, ToolCallBlock call, AgentMode mode, boolean read,
                                     Consumer<AgentEvent> events) {
        long started = System.nanoTime();
        events.accept(new AgentEvent.ToolCallStarted(call.id(), call.name(), call.arguments()));
        ToolResultBlock result = mode == AgentMode.PLAN_ONLY && !read
                ? new ToolResultBlock(call.id(), PLAN_ONLY_ERROR, true)
                : executor.execute(call);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        events.accept(new AgentEvent.ToolResultCompleted(call.id(), call.name(), result.output(),
                result.isError(), elapsed));
        return new IndexedResult(index, result);
    }

    private record IndexedResult(int index, ToolResultBlock result) {}
}
