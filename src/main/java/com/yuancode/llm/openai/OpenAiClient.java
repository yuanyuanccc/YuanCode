package com.yuancode.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuancode.config.ProviderConfig;
import com.yuancode.conversation.ConversationMessage;
import com.yuancode.llm.*;
import com.yuancode.llm.http.StreamingHttpTransport;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiClient implements LlmClient {
    private final ProviderConfig config;
    private final StreamingHttpTransport transport;

    public OpenAiClient(ProviderConfig config) { this(config, new StreamingHttpTransport()); }
    public OpenAiClient(ProviderConfig config, StreamingHttpTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    @Override public StreamHandle stream(LlmRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.normalizedBaseUrl() + "/v1/responses"))
                .timeout(Duration.ofMinutes(5))
                .header("content-type", "application/json")
                .header("accept", "text/event-stream")
                .header("authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(JsonSupport.write(body(request))))
                .build();
        return transport.stream(httpRequest, this::mapEvent, HttpErrors::classify);
    }

    private Map<String, Object> body(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("max_output_tokens", config.maxTokens());
        body.put("stream", true);
        body.put("store", false);
        if (!request.systemPrompt().isBlank()) body.put("instructions", request.systemPrompt());
        body.put("input", request.messages().stream().map(this::message).toList());
        return body;
    }

    private Map<String, Object> message(ConversationMessage message) {
        return Map.of("role", message.role() == ConversationMessage.Role.USER ? "user" : "assistant",
                "content", message.text());
    }

    private List<StreamEvent> mapEvent(com.yuancode.llm.http.SseEvent event) {
        if (event.data().equals("[DONE]")) return List.of();
        JsonNode root = JsonSupport.read(event.data());
        String type = root.path("type").asText(event.event());
        return switch (type) {
            case "response.output_text.delta" -> List.of(new StreamEvent.TextDelta(root.path("delta").asText()));
            case "response.completed" -> {
                JsonNode response = root.path("response");
                JsonNode usage = response.path("usage");
                yield List.of(new StreamEvent.Completed(response.path("status").asText("completed"),
                        new TokenUsage(usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong())));
            }
            case "response.failed", "error" -> List.of(new StreamEvent.Failed(new LlmException(
                    root.path("response").path("error").path("message").asText(
                            root.path("error").path("message").asText("模型流失败")))));
            default -> List.of();
        };
    }
}
