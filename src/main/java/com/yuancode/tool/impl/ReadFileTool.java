package com.yuancode.tool.impl;

import com.yuancode.tool.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReadFileTool implements Tool {
    private final WorkspacePaths paths;

    public ReadFileTool(Path workspace) { this.paths = new WorkspacePaths(workspace); }
    public String name() { return "ReadFile"; }
    public String description() { return "Read a UTF-8 text file with line numbers."; }
    public ToolCategory category() { return ToolCategory.READ; }
    public Map<String, Object> schema() {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put("file_path", ToolSchemas.string("Path relative to the workspace"));
        properties.put("offset", Map.of("type", "integer", "default", 0));
        properties.put("limit", Map.of("type", "integer", "default", 2000));
        return ToolSchemas.object(properties, "file_path");
    }
    public ToolResult execute(Map<String, Object> arguments) {
        try {
            Path file = paths.resolve(arguments.get("file_path"));
            if (!Files.exists(file)) return ToolResult.error("File does not exist: " + file);
            if (!Files.isRegularFile(file)) return ToolResult.error("Path is not a file: " + file);
            int offset = nonNegative(arguments.get("offset"), 0);
            int limit = nonNegative(arguments.get("limit"), 2000);
            String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\\R", -1);
            int end = Math.min(lines.length, offset + limit);
            StringBuilder output = new StringBuilder();
            for (int index = offset; index < end; index++) {
                if (index == lines.length - 1 && lines[index].isEmpty()) break;
                if (!output.isEmpty()) output.append('\n');
                output.append(index + 1).append('\t').append(lines[index]);
            }
            return ToolResult.success(output.toString());
        } catch (Exception error) {
            return ToolResult.error(message(error));
        }
    }
    private static int nonNegative(Object value, int fallback) {
        int number = value instanceof Number n ? n.intValue() : fallback;
        return Math.max(0, number);
    }
    private static String message(Exception error) { return error.getMessage() == null ? error.toString() : error.getMessage(); }
}
