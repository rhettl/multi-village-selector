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

            // Parse relaxed_biome_validation (optional, defaults to false)
            // When true, bypasses vanilla's biome check at structure placement point
            // Useful for 3D biome mods like Terralith where terrain adaptation shifts structures
            if (json.has("relaxed_biome_validation")) {
                builder.relaxedBiomeValidation(json.get("relaxed_biome_validation").getAsBoolean());
            }

            // Parse placement (optional)
            if (json.has("placement")) {
                Map<String, PlacementRule> placement = parsePlacement(json.get("placement").getAsJson5Object(), warnings);
                builder.placement(placement);
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
     * Valid spread types for placement configuration.
     */
    private static final java.util.Set<String> VALID_SPREAD_TYPES = java.util.Set.of(
        "linear", "triangular", "edge_biased", "corner_biased", "gaussian", "fixed_center"
    );

    /**
     * Valid strategy types for placement configuration.
     */
    private static final java.util.Set<String> VALID_STRATEGIES = java.util.Set.of(
        "random_spread", "concentric_rings"
    );

    /**
     * Parses placement object into Map<String, PlacementRule>.
     * Validates placement values, collects warnings for invalid entries.
     * Format: { "structure_set_id": { spacing: N, separation: N, salt: N, spreadType: "type", strategy: "type" }, ... }
     */
    private static Map<String, PlacementRule> parsePlacement(Json5Object placementObj, List<String> warnings) throws ConfigParseException {
        Map<String, PlacementRule> result = new LinkedHashMap<>();

        for (String structureSetId : placementObj.keySet()) {
            Json5Element ruleElement = placementObj.get(structureSetId);

            // Handle empty object {} - inherit everything
            if (!ruleElement.isJson5Object()) {
                warnings.add(String.format(
                    "placement[%s]: expected object, got %s - entry skipped",
                    structureSetId, ruleElement.getClass().getSimpleName()
                ));
                continue;
            }

            Json5Object ruleObj = ruleElement.getAsJson5Object();
            PlacementRule.Builder ruleBuilder = new PlacementRule.Builder();

            // Parse spacing (optional)
            if (ruleObj.has("spacing")) {
                try {
                    int spacing = ruleObj.get("spacing").getAsInt();
                    if (spacing <= 0) {
                        warnings.add(String.format(
                            "placement[%s].spacing: %d is invalid (must be > 0) - using inherited value",
                            structureSetId, spacing
                        ));
                    } else {
                        ruleBuilder.spacing(spacing);
                    }
                } catch (Exception e) {
                    warnings.add(String.format(
                        "placement[%s].spacing: invalid type (expected integer) - using inherited value",
                        structureSetId
                    ));
                }
            }

            // Parse separation (optional)
            if (ruleObj.has("separation")) {
                try {
                    int separation = ruleObj.get("separation").getAsInt();
                    if (separation < 0) {
                        warnings.add(String.format(
                            "placement[%s].separation: %d is invalid (must be >= 0) - using inherited value",
                            structureSetId, separation
                        ));
                    } else {
                        ruleBuilder.separation(separation);
                    }
                } catch (Exception e) {
                    warnings.add(String.format(
                        "placement[%s].separation: invalid type (expected integer) - using inherited value",
                        structureSetId
                    ));
                }
            }

            // Parse salt (optional)
            if (ruleObj.has("salt")) {
                Json5Element saltElement = ruleObj.get("salt");
                try {
                    // Handle empty string as invalid
                    if (saltElement.isJson5Primitive() && saltElement.getAsJson5Primitive().isString()) {
                        String saltStr = saltElement.getAsString();
                        if (saltStr.isEmpty()) {
                            warnings.add(String.format(
                                "placement[%s].salt: empty string is invalid - using inherited value",
                                structureSetId
                            ));
                        } else {
                            // Try to parse string as integer
                            try {
                                ruleBuilder.salt(Integer.parseInt(saltStr));
                            } catch (NumberFormatException nfe) {
                                warnings.add(String.format(
                                    "placement[%s].salt: '%s' is not a valid integer - using inherited value",
                                    structureSetId, saltStr
                                ));
                            }
                        }
                    } else {
                        ruleBuilder.salt(saltElement.getAsInt());
                    }
                } catch (Exception e) {
                    warnings.add(String.format(
                        "placement[%s].salt: invalid type (expected integer) - using inherited value",
                        structureSetId
                    ));
                }
            }

            // Parse spreadType (optional)
            if (ruleObj.has("spreadType")) {
                try {
                    String spreadType = ruleObj.get("spreadType").getAsString();
                    if (spreadType.isEmpty()) {
                        warnings.add(String.format(
                            "placement[%s].spreadType: empty string is invalid - using inherited value",
                            structureSetId
                        ));
                    } else if (!VALID_SPREAD_TYPES.contains(spreadType.toLowerCase())) {
                        warnings.add(String.format(
                            "placement[%s].spreadType: '%s' is not valid (expected one of: %s) - using inherited value",
                            structureSetId, spreadType, String.join(", ", VALID_SPREAD_TYPES)
                        ));
                    } else {
                        ruleBuilder.spreadType(spreadType.toLowerCase());
                    }
                } catch (Exception e) {
                    warnings.add(String.format(
                        "placement[%s].spreadType: invalid type (expected string) - using inherited value",
                        structureSetId
                    ));
                }
            }

            // Parse strategy (optional)
            if (ruleObj.has("strategy")) {
                try {
                    String strategy = ruleObj.get("strategy").getAsString();
                    if (strategy.isEmpty()) {
                        warnings.add(String.format(
                            "placement[%s].strategy: empty string is invalid - using inherited value",
                            structureSetId
                        ));
                    } else if (!VALID_STRATEGIES.contains(strategy.toLowerCase())) {
                        warnings.add(String.format(
                            "placement[%s].strategy: '%s' is not valid (expected one of: %s) - using inherited value",
                            structureSetId, strategy, String.join(", ", VALID_STRATEGIES)
                        ));
                    } else {
                        ruleBuilder.strategy(strategy.toLowerCase());
                    }
                } catch (Exception e) {
                    warnings.add(String.format(
                        "placement[%s].strategy: invalid type (expected string) - using inherited value",
                        structureSetId
                    ));
                }
            }

            PlacementRule rule = ruleBuilder.build();

            // Cross-field validation: separation must be < spacing if both specified
            if (rule.spacing != null && rule.separation != null) {
                if (rule.separation >= rule.spacing) {
                    warnings.add(String.format(
                        "placement[%s]: separation (%d) must be less than spacing (%d) - entry skipped",
                        structureSetId, rule.separation, rule.spacing
                    ));
                    continue; // Skip this entry entirely
                }
            }

            result.put(structureSetId, rule);
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
