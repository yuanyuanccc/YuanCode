package com.yuancode.input;

import com.yuancode.ui.PromptRenderer;
import com.yuancode.ui.Theme;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;

public final class InteractiveInput {
    private final LineReader reader;
    private final PromptRenderer prompt;
    private final Terminal terminal;

    public InteractiveInput(Terminal terminal) {
        this(terminal, Theme.plain());
    }

    public InteractiveInput(Terminal terminal, Theme theme) {
        this.terminal = terminal;
        this.prompt = new PromptRenderer(theme);
        DefaultParser parser = new DefaultParser();
        parser.setEofOnUnclosedQuote(false);
        parser.setEofOnEscapedNewLine(false);
        this.reader = LineReaderBuilder.builder()
                .appName("YuanCode")
                .terminal(terminal)
                .parser(parser)
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, prompt.continuation())
                .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                .build();
        reader.getWidgets().put("yuancode-insert-newline", () -> {
            reader.getBuffer().write('\n');
            return true;
        });
        reader.getKeyMaps().get(LineReader.MAIN)
                .bind(new Reference("yuancode-insert-newline"), KeyMap.alt('\r'), KeyMap.alt('\n'));
    }

    public ReadResult read() {
        int width = Math.max(20, terminal.getWidth());
        terminal.writer().println(prompt.identity(System.getProperty("user.name", "user")));
        terminal.writer().println(prompt.topBorder(width));
        terminal.writer().flush();
        try {
            return new ReadResult.Message(reader.readLine(
                    prompt.primary(), prompt.rightHint(), (MaskingCallback) null, null));
        } catch (UserInterruptException interrupt) {
            return new ReadResult.Interrupted();
        } catch (EndOfFileException eof) {
            return new ReadResult.EndOfFile();
        } finally {
            terminal.writer().println();
            terminal.writer().println(prompt.bottomBorder(width));
            terminal.writer().flush();
        }
    }

    public sealed interface ReadResult permits ReadResult.Message, ReadResult.Interrupted, ReadResult.EndOfFile {
        record Message(String text) implements ReadResult {}
        record Interrupted() implements ReadResult {}
        record EndOfFile() implements ReadResult {}
    }
}
