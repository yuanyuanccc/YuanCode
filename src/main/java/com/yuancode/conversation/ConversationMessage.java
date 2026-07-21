package com.yuancode.conversation;

import java.util.List;

public record ConversationMessage(Role role, String text, List<ThinkingBlock> thinkingBlocks,
                                  List<ToolCallBlock> toolCalls, List<ToolResultBlock> toolResults) {
    public ConversationMessage {
        if (role == null) throw new IllegalArgumentException("role is required");
        text = text == null ? "" : text;
        thinkingBlocks = thinkingBlocks == null ? List.of() : List.copyOf(thinkingBlocks);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }

    public static ConversationMessage user(String text) {
        return new ConversationMessage(Role.USER, text, List.of(), List.of(), List.of());
    }

    public static ConversationMessage assistant(String text, List<ThinkingBlock> thinking) {
        return new ConversationMessage(Role.ASSISTANT, text, thinking, List.of(), List.of());
    }

    public static ConversationMessage assistantTools(String text, List<ThinkingBlock> thinking,
                                                     List<ToolCallBlock> calls) {
        return new ConversationMessage(Role.ASSISTANT, text, thinking, calls, List.of());
    }

    public static ConversationMessage toolResults(List<ToolResultBlock> results) {
        return new ConversationMessage(Role.USER, "", List.of(), List.of(), results);
    }

    public enum Role { USER, ASSISTANT }
}
