package com.yuancode.llm;

import com.yuancode.conversation.ConversationMessage;
import java.util.List;
import java.util.Map;

public record LlmRequest(List<ConversationMessage> messages, String systemPrompt,
                         List<Map<String, Object>> tools) {
    public LlmRequest(List<ConversationMessage> messages, String systemPrompt) {
        this(messages, systemPrompt, List.of());
    }
    public LlmRequest {
        messages = List.copyOf(messages);
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
