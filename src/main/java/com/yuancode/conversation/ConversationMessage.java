package com.yuancode.conversation;

import java.util.List;

public record ConversationMessage(Role role, String text, List<ThinkingBlock> thinkingBlocks) {
    public ConversationMessage {
        if (role == null) throw new IllegalArgumentException("role is required");
        text = text == null ? "" : text;
        thinkingBlocks = thinkingBlocks == null ? List.of() : List.copyOf(thinkingBlocks);
    }

    public static ConversationMessage user(String text) {
        return new ConversationMessage(Role.USER, text, List.of());
    }

    public static ConversationMessage assistant(String text, List<ThinkingBlock> thinking) {
        return new ConversationMessage(Role.ASSISTANT, text, thinking);
    }

    public enum Role { USER, ASSISTANT }
}
