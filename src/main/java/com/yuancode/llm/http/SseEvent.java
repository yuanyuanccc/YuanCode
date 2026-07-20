package com.yuancode.llm.http;

public record SseEvent(String event, String data) {}
