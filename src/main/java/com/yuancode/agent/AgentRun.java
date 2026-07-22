package com.yuancode.agent;

import com.yuancode.llm.StreamHandle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentRun {
    public static final int EVENT_QUEUE_CAPACITY = 64;
    private final LinkedBlockingQueue<AgentEvent> events = new LinkedBlockingQueue<>(EVENT_QUEUE_CAPACITY);
    private final AtomicBoolean done = new AtomicBoolean();
    private volatile Thread worker;
    private volatile StreamHandle activeStream;

    public AgentEvent next(Duration timeout) throws InterruptedException {
        return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public List<AgentEvent> drainUntilComplete(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<AgentEvent> drained = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            long remaining = deadline - System.nanoTime();
            AgentEvent event = events.poll(Math.max(1, remaining), TimeUnit.NANOSECONDS);
            if (event == null) break;
            drained.add(event);
            if (event instanceof AgentEvent.LoopCompleted) return List.copyOf(drained);
        }
        return List.copyOf(drained);
    }

    public boolean isDone() { return done.get(); }

    public void cancel() {
        StreamHandle stream = activeStream;
        if (stream != null) stream.cancel();
        Thread thread = worker;
        if (thread != null) thread.interrupt();
    }

    void attachWorker(Thread worker) { this.worker = worker; }
    void attachStream(StreamHandle stream) { this.activeStream = stream; }
    void detachStream() { this.activeStream = null; }

    void publish(AgentEvent event) {
        try {
            events.put(event);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    void complete(AgentTermination reason, int iterations) {
        if (done.compareAndSet(false, true)) {
            AgentEvent terminal = new AgentEvent.LoopCompleted(reason, iterations);
            while (!events.offer(terminal)) events.poll();
        }
    }
}
