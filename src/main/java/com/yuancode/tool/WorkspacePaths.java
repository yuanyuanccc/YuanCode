package com.yuancode.tool;

import java.nio.file.Path;

public final class WorkspacePaths {
    private final Path root;

    public WorkspacePaths(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path resolve(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("file_path is required");
        }
        Path supplied = Path.of(text);
        Path resolved = (supplied.isAbsolute() ? supplied : root.resolve(supplied)).normalize().toAbsolutePath();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path is outside the workspace: " + text);
        }
        return resolved;
    }
}
