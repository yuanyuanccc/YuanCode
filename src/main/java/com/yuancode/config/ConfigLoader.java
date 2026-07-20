package com.yuancode.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigLoader {
    private static final Pattern ENV_REFERENCE = Pattern.compile("^\\$\\{([A-Za-z_][A-Za-z0-9_]*)}$");
    private static final String EXAMPLE = """
            # YuanCode configuration
            active_provider: claude
            providers:
              claude:
                protocol: anthropic
                model: claude-sonnet-4-6
                base_url: https://api.anthropic.com
                api_key: ${ANTHROPIC_API_KEY}
                max_tokens: 4096
                thinking: false
                show_thinking: false
              openai:
                protocol: openai
                model: gpt-5-mini
                base_url: https://api.openai.com
                api_key: ${OPENAI_API_KEY}
                max_tokens: 4096
              deepseek:
                protocol: anthropic
                model: deepseek-v4-flash
                base_url: https://api.deepseek.com/anthropic
                api_key: ${DEEPSEEK_API_KEY}
                max_tokens: 4096
                thinking: false
                show_thinking: false
            """;

    private final Map<String, String> environment;

    public ConfigLoader() { this(System.getenv()); }
    public ConfigLoader(Map<String, String> environment) { this.environment = Map.copyOf(environment); }

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".yuancode", "config.yaml");
    }

    public LoadResult load(Path path) {
        if (Files.notExists(path)) return createExample(path);
        try {
            Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(path));
            if (!(value instanceof Map<?, ?> root)) throw new ConfigException("配置文件必须是 YAML 对象");
            String active = string(root.get("active_provider"));
            if (active == null || active.isBlank()) throw new ConfigException("配置缺少字段: active_provider");
            if (!(root.get("providers") instanceof Map<?, ?> rawProviders) || rawProviders.isEmpty()) {
                throw new ConfigException("配置缺少字段: providers");
            }
            Map<String, ProviderConfig> providers = new LinkedHashMap<>();
            for (var entry : rawProviders.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                    throw new ConfigException("Provider " + name + " 必须是 YAML 对象");
                }
                providers.put(name, parseProvider(name, raw, name.equals(active.trim())));
            }
            return new LoadResult(new AppConfig(active.trim(), providers), false);
        } catch (IOException error) {
            throw new ConfigException("无法读取配置文件: " + path, error);
        } catch (ConfigException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new ConfigException("配置文件格式无效: " + error.getMessage(), error);
        }
    }

    private ProviderConfig parseProvider(String name, Map<?, ?> raw, boolean active) {
        return new ProviderConfig(name, string(raw.get("protocol")), string(raw.get("model")),
                string(raw.get("base_url")), resolveKey(string(raw.get("api_key")), active),
                integer(raw.get("max_tokens"), 4096), bool(raw.get("thinking"), false),
                bool(raw.get("show_thinking"), false));
    }

    private String resolveKey(String configured, boolean required) {
        if (configured == null) return null;
        Matcher matcher = ENV_REFERENCE.matcher(configured.trim());
        if (!matcher.matches()) return configured;
        String name = matcher.group(1);
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            if (!required) return configured.trim();
            throw new ConfigException("环境变量 " + name + " 未设置或为空");
        }
        return value;
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException error) { throw new ConfigException("max_tokens 必须是整数"); }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if (value.toString().equalsIgnoreCase("true")) return true;
        if (value.toString().equalsIgnoreCase("false")) return false;
        throw new ConfigException("布尔配置必须为 true 或 false");
    }

    private static String string(Object value) { return value == null ? null : value.toString(); }

    private LoadResult createExample(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, EXAMPLE, StandardCharsets.UTF_8);
            return new LoadResult(null, true);
        } catch (IOException error) {
            throw new ConfigException("无法创建示例配置: " + path, error);
        }
    }

    public record LoadResult(AppConfig config, boolean createdExample) {}
}
