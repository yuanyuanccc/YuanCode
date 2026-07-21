package com.yuancode.tool.impl;

import com.yuancode.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileToolsTest {
    @TempDir Path workspace;

    @Test
    void writeReadAndEditStayInsideWorkspace() throws Exception {
        WriteFileTool write = new WriteFileTool(workspace);
        ReadFileTool read = new ReadFileTool(workspace);
        EditFileTool edit = new EditFileTool(workspace);

        assertFalse(write.execute(Map.of("file_path", "nested/a.txt", "content", "one\ntwo\n")).isError());
        assertEquals("1\tone\n2\ttwo", read.execute(Map.of("file_path", "nested/a.txt", "limit", 2)).output());
        assertFalse(edit.execute(Map.of("file_path", "nested/a.txt", "old_string", "two", "new_string", "second")).isError());
        assertEquals("one\nsecond\n", Files.readString(workspace.resolve("nested/a.txt")));

        ToolResult escaped = read.execute(Map.of("file_path", "../outside.txt"));
        assertTrue(escaped.isError());
    }

    @Test
    void editRejectsMissingOrRepeatedTextWithoutChangingFile() throws Exception {
        Path file = workspace.resolve("a.txt");
        Files.writeString(file, "same same");
        EditFileTool edit = new EditFileTool(workspace);

        assertTrue(edit.execute(Map.of("file_path", "a.txt", "old_string", "missing", "new_string", "x")).isError());
        assertTrue(edit.execute(Map.of("file_path", "a.txt", "old_string", "same", "new_string", "x")).isError());
        assertEquals("same same", Files.readString(file));
    }
}
