package com.rhett.multivillageselector.config;

import com.rhett.multivillageselector.MVSCommon;

import dev.architectury.platform.Platform;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pure I/O class for config file operations.
 * No parsing logic, no business logic - just file operations.
 * Testable with mock file systems.
 */
public class ConfigLoader {

    private static final String CONFIG_FILENAME = "multivillageselector.json5";
    private static final String DEFAULT_CONFIG_RESOURCE = "/mvs_config.json5";

    /**
     * Result of loading config from disk.
     */
    public static class LoadResult {
        public final String content;
        public final boolean wasCreated;
        public final String path;

        public LoadResult(String content, boolean wasCreated, String path) {
            this.content = content;
            this.wasCreated = wasCreated;
            this.path = path;
        }
    }

    /**
     * Loads config file, creating default if it doesn't exist.
     * Pure I/O - no parsing.
     *
     * @return LoadResult with file content and metadata
     * @throws IOException if file operations fail
     */
    public static LoadResult loadOrCreate() throws IOException {
        Path configDir = Platform.getConfigFolder();
        Path configFile = configDir.resolve(CONFIG_FILENAME);

        MVSCommon.LOGGER.info("MVS: Config directory: {}", configDir);
        MVSCommon.LOGGER.info("MVS: Looking for config at: {}", configFile);

        // Create default config if it doesn't exist
        if (!Files.exists(configFile)) {
            MVSCommon.LOGGER.info("MVS: Config file not found, creating default...");
            createDefaultConfig(configFile, configDir);
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            return new LoadResult(content, true, configFile.toString());
        }

        // Load existing config
        MVSCommon.LOGGER.info("MVS: Found existing config file");
        MVSCommon.LOGGER.info("MVS: Loading config from: {}", configFile);
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        return new LoadResult(content, false, configFile.toString());
    }

    /**
     * Creates default config file from bundled resource.
     * Enables debug flags in dev environment.
     *
     * @param configFile Target config file path
     * @param configDir Config directory (created if needed)
     * @throws IOException if file operations fail
     */
    private static void createDefaultConfig(Path configFile, Path configDir) throws IOException {
        // Ensure config directory exists
        if (!Files.exists(configDir)) {
            MVSCommon.LOGGER.info("MVS: Creating config directory: {}", configDir);
            Files.createDirectories(configDir);
        }

        // Load default config from resources
        InputStream defaultConfig = ConfigLoader.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE);
        if (defaultConfig == null) {
            throw new IOException("Could not find default " + DEFAULT_CONFIG_RESOURCE + " in mod resources!");
        }

        MVSCommon.LOGGER.info("MVS: Copying default config to: {}", configFile);

        // Dev environment detection
        boolean isDevEnvironment = isDevEnvironment();

        if (isDevEnvironment) {
            // In dev mode - enable debug flags
            MVSCommon.LOGGER.info("MVS: ✓ Dev environment detected - enabling debug flags");
            String configContent = new String(defaultConfig.readAllBytes(), StandardCharsets.UTF_8);
            configContent = configContent
                .replace("debug_cmd: false", "debug_cmd: true")
                .replace("debug_logging: false", "debug_logging: true");
            Files.writeString(configFile, configContent, StandardCharsets.UTF_8);
        } else {
            // Production mode - copy as-is
            Files.copy(defaultConfig, configFile);
        }

        defaultConfig.close();

        MVSCommon.LOGGER.info("MVS: Successfully created default config at: {}", configFile);
    }

    /**
     * Detects if running in development environment.
     * Checks environment variable MVS_DEV=true.
     *
     * @return true if dev environment detected
     */
    private static boolean isDevEnvironment() {
        return "true".equalsIgnoreCase(System.getenv("MVS_DEV"));
    }
}
