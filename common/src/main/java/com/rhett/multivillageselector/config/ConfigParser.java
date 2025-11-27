package com.rhett.multivillageselector.config;

import com.rhett.multivillageselector.config.MVSConfig;

import de.marhali.json5.Json5;
import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure parsing logic for MVS config files.
 * No I/O, no logging - just JSON5 → ConfigState transformation.
 * Completely testable with string inputs.
 */
public class ConfigParser {

    private static final Json5 JSON5 = Json5.builder(builder -> builder
        .quoteSingle()  // Allow single quotes
        .trailingComma() // Allow trailing commas
        .build());

    /**
     * Parses JSON5 config string into ConfigState.
     * Validates required fields and throws exceptions for errors.
     * Collects validation warnings for non-fatal issues (like out-of-range values).
     *
     * @param json5Content Raw JSON5 config string
     * @return Parsed and validated ConfigState
     * @throws ConfigParseException if parsing fails or required fields missing
     */
    public static ConfigState parse(String json5Content) throws ConfigParseException {
        try {
            Json5Object json = JSON5.parse(json5Content).getAsJson5Object();

            List<String> warnings = new ArrayList<>();
            ConfigState.Builder builder = new ConfigState.Builder();

            // Parse enabled (optional, defaults to true)
            if (json.has("enabled")) {
                builder.enabled(json.get("enabled").getAsBoolean());
            }

            // Parse debug_logging (optional, defaults to false)
            if (json.has("debug_logging")) {
                builder.debugLogging(json.get("debug_logging").getAsBoolean());
            }

            // Parse debug_cmd (optional, defaults to false)
            if (json.has("debug_cmd")) {
                builder.debugCmd(json.get("debug_cmd").getAsBoolean());
            }

            // Parse show_launch_message (optional, defaults to false)
            if (json.has("show_launch_message") || json.has("show_first_launch_message")) {
                String field = json.has("show_launch_message") ? "show_launch_message" : "show_first_launch_message";
                builder.showLaunchMessage(json.get(field).getAsBoolean());
            }

            // Parse block_structure_sets (optional)
            if (json.has("block_structure_sets")) {
                List<String> blockSets = parseStringArray(json.get("block_structure_sets").getAsJson5Array());
                builder.blockStructureSets(blockSets);
            }

            // Parse intercept_structure_sets (required)
            if (!json.has("intercept_structure_sets")) {
                throw new ConfigParseException(
                    "Missing required field 'intercept_structure_sets'. " +
                    "Add at minimum: \"intercept_structure_sets\": [\"minecraft:villages\"]"
                );
            }
            List<String> interceptSets = parseStringArray(json.get("intercept_structure_sets").getAsJson5Array());
            builder.interceptStructureSets(interceptSets);

            // Parse structure_pool (required)
            if (!json.has("structure_pool")) {
                throw new ConfigParseException(
                    "Missing required field 'structure_pool'. " +
                    "Add at minimum: \"structure_pool\": [{ \"structure\": \"minecraft:village_plains\", \"biomes\": {\"#minecraft:is_plains\": 10} }]"
                );
            }

            Json5Array poolArray = json.get("structure_pool").getAsJson5Array();
            if (poolArray.isEmpty()) {
                throw new ConfigParseException("'structure_pool' is empty - no villages can spawn!");
            }

            List<MVSConfig.RawConfigEntry> rawPool = parseStructurePool(poolArray, warnings);
            builder.structurePoolRaw(rawPool);

            // Parse blacklisted_structures (optional)
            if (json.has("blacklisted_structures")) {
                List<String> blacklist = parseStringArray(json.get("blacklisted_structures").getAsJson5Array());
                builder.blacklistedStructures(blacklist);
            }

            // Parse biome_frequency (optional)
            if (json.has("biome_frequency")) {
                Map<String, Double> biomeFrequency = parseBiomeFrequency(json.get("biome_frequency").getAsJson5Object(), warnings);
                builder.biomeFrequency(biomeFrequency);
            }

            builder.validationWarnings(warnings);

            return builder.build();

        } catch (ConfigParseException e) {
            throw e; // Re-throw our own exceptions
        } catch (Exception e) {
            throw new ConfigParseException("Failed to parse config: " + e.getMessage(), e);
        }
    }

