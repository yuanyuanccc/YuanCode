package com.yuancode.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
    @TempDir Path tempDir;

    @Test
    void missingConfigCreatesSafeExample() throws Exception {
        Path config = tempDir.resolve("config.yaml");
        var result = new ConfigLoader(Map.of()).load(config);

        assertTrue(result.createdExample());
        assertNull(result.config());
        String example = Files.readString(config);
        assertTrue(example.contains("active_provider:"));
        assertTrue(example.contains("protocol:"));
        assertTrue(example.contains("deepseek:"));
        assertTrue(example.contains("model: deepseek-v4-flash"));
        assertTrue(example.contains("base_url: https://api.deepseek.com/anthropic"));
        assertTrue(example.contains("api_key: ${DEEPSEEK_API_KEY}"));
        assertFalse(example.contains("sk-"));
    }

    @Test
    void resolvesEnvironmentApiKeyAndDefaults() throws Exception {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
                active_provider: claude
                providers:
                  claude:
                    protocol: anthropic
                    model: claude-sonnet-4-6
                    base_url: https://api.anthropic.com/
                    api_key: ${ANTHROPIC_API_KEY}
                """);

        AppConfig loaded = new ConfigLoader(Map.of("ANTHROPIC_API_KEY", "secret-value"))
                .load(config).config();

        ProviderConfig provider = loaded.activeProvider();
        assertEquals("secret-value", provider.apiKey());
        assertEquals(4096, provider.maxTokens());
        assertFalse(provider.thinking());
        assertFalse(provider.showThinking());
        assertEquals("https://api.anthropic.com", provider.normalizedBaseUrl());
    }

    @Test
    void rejectsMissingEnvironmentVariableWithoutLeakingValue() throws Exception {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
                active_provider: claude
                providers:
                  claude: {protocol: anthropic, model: m, base_url: http://localhost, api_key: "${MISSING_KEY}"}
                """);

        ConfigException error = assertThrows(ConfigException.class,
                () -> new ConfigLoader(Map.of()).load(config));
        assertEquals("环境变量 MISSING_KEY 未设置或为空", error.getMessage());
    }

    @Test
    void inactiveProvidersMayReferenceUnsetEnvironmentKeys() throws Exception {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
                active_provider: deepseek
                providers:
                  claude:
                    protocol: anthropic
                    model: claude-sonnet-4-6
                    base_url: https://api.anthropic.com
                    api_key: ${ANTHROPIC_API_KEY}
                  deepseek:
                    protocol: anthropic
                    model: deepseek-v4-flash
                    base_url: https://api.deepseek.com/anthropic
                    api_key: ${DEEPSEEK_API_KEY}
                """);

        AppConfig loaded = new ConfigLoader(Map.of("DEEPSEEK_API_KEY", "deepseek-secret"))
                .load(config).config();

        assertEquals("deepseek-secret", loaded.activeProvider().apiKey());
        assertEquals("${ANTHROPIC_API_KEY}", loaded.provider("claude").apiKey());
    }
}
