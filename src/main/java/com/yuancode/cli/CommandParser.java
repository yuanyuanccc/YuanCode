package com.yuancode.cli;

public final class CommandParser {
    public Command parse(String input) {
        String value = input == null ? "" : input.trim();
        if (!value.startsWith("/")) return new Command.Message(input == null ? "" : input);
        String[] parts = value.split("\\s+", 2);
        return switch (parts[0]) {
            case "/help" -> new Command.Help();
            case "/clear" -> new Command.Clear();
            case "/exit", "/quit" -> new Command.Exit();
            case "/model" -> new Command.Model();
            case "/status" -> new Command.Status();
            case "/config" -> new Command.Config();
            case "/plan" -> parsePlan(parts);
            case "/provider" -> parts.length == 2 && !parts[1].isBlank()
                    ? new Command.SwitchProvider(parts[1].trim()) : new Command.Invalid("用法: /provider <name>");
            default -> new Command.Invalid("未知命令: " + parts[0]);
        };
    }

    private static Command parsePlan(String[] parts) {
        if (parts.length == 1) return new Command.Plan(null);
        return switch (parts[1].trim().toLowerCase()) {
            case "on" -> new Command.Plan(true);
            case "off" -> new Command.Plan(false);
            default -> new Command.Invalid("用法: /plan [on|off]");
        };
    }

    public sealed interface Command permits Command.Message, Command.Help, Command.Clear,
            Command.Exit, Command.SwitchProvider, Command.Model, Command.Status, Command.Config,
            Command.Plan, Command.Invalid {
        record Message(String text) implements Command {}
        record Help() implements Command {}
        record Clear() implements Command {}
        record Exit() implements Command {}
        record SwitchProvider(String name) implements Command {}
        record Model() implements Command {}
        record Status() implements Command {}
        record Config() implements Command {}
        record Plan(Boolean enabled) implements Command {}
        record Invalid(String message) implements Command {}
    }
}
