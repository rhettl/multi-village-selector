package com.rhett.multivillageselector.yacl;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;

import de.marhali.json5.Json5;
import de.marhali.json5.Json5Object;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
