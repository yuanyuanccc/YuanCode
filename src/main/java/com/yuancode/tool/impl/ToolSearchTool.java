package com.yuancode.tool.impl;

import com.yuancode.llm.JsonSupport;
import com.yuancode.tool.*;

import java.util.*;

public final class ToolSearchTool implements Tool {
    private final ToolRegistry registry;
    private final String protocol;

    public ToolSearchTool(ToolRegistry registry, String protocol) {
        this.registry = Objects.requireNonNull(registry);
        this.protocol = protocol;
    }
    public String name() { return "ToolSearch"; }
    public String description() { return "Search and enable deferred tools."; }
    public ToolCategory category() { return ToolCategory.READ; }
    public boolean shouldDefer() { return false; }
    public Map<String, Object> schema() {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", ToolSchemas.string("Search text or select:Name1,Name2"));
        properties.put("max_results", Map.of("type", "integer", "default", 5, "minimum", 1, "maximum", 20));
        return ToolSchemas.object(properties, "query");
    }
    public ToolResult execute(Map<String, Object> arguments) {
        Object value = arguments.get("query");
        if (!(value instanceof String query) || query.isBlank()) return ToolResult.error("query is required");
        int max = arguments.get("max_results") instanceof Number n ? n.intValue() : 5;
        max = Math.max(1, Math.min(20, max));
        List<Map<String, Object>> matches;
        if (query.regionMatches(true, 0, "select:", 0, 7)) {
            List<String> names = Arrays.stream(query.substring(7).split(","))
                    .map(String::trim).filter(text -> !text.isEmpty()).toList();
            matches = registry.findDeferredByNames(names, protocol);
        } else {
            matches = registry.searchDeferred(query, max, protocol);
        }
        for (Map<String, Object> schema : matches) registry.markDiscovered(String.valueOf(schema.get("name")));
        return ToolResult.success(JsonSupport.write(matches));
    }
}
