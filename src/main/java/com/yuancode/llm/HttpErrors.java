package com.yuancode.llm;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class HttpErrors {
    private HttpErrors() {}

    public static LlmException classify(HttpResponse<InputStream> response) {
        String body = readSummary(response.body());
        String lower = body.toLowerCase(Locale.ROOT);
        return switch (response.statusCode()) {
            case 401, 403 -> new LlmException.Authentication();
            case 429 -> new LlmException.RateLimited();
            case 413 -> new LlmException.ContextTooLong();
            case 400 -> lower.contains("context_length") || lower.contains("prompt is too long")
                    || lower.contains("too many tokens") ? new LlmException.ContextTooLong()
                    : new LlmException("模型请求无效: " + body);
            default -> new LlmException("模型服务返回 HTTP " + response.statusCode() + ": " + body);
        };
    }

    private static String readSummary(InputStream input) {
        try {
            byte[] bytes = input.readNBytes(501);
            String value = new String(bytes, StandardCharsets.UTF_8)
                    .replaceAll("(?i)(api[_-]?key|authorization)\\s*[:=]\s*[^,}\\s]+", "$1: [REDACTED]");
            return value.length() > 500 ? value.substring(0, 500) : value;
        } catch (Exception error) {
            return "无法读取错误响应";
        }
    }
}
