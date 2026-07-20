package com.yuancode.llm;

import com.yuancode.config.ProviderConfig;
import com.yuancode.config.ConfigException;
import com.yuancode.llm.anthropic.AnthropicClient;
import com.yuancode.llm.openai.OpenAiClient;

public final class LlmClientFactory {
    private LlmClientFactory() {}
    public static LlmClient create(ProviderConfig config) {
        if (config.apiKey().matches("^\\$\\{[A-Za-z_][A-Za-z0-9_]*}$")) {
            String variable = config.apiKey().substring(2, config.apiKey().length() - 1);
            throw new ConfigException("环境变量 " + variable + " 未设置或为空");
        }
        return switch (config.protocol()) {
            case "anthropic" -> new AnthropicClient(config);
            case "openai" -> new OpenAiClient(config);
            default -> throw new IllegalArgumentException("不支持的 protocol: " + config.protocol());
        };
    }
}
