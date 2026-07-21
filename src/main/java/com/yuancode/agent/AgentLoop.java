package com.yuancode.agent;

import com.yuancode.conversation.*;
import com.yuancode.llm.*;
import com.yuancode.tool.ToolRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AgentLoop {
    public static final int MAX_ITERATIONS = 20;
    private final LlmClient client;
    private final Conversation conversation;
    private final ToolRegistry registry;
    private final ToolExecutor executor;
    private final String protocol;
    private final String systemPrompt;
    private final Duration streamTimeout;

    public AgentLoop(LlmClient client, Conversation conversation, ToolRegistry registry,
                     String protocol, String systemPrompt, Duration streamTimeout) {
        this.client = Objects.requireNonNull(client);
        this.conversation = Objects.requireNonNull(conversation);
        this.registry = Objects.requireNonNull(registry);
        this.executor = new ToolExecutor(registry);
        this.protocol = protocol;
        this.systemPrompt = systemPrompt;
        this.streamTimeout = streamTimeout;
    }

    public void run(String userPrompt, Listener listener) throws InterruptedException {
        conversation.addUser(userPrompt);
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            StringBuilder text = new StringBuilder();
            StringBuilder thinking = new StringBuilder();
            StringBuilder signature = new StringBuilder();
            List<ToolCallBlock> calls = new ArrayList<>();
            TokenUsage usage = new TokenUsage(0, 0);
            boolean completed = false;
            try (StreamHandle stream = client.stream(new LlmRequest(conversation.messages(), effectiveSystemPrompt(),
                    registry.getAllSchemas(protocol)))) {
                listener.streamOpened(stream);
                while (!completed) {
                    StreamEvent event = stream.next(streamTimeout);
                    switch (event) {
                        case StreamEvent.TextDelta delta -> { text.append(delta.text()); listener.text(delta.text()); }
                        case StreamEvent.ThinkingDelta delta -> { thinking.append(delta.text()); listener.thinking(delta.text()); }
                        case StreamEvent.ThinkingSignature delta -> signature.append(delta.signature());
                        case StreamEvent.ToolCallStart start -> listener.toolCall(start.name());
                        case StreamEvent.ToolCallDelta ignored -> { }
                        case StreamEvent.ToolCallComplete call -> calls.add(new ToolCallBlock(
                                call.id(), call.name(), call.arguments()));
                        case StreamEvent.Completed done -> { usage = done.usage(); completed = true; }
                        case StreamEvent.Failed failed -> throw failed.error();
                    }
                }
            } finally {
                listener.streamClosed();
            }
            List<ThinkingBlock> thinkingBlocks = signature.isEmpty() && thinking.isEmpty() ? List.of()
                    : List.of(new ThinkingBlock(thinking.toString(), signature.toString()));
            if (calls.isEmpty()) {
                conversation.addAssistant(text.toString(), thinkingBlocks);
                listener.completed(usage);
                return;
            }
            conversation.addAssistantTools(text.toString(), thinkingBlocks, calls);
            List<ToolResultBlock> results = new ArrayList<>();
            for (ToolCallBlock call : calls) {
                ToolResultBlock result = executor.execute(call);
                results.add(result);
                listener.toolResult(call.name(), result);
            }
            conversation.addToolResults(results);
        }
        throw new LlmException("Agent exceeded " + MAX_ITERATIONS + " model iterations");
    }

    private String effectiveSystemPrompt() {
        List<String> deferred = registry.getDeferredToolNames();
        if (deferred.isEmpty()) return systemPrompt;
        return systemPrompt + "\n\nDeferred tools available: " + String.join(", ", deferred)
                + ". Use ToolSearch to enable one before calling it.";
    }

    public interface Listener {
        default void streamOpened(StreamHandle stream) {}
        default void streamClosed() {}
        default void text(String delta) {}
        default void thinking(String delta) {}
        default void toolCall(String name) {}
        default void toolResult(String name, ToolResultBlock result) {}
        default void completed(TokenUsage usage) {}
    }
}
