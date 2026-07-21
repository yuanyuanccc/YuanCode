package com.yuancode.tool;

import java.util.Set;

public final class FileScanPolicy {
    public static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".venv", "node_modules", "__pycache__", ".tox", ".mypy_cache");

    private FileScanPolicy() {}
}
