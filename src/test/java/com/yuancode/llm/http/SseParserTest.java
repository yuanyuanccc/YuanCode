package com.yuancode.llm.http;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SseParserTest {
    @Test
    void parsesNamedEventsMultilineDataAndFinalFrame() throws Exception {
        var events = new ArrayList<SseEvent>();
        new SseParser().parse(new StringReader("""
                event: ping
                data: one
                data: two

                data: {"type":"done"}
                """), events::add, () -> false);

        assertEquals(2, events.size());
        assertEquals("ping", events.getFirst().event());
        assertEquals("one\ntwo", events.getFirst().data());
        assertEquals("message", events.getLast().event());
    }
}
