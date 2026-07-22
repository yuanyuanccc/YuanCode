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
    private static final String PLAN_REMINDER = "Plan-only 模式：只分析，只使用读取工具，输出待用户审批的计划。";
    private final LlmClient client;
    private final Conversation conversation;
    private final ToolRegistry registry;
    private final StreamingToolExecutor executor;
    private final String protocol;
    private final String systemPrompt;
    private final Duration streamTimeout;
    private volatile AgentMode mode = AgentMode.NORMAL;

    public AgentLoop(LlmClient client, Conversation conversation, ToolRegistry registry,
                     String protocol, String systemPrompt, Duration streamTimeout) {
        this.client = Objects.requireNonNull(client);
        this.conversation = Objects.requireNonNull(conversation);
        this.registry = Objects.requireNonNull(registry);
        this.executor = new StreamingToolExecutor(registry);
        this.protocol = Objects.requireNonNull(protocol);
        this.systemPrompt = Objects.requireNonNull(systemPrompt);
        this.streamTimeout = Objects.requireNonNull(streamTimeout);
    }

    public AgentMode mode() { return mode; }

    public void setMode(AgentMode mode) { this.mode = Objects.requireNonNull(mode); }

    public AgentRun start(String userPrompt) {
        Objects.requireNonNull(userPrompt);
        AgentRun run = new AgentRun();
        run.publish(new AgentEvent.UserMessage(userPrompt));
        run.publish(new AgentEvent.PlanModeChanged(mode));
        Thread worker = Thread.startVirtualThread(() -> executeRun(userPrompt, run));
        run.attachWorker(worker);
        return run;
    }

    private void executeRun(String userPrompt, AgentRun run) {
        int checkpoint = conversation.checkpoint();
        int iterations = 0;
        try {
            conversation.addUser(userPrompt);
            for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
                iterations = iteration;
                ModelTurn turn = readModelTurn(run);
                run.publish(new AgentEvent.UsageUpdated(turn.usage()));
                run.publish(new AgentEvent.TurnCompleted(iteration));
                List<ThinkingBlock> thinking = turn.thinkingBlock();
                if (turn.calls().isEmpty()) {
                    conversation.addAssistant(turn.text(), thinking);
                    run.publish(new AgentEvent.FinalReply(turn.text(), turn.usage()));
                    run.complete(AgentTermination.COMPLETED, iteration);
                    return;
                }

                List<ToolResultBlock> results = executor.execute(turn.calls(), mode, run::publish);
                if (userDeclinedClarification(turn.calls(), results)) {
                    conversation.rollbackTo(checkpoint);
                    run.complete(AgentTermination.CANCELLED, iteration);
                    return;
                }
                conversation.addAssistantTools(turn.text(), thinking, turn.calls());
                conversation.addToolResults(results);
            }
            run.complete(AgentTermination.MAX_ITERATIONS, iterations);
        } catch (InterruptedException error) {
            conversation.rollbackTo(checkpoint);
            Thread.currentThread().interrupt();
            run.complete(AgentTermination.CANCELLED, iterations);
        } catch (RuntimeException error) {
            conversation.rollbackTo(checkpoint);
            boolean timeout = error instanceof LlmException.StreamTimeout;
            run.publish(new AgentEvent.Error(timeout ? "Stream timeout" : safeMessage(error)));
            run.complete(timeout ? AgentTermination.TIMED_OUT : AgentTermination.ERROR, iterations);
        }
    }

    private static boolean userDeclinedClarification(List<ToolCallBlock> calls,
                                                      List<ToolResultBlock> results) {
        for (int index = 0; index < calls.size(); index++) {
            if (calls.get(index).name().equals("AskUserQuestion")
                    && results.get(index).isError()
                    && results.get(index).output().equals("User declined the question")) return true;
        }
        return false;
    }

    private ModelTurn readModelTurn(AgentRun run) throws InterruptedException {
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        StringBuilder signature = new StringBuilder();
        List<ToolCallBlock> calls = new ArrayList<>();
        TokenUsage usage = TokenUsage.EMPTY;
        try (StreamHandle stream = client.stream(new LlmRequest(conversation.messages(), effectiveSystemPrompt(),
                registry.getAllSchemas(protocol)))) {
            run.attachStream(stream);
            while (true) {
                switch (stream.next(streamTimeout)) {
                    case StreamEvent.TextDelta delta -> {
                        text.append(delta.text());
                        run.publish(new AgentEvent.TextDelta(delta.text()));
                    }
                    case StreamEvent.ThinkingDelta delta -> {
                        thinking.append(delta.text());
                        run.publish(new AgentEvent.ThinkingDelta(delta.text()));
                    }
                    case StreamEvent.ThinkingSignature delta -> signature.append(delta.signature());
                    case StreamEvent.ToolCallStart ignored -> { }
                    case StreamEvent.ToolCallDelta ignored -> { }
                    case StreamEvent.ToolCallComplete call -> calls.add(new ToolCallBlock(
                            call.id(), call.name(), call.arguments()));
                    case StreamEvent.Completed completed -> {
                        usage = completed.usage();
                        return new ModelTurn(text.toString(), thinking.toString(), signature.toString(),
                                List.copyOf(calls), usage);
                    }
                    case StreamEvent.Failed failed -> throw failed.error();
                }
            }
        } finally {
            run.detachStream();
        }
    }

    private String effectiveSystemPrompt() {
        StringBuilder prompt = new StringBuilder(systemPrompt);
        if (mode == AgentMode.PLAN_ONLY) prompt.append("\n\n").append(PLAN_REMINDER);
        List<String> deferred = registry.getDeferredToolNames();
        if (!deferred.isEmpty()) prompt.append("\n\nDeferred tools available: ")
                .append(String.join(", ", deferred))
                .append(". Use ToolSearch to enable one before calling it.");
        return prompt.toString();
    }

    /** Compatibility bridge for callers migrating from the ch03 listener API. */
    public void run(String userPrompt, Listener listener) throws InterruptedException {
        AgentRun run = start(userPrompt);
        while (true) {
            AgentEvent event = run.next(streamTimeout.plusSeconds(1));
            if (event == null) throw new LlmException.StreamTimeout();
            switch (event) {
                case AgentEvent.TextDelta delta -> listener.text(delta.text());
                case AgentEvent.ThinkingDelta delta -> listener.thinking(delta.text());
                case AgentEvent.ToolCallStarted started -> listener.toolCall(started.toolName());
                case AgentEvent.ToolResultCompleted result -> listener.toolResult(result.toolName(),
                        new ToolResultBlock(result.callId(), result.output(), result.isError()));
                case AgentEvent.UsageUpdated updated -> listener.completed(updated.usage());
                case AgentEvent.Error error -> throw new LlmException(error.message());
                case AgentEvent.LoopCompleted completed -> {
                    if (completed.reason() == AgentTermination.COMPLETED) return;
                    if (completed.reason() == AgentTermination.CANCELLED) throw new InterruptedException("cancelled");
                    throw new LlmException("Agent stopped: " + completed.reason());
                }
                default -> { }
            }
        }
    }

    private static String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private record ModelTurn(String text, String thinking, String signature,
                             List<ToolCallBlock> calls, TokenUsage usage) {
        List<ThinkingBlock> thinkingBlock() {
            return thinking.isEmpty() && signature.isEmpty() ? List.of()
                    : List.of(new ThinkingBlock(thinking, signature));
        }
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
