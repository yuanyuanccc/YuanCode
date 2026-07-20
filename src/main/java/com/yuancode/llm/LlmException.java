package com.yuancode.llm;

public class LlmException extends RuntimeException {
    public LlmException(String message) { super(message); }
    public LlmException(String message, Throwable cause) { super(message, cause); }

    public static final class Authentication extends LlmException {
        public Authentication() { super("API 认证失败，请检查 API Key"); }
    }
    public static final class RateLimited extends LlmException {
        public RateLimited() { super("请求过于频繁，请稍后重试"); }
    }
    public static final class ContextTooLong extends LlmException {
        public ContextTooLong() { super("对话超过模型上下文限制，请使用 /clear 后重试"); }
    }
    public static final class Network extends LlmException {
        public Network(Throwable cause) { super("无法连接到模型服务", cause); }
    }
    public static final class StreamTimeout extends LlmException {
        public StreamTimeout() { super("流式响应超时"); }
    }
}
