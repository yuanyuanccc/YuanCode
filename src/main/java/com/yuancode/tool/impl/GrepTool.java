package com.yuancode.tool.impl;

import com.yuancode.tool.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class GrepTool implements Tool {
    private final WorkspacePaths paths;
    public GrepTool(Path workspace) { paths = new WorkspacePaths(workspace); }
    public String name() { return "Grep"; }
    public String description() { return "Search UTF-8 workspace files with a regular expression."; }
    public ToolCategory category() { return ToolCategory.READ; }
    public Map<String, Object> schema() {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put("pattern", ToolSchemas.string("Regular expression"));
        properties.put("include", ToolSchemas.string("Optional filename glob"));
        return ToolSchemas.object(properties, "pattern");
    }
    public ToolResult execute(Map<String, Object> arguments) {
        Object value = arguments.get("pattern");
        if (!(value instanceof String regex)) return ToolResult.error("pattern is required");
        final Pattern pattern;
        try { pattern = Pattern.compile(regex); }
        catch (PatternSyntaxException error) { return ToolResult.error("Invalid regular expression: " + error.getDescription()); }
        String include = arguments.get("include") instanceof String text && !text.isBlank() ? text : null;
        PathMatcher includeMatcher = include == null ? null : FileSystems.getDefault().getPathMatcher("glob:" + include);
        List<String> matches = new ArrayList<>();
        try {
            Files.walkFileTree(paths.root(), new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(paths.root()) && FileScanPolicy.SKIP_DIRS.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (includeMatcher != null && !includeMatcher.matches(file.getFileName())) return FileVisitResult.CONTINUE;
                    if (isBinary(file)) return FileVisitResult.CONTINUE;
                    List<String> lines;
                    try { lines = Files.readAllLines(file, StandardCharsets.UTF_8); }
                    catch (Exception ignored) { return FileVisitResult.CONTINUE; }
                    String relative = paths.root().relativize(file).toString();
                    for (int index = 0; index < lines.size(); index++) {
                        if (pattern.matcher(lines.get(index)).find()) matches.add(relative + ":" + (index + 1) + ":" + lines.get(index));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            Collections.sort(matches);
            String output = String.join("\n", matches);
            if (output.length() > ToolRegistry.MAX_OUTPUT_CHARS) {
                output = output.substring(0, ToolRegistry.MAX_OUTPUT_CHARS) + "... output truncated";
            }
            return ToolResult.success(output);
        } catch (Exception error) {
            return ToolResult.error(error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }
    private static boolean isBinary(Path file) throws IOException {
        byte[] bytes;
        try (var stream = Files.newInputStream(file)) { bytes = stream.readNBytes(512); }
        for (byte value : bytes) if (value == 0) return true;
        return false;
    }
}
