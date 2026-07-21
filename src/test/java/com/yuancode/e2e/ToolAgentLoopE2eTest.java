package com.yuancode.e2e;

import com.yuancode.agent.AgentLoop;
import com.yuancode.conversation.Conversation;
import com.yuancode.llm.*;
import com.yuancode.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolAgentLoopE2eTest {
    @TempDir Path workspace;

    @Test
    void modelReadsAFileThenAnswersInTheSameUserTurn() throws Exception {
        Files.writeString(workspace.resolve("note.txt"), "YuanCode tool result");
        Conversation conversation = new Conversation();
        ToolRegistry registry = ToolRegistry.createDefault(workspace);
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = request -> {
            int call = calls.getAndIncrement();
            if (call == 0) {
                assertFalse(request.tools().isEmpty());
                return stream(
                        new StreamEvent.ToolCallStart(0, "call-1", "ReadFile"),
                        new StreamEvent.ToolCallComplete(0, "call-1", "ReadFile", Map.of("file_path", "note.txt")),
                        new StreamEvent.Completed("tool_use", new TokenUsage(10, 3)));
            }
            assertTrue(request.messages().getLast().toolResults().getFirst().output().contains("YuanCode tool result"));
            return stream(new StreamEvent.TextDelta("I read it."),
                    new StreamEvent.Completed("end_turn", new TokenUsage(20, 4)));
        };
        StringBuilder rendered = new StringBuilder();
        AgentLoop loop = new AgentLoop(client, conversation, registry, "anthropic", "system", Duration.ofSeconds(1));

        loop.run("read note.txt", new AgentLoop.Listener() {
            @Override public void text(String delta) { rendered.append(delta); }
        });

        assertEquals("I read it.", rendered.toString());
        assertEquals(2, calls.get());
        assertEquals(4, conversation.messages().size());
        assertEquals("ReadFile", conversation.messages().get(1).toolCalls().getFirst().name());
        assertEquals("I read it.", conversation.messages().getLast().text());
    }

    private static StreamHandle stream(StreamEvent... events) {
        LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
        queue.addAll(List.of(events));
        return new StreamHandle(queue, () -> {});
    }
}
