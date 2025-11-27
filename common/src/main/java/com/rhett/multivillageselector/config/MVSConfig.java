package com.rhett.multivillageselector.config;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.util.PatternMatcher;
import com.rhett.multivillageselector.util.BiomeTagExpander;
import com.rhett.multivillageselector.strategy.StructurePicker;

import de.marhali.json5.Json5;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Primitive;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import dev.architectury.platform.Platform;

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
    public static boolean debugLogging = false;
    public static boolean debugCmd = false; // Enables /mvs debug commands (advanced users only)
    public static boolean showLaunchMessage = false; // Default false, only true in bundled default

    // v0.3.0 config fields
    public static List<String> blockStructureSets = new ArrayList<>();
    public static List<String> interceptStructureSets = new ArrayList<>();
    public static List<RawConfigEntry> structurePoolRaw = new ArrayList<>();
    public static List<ConfiguredStructure> structurePool = new ArrayList<>();
    public static List<String> blacklistedStructures = new ArrayList<>();
    public static Map<String, Double> biomeFrequency = new LinkedHashMap<>();

    // Track whether structures have been discovered yet
    private static boolean structuresDiscovered = false;

    /**
     * v0.3.0 Raw config entry (before pattern expansion)
     * One of: empty, structure, pattern (mutually exclusive)
     * biomes is REQUIRED - must be explicitly specified in config
     */
    public static class RawConfigEntry {
        public final boolean isEmpty;
        public final String structure;    // Can be null if empty; supports wildcards (*)
        public final Map<String, Integer> biomes;  // Required, never null

        public RawConfigEntry(boolean isEmpty, String structure,
                              Map<String, Integer> biomes) {
            this.isEmpty = isEmpty;
            this.structure = structure;
            this.biomes = biomes != null ? biomes : new HashMap<>();
        }
    }

    /**
     * v0.3.0 Configured structure (after pattern expansion and tag resolution)
     * Weight is per-biome-tag, not structure-wide
     */
    public static class ConfiguredStructure {
        public final ResourceLocation structure;  // null if isEmpty
        public final boolean isEmpty;
        public final Map<String, Integer> _biomes;  // Original patterns (before expansion)
        public final Map<String, Integer> biomes;   // Expanded tags (after pattern expansion)

        public ConfiguredStructure(ResourceLocation structure,
                                   Map<String, Integer> originalBiomeTags,
                                   Map<String, Integer> expandedBiomeTags) {
            this.structure = structure;
            this.isEmpty = false;
            this._biomes = originalBiomeTags != null ? originalBiomeTags : new HashMap<>();
            this.biomes = expandedBiomeTags != null ? expandedBiomeTags : new HashMap<>();
        }

        public ConfiguredStructure(Map<String, Integer> originalBiomeTags,
                                   Map<String, Integer> expandedBiomeTags) {
            // Empty entry constructor
            this.structure = null;
            this.isEmpty = true;
            this._biomes = originalBiomeTags != null ? originalBiomeTags : new HashMap<>();
            this.biomes = expandedBiomeTags != null ? expandedBiomeTags : new HashMap<>();
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
            // Step 1: Load config file (I/O only)
            ConfigLoader.LoadResult loadResult = ConfigLoader.loadOrCreate();

            // Step 2: Parse config content (logic only)
            ConfigState config = ConfigParser.parse(loadResult.content);

            // Step 3: Copy to static fields (backward compatibility)
            enabled = config.enabled;
            debugLogging = config.debugLogging;
            debugCmd = config.debugCmd;
            showLaunchMessage = config.showLaunchMessage;
            blockStructureSets = new ArrayList<>(config.blockStructureSets);
            interceptStructureSets = new ArrayList<>(config.interceptStructureSets);
            structurePoolRaw = new ArrayList<>(config.structurePoolRaw);
            blacklistedStructures = new ArrayList<>(config.blacklistedStructures);
            biomeFrequency = new LinkedHashMap<>(config.biomeFrequency);

            // Step 4: Log validation warnings (always, regardless of debug_logging)
            if (!config.validationWarnings.isEmpty()) {
                MVSCommon.LOGGER.warn("╔════════════════════════════════════════════════════════╗");
                MVSCommon.LOGGER.warn("║  ⚠️  CONFIG VALIDATION WARNINGS                       ║");
                MVSCommon.LOGGER.warn("╠════════════════════════════════════════════════════════╣");
                for (String warning : config.validationWarnings) {
                    MVSCommon.LOGGER.warn("║  " + warning);
                }
                MVSCommon.LOGGER.warn("╚════════════════════════════════════════════════════════╝");
                MVSCommon.LOGGER.warn("");
            }

            // Step 5: Log configuration summary
            logConfigSummary(config);

        } catch (ConfigParser.ConfigParseException e) {
            MVSCommon.LOGGER.error("╔════════════════════════════════════════════════════════╗");
            MVSCommon.LOGGER.error("║  ❌ FAILED TO PARSE MVS CONFIG                        ║");
            MVSCommon.LOGGER.error("╠════════════════════════════════════════════════════════╣");
            MVSCommon.LOGGER.error("║  {}", String.format("%-54s", e.getMessage()) + "║");
            MVSCommon.LOGGER.error("║                                                        ║");
            MVSCommon.LOGGER.error("║  Fix: Check the error message above                   ║");
            MVSCommon.LOGGER.error("║  Or: Delete config file and restart to regenerate     ║");
            MVSCommon.LOGGER.error("║  Or: Run /mvs generate to create a fresh config       ║");
            MVSCommon.LOGGER.error("║                                                        ║");
            MVSCommon.LOGGER.error("║  ⚠️  MOD DISABLED UNTIL CONFIG IS FIXED               ║");
            MVSCommon.LOGGER.error("╚════════════════════════════════════════════════════════╝");
            enabled = false;
        } catch (Exception e) {
            MVSCommon.LOGGER.error("╔════════════════════════════════════════════════════════╗");
            MVSCommon.LOGGER.error("║  ❌ FAILED TO LOAD MVS CONFIG                         ║");
            MVSCommon.LOGGER.error("╠════════════════════════════════════════════════════════╣");
            MVSCommon.LOGGER.error("║  Your config file is MALFORMED and cannot be parsed.  ║");
            MVSCommon.LOGGER.error("║                                                        ║");
            MVSCommon.LOGGER.error("║  Common issues:                                        ║");
            MVSCommon.LOGGER.error("║  - Missing commas between entries                      ║");
            MVSCommon.LOGGER.error("║  - Mismatched brackets {{ }} or [ ]                   ║");
            MVSCommon.LOGGER.error("║  - Wrong type (number instead of array, etc)          ║");
            MVSCommon.LOGGER.error("║  - Invalid JSON5 syntax                                ║");
            MVSCommon.LOGGER.error("║                                                        ║");
            MVSCommon.LOGGER.error("║  Fix: Delete config file and restart to regenerate    ║");
            MVSCommon.LOGGER.error("║  Or: Run /mvs generate to create a fresh config       ║");
            MVSCommon.LOGGER.error("║                                                        ║");
            MVSCommon.LOGGER.error("║  Config location: config/multivillageselector.json5   ║");
            MVSCommon.LOGGER.error("║                                                        ║");
            MVSCommon.LOGGER.error("║  ⚠️  MOD DISABLED UNTIL CONFIG IS FIXED               ║");
            MVSCommon.LOGGER.error("╚════════════════════════════════════════════════════════╝");
            MVSCommon.LOGGER.error("Parse error details:", e);
            enabled = false;
        }
    }

    /**
     * Logs configuration summary (extracted for readability).
     * INIT level: Always shown, concise summary.
     * DEBUG level: Detailed lists (behind debugLogging flag).
     */
    private static void logConfigSummary(ConfigState config) {
        // INIT: Clean one-line summary
        String interceptList = String.join(", ", config.interceptStructureSets);
        MVSCommon.LOGGER.info("[MVS] Config loaded: intercepting [{}], {} pool entries, {} blocked, {} blacklisted",
            interceptList,
            config.structurePoolRaw.size(),
            config.blockStructureSets.size(),
            config.blacklistedStructures.size());

        // DEBUG: Detailed breakdown (only if debug_logging enabled)
        if (debugLogging) {
            MVSCommon.LOGGER.info("[MVS] Debug: {} biome_frequency rules", config.biomeFrequency.size());

            // Log blocked structure_sets
            if (!config.blockStructureSets.isEmpty()) {
                MVSCommon.LOGGER.info("[MVS] Debug: Blocked structure_sets:");
                List<String> sortedBlocked = new ArrayList<>(config.blockStructureSets);
                Collections.sort(sortedBlocked);
                for (String structureSet : sortedBlocked) {
                    MVSCommon.LOGGER.info("[MVS]   → {}", structureSet);
                }
            }

            // Log blacklisted structures
            if (!config.blacklistedStructures.isEmpty()) {
                MVSCommon.LOGGER.info("[MVS] Debug: Blacklisted structures:");
                List<String> sortedBlacklisted = new ArrayList<>(config.blacklistedStructures);
                Collections.sort(sortedBlacklisted);
                for (String structure : sortedBlacklisted) {
                    MVSCommon.LOGGER.info("[MVS]   → {}", structure);
                }
            }
        }
    }

    /**
     * Converts a wildcard pattern to a regex pattern.
     * Examples:
     *   "ctov:*" → "ctov:.*"
     *   "*:village_*" → ".*:village_.*"
     *   "mod:exact_name" → "mod:exact_name"
     */
    /**
     * Checks if a structure name matches a wildcard pattern.
     * @deprecated Use PatternMatcher.matches() directly for better testability
     */
    public static boolean matchesPattern(String structureName, String pattern) {
        return PatternMatcher.matches(structureName, pattern);
    }

    /**
     * v0.3.0: Discovers structures and builds the final ConfiguredStructure pool.
     * Phase 1: Expand patterns against registry
     * Phase 2: Get biome tags from structures
     * Phase 3: Apply add_biomes/replace_biomes overrides
     * Phase 4: Expand biome tag patterns
     * Phase 5: Merge duplicates (last wins)
     *
     * Must be called AFTER registries are available (during FMLCommonSetupEvent).
     */
    public static void discoverStructures(net.minecraft.core.RegistryAccess registryAccess, Registry<Structure> structureRegistry) {
        if (structuresDiscovered) {
            if (debugLogging) {
                MVSCommon.LOGGER.info("[MVS] Debug: Structure discovery already completed, skipping");
            }
            return;
        }

        if (debugLogging) {
            MVSCommon.LOGGER.info("[MVS] Debug: Starting structure discovery...");
        }

        // Get all available structures
        Set<ResourceLocation> allStructures = structureRegistry.keySet();

        // Phase 1 & 2: Expand patterns and build ConfiguredStructure list
        List<ConfiguredStructure> expandedList = new ArrayList<>();
        int patternCount = 0;
        int emptyCount = 0;

        for (RawConfigEntry rawEntry : structurePoolRaw) {
            if (rawEntry.isEmpty) {
                // Empty entry - preserve original and expand biome tag patterns
                Map<String, Integer> originalBiomeTags = rawEntry.biomes != null ?
                    new LinkedHashMap<>(rawEntry.biomes) : new LinkedHashMap<>();

                // Expand any patterns in biome tags
                Map<String, Integer> expandedBiomeTags = expandBiomeTagPatterns(originalBiomeTags, registryAccess);

                expandedList.add(new ConfiguredStructure(originalBiomeTags, expandedBiomeTags));
                emptyCount++;
                if (debugLogging) {
                    MVSCommon.LOGGER.info("[MVS] Debug: Empty entry: {} original tags, {} after expansion",
                        originalBiomeTags.size(), expandedBiomeTags.size());
                }

            } else if (rawEntry.structure != null) {
                // Auto-detect: wildcards (*) = pattern, otherwise = exact structure
                if (rawEntry.structure.contains("*")) {
                    // Pattern - expand to all matching structures
                    int matchCount = 0;
                    for (ResourceLocation structureLoc : allStructures) {
                        if (PatternMatcher.matches(structureLoc.toString(), rawEntry.structure)) {
                            ConfiguredStructure configured = buildConfiguredStructure(
                                structureLoc, registryAccess, structureRegistry, rawEntry);
                            if (configured != null) {
                                expandedList.add(configured);
                                matchCount++;
                            }
                        }
                    }
                    patternCount++;
                    if (debugLogging) {
                        MVSCommon.LOGGER.info("[MVS] Debug: Pattern '{}' matched {} structures", rawEntry.structure, matchCount);
                    }
                    if (matchCount == 0) {
                        // WARNING: Pattern matched nothing - user should know
                        MVSCommon.LOGGER.warn("[MVS] Pattern '{}' matched 0 structures - check if mod is installed", rawEntry.structure);
                    }
                } else {
                    // Specific structure
                    ResourceLocation structureLoc = ResourceLocation.parse(rawEntry.structure);
                    ConfiguredStructure configured = buildConfiguredStructure(
                        structureLoc, registryAccess, structureRegistry, rawEntry);
                    if (configured != null) {
                        expandedList.add(configured);
                        if (debugLogging) {
                            MVSCommon.LOGGER.info("[MVS] Debug: Structure: {}", structureLoc);
                        }
                    }
                }
            }
        }

        // Phase 3: Merge duplicates (last wins)
        Map<String, ConfiguredStructure> merged = new LinkedHashMap<>();
        for (ConfiguredStructure entry : expandedList) {
            String key = entry.isEmpty ? "EMPTY_" + System.identityHashCode(entry) : entry.structure.toString();
            merged.put(key, entry);
        }

        structurePool = new ArrayList<>(merged.values());

        structuresDiscovered = true;

        // INIT: Clean summary
        MVSCommon.LOGGER.info("[MVS] Structure discovery complete: {} structures ready", structurePool.size());
    }

    /**
     * Builds a ConfiguredStructure from a RawConfigEntry, expanding biome tag patterns
     * biomes must be explicitly provided in config (required field)
     */
    private static ConfiguredStructure buildConfiguredStructure(
            ResourceLocation structureLoc,
            net.minecraft.core.RegistryAccess registryAccess,
            Registry<Structure> structureRegistry,
            RawConfigEntry rawEntry) {

        Structure structure = structureRegistry.get(structureLoc);
        if (structure == null) {
            // WARNING: Structure from config not found - always show
            MVSCommon.LOGGER.warn("[MVS] Structure '{}' not found in registry - check if mod is installed", structureLoc);
            return null;
        }

        // Preserve original biomes (before expansion)
        Map<String, Integer> originalBiomeTags = new LinkedHashMap<>(rawEntry.biomes);

        // Expand any patterns in biome tags (#minecraft:is_*, #*:*, etc.)
        // Processes in specificity order: #*:* < #minecraft:* < #minecraft:is_plains
        // Returns NEW map (no side effects)
        Map<String, Integer> expandedBiomeTags = expandBiomeTagPatterns(originalBiomeTags, registryAccess);

        // DEBUG: Check for expansion bug (should never happen)
        if (expandedBiomeTags.containsKey("#*:*")) {
            MVSCommon.LOGGER.warn("[MVS] BUG: #*:* still present after expansion for {} - please report!", structureLoc);
        }

        return new ConfiguredStructure(structureLoc, originalBiomeTags, expandedBiomeTags);
    }

    /**
     * Extract biome tags from a structure's biomes() HolderSet
     * Returns map of tag → weight (all tags get the same weight from structure)
     */
    private static Map<String, Integer> extractBiomeTagsFromStructure(Structure structure, int weight) {
        Map<String, Integer> biomes = new HashMap<>();

        // Get the structure's biome holder set
        var biomeHolderSet = structure.biomes();

        // Iterate through all biomes this structure can spawn in
        for (Holder<Biome> biomeHolder : biomeHolderSet) {
            // Get all tags for this biome
            biomeHolder.tags().forEach(tagKey -> {
                // Format: #namespace:path
                String tagString = "#" + tagKey.location().toString();
                // All tags get the structure's weight
                biomes.put(tagString, weight);
            });
        }

        if (biomes.isEmpty()) {
            MVSCommon.LOGGER.debug("    Structure has no biome tags (may be dimension-specific or always-spawn)");
        }

        return biomes;
    }

    /**
     * Expand biome tag patterns like #minecraft:is_* to concrete tags
     * Pre-expands at discovery time for O(1) runtime lookups
     *
     * Delegates to BiomeTagExpander for testable logic.
     *
     * Processes patterns in specificity order (vague → specific) so that
     * more specific entries override vague ones:
     * 1. #*:* (most vague) - matches everything
     * 2. #minecraft:*, #*:plains* (medium) - partial wildcards
     * 3. #minecraft:is_plains (most specific) - literals override
     */
    private static Map<String, Integer> expandBiomeTagPatterns(
            Map<String, Integer> biomes,
            net.minecraft.core.RegistryAccess registryAccess) {

        // Get all biome tags from registry
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
        Set<ResourceLocation> allBiomeTags = new HashSet<>();

        // Collect all unique biome tags across all biomes
        for (Holder<Biome> biomeHolder : biomeRegistry.holders().toList()) {
            biomeHolder.tags().forEach(tagKey -> {
                allBiomeTags.add(tagKey.location());
            });
        }

        // Delegate to pure utility class (testable!)
        BiomeTagExpander.ExpansionResult result = BiomeTagExpander.expandWithStats(biomes, allBiomeTags);

        // Log expansion results
        for (Map.Entry<String, Integer> entry : result.expansionCounts.entrySet()) {
            String pattern = entry.getKey();
            int count = entry.getValue();

            if (pattern.contains("*")) {
                if (count > 0) {
                    // DEBUG: Successful expansion details
                    if (debugLogging) {
                        MVSCommon.LOGGER.info("[MVS] Debug: Expanded biome pattern '{}' → {} tags",
                            pattern, count);
                    }
                } else {
                    // WARNING: Pattern matched nothing - always show
                    MVSCommon.LOGGER.warn("[MVS] Biome pattern '{}' matched 0 tags - check pattern syntax", pattern);
                }
            }
        }

        return result.expandedTags;
    }

    /**
     * v0.3.0: Filter structures by biome, then select using weighted random
     * Implements the MVS strategy (filter-first, only strategy)
     * Delegates to StructurePicker for testable business logic.
     */
    public static ConfiguredStructure selectStructure(Random random, Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
        StructurePicker picker = new StructurePicker(structurePool);
        ConfiguredStructure selected = picker.select(random, biomeHolder);

        if (selected == null && debugLogging) {
            MVSCommon.LOGGER.info("  No structures match biome tags - no spawn");
        }

        return selected;
    }

}
