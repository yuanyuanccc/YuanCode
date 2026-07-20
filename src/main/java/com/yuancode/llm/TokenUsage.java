package com.yuancode.llm;

public record TokenUsage(long inputTokens, long outputTokens) {
    public static final TokenUsage EMPTY = new TokenUsage(0, 0);
}
