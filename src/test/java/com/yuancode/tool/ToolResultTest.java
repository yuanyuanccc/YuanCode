package com.yuancode.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolResultTest {
    @Test
    void factoriesExposeSuccessfulAndFailedOutcomes() {
        assertEquals(new ToolResult("ok", false), ToolResult.success("ok"));
        assertEquals(new ToolResult("bad", true), ToolResult.error("bad"));
    }
}
