package com.yuancode.tool;

import com.yuancode.tool.impl.AskUserTool;
import com.yuancode.tool.impl.ToolSearchTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolExtensionsTest {
    @Test
    void askUserQuestionIsAlwaysAvailableWithoutToolSearch() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AskUserTool(questions -> Map.of("choice", "yes")));
        ToolSearchTool search = new ToolSearchTool(registry, "anthropic");

        ToolResult result = search.execute(Map.of("query", "select:AskUserQuestion"));

        assertFalse(result.isError());
        assertEquals("[]", result.output());
        assertFalse(registry.isDiscovered("AskUserQuestion"));
        assertTrue(registry.getAllSchemas("anthropic").stream()
                .anyMatch(schema -> "AskUserQuestion".equals(schema.get("name"))));
    }

    @Test
    void askUserReturnsHandlerAnswersAndRejectsInvalidQuestionCount() {
        AskUserTool ask = new AskUserTool(questions -> Map.of("language", "Java"));
        Map<String, Object> question = Map.of("header", "Language", "question", "Choose",
                "options", List.of(Map.of("label", "Java"), Map.of("label", "Go")));

        ToolResult answer = ask.execute(Map.of("questions", List.of(question)));

        assertFalse(answer.isError());
        assertTrue(answer.output().contains("Java"));
        assertTrue(ask.execute(Map.of("questions", List.of())).isError());
    }
}
