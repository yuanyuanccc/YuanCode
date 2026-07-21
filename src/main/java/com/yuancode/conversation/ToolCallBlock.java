package com.yuancode.conversation;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolCallBlock(String id, String name, Map<String, Object> arguments) {
    public ToolCallBlock {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("tool call id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name is required");
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
    }
}
