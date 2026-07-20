package com.yuancode.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record AppConfig(String activeProviderName, Map<String, ProviderConfig> providers) {
    public AppConfig {
        providers = Map.copyOf(new LinkedHashMap<>(providers));
        if (!providers.containsKey(activeProviderName)) {
            throw new ConfigException("未找到当前 Provider: " + activeProviderName);
        }
    }

    public ProviderConfig activeProvider() { return providers.get(activeProviderName); }

    public ProviderConfig provider(String name) {
        ProviderConfig provider = providers.get(name);
        if (provider == null) throw new ConfigException("未找到 Provider: " + name);
        return provider;
    }
}
