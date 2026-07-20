package com.yuancode.llm;

import com.yuancode.conversation.ConversationMessage;
import java.util.List;

public record LlmRequest(List<ConversationMessage> messages, String systemPrompt) {
    public LlmRequest {
        messages = List.copyOf(messages);
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }
}
