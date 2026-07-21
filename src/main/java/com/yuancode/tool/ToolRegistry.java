package com.yuancode.tool;

import java.util.*;
import java.nio.file.Path;
import com.yuancode.tool.impl.*;

public final class ToolRegistry {
    public static final int MAX_OUTPUT_CHARS = 10_000;

    private final LinkedHashMap<String, Tool> tools = new LinkedHashMap<>();
    private final Set<String> discoveredTools = new HashSet<>();

    public ToolRegistry register(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("Tool already registered: " + tool.name());
        }
        return this;
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<Tool> listTools() {
        return List.copyOf(tools.values());
    }

    public boolean markDiscovered(String name) {
        Tool tool = tools.get(name);
        return tool != null && tool.shouldDefer() && discoveredTools.add(name);
    }

    public boolean isDiscovered(String name) {
        return discoveredTools.contains(name);
    }

    public List<String> getDeferredToolNames() {
        return tools.values().stream()
                .filter(Tool::shouldDefer)
                .filter(tool -> !isDiscovered(tool.name()))
                .map(Tool::name)
                .toList();
    }

    public List<Map<String, Object>> getAllSchemas(String protocol) {
        return tools.values().stream()
                .filter(tool -> !tool.shouldDefer() || isDiscovered(tool.name()))
                .map(tool -> schemaFor(tool, protocol))
                .toList();
    }

    public List<Map<String, Object>> searchDeferred(String query, int maxResults, String protocol) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        int limit = Math.max(1, Math.min(20, maxResults));
        return tools.values().stream()
                .filter(Tool::shouldDefer)
                .filter(tool -> !isDiscovered(tool.name()))
                .filter(tool -> tool.name().toLowerCase(Locale.ROOT).contains(needle)
                        || tool.description().toLowerCase(Locale.ROOT).contains(needle))
                .limit(limit)
                .map(tool -> schemaFor(tool, protocol))
                .toList();
    }

    public List<Map<String, Object>> findDeferredByNames(List<String> names, String protocol) {
        if (names == null) return List.of();
        return names.stream().map(tools::get).filter(Objects::nonNull)
                .filter(Tool::shouldDefer).filter(tool -> !isDiscovered(tool.name()))
                .map(tool -> schemaFor(tool, protocol)).toList();
    }

    public static ToolRegistry createDefault(Path workspace) {
        return new ToolRegistry()
                .register(new ReadFileTool(workspace))
                .register(new WriteFileTool(workspace))
                .register(new EditFileTool(workspace))
                .register(new BashTool(workspace))
                .register(new GlobTool(workspace))
                .register(new GrepTool(workspace));
    }

    private static Map<String, Object> schemaFor(Tool tool, String protocol) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if ("openai".equalsIgnoreCase(protocol)) result.put("type", "function");
        result.put("name", tool.name());
        result.put("description", tool.description());
        result.put("openai".equalsIgnoreCase(protocol) ? "parameters" : "input_schema", tool.schema());
        return Collections.unmodifiableMap(result);
    }
}
