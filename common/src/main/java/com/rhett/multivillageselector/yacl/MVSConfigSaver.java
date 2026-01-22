package com.rhett.multivillageselector.yacl;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;

import de.marhali.json5.Json5;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Element;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles saving individual config fields back to the JSON5 file.
 * Preserves comments, formatting, and other fields not being modified.
 */
public class MVSConfigSaver {

    private static final String CONFIG_FILENAME = "multivillageselector.json5";
    private static final Json5 JSON5 = Json5.builder(builder -> builder
        .quoteSingle()  // Allow single quotes
        .trailingComma() // Allow trailing commas
        .build());

    /**
     * Gets the config file path.
     */
    private static Path getConfigPath() {
        return Platform.getConfigFolder().resolve(CONFIG_FILENAME);
    }

    /**
     * Saves the "enabled" field to config and reloads.
     */
    public static void saveEnabled(boolean value) {
        try {
            updateField("enabled", value);
            MVSConfig.enabled = value;
            MVSCommon.LOGGER.info("MVS: Saved enabled = {}", value);
        } catch (Exception e) {
            MVSCommon.LOGGER.error("MVS: Failed to save enabled setting", e);
        }
    }

    /**
     * Saves the "relaxed_biome_validation" field to config and reloads.
     */
    public static void saveRelaxedBiomeValidation(boolean value) {
        try {
            updateField("relaxed_biome_validation", value);
            MVSConfig.relaxedBiomeValidation = value;
            MVSCommon.LOGGER.info("MVS: Saved relaxed_biome_validation = {}", value);
        } catch (Exception e) {
            MVSCommon.LOGGER.error("MVS: Failed to save relaxed biome validation setting", e);
        }
    }

    /**
     * Saves the "debug_logging" field to config and reloads.
     */
    public static void saveDebugLogging(boolean value) {
        try {
            updateField("debug_logging", value);
            MVSConfig.debugLogging = value;
            MVSCommon.LOGGER.info("MVS: Saved debug_logging = {}", value);
        } catch (Exception e) {
            MVSCommon.LOGGER.error("MVS: Failed to save debug logging setting", e);
        }
    }

    /**
     * Saves the "debug_cmd" field to config and reloads.
     */
    public static void saveDebugCmd(boolean value) {
        try {
            updateField("debug_cmd", value);
            MVSConfig.debugCmd = value;
            MVSCommon.LOGGER.info("MVS: Saved debug_cmd = {}", value);
        } catch (Exception e) {
            MVSCommon.LOGGER.error("MVS: Failed to save debug cmd setting", e);
        }
    }

    /**
     * Saves the "show_launch_message" / "show_first_launch_message" field to config and reloads.
     */
    public static void saveShowLaunchMessage(boolean value) {
        try {
            // Try both field names (aliases)
            Path configPath = getConfigPath();
            String content = Files.readString(configPath, StandardCharsets.UTF_8);

            // Determine which field name is used
            if (content.contains("show_first_launch_message")) {
                updateField("show_first_launch_message", value);
            } else {
                updateField("show_launch_message", value);
            }

            MVSConfig.showLaunchMessage = value;
            MVSCommon.LOGGER.info("MVS: Saved show_launch_message = {}", value);
        } catch (Exception e) {
            MVSCommon.LOGGER.error("MVS: Failed to save show launch message setting", e);
        }
    }

    /**
     * Updates a biome frequency value in the biome_frequency map.
     *
     * @param biomePattern The biome pattern key
     * @param frequency The new frequency value (0.0 to 1.0)
     */
    public static void saveBiomeFrequency(String biomePattern, double frequency) {
        try {
            Path configPath = getConfigPath();
            String content = Files.readString(configPath, StandardCharsets.UTF_8);

            // Escape special regex characters in the biome pattern
            String escapedPattern = Pattern.quote(biomePattern);

            // Find and replace the biome frequency entry
            // Pattern: "pattern": 0.5 (with various quote styles and spacing)
            String regex = "([\"\']" + escapedPattern + "[\"\']\\s*:\\s*)[0-9]*\\.?[0-9]+";
            String replacement = "$1" + frequency;

            String updatedContent = content.replaceAll(regex, replacement);

            // Write back
            Files.writeString(configPath, updatedContent, StandardCharsets.UTF_8);

            // Update in-memory config
            MVSConfig.biomeFrequency.put(biomePattern, frequency);

            MVSCommon.LOGGER.info("MVS: Saved biome frequency {} = {}", biomePattern, frequency);
        } catch (Exception e) {
            MVSCommon.LOGGER.error("MVS: Failed to save biome frequency", e);
        }
    }

    /**
     * Updates a placement rule field for a specific structure set.
     *
     * @param structureSet The structure set ID
     * @param fieldName The field name (spacing, separation, etc.)
     * @param value The new value
     */
    public static void savePlacementRuleField(String structureSet, String fieldName, String value) {
        try {
            Path configPath = getConfigPath();
            String content = Files.readString(configPath, StandardCharsets.UTF_8);

            // Find the placement section for this structure set
            // This is complex - we need to find the structure set block and update the field within it
            String escapedSet = Pattern.quote(structureSet);

            // Pattern to match the field within the structure set's placement block
            // "structureSet": { ... fieldName: oldValue ... }
            String regex = "(\"" + escapedSet + "\"\\s*:\\s*\\{[^}]*" + fieldName + "\\s*:\\s*)([^,}]+)";
            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                String replacement = "$1" + value;
                String updatedContent = matcher.replaceFirst(replacement);
                Files.writeString(configPath, updatedContent, StandardCharsets.UTF_8);

                // Reload config to update in-memory state
                MVSConfig.load();

                MVSCommon.LOGGER.info("MVS: Saved placement rule {}.{} = {}", structureSet, fieldName, value);
            } else {
                MVSCommon.LOGGER.warn("MVS: Could not find placement rule {}.{}", structureSet, fieldName);
            }
        } catch (Exception e) {
            MVSCommon.LOGGER.error("MVS: Failed to save placement rule", e);
        }
    }

    /**
     * Reloads the entire config from disk.
     * Call this after making changes to ensure in-memory state is updated.
     */
    public static void reloadConfig() {
        try {
            MVSConfig.load();
            MVSCommon.LOGGER.info("MVS: Config reloaded");
        } catch (Exception e) {
            MVSCommon.LOGGER.error("MVS: Failed to reload config", e);
        }
    }

    /**
     * Updates a single field in the JSON5 config file.
     * Preserves all other content, including comments.
     *
     * @param fieldName The field name to update
     * @param value The new value (boolean)
     */
    private static void updateField(String fieldName, boolean value) throws IOException {
        Path configPath = getConfigPath();

        if (!Files.exists(configPath)) {
            MVSCommon.LOGGER.error("MVS: Config file not found at {}", configPath);
            return;
        }

        // Read current config content
        String content = Files.readString(configPath, StandardCharsets.UTF_8);

        // Simple regex-based replacement to preserve formatting and comments
        // Pattern: "fieldName: true/false," or "fieldName: true/false}"
        String pattern = "(" + fieldName + "\\s*:\\s*)(true|false)([,\\s}])";
        String replacement = "$1" + value + "$3";
        String updatedContent = content.replaceAll(pattern, replacement);

        // Write back to file
        Files.writeString(configPath, updatedContent, StandardCharsets.UTF_8);
        MVSCommon.LOGGER.debug("MVS: Updated {} in config file", fieldName);
    }
}
