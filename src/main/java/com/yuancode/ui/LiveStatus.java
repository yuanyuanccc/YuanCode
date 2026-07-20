package com.yuancode.ui;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class LiveStatus implements AutoCloseable {
    private final PrintWriter output;
    private final Theme theme;
    private final boolean dynamic;
    private final Instant started = Instant.now();
    private final ScheduledExecutorService scheduler;
    private volatile boolean visible;

    public LiveStatus(PrintWriter output, Theme theme, boolean dynamic) {
        this.output = output;
        this.theme = theme;
        this.dynamic = dynamic;
        this.scheduler = dynamic ? Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()) : null;
    }

    public synchronized void start() {
        visible = true;
        draw();
        if (scheduler != null) scheduler.scheduleAtFixedRate(this::draw, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void firstToken() {
        if (!visible) return;
        if (dynamic) output.print("\r\u001B[2K"); else output.println();
        output.flush();
        visible = false;
        stopScheduler();
    }

    public synchronized Duration elapsed() { return Duration.between(started, Instant.now()); }

    private synchronized void draw() {
        if (!visible) return;
        long seconds = elapsed().toSeconds();
        String text = "✻ Thinking…" + (seconds == 0 ? "" : " (" + seconds + "s)");
        if (dynamic) output.print("\r\u001B[2K" + theme.muted(text));
        else output.print(theme.muted(text));
        output.flush();
    }

    private void stopScheduler() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override public synchronized void close() {
        firstToken();
        stopScheduler();
    }
}
