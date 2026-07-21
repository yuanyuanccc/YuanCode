package com.yuancode.tool.impl;

import com.yuancode.tool.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WriteFileTool implements Tool {
    private final WorkspacePaths paths;

    public WriteFileTool(Path workspace) { this.paths = new WorkspacePaths(workspace); }
    public String name() { return "WriteFile"; }
    public String description() { return "Write a UTF-8 text file, creating parent directories."; }
    public ToolCategory category() { return ToolCategory.WRITE; }
    public Map<String, Object> schema() {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put("file_path", ToolSchemas.string("Path relative to the workspace"));
        properties.put("content", ToolSchemas.string("Complete file content"));
        return ToolSchemas.object(properties, "file_path", "content");
    }
    public ToolResult execute(Map<String, Object> arguments) {
        try {
            Path file = paths.resolve(arguments.get("file_path"));
            Object content = arguments.get("content");
            if (!(content instanceof String text)) return ToolResult.error("content is required");
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                setPermissions(parent, "rwxr-xr-x");
            }
            Files.writeString(file, text, StandardCharsets.UTF_8);
            setPermissions(file, "rw-r--r--");
            return ToolResult.success("Wrote " + file);
        } catch (Exception error) {
            return ToolResult.error(error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }
    private static void setPermissions(Path path, String permissions) {
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions)); }
        catch (UnsupportedOperationException | java.io.IOException ignored) { }
    }
}
