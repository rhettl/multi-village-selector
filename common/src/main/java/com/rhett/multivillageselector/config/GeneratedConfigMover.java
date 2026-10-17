package com.rhett.multivillageselector.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Moves a generated config into the live config directory.
 * Plain file I/O only: callers decide when to run it (the admin must confirm first).
 */
public final class GeneratedConfigMover {

    public enum Status { MOVED, SOURCE_MISSING }

    /** @param backup where the previous config was saved, or null if there was none */
    public record Result(Status status, Path backup) {}

    private GeneratedConfigMover() {}

    public static boolean hasGenerated(Path generated) {
        return Files.isRegularFile(generated);
    }

    /**
     * Move {@code generated} to {@code target}. An existing target is kept as
     * {@code <name>.bak-<backupSuffix>} next to it, never silently overwritten.
     */
    public static Result apply(Path generated, Path target, String backupSuffix) throws IOException {
        if (!hasGenerated(generated)) {
            return new Result(Status.SOURCE_MISSING, null);
        }

        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path backup = null;
        if (Files.exists(target)) {
            backup = target.resolveSibling(target.getFileName() + ".bak-" + backupSuffix);
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.move(generated, target, StandardCopyOption.REPLACE_EXISTING);
        return new Result(Status.MOVED, backup);
    }
}
