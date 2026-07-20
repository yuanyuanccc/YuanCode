package com.yuancode.e2e;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yuancode.cli.InteractiveShell;
import com.yuancode.config.AppConfig;
import com.yuancode.config.ProviderConfig;
import com.yuancode.llm.LlmClientFactory;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversationE2eTest {
    @Test
    void cliStreamsTwoTurnsAndRoundTripsHistoryAndThinkingSignature() throws Exception {
        List<String> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/messages", exchange -> respond(exchange, requests));
        server.start();
        try {
            ProviderConfig provider = new ProviderConfig("claude", "anthropic", "claude-sonnet-4-6",
                    "http://localhost:" + server.getAddress().getPort(), "secret-canary-key",
                    4096, true, false);
            AppConfig config = new AppConfig("claude", Map.of("claude", provider));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            var shell = new InteractiveShell(new BufferedReader(new StringReader("first\nfollow-up\n/exit\n")),
                    new PrintWriter(bytes, true, StandardCharsets.UTF_8), config, LlmClientFactory::create);

            shell.run();

            String output = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("one-two"));
            assertFalse(output.contains("private-thought-canary"));
            assertEquals(2, requests.size());
            assertTrue(requests.get(1).contains("first"));
            assertTrue(requests.get(1).contains("one-two"));
            assertTrue(requests.get(1).contains("opaque-signature-+/="));
            assertFalse(output.contains("secret-canary-key"));
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, List<String> requests) throws IOException {
        requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String response = """
                event: message_start
                data: {"type":"message_start","message":{"usage":{"input_tokens":4}}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"private-thought-canary"}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"signature_delta","signature":"opaque-signature-+/="}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"one-"}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"two"}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}

                event: message_stop
                data: {"type":"message_stop"}

                """;
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("content-type", "text/event-stream");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
