package com.yuancode.tool;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    @Test
    void preservesOrderAndTranslatesSchemas() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool("First", false));
        registry.register(tool("Second", false));

        assertEquals(List.of("First", "Second"), registry.listTools().stream().map(Tool::name).toList());
        assertEquals(List.of("First", "Second"), registry.getAllSchemas("anthropic").stream()
                .map(schema -> schema.get("name")).toList());
        Map<String, Object> openAi = registry.getAllSchemas("openai").getFirst();
        assertEquals("function", openAi.get("type"));
        assertEquals("First", openAi.get("name"));
        assertEquals(Map.of("type", "object"), openAi.get("parameters"));
    }

    @Test
    void deferredToolAppearsOnlyAfterDiscovery() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool("AskUser", true));

        assertTrue(registry.getAllSchemas("anthropic").isEmpty());
        assertEquals(List.of("AskUser"), registry.getDeferredToolNames());
        assertEquals(1, registry.searchDeferred("ask", 5, "anthropic").size());

        assertTrue(registry.markDiscovered("AskUser"));
        assertEquals("AskUser", registry.getAllSchemas("anthropic").getFirst().get("name"));
        assertTrue(registry.getDeferredToolNames().isEmpty());
    }

    private static Tool tool(String name, boolean deferred) {
        return new Tool() {
            public String name() { return name; }
            public String description() { return name + " description"; }
            public ToolCategory category() { return ToolCategory.READ; }
            public Map<String, Object> schema() { return new LinkedHashMap<>(Map.of("type", "object")); }
            public ToolResult execute(Map<String, Object> arguments) { return ToolResult.success(""); }
            public boolean shouldDefer() { return deferred; }
        };
    }
}
