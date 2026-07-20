package com.yuancode.ui;

import com.yuancode.llm.TokenUsage;
import java.time.Duration;

public final class ResponseSummaryRenderer {
    private final Theme theme;
    public ResponseSummaryRenderer(Theme theme) { this.theme = theme; }

    public String render(Duration elapsed, TokenUsage usage) {
        double seconds = elapsed.toMillis() / 1000.0;
        String base = String.format("%.1fs", seconds);
        if (usage != null && (usage.inputTokens() > 0 || usage.outputTokens() > 0)) {
            base += " · ↑" + usage.inputTokens() + " ↓" + usage.outputTokens();
        }
        return theme.muted(base);
    }
}
