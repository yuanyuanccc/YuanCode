package com.yuancode.ui;

public final class Theme {
    private static final String RESET = "\u001B[0m";
    private final boolean color;

    private Theme(boolean color) { this.color = color; }
    public static Theme color() { return new Theme(true); }
    public static Theme plain() { return new Theme(false); }
    public static Theme detect(boolean interactive) {
        return interactive && System.getenv("NO_COLOR") == null ? color() : plain();
    }

    public boolean colorEnabled() { return color; }
    public String logo(String value) { return style(value, "\u001B[38;5;209m"); }
    public String accent(String value) { return style(value, "\u001B[38;5;39m"); }
    public String badge(String value) { return style(value, "\u001B[1;97;48;5;33m"); }
    public String title(String value) { return style(value, "\u001B[1;97m"); }
    public String muted(String value) { return style(value, "\u001B[38;5;245m"); }
    public String user(String value) { return style(value, "\u001B[1;96m"); }
    public String code(String value) { return style(value, "\u001B[38;5;223m"); }
    public String heading(String value) { return style(value, "\u001B[1;94m"); }
    public String strong(String value) { return style(value, "\u001B[1m"); }
    public String error(String value) { return style(value, "\u001B[1;91m"); }
    public String warning(String value) { return style(value, "\u001B[38;5;220m"); }

    private String style(String value, String prefix) {
        return color ? prefix + value + RESET : value;
    }

    public static int visibleLength(String value) {
        return value.replaceAll("\\u001B\\[[;\\d]*m", "").length();
    }
}
