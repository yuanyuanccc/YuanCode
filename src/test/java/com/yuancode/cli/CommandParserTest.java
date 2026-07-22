package com.yuancode.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandParserTest {
    private final CommandParser parser = new CommandParser();

    @Test
    void parsesStatusCommandsAndQuitAlias() {
        assertInstanceOf(CommandParser.Command.Model.class, parser.parse("/model"));
        assertInstanceOf(CommandParser.Command.Status.class, parser.parse("/status"));
        assertInstanceOf(CommandParser.Command.Config.class, parser.parse("/config"));
        assertInstanceOf(CommandParser.Command.Exit.class, parser.parse("/quit"));
    }

    @Test
    void parsesPlanToggleAndExplicitModes() {
        assertEquals(null, ((CommandParser.Command.Plan) parser.parse("/plan")).enabled());
        assertEquals(true, ((CommandParser.Command.Plan) parser.parse("/plan on")).enabled());
        assertEquals(false, ((CommandParser.Command.Plan) parser.parse("/plan off")).enabled());
        CommandParser.Command.Invalid invalid = (CommandParser.Command.Invalid) parser.parse("/plan maybe");
        assertEquals("用法: /plan [on|off]", invalid.message());
    }
}
