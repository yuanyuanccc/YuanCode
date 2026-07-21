package com.yuancode.tool.impl;

import com.yuancode.tool.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EditFileTool implements Tool {
    private final WorkspacePaths paths;

    public EditFileTool(Path workspace) { this.paths = new WorkspacePaths(workspace); }
    public String name() { return "EditFile"; }
    public String description() { return "Replace text that occurs exactly once in an existing file."; }
    public ToolCategory category() { return ToolCategory.WRITE; }
    public Map<String, Object> schema() {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put("file_path", ToolSchemas.string("Path relative to the workspace"));
        properties.put("old_string", ToolSchemas.string("Exact text to replace"));
        properties.put("new_string", ToolSchemas.string("Replacement text"));
        return ToolSchemas.object(properties, "file_path", "old_string", "new_string");
    }
    public ToolResult execute(Map<String, Object> arguments) {
        try {
            Path file = paths.resolve(arguments.get("file_path"));
            if (!Files.isRegularFile(file)) return ToolResult.error("File does not exist: " + file);
            Object oldValue = arguments.get("old_string");
            Object newValue = arguments.get("new_string");
            if (!(oldValue instanceof String oldText) || !(newValue instanceof String newText) || oldText.isEmpty()) {
                return ToolResult.error("old_string and new_string are required");
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int count = countOccurrences(content, oldText);
            if (count == 0) return ToolResult.error("old_string was not found");
            if (count > 1) return ToolResult.error("old_string occurs more than once: " + count);
            Files.writeString(file, content.replace(oldText, newText), StandardCharsets.UTF_8);
            return ToolResult.success("Updated " + file);
        } catch (Exception error) {
            return ToolResult.error(error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }
    private static int countOccurrences(String content, String needle) {
        int count = 0;
        for (int index = 0; (index = content.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }
}
