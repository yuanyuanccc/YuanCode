package com.yuancode.terminal;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.io.PrintWriter;

public final class TerminalSession implements AutoCloseable {
    private final Terminal terminal;
    private final TerminalCapabilities capabilities;

    private TerminalSession(Terminal terminal) {
        this.terminal = terminal;
        boolean interactive = !terminal.getType().startsWith("dumb");
        boolean color = interactive && System.getenv("NO_COLOR") == null
                && terminal.getNumericCapability(InfoCmp.Capability.max_colors) != null;
        boolean cursor = interactive
                && terminal.getStringCapability(InfoCmp.Capability.carriage_return) != null
                && terminal.getStringCapability(InfoCmp.Capability.clr_eol) != null;
        this.capabilities = new TerminalCapabilities(interactive, color, cursor,
                Math.max(20, terminal.getWidth()));
    }

    public static TerminalSession open() throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .name("YuanCode")
                .system(true)
                .dumb(true)
                .build();
        return new TerminalSession(terminal);
    }

    public Terminal terminal() { return terminal; }
    public PrintWriter writer() { return terminal.writer(); }
    public TerminalCapabilities capabilities() {
        return new TerminalCapabilities(capabilities.interactive(), capabilities.ansi(),
                capabilities.cursorMovement(), Math.max(20, terminal.getWidth()));
    }
    @Override public void close() throws IOException { terminal.close(); }
}
