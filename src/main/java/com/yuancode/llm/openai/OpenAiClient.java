package com.yuancode.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuancode.config.ProviderConfig;
import com.yuancode.conversation.ConversationMessage;
import com.yuancode.conversation.ToolCallBlock;
import com.yuancode.conversation.ToolResultBlock;
import com.yuancode.llm.*;
import com.yuancode.llm.http.StreamingHttpTransport;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
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
        return transport.stream(httpRequest, new StreamMapper()::map, HttpErrors::classify);
    }

    private Map<String, Object> body(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("max_output_tokens", config.maxTokens());
        body.put("stream", true);
        body.put("store", false);
        if (!request.systemPrompt().isBlank()) body.put("instructions", request.systemPrompt());
        List<Map<String, Object>> input = new ArrayList<>();
        request.messages().forEach(message -> input.addAll(items(message)));
        body.put("input", input);
        if (!request.tools().isEmpty()) body.put("tools", request.tools());
        return body;
    }

    private List<Map<String, Object>> items(ConversationMessage message) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (!message.text().isEmpty()) items.add(Map.of(
                "role", message.role() == ConversationMessage.Role.USER ? "user" : "assistant",
                "content", message.text()));
        for (ToolCallBlock call : message.toolCalls()) {
            items.add(Map.of("type", "function_call", "call_id", call.id(), "name", call.name(),
                    "arguments", JsonSupport.write(call.arguments())));
        }
        for (ToolResultBlock result : message.toolResults()) {
            items.add(Map.of("type", "function_call_output", "call_id", result.toolCallId(),
                    "output", result.output()));
        }
        return items;
    }

    private final class StreamMapper {
        private final Map<Integer, CallState> calls = new HashMap<>();
        private List<StreamEvent> map(com.yuancode.llm.http.SseEvent event) {
            if (event.data().equals("[DONE]")) return List.of();
            JsonNode root = JsonSupport.read(event.data());
            String type = root.path("type").asText(event.event());
            return switch (type) {
                case "response.output_text.delta" -> List.of(new StreamEvent.TextDelta(root.path("delta").asText()));
                case "response.output_item.added" -> {
                    JsonNode item = root.path("item");
                    if (!item.path("type").asText().equals("function_call")) yield List.of();
                    int index = root.path("output_index").asInt();
                    CallState call = new CallState(item.path("call_id").asText(item.path("id").asText()),
                            item.path("name").asText());
                    calls.put(index, call);
                    yield List.of(new StreamEvent.ToolCallStart(index, call.id, call.name));
                }
                case "response.function_call_arguments.delta" -> {
                    int index = root.path("output_index").asInt();
                    String delta = root.path("delta").asText();
                    CallState call = calls.get(index);
                    if (call != null) call.arguments.append(delta);
                    yield List.of(new StreamEvent.ToolCallDelta(index, delta));
                }
                case "response.function_call_arguments.done" -> {
                    int index = root.path("output_index").asInt();
                    CallState call = calls.remove(index);
                    String arguments = root.path("arguments").asText(call == null ? "{}" : call.arguments.toString());
                    if (call == null) yield List.of();
                    yield List.of(new StreamEvent.ToolCallComplete(index, call.id, call.name, parseArguments(arguments)));
                }
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
        @SuppressWarnings("unchecked")
        private Map<String, Object> parseArguments(String json) {
            if (json.isBlank()) return Map.of();
            return JsonSupport.JSON.convertValue(JsonSupport.read(json), Map.class);
        }
        private final class CallState {
            private final String id;
            private final String name;
            private final StringBuilder arguments = new StringBuilder();
            private CallState(String id, String name) { this.id = id; this.name = name; }
        }
    }
}
