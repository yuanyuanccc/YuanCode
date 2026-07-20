package com.yuancode.config;

import java.net.URI;
import java.util.Locale;

public record ProviderConfig(
        String name,
        String protocol,
        String model,
        String baseUrl,
        String apiKey,
        int maxTokens,
        boolean thinking,
        boolean showThinking) {

    public ProviderConfig {
        name = require(name, "name", name);
        protocol = require(protocol, name, "protocol").toLowerCase(Locale.ROOT);
        model = require(model, name, "model");
        baseUrl = require(baseUrl, name, "base_url");
        apiKey = require(apiKey, name, "api_key");
        if (!protocol.equals("anthropic") && !protocol.equals("openai")) {
            throw new ConfigException("不支持的 protocol: " + protocol);
        }
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getScheme() == null || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            throw new ConfigException("Provider " + name + " 的 base_url 不是有效 HTTP 地址");
        }
        if (maxTokens <= 0) throw new ConfigException("Provider " + name + " 的 max_tokens 必须大于 0");
    }

    public String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String require(String value, String provider, String field) {
        if (value == null || value.isBlank()) throw new ConfigException("Provider " + provider + " 缺少字段: " + field);
        return value.trim();
    }
}
