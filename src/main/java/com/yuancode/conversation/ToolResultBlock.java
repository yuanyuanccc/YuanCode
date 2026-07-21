package com.yuancode.conversation;

public record ToolResultBlock(String toolCallId, String output, boolean isError) {
    public ToolResultBlock {
        if (toolCallId == null || toolCallId.isBlank()) throw new IllegalArgumentException("tool call id is required");
        output = output == null ? "" : output;
    }
}
