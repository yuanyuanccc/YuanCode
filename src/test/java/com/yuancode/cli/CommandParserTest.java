package com.yuancode.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CommandParserTest {
    private final CommandParser parser = new CommandParser();

    @Test
    void parsesStatusCommandsAndQuitAlias() {
        assertInstanceOf(CommandParser.Command.Model.class, parser.parse("/model"));
        assertInstanceOf(CommandParser.Command.Status.class, parser.parse("/status"));
        assertInstanceOf(CommandParser.Command.Config.class, parser.parse("/config"));
        assertInstanceOf(CommandParser.Command.Exit.class, parser.parse("/quit"));
    }
}
