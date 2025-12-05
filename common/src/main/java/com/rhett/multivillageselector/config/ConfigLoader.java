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

        // v0.4.0 migration: add empty placement{} if missing
        content = migrateToV040(content, configFile);

        // v0.4.0 migration: add relaxed_biome_validation if missing
        content = migrateRelaxedBiomeValidation(content, configFile);

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

    /**
     * Migrate config to v0.4.0 format.
     * Adds empty placement{} section if missing (for discoverability).
     *
     * @param content Original config content
     * @param configFile Path to config file (for writing back)
     * @return Updated config content
     */
    private static String migrateToV040(String content, Path configFile) {
        // Check if placement section already exists
        if (content.contains("placement:") || content.contains("placement {")) {
            return content; // Already has placement, no migration needed
        }

        MVSCommon.LOGGER.info("MVS: Migrating config to v0.4.0 (adding placement section)");

        // Build the placement section to insert
        String placementSection =
            "\n" +
            "  // v0.4.0: Override structure placement (spacing, separation, salt, spreadType)\n" +
            "  // Use /mvs config fill-placements to populate with registry values\n" +
            "  placement: {\n" +
            "    \"minecraft:villages\": {},  // Empty = inherit from registry\n" +
            "  },\n";

        // Try to insert before debug section (comments + variables)
        String updatedContent = content;
        boolean inserted = false;

        // Look for debug comment block first ("// ## Debugging" or "// Auto-enabled in dev")
        java.util.regex.Pattern debugCommentPattern = java.util.regex.Pattern.compile(
            "(\\n)(\\s*//\\s*(##\\s*)?[Dd]ebug)",
            java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher commentMatcher = debugCommentPattern.matcher(content);
        if (commentMatcher.find()) {
            int insertPos = commentMatcher.start() + 1; // After the newline
            updatedContent = content.substring(0, insertPos) + placementSection + content.substring(insertPos);
            inserted = true;
        }

        // Fallback: look for debug_cmd or debug_logging directly
        if (!inserted) {
            java.util.regex.Pattern debugPattern = java.util.regex.Pattern.compile(
                "(\\n)(\\s*)(debug_cmd|debug_logging)\\s*:",
                java.util.regex.Pattern.MULTILINE
            );
            java.util.regex.Matcher matcher = debugPattern.matcher(content);
            if (matcher.find()) {
                int insertPos = matcher.start() + 1;
                updatedContent = content.substring(0, insertPos) + placementSection + content.substring(insertPos);
                inserted = true;
            }
        }

        // Fallback: insert before final closing brace
        if (!inserted) {
            int lastBrace = content.lastIndexOf('}');
            if (lastBrace > 0) {
                updatedContent = content.substring(0, lastBrace) + placementSection + "\n" + content.substring(lastBrace);
                inserted = true;
            }
        }

        if (inserted) {
            // Write back to file
            try {
                Files.writeString(configFile, updatedContent, StandardCharsets.UTF_8);
                MVSCommon.LOGGER.info("MVS: Config migrated - added placement{} section");
            } catch (IOException e) {
                MVSCommon.LOGGER.warn("MVS: Could not write migrated config: {}", e.getMessage());
                // Return original content if write fails
                return content;
            }
        }

        return updatedContent;
    }

    /**
     * Migrate config to add relaxed_biome_validation if missing.
     * Adds as last property before closing brace.
     *
     * @param content Original config content
     * @param configFile Path to config file (for writing back)
     * @return Updated config content
     */
    private static String migrateRelaxedBiomeValidation(String content, Path configFile) {
        // Check if relaxed_biome_validation already exists
        if (content.contains("relaxed_biome_validation")) {
            return content; // Already has it, no migration needed
        }

        MVSCommon.LOGGER.info("MVS: Adding relaxed_biome_validation to config");

        // Insert before final closing brace
        int lastBrace = content.lastIndexOf('}');
        if (lastBrace < 0) {
            return content; // No closing brace found, can't migrate
        }

        // Find the last property value before the closing brace and ensure it has a trailing comma
        // Look backwards from lastBrace to find where we need to insert
        String beforeBrace = content.substring(0, lastBrace);
        String trimmed = beforeBrace.stripTrailing();

        // Check if the last non-whitespace char needs a comma
        // (could be: true, false, number, string quote, ], })
        boolean needsComma = !trimmed.isEmpty() && !trimmed.endsWith(",") && !trimmed.endsWith("{");

        // Build the property to insert
        String newProperty =
            (needsComma ? "," : "") +
            "\n\n" +
            "  // v0.4.0: Relaxed biome validation\n" +
            "  // When false (default): vanilla validates biome at structure's bounding box center.\n" +
            "  //   Works well for vanilla-sized structures but may reject large mod structures\n" +
            "  //   (BCA, etc.) whose bounding box center lands in a different biome than chunk center.\n" +
            "  // When true: bypass vanilla's biome check and trust MVS's chunk-center selection.\n" +
            "  //   Recommended for modpacks with large village structures (BCA, CTOV large, etc.)\n" +
            "  relaxed_biome_validation: false,\n";

        String updatedContent = trimmed + newProperty + content.substring(lastBrace);

        // Write back to file
        try {
            Files.writeString(configFile, updatedContent, StandardCharsets.UTF_8);
            MVSCommon.LOGGER.info("MVS: Config updated - added relaxed_biome_validation");
        } catch (IOException e) {
            MVSCommon.LOGGER.warn("MVS: Could not write migrated config: {}", e.getMessage());
            return content;
        }

        return updatedContent;
    }
}
