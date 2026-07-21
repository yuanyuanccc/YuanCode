package com.yuancode.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolSchemas {
    private ToolSchemas() {}

    public static Map<String, Object> object(Map<String, Object> properties, String... required) {
        LinkedHashMap<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(properties));
        if (required.length > 0) schema.put("required", List.of(required));
        return schema;
    }

    public static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }
}
