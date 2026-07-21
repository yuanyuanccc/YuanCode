package com.yuancode.llm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yuancode.config.ProviderConfig;
import com.yuancode.conversation.Conversation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProviderStreamingTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void anthropicRoundTripsStreamingToolCalls() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        start("/v1/messages", body, """
                data: {"type":"message_start","message":{"usage":{"input_tokens":5}}}

                data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"call-1","name":"ReadFile","input":{}}}

                data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"file_path\\":\\"a.txt\\"}"}}

                data: {"type":"content_block_stop","index":0}

                data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":3}}

                """);
        ProviderConfig cfg = config("anthropic", "claude-sonnet-4-6", false, false);
        Conversation conversation = new Conversation();
        conversation.addUser("read");
        LlmRequest request = new LlmRequest(conversation.messages(), "", List.of(Map.of(
                "name", "ReadFile", "description", "read", "input_schema", Map.of("type", "object"))));

        try (StreamHandle stream = LlmClientFactory.create(cfg).stream(request)) {
            assertInstanceOf(StreamEvent.ToolCallStart.class, stream.next(Duration.ofSeconds(2)));
            assertInstanceOf(StreamEvent.ToolCallDelta.class, stream.next(Duration.ofSeconds(2)));
            StreamEvent.ToolCallComplete complete = (StreamEvent.ToolCallComplete) stream.next(Duration.ofSeconds(2));
            assertEquals("a.txt", complete.arguments().get("file_path"));
            assertEquals("tool_use", ((StreamEvent.Completed) stream.next(Duration.ofSeconds(2))).stopReason());
        }
        assertTrue(body.get().contains("\"tools\""));
        assertTrue(body.get().contains("\"ReadFile\""));
    }

    @Test
    void openAiMapsStreamingFunctionCalls() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        start("/v1/responses", body, """
                data: {"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","call_id":"call-2","name":"Bash"}}

                data: {"type":"response.function_call_arguments.delta","output_index":0,"delta":"{\\"command\\":\\"pwd\\"}"}

                data: {"type":"response.function_call_arguments.done","output_index":0,"arguments":"{\\"command\\":\\"pwd\\"}"}

                data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":2,"output_tokens":2}}}

                """);
        ProviderConfig cfg = config("openai", "gpt-5-mini", false, false);
        Conversation conversation = new Conversation();
        conversation.addUser("pwd");

        try (StreamHandle stream = LlmClientFactory.create(cfg).stream(new LlmRequest(
                conversation.messages(), "", List.of(Map.of("type", "function", "name", "Bash",
                "description", "run", "parameters", Map.of("type", "object")))))) {
            assertInstanceOf(StreamEvent.ToolCallStart.class, stream.next(Duration.ofSeconds(2)));
            assertInstanceOf(StreamEvent.ToolCallDelta.class, stream.next(Duration.ofSeconds(2)));
            StreamEvent.ToolCallComplete complete = (StreamEvent.ToolCallComplete) stream.next(Duration.ofSeconds(2));
            assertEquals("pwd", complete.arguments().get("command"));
            assertInstanceOf(StreamEvent.Completed.class, stream.next(Duration.ofSeconds(2)));
        }
        assertTrue(body.get().contains("\"tools\""));
    }

    @Test
    void anthropicStreamsTextAndUsesAdaptiveThinking() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        start("/v1/messages", body, """
                event: message_start
                data: {"type":"message_start","message":{"usage":{"input_tokens":7}}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hel"}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"lo"}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":3}}

                """);
        ProviderConfig cfg = config("anthropic", "claude-sonnet-4-6", true, false);
        Conversation conversation = new Conversation();
        conversation.addUser("hi");

        try (StreamHandle stream = LlmClientFactory.create(cfg).stream(new LlmRequest(conversation.messages(), "system"))) {
            assertEquals("Hel", ((StreamEvent.TextDelta) stream.next(Duration.ofSeconds(2))).text());
            assertEquals("lo", ((StreamEvent.TextDelta) stream.next(Duration.ofSeconds(2))).text());
            StreamEvent.Completed completed = (StreamEvent.Completed) stream.next(Duration.ofSeconds(2));
            assertEquals(new TokenUsage(7, 3), completed.usage());
        }
        assertTrue(body.get().contains("\"type\":\"adaptive\""));
        assertTrue(body.get().contains("\"display\":\"omitted\""));
    }

    @Test
    void openAiSendsExplicitHistoryAndStreamsDeltas() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        start("/v1/responses", body, """
                data: {"type":"response.output_text.delta","delta":"A"}

                data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":4,"output_tokens":1}}}

                """);
        ProviderConfig cfg = config("openai", "gpt-5-mini", false, false);
        Conversation conversation = new Conversation();
        conversation.addUser("first");
        conversation.addAssistant("answer", null);
        conversation.addUser("next");

        try (StreamHandle stream = LlmClientFactory.create(cfg).stream(new LlmRequest(conversation.messages(), ""))) {
            assertEquals("A", ((StreamEvent.TextDelta) stream.next(Duration.ofSeconds(2))).text());
            assertInstanceOf(StreamEvent.Completed.class, stream.next(Duration.ofSeconds(2)));
        }
        String json = body.get();
        assertTrue(json.indexOf("first") < json.indexOf("answer"));
        assertTrue(json.indexOf("answer") < json.indexOf("next"));
        assertTrue(json.contains("\"store\":false"));
    }

    @Test
    void olderClaudeUsesFixedThinkingBudget() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        start("/v1/messages", body, """
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":0}}

                """);
        ProviderConfig cfg = config("anthropic", "claude-opus-4-5", true, true);
        Conversation conversation = new Conversation();
        conversation.addUser("hi");

        try (StreamHandle stream = LlmClientFactory.create(cfg).stream(new LlmRequest(conversation.messages(), ""))) {
            assertInstanceOf(StreamEvent.Completed.class, stream.next(Duration.ofSeconds(2)));
        }
        assertTrue(body.get().contains("\"type\":\"enabled\""));
        assertTrue(body.get().contains("\"budget_tokens\":2048"));
        assertTrue(body.get().contains("\"display\":\"summarized\""));
    }

    @Test
    void authenticationErrorIsSanitizedAndObservable() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = "{\"error\":{\"message\":\"bad authorization: secret-canary-key\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        ProviderConfig cfg = new ProviderConfig("test", "openai", "model",
                "http://localhost:" + server.getAddress().getPort(), "secret-canary-key",
                4096, false, false);
        Conversation conversation = new Conversation();
        conversation.addUser("hi");

        try (StreamHandle stream = LlmClientFactory.create(cfg).stream(new LlmRequest(conversation.messages(), ""))) {
            StreamEvent.Failed failed = (StreamEvent.Failed) stream.next(Duration.ofSeconds(2));
            assertInstanceOf(LlmException.Authentication.class, failed.error());
            assertEquals("API 认证失败，请检查 API Key", failed.error().getMessage());
            assertFalse(failed.error().getMessage().contains("secret-canary-key"));
        }
    }

    private ProviderConfig config(String protocol, String model, boolean thinking, boolean showThinking) {
        return new ProviderConfig("test", protocol, model, "http://localhost:" + server.getAddress().getPort(),
                "secret", 4096, thinking, showThinking);
    }

    private void start(String path, AtomicReference<String> requestBody, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> respond(exchange, requestBody, response));
        server.start();
    }

    private static void respond(HttpExchange exchange, AtomicReference<String> body, String response) throws IOException {
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        exchange.getResponseHeaders().set("content-type", "text/event-stream");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
