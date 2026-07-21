package com.yuancode.tool.impl;

import com.yuancode.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public final class GlobTool implements Tool {
    private final WorkspacePaths paths;
    public GlobTool(Path workspace) { paths = new WorkspacePaths(workspace); }
    public String name() { return "Glob"; }
    public String description() { return "Find workspace files matching a glob pattern."; }
    public ToolCategory category() { return ToolCategory.READ; }
    public Map<String, Object> schema() { return ToolSchemas.object(Map.of("pattern", ToolSchemas.string("Glob pattern")), "pattern"); }
    public ToolResult execute(Map<String, Object> arguments) {
        Object value = arguments.get("pattern");
        if (!(value instanceof String pattern) || pattern.isBlank()) return ToolResult.error("pattern is required");
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<String> matches = new ArrayList<>();
            Files.walkFileTree(paths.root(), new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(paths.root()) && FileScanPolicy.SKIP_DIRS.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = paths.root().relativize(file);
                    if (matcher.matches(relative) || matcher.matches(file.getFileName())) matches.add(relative.toString());
                    return FileVisitResult.CONTINUE;
                }
            });
            Collections.sort(matches);
            return ToolResult.success(String.join("\n", matches));
        } catch (Exception error) {
            return ToolResult.error(error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }
}
