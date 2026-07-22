package com.yuancode.llm;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StreamHandle implements AutoCloseable {
    private final BlockingQueue<StreamEvent> events;
    private final Runnable cancel;
    private final AtomicBoolean closed = new AtomicBoolean();

    public StreamHandle(BlockingQueue<StreamEvent> events, Runnable cancel) {
        this.events = events;
        this.cancel = cancel;
    }

    public StreamEvent next(Duration timeout) throws InterruptedException {
        StreamEvent event = events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return event == null ? new StreamEvent.Failed(new LlmException.StreamTimeout()) : event;
    }

    public void cancel() {
        if (closed.compareAndSet(false, true)) cancel.run();
    }
    @Override public void close() { cancel(); }
}
