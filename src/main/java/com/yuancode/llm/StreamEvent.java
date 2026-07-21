package com.yuancode.llm;

public sealed interface StreamEvent permits StreamEvent.TextDelta, StreamEvent.ThinkingDelta,
        StreamEvent.ThinkingSignature, StreamEvent.ToolCallStart, StreamEvent.ToolCallDelta,
        StreamEvent.ToolCallComplete, StreamEvent.Completed, StreamEvent.Failed {
    record TextDelta(String text) implements StreamEvent {}
    record ThinkingDelta(String text) implements StreamEvent {}
    record ThinkingSignature(String signature) implements StreamEvent {}
    record ToolCallStart(int index, String id, String name) implements StreamEvent {}
    record ToolCallDelta(int index, String argumentsDelta) implements StreamEvent {}
    record ToolCallComplete(int index, String id, String name,
                            java.util.Map<String, Object> arguments) implements StreamEvent {}
    record Completed(String stopReason, TokenUsage usage) implements StreamEvent {}
    record Failed(LlmException error) implements StreamEvent {}
}
