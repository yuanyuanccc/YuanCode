package com.yuancode.llm.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuancode.config.ProviderConfig;
import com.yuancode.conversation.ConversationMessage;
import com.yuancode.conversation.ThinkingBlock;
import com.yuancode.llm.*;
import com.yuancode.llm.http.StreamingHttpTransport;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnthropicClient implements LlmClient {
    private final ProviderConfig config;
    private final StreamingHttpTransport transport;

    public AnthropicClient(ProviderConfig config) { this(config, new StreamingHttpTransport()); }
    public AnthropicClient(ProviderConfig config, StreamingHttpTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    @Override public StreamHandle stream(LlmRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.normalizedBaseUrl() + "/v1/messages"))
                .timeout(Duration.ofMinutes(5))
                .header("content-type", "application/json")
                .header("accept", "text/event-stream")
                .header("x-api-key", config.apiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(JsonSupport.write(body(request))))
                .build();
        return transport.stream(httpRequest, new StreamMapper()::map, HttpErrors::classify);
    }

    private Map<String, Object> body(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("max_tokens", config.maxTokens());
        body.put("stream", true);
        if (!request.systemPrompt().isBlank()) body.put("system", request.systemPrompt());
        body.put("messages", request.messages().stream().map(this::message).toList());
        if (config.thinking()) {
            Map<String, Object> thinking = new LinkedHashMap<>();
            if (supportsAdaptive(config.model())) {
                thinking.put("type", "adaptive");
            } else {
                thinking.put("type", "enabled");
                thinking.put("budget_tokens", 2048);
            }
            thinking.put("display", config.showThinking() ? "summarized" : "omitted");
            body.put("thinking", thinking);
        }
        return body;
    }

    private Map<String, Object> message(ConversationMessage message) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("role", message.role() == ConversationMessage.Role.USER ? "user" : "assistant");
        if (message.role() == ConversationMessage.Role.ASSISTANT && !message.thinkingBlocks().isEmpty()) {
            List<Map<String, Object>> content = new ArrayList<>();
            for (ThinkingBlock thinking : message.thinkingBlocks()) {
                content.add(Map.of("type", "thinking", "thinking", thinking.thinking(),
                        "signature", thinking.signature()));
            }
            if (!message.text().isEmpty()) content.add(Map.of("type", "text", "text", message.text()));
            mapped.put("content", content);
        } else {
            mapped.put("content", message.text());
        }
        return mapped;
    }

    private final class StreamMapper {
        private long inputTokens;
        List<StreamEvent> map(com.yuancode.llm.http.SseEvent event) {
            JsonNode root = JsonSupport.read(event.data());
            String type = root.path("type").asText(event.event());
            if (type.equals("message_start")) {
                inputTokens = root.path("message").path("usage").path("input_tokens").asLong();
            } else if (type.equals("content_block_delta")) {
                JsonNode delta = root.path("delta");
                return switch (delta.path("type").asText()) {
                    case "text_delta" -> List.of(new StreamEvent.TextDelta(delta.path("text").asText()));
                    case "thinking_delta" -> config.showThinking()
                            ? List.of(new StreamEvent.ThinkingDelta(delta.path("thinking").asText())) : List.of();
                    case "signature_delta" -> List.of(new StreamEvent.ThinkingSignature(delta.path("signature").asText()));
                    default -> List.of();
                };
            } else if (type.equals("message_delta")) {
                String reason = root.path("delta").path("stop_reason").asText("end_turn");
                return List.of(new StreamEvent.Completed(reason,
                        new TokenUsage(inputTokens, root.path("usage").path("output_tokens").asLong())));
            } else if (type.equals("error")) {
                return List.of(new StreamEvent.Failed(new LlmException(root.path("error").path("message").asText("模型流失败"))));
            }
            return List.of();
        }
    }

    public static boolean supportsAdaptive(String model) {
        String value = model.toLowerCase();
        return value.contains("opus-4-6") || value.contains("opus-4-7") || value.contains("opus-4-8")
                || value.contains("sonnet-4-6") || value.contains("sonnet-5")
                || value.contains("fable-5") || value.contains("mythos");
    }
}
