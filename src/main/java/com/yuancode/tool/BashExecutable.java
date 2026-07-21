package com.yuancode.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BashExecutable {
    private BashExecutable() {}

    public static Optional<String> resolve() {
        String override = System.getenv("YUANCODE_BASH");
        if (override != null && !override.isBlank() && Files.isRegularFile(Path.of(override))) {
            return Optional.of(Path.of(override).toAbsolutePath().normalize().toString());
        }
        if (!isWindows()) return Optional.of("bash");

        for (Path candidate : windowsCandidates()) {
            if (Files.isRegularFile(candidate)) return Optional.of(candidate.toAbsolutePath().normalize().toString());
        }
        return Optional.empty();
    }

    private static List<Path> windowsCandidates() {
        List<Path> candidates = new ArrayList<>();
        String pathValue = System.getenv("PATH");
        if (pathValue != null) {
            for (String entry : pathValue.split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) continue;
                Path directory;
                try { directory = Path.of(entry); }
                catch (RuntimeException ignored) { continue; }
                String lower = directory.toString().toLowerCase(Locale.ROOT);
                if (lower.contains("git")) candidates.add(directory.resolve("bash.exe"));
                if (Files.isRegularFile(directory.resolve("git.exe"))) {
                    Path root = directory.getParent();
                    if (root != null) {
                        candidates.add(root.resolve("bin").resolve("bash.exe"));
                        candidates.add(root.resolve("usr").resolve("bin").resolve("bash.exe"));
                    }
                }
            }
        }
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) candidates.add(Path.of(programFiles, "Git", "bin", "bash.exe"));
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) candidates.add(Path.of(localAppData, "Programs", "Git", "bin", "bash.exe"));
        return candidates;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
