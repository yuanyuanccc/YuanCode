package com.yuancode.tool.impl;

import com.yuancode.tool.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

public final class BashTool implements Tool {
    public static final int MAX_TIMEOUT_SECONDS = 600;
    private final WorkspacePaths paths;
    private final String bashExecutable;

    public BashTool(Path workspace) {
        this.paths = new WorkspacePaths(workspace);
        this.bashExecutable = BashExecutable.resolve().orElse(null);
    }
    public String name() { return "Bash"; }
    public String description() { return "Execute a command with bash -c in the workspace."; }
    public ToolCategory category() { return ToolCategory.COMMAND; }
    public Map<String, Object> schema() {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put("command", ToolSchemas.string("Bash command"));
        properties.put("timeout", Map.of("type", "integer", "default", 120, "maximum", 600));
        return ToolSchemas.object(properties, "command");
    }
    public ToolResult execute(Map<String, Object> arguments) {
        Object value = arguments.get("command");
        if (!(value instanceof String command) || command.isBlank()) return ToolResult.error("command is required");
        if (bashExecutable == null) {
            return ToolResult.error("Git Bash was not found. Install Git for Windows or set YUANCODE_BASH.");
        }
        int requested = arguments.get("timeout") instanceof Number n ? n.intValue() : 120;
        int timeout = Math.max(1, Math.min(MAX_TIMEOUT_SECONDS, requested));
        Process process = null;
        try (ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
            process = new ProcessBuilder(bashExecutable, "-c", command).directory(paths.root().toFile()).start();
            Process running = process;
            Future<String> stdout = readers.submit(() -> new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<String> stderr = readers.submit(() -> new String(running.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return ToolResult.error("$ " + command + "\nCommand timed out after " + timeout + " seconds");
            }
            int exitCode = process.exitValue();
            String output = format(command, stdout.get(), stderr.get(), exitCode);
            return exitCode == 0 ? ToolResult.success(output) : ToolResult.error(output);
        } catch (InterruptedException error) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            return ToolResult.error("Command interrupted");
        } catch (Exception error) {
            if (process != null) process.destroyForcibly();
            return ToolResult.error(error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }
    private static String format(String command, String stdout, String stderr, int exitCode) {
        StringBuilder result = new StringBuilder("$ ").append(command);
        if (!stdout.isEmpty()) result.append('\n').append(stdout.stripTrailing());
        if (!stderr.isEmpty()) result.append("\nSTDERR: ").append(stderr.stripTrailing());
        return result.append("\n(exit code ").append(exitCode).append(')').toString();
    }
}
