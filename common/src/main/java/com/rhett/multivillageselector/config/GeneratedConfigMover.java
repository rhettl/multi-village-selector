package com.rhett.multivillageselector.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

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

    /** Always backs up an existing target. */
    public static Result apply(Path generated, Path target) throws IOException {
        return apply(generated, target, null);
    }

    /**
     * True if {@code existing} is byte-for-byte the default config MVS wrote at init (SHA-256 match).
     */
    public static boolean isUnmodifiedDefault(byte[] existing, byte[] bundledDefault) {
        return bundledDefault != null && Arrays.equals(sha256(existing), sha256(bundledDefault));
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /**
     * Move {@code generated} to {@code target}. An existing target is kept as
     * {@code <name>.bak} next to it (only the latest backup is kept), never silently overwritten, unless it is just
     * the untouched {@code bundledDefault} by hash (nothing of the admin's to lose; may be null).
     */
    public static Result apply(Path generated, Path target, byte[] bundledDefault)
            throws IOException {
        if (!hasGenerated(generated)) {
            return new Result(Status.SOURCE_MISSING, null);
        }

        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path backup = null;
        if (Files.exists(target) && !isUnmodifiedDefault(Files.readAllBytes(target), bundledDefault)) {
            backup = target.resolveSibling(target.getFileName() + ".bak");
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.move(generated, target, StandardCopyOption.REPLACE_EXISTING);
        return new Result(Status.MOVED, backup);
    }
}
