package com.yuancode.llm.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SseParser {
    public void parse(Reader input, Consumer<SseEvent> consumer, BooleanSupplier cancelled) throws IOException {
        BufferedReader reader = input instanceof BufferedReader buffered ? buffered : new BufferedReader(input);
        String event = "message";
        StringBuilder data = new StringBuilder();
        String line;
        while (!cancelled.getAsBoolean() && (line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (!data.isEmpty()) consumer.accept(new SseEvent(event, trimLastNewline(data)));
                event = "message";
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                event = stripOptionalSpace(line.substring(6));
            } else if (line.startsWith("data:")) {
                data.append(stripOptionalSpace(line.substring(5))).append('\n');
            }
        }
        if (!cancelled.getAsBoolean() && !data.isEmpty()) {
            consumer.accept(new SseEvent(event, trimLastNewline(data)));
        }
    }

    private static String stripOptionalSpace(String value) {
        return value.startsWith(" ") ? value.substring(1) : value;
    }
    private static String trimLastNewline(StringBuilder data) {
        return data.substring(0, data.length() - 1);
    }
}
