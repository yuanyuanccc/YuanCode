package com.yuancode.llm;

public interface LlmClient {
    StreamHandle stream(LlmRequest request);
}
