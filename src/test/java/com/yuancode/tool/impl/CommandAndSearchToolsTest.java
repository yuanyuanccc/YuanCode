package com.yuancode.tool.impl;

import com.yuancode.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandAndSearchToolsTest {
    @TempDir Path workspace;

    @Test
    void bashCapturesBothStreamsAndExitCode() {
        ToolResult result = new BashTool(workspace).execute(Map.of(
                "command", "printf out; printf err >&2; exit 7", "timeout", 10));

        assertTrue(result.isError());
        assertTrue(result.output().contains("$ printf out"));
        assertTrue(result.output().contains("out"));
        assertTrue(result.output().contains("STDERR: err"));
        assertTrue(result.output().contains("(exit code 7)"));
    }

    @Test
    void globAndGrepSkipGeneratedDirectoriesAndReturnStableLocations() throws Exception {
        Files.createDirectories(workspace.resolve("src"));
        Files.createDirectories(workspace.resolve("node_modules"));
        Files.writeString(workspace.resolve("src/Main.java"), "class Main { // needle\n}\n");
        Files.writeString(workspace.resolve("src/note.txt"), "needle\n");
        Files.writeString(workspace.resolve("node_modules/Hidden.java"), "needle\n");

        ToolResult glob = new GlobTool(workspace).execute(Map.of("pattern", "**/*.java"));
        assertEquals("src/Main.java", glob.output().replace('\\', '/'));

        ToolResult grep = new GrepTool(workspace).execute(Map.of("pattern", "needle", "include", "*.java"));
        assertEquals("src/Main.java:1:class Main { // needle", grep.output().replace('\\', '/'));
    }

}
