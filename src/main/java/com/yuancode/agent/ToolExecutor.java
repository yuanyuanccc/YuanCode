package com.yuancode.agent;

import com.yuancode.conversation.ToolCallBlock;
import com.yuancode.conversation.ToolResultBlock;
import com.yuancode.tool.*;

import java.util.Objects;

public final class ToolExecutor {
    private static final String TRUNCATED = "... (truncated)";
    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) { this.registry = Objects.requireNonNull(registry); }

    public ToolResultBlock execute(ToolCallBlock call) {
        ToolResult result = registry.get(call.name())
                .map(tool -> safeExecute(tool, call))
                .orElseGet(() -> ToolResult.error("Unknown tool: " + call.name()));
        String output = result.output();
        if (output.length() > ToolRegistry.MAX_OUTPUT_CHARS) {
            output = output.substring(0, ToolRegistry.MAX_OUTPUT_CHARS) + TRUNCATED;
        }
        return new ToolResultBlock(call.id(), output, result.isError());
    }

    private static ToolResult safeExecute(Tool tool, ToolCallBlock call) {
        try { return tool.execute(call.arguments()); }
        catch (RuntimeException error) {
            return ToolResult.error(error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }
}
