package com.yuancode.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSupport {
    public static final ObjectMapper JSON = new ObjectMapper();
    private JsonSupport() {}

    public static JsonNode read(String json) {
        try { return JSON.readTree(json); }
        catch (JsonProcessingException error) { throw new LlmException("模型服务返回了无效 JSON", error); }
    }

    public static String write(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new LlmException("无法构造模型请求", error); }
    }
}
