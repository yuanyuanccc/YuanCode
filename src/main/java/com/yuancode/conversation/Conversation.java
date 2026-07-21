package com.yuancode.conversation;

import java.util.ArrayList;
import java.util.List;

public final class Conversation {
    private final List<ConversationMessage> history = new ArrayList<>();

    public void addUser(String text) { history.add(ConversationMessage.user(text)); }
    public void addAssistant(String text, List<ThinkingBlock> thinking) {
        history.add(ConversationMessage.assistant(text, thinking));
    }
    public void addAssistantTools(String text, List<ThinkingBlock> thinking, List<ToolCallBlock> calls) {
        history.add(ConversationMessage.assistantTools(text, thinking, calls));
    }
    public void addToolResults(List<ToolResultBlock> results) {
        history.add(ConversationMessage.toolResults(results));
    }
    public List<ConversationMessage> messages() { return List.copyOf(history); }
    public long completedTurns() {
        return history.stream().filter(message -> message.role() == ConversationMessage.Role.ASSISTANT).count();
    }
    public void clear() { history.clear(); }
}
