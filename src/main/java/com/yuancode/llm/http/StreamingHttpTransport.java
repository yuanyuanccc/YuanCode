package com.yuancode.llm.http;

import com.yuancode.llm.LlmException;
import com.yuancode.llm.StreamEvent;
import com.yuancode.llm.StreamHandle;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.List;

public final class StreamingHttpTransport {
    private final HttpClient client;

    public StreamingHttpTransport() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(90)).build());
    }
    public StreamingHttpTransport(HttpClient client) { this.client = client; }

    public StreamHandle stream(HttpRequest request, Function<SseEvent, List<StreamEvent>> mapper,
                               Function<HttpResponse<InputStream>, LlmException> httpError) {
        var queue = new LinkedBlockingQueue<StreamEvent>(64);
        var cancelled = new AtomicBoolean();
        var terminalSeen = new AtomicBoolean();
        var responseBody = new AtomicReference<InputStream>();
        var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .orTimeout(90, java.util.concurrent.TimeUnit.SECONDS);

        Thread worker = Thread.startVirtualThread(() -> {
            try {
                HttpResponse<InputStream> response = future.join();
                responseBody.set(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    queue.put(new StreamEvent.Failed(httpError.apply(response)));
                    return;
                }
                new SseParser().parse(new InputStreamReader(response.body(), StandardCharsets.UTF_8), event -> {
                    for (StreamEvent mapped : mapper.apply(event)) {
                        if (mapped instanceof StreamEvent.Completed || mapped instanceof StreamEvent.Failed) {
                            terminalSeen.set(true);
                        }
                        try { queue.put(mapped); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }, cancelled::get);
                if (!cancelled.get() && !terminalSeen.get()) {
                    queue.put(new StreamEvent.Failed(new LlmException("模型流意外结束")));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                if (!cancelled.get()) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    try { queue.put(new StreamEvent.Failed(new LlmException.Network(cause))); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            } finally {
                InputStream body = responseBody.get();
                if (body != null) try { body.close(); } catch (Exception ignored) {}
            }
        });
        return new StreamHandle(queue, () -> {
            cancelled.set(true);
            future.cancel(true);
            InputStream body = responseBody.get();
            if (body != null) try { body.close(); } catch (Exception ignored) {}
            worker.interrupt();
        });
    }
}
