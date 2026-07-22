package com.yuancode.e2e;

import com.yuancode.agent.*;
import com.yuancode.conversation.Conversation;
import com.yuancode.llm.*;
import com.yuancode.tool.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlanModeE2eTest {
    @Test
    void blocksWriteThenLetsModelReturnAnApprovalPlan() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry().register(new Tool() {
            public String name() { return "Write"; }
            public String description() { return "write"; }
            public ToolCategory category() { return ToolCategory.WRITE; }
            public Map<String, Object> schema() { return Map.of("type", "object"); }
            public ToolResult execute(Map<String, Object> arguments) {
                writes.incrementAndGet();
                return ToolResult.success("written");
            }
        });
        AtomicInteger requests = new AtomicInteger();
        LlmClient provider = request -> {
            if (requests.getAndIncrement() == 0) {
                assertTrue(request.systemPrompt().contains("只使用读取工具"));
                return stream(new StreamEvent.ToolCallComplete(0, "w1", "Write", Map.of("text", "x")),
                        new StreamEvent.Completed("tool_use", TokenUsage.EMPTY));
            }
            assertTrue(request.messages().getLast().toolResults().getFirst().isError());
            assertTrue(request.messages().getLast().toolResults().getFirst().output().contains("/plan off"));
            return stream(new StreamEvent.TextDelta("待审批计划"),
                    new StreamEvent.Completed("end_turn", TokenUsage.EMPTY));
        };
        AgentLoop loop = new AgentLoop(provider, new Conversation(), registry,
                "anthropic", "system", Duration.ofSeconds(1));
        loop.setMode(AgentMode.PLAN_ONLY);

        List<AgentEvent> events = loop.start("modify").drainUntilComplete(Duration.ofSeconds(3));

        assertEquals(0, writes.get());
        assertTrue(events.stream().anyMatch(event -> event instanceof AgentEvent.ToolResultCompleted result
                && result.isError()));
        assertTrue(events.stream().anyMatch(event -> event instanceof AgentEvent.FinalReply reply
                && reply.text().equals("待审批计划")));
    }

    private static StreamHandle stream(StreamEvent... events) {
        LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
        queue.addAll(List.of(events));
        return new StreamHandle(queue, () -> {});
    }
}
