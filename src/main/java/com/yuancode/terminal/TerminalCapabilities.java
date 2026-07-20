package com.yuancode.terminal;

public record TerminalCapabilities(boolean interactive, boolean ansi, boolean cursorMovement, int width) {}
