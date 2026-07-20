package com.yuancode.ui.markdown;

import com.yuancode.ui.Theme;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownStreamRendererTest {
    @Test
    void splitMarkdownAndCodeFenceNeverLoseOrDuplicateText() {
        StringWriter output = new StringWriter();
        MarkdownStreamRenderer renderer = new MarkdownStreamRenderer(new PrintWriter(output), Theme.plain());

        renderer.accept("**bo");
        renderer.accept("ld**\n``");
        renderer.accept("`java\nint x = 1;\n");
        renderer.accept("```\n");
        renderer.finish();

        assertEquals("**bold**\n```java\nint x = 1;\n```\n", output.toString());
    }
}
