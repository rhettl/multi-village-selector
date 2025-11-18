package com.rhett.multivillageselector;

import de.marhali.json5.Json5;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Primitive;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class MVSConfig {
    private static final Json5 JSON5 = Json5.builder(builder -> builder
        .quoteSingle()  // Allow single quotes
        .trailingComma() // Allow trailing commas
        .build());

    public static boolean enabled = true;
    public static List<String> replaceOfPatterns = new ArrayList<>();
    public static List<String> preventSpawnPatterns = new ArrayList<>();
    public static Map<String, List<ReplacementEntry>> replaceWithRaw = new HashMap<>();
    public static Map<String, List<WeightedStructure>> biomeReplacements = new HashMap<>();
    public static Map<String, String> biomeCategoryOverrides = new HashMap<>();
    public static boolean debugLogging = false;

    // Track whether structures have been discovered yet
    private static boolean structuresDiscovered = false;

    public static class ReplacementEntry {
        public final String pattern; // Can be null if this is a specific structure
        public final ResourceLocation structure; // Can be null if this is a pattern
        public final int weight;
        public final boolean isPattern;
        public final boolean isEmpty;

        private ReplacementEntry(String pattern, ResourceLocation structure, int weight, boolean isPattern, boolean isEmpty) {
            this.pattern = pattern;
            this.structure = structure;
            this.weight = weight;
            this.isPattern = isPattern;
            this.isEmpty = isEmpty;
        }

        public static ReplacementEntry pattern(String pattern, int weight) {
            return new ReplacementEntry(pattern, null, weight, true, false);
        }

        public static ReplacementEntry structure(ResourceLocation structure, int weight) {
            return new ReplacementEntry(null, structure, weight, false, false);
        }

        public static ReplacementEntry empty(int weight) {
            return new ReplacementEntry(null, null, weight, false, true);
        }
    }

    public static class WeightedStructure {
        public final ResourceLocation structure;
        public final int weight;
        public final boolean isEmpty;

        public WeightedStructure(ResourceLocation structure, int weight) {
            this.structure = structure;
            this.weight = weight;
            this.isEmpty = false;
        }

        public WeightedStructure(int weight) {
            this.structure = null;
            this.weight = weight;
            this.isEmpty = true;
        }

        public static WeightedStructure empty(int weight) {
            return new WeightedStructure(weight);
        }
    }

    public static void load() {
        try {
            // Get the config directory path
            Path configDir = FMLPaths.CONFIGDIR.get();
            MultiVillageSelector.LOGGER.info("MVS: Config directory: {}", configDir);

            Path configFile = configDir.resolve("multivillageselector.json5");
            MultiVillageSelector.LOGGER.info("MVS: Looking for config at: {}", configFile);

            // If config doesn't exist, create it from the default bundled in the mod
            if (!Files.exists(configFile)) {
                MultiVillageSelector.LOGGER.info("MVS: Config file not found, creating default...");

                // Ensure config directory exists
                if (!Files.exists(configDir)) {
                    MultiVillageSelector.LOGGER.info("MVS: Creating config directory: {}", configDir);
                    Files.createDirectories(configDir);
                }

                InputStream defaultConfig = MVSConfig.class.getResourceAsStream("/mvs_config.json5");
                if (defaultConfig == null) {
                    MultiVillageSelector.LOGGER.error("MVS: Could not find default mvs_config.json5 in mod resources!");
                    MultiVillageSelector.LOGGER.error("MVS: This is a critical error - the mod cannot function without config!");
                    return;
                }

                // Copy default config to config directory
                MultiVillageSelector.LOGGER.info("MVS: Copying default config to: {}", configFile);
                Files.copy(defaultConfig, configFile);
                defaultConfig.close();

                MultiVillageSelector.LOGGER.info("MVS: Successfully created default config at: {}", configFile);
            } else {
                MultiVillageSelector.LOGGER.info("MVS: Found existing config file");
            }

            // Load the config from the config directory
            MultiVillageSelector.LOGGER.info("MVS: Loading config from: {}", configFile);

            // Load JSON5 using json5-java
            String json5Content = Files.readString(configFile, StandardCharsets.UTF_8);
            Json5Object json = JSON5.parse(json5Content).getAsJson5Object();

            enabled = json.get("enabled").getAsBoolean();
            debugLogging = json.get("debug_logging").getAsBoolean();

            // Load biome category overrides if present
            if (json.has("biome_category_overrides")) {
                Json5Object overrides = json.get("biome_category_overrides").getAsJson5Object();
                for (String key : overrides.keySet()) {
                    biomeCategoryOverrides.put(key, overrides.get(key).getAsString());
                }
            }

            // Load replace_of patterns
            Json5Array replaceOf = json.get("replace_of").getAsJson5Array();
            for (Json5Element element : replaceOf) {
                replaceOfPatterns.add(element.getAsString());
            }

            // Load prevent_spawn patterns (optional, for blocking modded villages)
            if (json.has("prevent_spawn")) {
                Json5Array preventSpawn = json.get("prevent_spawn").getAsJson5Array();
                for (Json5Element element : preventSpawn) {
                    preventSpawnPatterns.add(element.getAsString());
                }
            }

            // Load replace_with entries (raw, before structure discovery)
            Json5Object replaceWith = json.get("replace_with").getAsJson5Object();
            for (String biomeCategory : replaceWith.keySet()) {
                Json5Array entries = replaceWith.get(biomeCategory).getAsJson5Array();

                List<ReplacementEntry> replacementEntries = new ArrayList<>();
                for (Json5Element entryElement : entries) {
                    Json5Object entryObj = entryElement.getAsJson5Object();
                    int weight = entryObj.get("weight").getAsInt();

                    if (entryObj.has("empty") && entryObj.get("empty").getAsBoolean()) {
                        // This is an empty entry (weighted no-spawn)
                        replacementEntries.add(ReplacementEntry.empty(weight));
                    } else if (entryObj.has("pattern")) {
                        // This is a pattern entry
                        String pattern = entryObj.get("pattern").getAsString();
                        replacementEntries.add(ReplacementEntry.pattern(pattern, weight));
                    } else if (entryObj.has("structure")) {
                        // This is a specific structure entry
                        String structureName = entryObj.get("structure").getAsString();
                        ResourceLocation structureLocation = ResourceLocation.parse(structureName);
                        replacementEntries.add(ReplacementEntry.structure(structureLocation, weight));
                    }
                }

                replaceWithRaw.put(biomeCategory, replacementEntries);
            }

            MultiVillageSelector.LOGGER.info("Loaded MVS configuration with {} biome categories", replaceWithRaw.size());
            MultiVillageSelector.LOGGER.info("Loaded {} replace_of patterns", replaceOfPatterns.size());
            MultiVillageSelector.LOGGER.info("Loaded {} prevent_spawn patterns", preventSpawnPatterns.size());

        } catch (Exception e) {
            MultiVillageSelector.LOGGER.error("Failed to load MVS config", e);
        }
    }

    /**
     * Converts a wildcard pattern to a regex pattern.
     * Examples:
     *   "ctov:*" → "ctov:.*"
     *   "*:village_*" → ".*:village_.*"
     *   "mod:exact_name" → "mod:exact_name"
     */
    private static Pattern wildcardToRegex(String wildcardPattern) {
        String regex = wildcardPattern
                .replace(".", "\\.")  // Escape dots
                .replace("*", ".*");   // Convert * to .*
        return Pattern.compile("^" + regex + "$");
    }

    /**
     * Checks if a structure name matches a wildcard pattern.
     */
    public static boolean matchesPattern(String structureName, String pattern) {
        Pattern regex = wildcardToRegex(pattern);
        return regex.matcher(structureName).matches();
    }

    /**
     * Discovers structures from the registry and builds the final weighted structure lists.
     * This implements the two-phase algorithm:
     *   Phase 1: Build pattern → weight map (later entries override earlier)
     *   Phase 2: Resolve structures (most specific pattern wins)
     *
     * Must be called AFTER registries are available (during FMLCommonSetupEvent).
     */
    public static void discoverStructures(Registry<Structure> structureRegistry) {
        if (structuresDiscovered) {
            MultiVillageSelector.LOGGER.warn("Structure discovery already completed, skipping");
            return;
        }

        MultiVillageSelector.LOGGER.info("===== MVS: DISCOVERING STRUCTURES =====");

        // Get all available structure names
        Set<ResourceLocation> allStructures = structureRegistry.keySet();
        MultiVillageSelector.LOGGER.info("Found {} total structures in registry", allStructures.size());

        // Process each biome category
        for (Map.Entry<String, List<ReplacementEntry>> entry : replaceWithRaw.entrySet()) {
            String biomeCategory = entry.getKey();
            List<ReplacementEntry> entries = entry.getValue();

            MultiVillageSelector.LOGGER.info("Processing biome category: {}", biomeCategory);

            // Phase 1: Build pattern → weight map (later entries override earlier)
            Map<String, Integer> patternWeights = new LinkedHashMap<>();
            List<WeightedStructure> explicitStructures = new ArrayList<>();

            for (ReplacementEntry replacementEntry : entries) {
                if (replacementEntry.isEmpty) {
                    explicitStructures.add(WeightedStructure.empty(replacementEntry.weight));
                    MultiVillageSelector.LOGGER.info("  Empty: (no spawn) → weight {}", replacementEntry.weight);
                } else if (replacementEntry.isPattern) {
                    patternWeights.put(replacementEntry.pattern, replacementEntry.weight);
                    MultiVillageSelector.LOGGER.info("  Pattern: {} → weight {}", replacementEntry.pattern, replacementEntry.weight);
                } else {
                    explicitStructures.add(new WeightedStructure(replacementEntry.structure, replacementEntry.weight));
                    MultiVillageSelector.LOGGER.info("  Explicit: {} → weight {}", replacementEntry.structure, replacementEntry.weight);
                }
            }

            // Phase 2: Resolve structures (most specific pattern wins)
            List<WeightedStructure> resolvedStructures = new ArrayList<>();

            for (ResourceLocation structure : allStructures) {
                String structureName = structure.toString();

                // Check if any pattern matches this structure
                String bestMatchPattern = null;
                int bestMatchLength = -1;

                for (String pattern : patternWeights.keySet()) {
                    if (matchesPattern(structureName, pattern)) {
                        // More specific patterns win (longer pattern = more specific)
                        int patternLength = pattern.replace("*", "").length();
                        if (patternLength > bestMatchLength) {
                            bestMatchPattern = pattern;
                            bestMatchLength = patternLength;
                        }
                    }
                }

                if (bestMatchPattern != null) {
                    int weight = patternWeights.get(bestMatchPattern);
                    resolvedStructures.add(new WeightedStructure(structure, weight));
                    if (debugLogging) {
                        MultiVillageSelector.LOGGER.info("    Matched: {} via pattern {} → weight {}",
                            structureName, bestMatchPattern, weight);
                    }
                }
            }

            // Add explicit structures (they override patterns)
            resolvedStructures.addAll(explicitStructures);

            // Store the final list
            biomeReplacements.put(biomeCategory, resolvedStructures);
            MultiVillageSelector.LOGGER.info("  Total structures for {}: {}", biomeCategory, resolvedStructures.size());
        }

        structuresDiscovered = true;
        MultiVillageSelector.LOGGER.info("===== MVS: STRUCTURE DISCOVERY COMPLETE =====");
    }

    public static WeightedStructure getRandomReplacement(String biomeCategory, Random random) {
        // Try the specific category first
        List<WeightedStructure> structures = biomeReplacements.get(biomeCategory);

        // Fall back to DEFAULT if no structures found
        if ((structures == null || structures.isEmpty()) && biomeReplacements.containsKey("DEFAULT")) {
            structures = biomeReplacements.get("DEFAULT");
            if (debugLogging) {
                MultiVillageSelector.LOGGER.info("No structures for category {}, using DEFAULT", biomeCategory);
            }
        }

        if (structures == null || structures.isEmpty()) {
            return null;
        }

        int totalWeight = structures.stream().mapToInt(s -> s.weight).sum();
        int randomWeight = random.nextInt(totalWeight);

        int currentWeight = 0;
        for (WeightedStructure structure : structures) {
            currentWeight += structure.weight;
            if (randomWeight < currentWeight) {
                return structure;
            }
        }

        return structures.get(0); // Fallback
    }
}
