package com.rhett.multivillageselector.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedConfigMoverTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("apply: fails without touching anything when the generated file is missing")
    void missingSource() throws IOException {
        Path generated = tmp.resolve("local/mvs/multivillageselector.json5");
        Path target = tmp.resolve("config/multivillageselector.json5");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "existing");

        GeneratedConfigMover.Result result = GeneratedConfigMover.apply(generated, target, "t1");

        assertEquals(GeneratedConfigMover.Status.SOURCE_MISSING, result.status());
        assertEquals("existing", Files.readString(target));
        assertNull(result.backup());
    }

    @Test
    @DisplayName("apply: moves the generated file into place when no config exists")
    void movesIntoPlace() throws IOException {
        Path generated = tmp.resolve("local/mvs/multivillageselector.json5");
        Path target = tmp.resolve("config/multivillageselector.json5");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, "generated");

        GeneratedConfigMover.Result result = GeneratedConfigMover.apply(generated, target, "t1");

        assertEquals(GeneratedConfigMover.Status.MOVED, result.status());
        assertNull(result.backup());
        assertEquals("generated", Files.readString(target));
        assertFalse(Files.exists(generated), "generated file is moved, not copied");
    }

    @Test
    @DisplayName("apply: backs up an existing config before replacing it")
    void backsUpExisting() throws IOException {
        Path generated = tmp.resolve("local/mvs/multivillageselector.json5");
        Path target = tmp.resolve("config/multivillageselector.json5");
        Files.createDirectories(generated.getParent());
        Files.createDirectories(target.getParent());
        Files.writeString(generated, "generated");
        Files.writeString(target, "my tuned config");

        GeneratedConfigMover.Result result = GeneratedConfigMover.apply(generated, target, "t1");

        assertEquals(GeneratedConfigMover.Status.MOVED, result.status());
        assertEquals("generated", Files.readString(target));
        assertNotNull(result.backup());
        assertEquals("my tuned config", Files.readString(result.backup()));
        assertEquals("multivillageselector.json5.bak-t1", result.backup().getFileName().toString());
    }

    @Test
    @DisplayName("hasGenerated: reflects whether the generated file is present")
    void hasGenerated() throws IOException {
        Path generated = tmp.resolve("multivillageselector.json5");
        assertFalse(GeneratedConfigMover.hasGenerated(generated));
        Files.writeString(generated, "x");
        assertTrue(GeneratedConfigMover.hasGenerated(generated));
    }

    private static final String DEFAULT_TEXT = "{\n  enabled: true,\n  debug_cmd: false,\n  debug_logging: false,\n}\n";
    private static final byte[] DEFAULT = DEFAULT_TEXT.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private GeneratedConfigMover.Result applyOver(String existing) throws IOException {
        Path generated = tmp.resolve("local/mvs/multivillageselector.json5");
        Path target = tmp.resolve("config/multivillageselector.json5");
        Files.createDirectories(generated.getParent());
        Files.createDirectories(target.getParent());
        Files.writeString(generated, "generated");
        Files.writeString(target, existing);
        return GeneratedConfigMover.apply(generated, target, "t1", DEFAULT);
    }

    @Test
    @DisplayName("apply: no backup when the existing config is the untouched default")
    void noBackupForDefault() throws IOException {
        GeneratedConfigMover.Result result = applyOver(DEFAULT_TEXT);
        assertEquals(GeneratedConfigMover.Status.MOVED, result.status());
        assertNull(result.backup());
        assertEquals("generated", Files.readString(tmp.resolve("config/multivillageselector.json5")));
    }

    @Test
    @DisplayName("apply: backs up a config that differs from the default")
    void backupWhenEdited() throws IOException {
        GeneratedConfigMover.Result result = applyOver(DEFAULT_TEXT.replace("enabled: true", "enabled: false"));
        assertNotNull(result.backup());
        assertTrue(Files.readString(result.backup()).contains("enabled: false"));
    }

    @Test
    @DisplayName("isUnmodifiedDefault: matches identical bytes only; null default never matches")
    void hashCompare() {
        assertTrue(GeneratedConfigMover.isUnmodifiedDefault(DEFAULT, DEFAULT.clone()));
        assertFalse(GeneratedConfigMover.isUnmodifiedDefault(DEFAULT_TEXT.concat(" ").getBytes(), DEFAULT));
        assertFalse(GeneratedConfigMover.isUnmodifiedDefault(DEFAULT, null));
    }
}
