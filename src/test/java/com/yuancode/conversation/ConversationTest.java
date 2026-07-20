package com.yuancode.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {
    @Test
    void keepsCompletedTurnsInOrderAndReturnsImmutableSnapshot() {
        Conversation conversation = new Conversation();
        conversation.addUser("first");
        conversation.addAssistant("answer", null);
        conversation.addUser("follow-up");

        var messages = conversation.messages();
        assertEquals(3, messages.size());
        assertEquals("first", messages.getFirst().text());
        assertThrows(UnsupportedOperationException.class,
                () -> messages.add(ConversationMessage.user("no")));

        conversation.clear();
        assertEquals(3, messages.size());
        assertTrue(conversation.messages().isEmpty());
    }
}