    /**
     * Parses structure_pool array into RawConfigEntry list.
     * Validates biome weights (must be > 0), collects warnings for invalid entries.
     */
    private static List<MVSConfig.RawConfigEntry> parseStructurePool(Json5Array poolArray, List<String> warnings) throws ConfigParseException {
        List<MVSConfig.RawConfigEntry> rawPool = new ArrayList<>();

        int entryIndex = 0;
        for (Json5Element poolElement : poolArray) {
            Json5Object entryObj = poolElement.getAsJson5Object();
            entryIndex++;

            // Validate: ONE OF empty or structure
            boolean hasEmpty = entryObj.has("empty") && entryObj.get("empty").getAsBoolean();
            boolean hasStructure = entryObj.has("structure");

            if (!hasEmpty && !hasStructure) {
                // Skip invalid entries (will be logged by caller)
                continue;
            }

            // Parse biomes (REQUIRED)
            if (!entryObj.has("biomes")) {
                throw new ConfigParseException(
                    "Entry missing required 'biomes' field. " +
                    "All entries must specify biomes."
                );
            }

            Map<String, Integer> biomes = new HashMap<>();
            Json5Object biomesObj = entryObj.get("biomes").getAsJson5Object();

            String structureName = hasStructure ? entryObj.get("structure").getAsString() : "(empty)";

            for (String tag : biomesObj.keySet()) {
                int weight = biomesObj.get(tag).getAsInt();

                // Validate: weight must be > 0
                if (weight <= 0) {
                    warnings.add(String.format(
                        "structure_pool entry #%d (%s): biome '%s' has invalid weight %d (must be > 0) - entry skipped",
                        entryIndex, structureName, tag, weight
                    ));
                    continue; // Skip this biome tag entry
                }

                biomes.put(tag, weight);
            }

            if (biomes.isEmpty()) {
                warnings.add(String.format(
                    "structure_pool entry #%d (%s): all biome weights invalid (must be > 0) - entry skipped",
                    entryIndex, structureName
                ));
                continue; // Skip entire entry
            }

            // Create raw config entry
            MVSConfig.RawConfigEntry rawEntry = new MVSConfig.RawConfigEntry(
                hasEmpty,
                hasStructure ? entryObj.get("structure").getAsString() : null,
                biomes
            );

            rawPool.add(rawEntry);
        }

        return rawPool;
    }

    /**
     * Parses JSON array into String list.
     */
    private static List<String> parseStringArray(Json5Array array) {
        List<String> result = new ArrayList<>();
        for (Json5Element element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    /**
     * Parses biome_frequency object into Map<String, Double>.
     * Validates frequency range (0.0 to 1.0), collects warnings for invalid entries.
     * Format: { "pattern": frequency, ... }
     * Example: { "#minecraft:is_plains": 1.0, "#*:*": 0.5 }
     */
    private static Map<String, Double> parseBiomeFrequency(Json5Object frequencyObj, List<String> warnings) throws ConfigParseException {
        Map<String, Double> result = new LinkedHashMap<>();

        for (String pattern : frequencyObj.keySet()) {
            double frequency = frequencyObj.get(pattern).getAsDouble();

            // Validate frequency range (0.0 to 1.0)
            if (frequency < 0.0 || frequency > 1.0) {
                warnings.add(String.format(
                    "biome_frequency pattern '%s' has invalid frequency %.2f (must be 0.0-1.0) - entry skipped",
                    pattern, frequency
                ));
                continue; // Skip invalid entry
            }

            result.put(pattern, frequency);
        }

        return result;
    }

    /**
     * Custom exception for config parsing errors.
     */
    public static class ConfigParseException extends Exception {
        public ConfigParseException(String message) {
            super(message);
        }

        public ConfigParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
