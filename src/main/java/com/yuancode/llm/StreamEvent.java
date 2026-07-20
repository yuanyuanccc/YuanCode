package com.yuancode.llm;

public sealed interface StreamEvent permits StreamEvent.TextDelta, StreamEvent.ThinkingDelta,
        StreamEvent.ThinkingSignature, StreamEvent.Completed, StreamEvent.Failed {
    record TextDelta(String text) implements StreamEvent {}
    record ThinkingDelta(String text) implements StreamEvent {}
    record ThinkingSignature(String signature) implements StreamEvent {}
    record Completed(String stopReason, TokenUsage usage) implements StreamEvent {}
    record Failed(LlmException error) implements StreamEvent {}
}
