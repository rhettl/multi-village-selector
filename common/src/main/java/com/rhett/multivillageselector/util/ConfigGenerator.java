package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.commands.model.CommandModels.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Generates MVS configuration files from scanned structure data.
 * Extracted from MVSCommands for testability.
 */
public class ConfigGenerator {

    private static final String MINECRAFT_VILLAGES_SET = "minecraft:villages";
    private static final int TARGET_AVERAGE_WEIGHT = 25;

    /**
     * Result of config generation containing lines and metadata.
     */
    public static class GenerationResult {
        public final List<String> lines;
        public final int modsDetected;
        public final int villagesDetected;
        public final int structureSetsDetected;

        public GenerationResult(List<String> lines, int modsDetected, int villagesDetected, int structureSetsDetected) {
            this.lines = lines;
            this.modsDetected = modsDetected;
            this.villagesDetected = villagesDetected;
            this.structureSetsDetected = structureSetsDetected;
        }
    }

    /**
     * Generate a complete MVS config from scan results.
     *
     * @param server The Minecraft server for registry access
     * @param scan   The scan result from StructureScanner
     * @return GenerationResult containing config lines and metadata
     */
    public static GenerationResult generate(MinecraftServer server, ScanResult scan) {
        List<String> lines = generateConfigLines(server, scan);

        int modsDetected = scan.allStructures.values().stream()
            .map(s -> s.modId)
            .collect(java.util.stream.Collectors.toSet())
            .size();
        int villagesDetected = scan.allStructures.size();
        int structureSetsDetected = scan.structureSetInfo.size();

        return new GenerationResult(lines, modsDetected, villagesDetected, structureSetsDetected);
    }

    /**
     * Generate v0.3.0+ config lines with structure_pool and normalized weights.
     */
    public static List<String> generateConfigLines(MinecraftServer server, ScanResult scan) {
        List<String> lines = new ArrayList<>();

        // Header
        addHeader(lines);

        lines.add("{");
        lines.add("  enabled: true,");
        lines.add("");

        // Calculate normalized weights
        Map<String, Integer> normalizedWeightsByStructure = calculateNormalizedWeights(scan);

        // Group structures by set for block_structure_sets
        Map<String, List<StructureInfo>> likelyBySet = groupBySet(scan.likelyStructures);
        Map<String, List<StructureInfo>> questionableBySet = groupBySet(scan.questionableStructures);
        Map<String, List<StructureInfo>> structuresBySet = groupBySet(getAllFilteredStructures(scan));

        // block_structure_sets section
        addBlockStructureSets(lines, structuresBySet, likelyBySet, questionableBySet);

        // intercept_structure_sets section
        addInterceptStructureSets(lines);

        // structure_pool section
        addStructurePool(lines, scan, normalizedWeightsByStructure, server);

        // blacklisted_structures section
        addBlacklistedStructures(lines);

        // biome_frequency section
        addBiomeFrequency(lines);

        // placement section
        addPlacement(lines, server);

        // relaxed_biome_validation
        addRelaxedBiomeValidation(lines);

        // debug flags
        addDebugFlags(lines);

        lines.add("}");

        return lines;
    }

    private static void addHeader(List<String> lines) {
        lines.add("// ===========================================");
        lines.add("//  Multi Village Selector - Generated Config (v0.3.0)");
        lines.add("//  Generated: " + LocalDateTime.now());
        lines.add("// ===========================================");
        lines.add("//");
        lines.add("// This config uses NORMALIZED WEIGHTS based on mod-intended spawn rates.");
        lines.add("// Weights have been adjusted to:");
        lines.add("//   1. Equalize representation across mods");
        lines.add("//   2. Preserve internal mod ratios (small:medium:large)");
        lines.add("//   3. Account for structure spacing (rarer structures = higher weights)");
        lines.add("//");
        lines.add("// ═══════════════════════════════════════════════════════════════");
        lines.add("//  📋 NEXT STEPS:");
        lines.add("//  1. Review weights below (higher = spawns more often)");
        lines.add("//  2. Use biome_frequency{} to control spawn density per biome");
        lines.add("//  3. Copy to: config/multivillageselector.json5");
        lines.add("//  4. Restart Minecraft");
        lines.add("// ═══════════════════════════════════════════════════════════════");
        lines.add("//");
        lines.add("// 📖 For complete documentation, see:");
        lines.add("//    https://github.com/RhettL/multi-village-selector/blob/master/docs/Configuration.md");
        lines.add("//");
        lines.add("// ═══════════════════════════════════════════════════════════════");
        lines.add("");
    }

    private static List<StructureInfo> getAllFilteredStructures(ScanResult scan) {
        List<StructureInfo> all = new ArrayList<>();
        all.addAll(scan.coreStructures);
        all.addAll(scan.likelyStructures);
        all.addAll(scan.questionableStructures);
        return all;
    }

    private static Map<String, List<StructureInfo>> groupBySet(List<StructureInfo> structures) {
        Map<String, List<StructureInfo>> bySet = new TreeMap<>();
        for (StructureInfo info : structures) {
            String setId = info.getFinalSet();
            if (setId != null && !setId.equals("NONE (uncategorized)")) {
                bySet.computeIfAbsent(setId, k -> new ArrayList<>()).add(info);
            }
        }
        return bySet;
    }

    private static Map<String, Integer> calculateNormalizedWeights(ScanResult scan) {
        Map<String, Integer> normalizedWeightsByStructure = new HashMap<>();
        Map<String, List<StructureInfo>> structuresBySet = groupBySet(getAllFilteredStructures(scan));

        for (Map.Entry<String, List<StructureInfo>> entry : structuresBySet.entrySet()) {
            String setId = entry.getKey();
            List<StructureInfo> setStructures = entry.getValue();
            StructureSetInfo setInfo = scan.structureSetInfo.get(setId);

            // Group structures by mod within this structure_set
            Map<String, List<StructureInfo>> structuresByMod = new TreeMap<>();
            for (StructureInfo info : setStructures) {
                structuresByMod.computeIfAbsent(info.modId, k -> new ArrayList<>()).add(info);
            }

            // Normalize each mod separately
            for (Map.Entry<String, List<StructureInfo>> modEntry : structuresByMod.entrySet()) {
                List<StructureInfo> modStructures = modEntry.getValue();
                Map<Integer, Integer> normalizedWeights = StructureScanner.calculateNormalizedWeights(modStructures, setInfo);

                for (StructureInfo info : modStructures) {
                    Integer originalWeight = info.getFinalWeight();
                    if (originalWeight != null && normalizedWeights.containsKey(originalWeight)) {
                        normalizedWeightsByStructure.put(info.id, normalizedWeights.get(originalWeight));
                    } else {
                        normalizedWeightsByStructure.put(info.id, TARGET_AVERAGE_WEIGHT);
                    }
                }
            }
        }

        return normalizedWeightsByStructure;
    }

    private static void addBlockStructureSets(List<String> lines,
                                              Map<String, List<StructureInfo>> structuresBySet,
                                              Map<String, List<StructureInfo>> likelyBySet,
                                              Map<String, List<StructureInfo>> questionableBySet) {
        lines.add("  // Structure sets to block (prevents double-spawning, MVS controls these instead)");
        lines.add("  block_structure_sets: [");

        boolean hasCore = false;
        for (String setId : structuresBySet.keySet()) {
            if (!setId.equals(MINECRAFT_VILLAGES_SET) && setId.endsWith(":villages")) {
                lines.add("    \"" + setId + "\",");
                hasCore = true;
            }
        }

        if (!likelyBySet.isEmpty()) {
            if (hasCore) lines.add("");
            lines.add("    // LIKELY village sets (review - remove if incorrect)");
            for (String setId : likelyBySet.keySet()) {
                lines.add("    \"" + setId + "\",");
            }
        }

        if (!questionableBySet.isEmpty()) {
            lines.add("");
            lines.add("    // QUESTIONABLE (uncomment to use - verify these are actually villages)");
            for (String setId : questionableBySet.keySet()) {
                lines.add("    // \"" + setId + "\",");
            }
        }

        lines.add("  ],");
        lines.add("");
    }

    private static void addInterceptStructureSets(List<String> lines) {
        lines.add("  // Structure sets to intercept (MVS controls structure selection)");
        lines.add("  intercept_structure_sets: [");
        lines.add("    \"" + MINECRAFT_VILLAGES_SET + "\",");
        lines.add("  ],");
        lines.add("");
    }

    private static void addStructurePool(List<String> lines, ScanResult scan,
                                         Map<String, Integer> normalizedWeightsByStructure,
                                         MinecraftServer server) {
        lines.add("  // Structure pool - MVS selects from this list");
        lines.add("  // biomes controls spawn weights per biome (same pattern system as biome_frequency)");
        lines.add("  //");
        lines.add("  // Supports: Direct IDs (\"minecraft:plains\": 10), Tags (\"#minecraft:is_plains\": 10), Patterns (\"minecraft:*\": 5)");
        lines.add("  // Specificity: More specific patterns win, literal IDs beat tags, tie-breaker = higher weight");
        lines.add("  structure_pool: [");
        lines.add("");

        // Core structures grouped by mod
        Map<String, List<StructureInfo>> structuresByMod = new TreeMap<>();
        for (StructureInfo info : scan.coreStructures) {
            structuresByMod.computeIfAbsent(info.modId, k -> new ArrayList<>()).add(info);
        }

        for (Map.Entry<String, List<StructureInfo>> modEntry : structuresByMod.entrySet()) {
            String modId = modEntry.getKey();
            List<StructureInfo> modStructures = modEntry.getValue();

            lines.add("    // === " + modId.toUpperCase() + " ===");
            lines.add("");

            Map<String, List<StructureInfo>> modStructuresBySet = new TreeMap<>();
            for (StructureInfo info : modStructures) {
                String setId = info.getFinalSet();
                modStructuresBySet.computeIfAbsent(setId, k -> new ArrayList<>()).add(info);
            }

            for (Map.Entry<String, List<StructureInfo>> setEntry : modStructuresBySet.entrySet()) {
                String setId = setEntry.getKey();
                List<StructureInfo> setStructures = setEntry.getValue();
                setStructures.sort(Comparator.comparing(s -> s.id));

                lines.add("    // " + setId);

                for (StructureInfo info : setStructures) {
                    Integer normalizedWeight = normalizedWeightsByStructure.get(info.id);
                    if (normalizedWeight == null) continue;

                    addStructureEntry(lines, info, normalizedWeight, server, false);
                }

                lines.add("");
            }
        }

        // Likely structures
        addLikelyStructures(lines, scan, normalizedWeightsByStructure, server);

        // Questionable structures
        addQuestionableStructures(lines, scan, normalizedWeightsByStructure, server);

        lines.add("  ],");
        lines.add("");
    }

    private static void addLikelyStructures(List<String> lines, ScanResult scan,
                                            Map<String, Integer> normalizedWeightsByStructure,
                                            MinecraftServer server) {
        Map<String, List<StructureInfo>> likelyBySet = groupBySet(scan.likelyStructures);
        if (likelyBySet.isEmpty()) return;

        lines.add("    // === LIKELY VILLAGE MODS (review and remove if incorrect) ===");
        lines.add("    // These appear to be villages. Their structure_sets are blocked above.");
        lines.add("");

        for (Map.Entry<String, List<StructureInfo>> setEntry : likelyBySet.entrySet()) {
            String setId = setEntry.getKey();
            List<StructureInfo> setStructures = setEntry.getValue();

            lines.add("    // " + setId + " (" + setStructures.size() + " structure(s))");

            for (StructureInfo info : setStructures) {
                Integer normalizedWeight = normalizedWeightsByStructure.get(info.id);
                if (normalizedWeight == null) continue;
                addStructureEntry(lines, info, normalizedWeight, server, false);
            }

            lines.add("");
        }
    }

    private static void addQuestionableStructures(List<String> lines, ScanResult scan,
                                                  Map<String, Integer> normalizedWeightsByStructure,
                                                  MinecraftServer server) {
        Map<String, List<StructureInfo>> questionableBySet = groupBySet(scan.questionableStructures);
        if (questionableBySet.isEmpty()) return;

        lines.add("    // === UNCERTAIN STRUCTURES (verify before enabling) ===");
        lines.add("    // These may or may not be villages. To enable:");
        lines.add("    // 1. Uncomment the structure entries below");
        lines.add("    // 2. Uncomment the structure_set in block_structure_sets above");
        lines.add("");

        for (Map.Entry<String, List<StructureInfo>> setEntry : questionableBySet.entrySet()) {
            String setId = setEntry.getKey();
            List<StructureInfo> setStructures = setEntry.getValue();

            lines.add("    // " + setId + " (" + setStructures.size() + " structure(s))");

            for (StructureInfo info : setStructures) {
                Integer normalizedWeight = normalizedWeightsByStructure.get(info.id);
                if (normalizedWeight == null) continue;
                addStructureEntry(lines, info, normalizedWeight, server, true);
            }

            lines.add("");
        }
    }

    private static void addStructureEntry(List<String> lines, StructureInfo info, int normalizedWeight,
                                          MinecraftServer server, boolean commented) {
        String prefix = commented ? "    // " : "    ";
        String biomeTag = info.getFinalBiome();

        if ("direct_biomes".equals(biomeTag)) {
            Map<String, Integer> expandedBiomes = expandDirectBiomes(info.id, normalizedWeight, server);
            if (expandedBiomes.isEmpty()) {
                biomeTag = "#minecraft:is_overworld";
                lines.add(prefix + "{ structure: \"" + info.id + "\",");
                lines.add(prefix + "  biomes: {");
                lines.add(prefix + "    \"" + biomeTag + "\": " + normalizedWeight + ",");
                lines.add(prefix + "  } },");
            } else {
                lines.add(prefix + "{ structure: \"" + info.id + "\",");
                lines.add(prefix + "  biomes: {");
                for (Map.Entry<String, Integer> biomeEntry : expandedBiomes.entrySet()) {
                    lines.add(prefix + "    \"" + biomeEntry.getKey() + "\": " + biomeEntry.getValue() + ",");
                }
                lines.add(prefix + "  } },");
            }
        } else {
            if (biomeTag == null || biomeTag.equals("unknown")) {
                biomeTag = "#minecraft:is_overworld";
            }
            lines.add(prefix + "{ structure: \"" + info.id + "\",");
            lines.add(prefix + "  biomes: {");
            lines.add(prefix + "    \"" + biomeTag + "\": " + normalizedWeight + ",");
            lines.add(prefix + "  } },");
        }
    }

    /**
     * Expand "direct_biomes" placeholder into actual biome IDs from structure definition.
     */
    public static Map<String, Integer> expandDirectBiomes(String structureId, int weight, MinecraftServer server) {
        Map<String, Integer> result = new LinkedHashMap<>();

        try {
            var registryAccess = server.registryAccess();
            var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

            ResourceLocation structureLoc = ResourceLocation.parse(structureId);
            var structure = structureRegistry.get(structureLoc);

            if (structure == null) {
                return result;
            }

            var biomeHolderSet = structure.biomes();

            for (var biomeEntry : biomeRegistry.entrySet()) {
                var biomeHolder = biomeRegistry.wrapAsHolder(biomeEntry.getValue());
                if (biomeHolderSet.contains(biomeHolder)) {
                    String biomeId = biomeEntry.getKey().location().toString();
                    result.put(biomeId, weight);
                }
            }

        } catch (Exception e) {
            MVSCommon.LOGGER.debug("Failed to expand direct_biomes for {}: {}", structureId, e.getMessage());
        }

        return result;
    }

    private static void addBlacklistedStructures(List<String> lines) {
        lines.add("  // Blacklist specific structures from non-intercepted sources");
        lines.add("  blacklisted_structures: [");
        lines.add("    // Example: \"bca:village/witch_hut\",");
        lines.add("  ],");
        lines.add("");
    }

    private static void addBiomeFrequency(List<String> lines) {
        lines.add("  // ## Biome-specific spawn frequency (0.0-1.0, applied before structure selection)");
        lines.add("  // Controls % chance of structure spawn attempt per biome");
        lines.add("  //");
        lines.add("  // Supports three formats:");
        lines.add("  // 1. Direct biome ID: \"minecraft:plains\": 1.0 (most specific, easiest for beginners)");
        lines.add("  // 2. Biome tags: \"#minecraft:is_ocean\": 0.2 (affects all matching biomes)");
        lines.add("  // 3. Patterns: \"minecraft:*\": 0.7, \"#terralith:*\": 0.85 (wildcards supported)");
        lines.add("  //");
        lines.add("  // Specificity scoring (more specific wins):");
        lines.add("  // - Base: 20, No #: +1, Literals before colon: +2, Literals after colon: +2, Each *: -5");
        lines.add("  // - Examples: \"minecraft:plains\" = 25, \"#minecraft:is_plains\" = 24, \"#*craft*:*village*\" = 4, \"*:*\" = -10");
        lines.add("  // - Tie-breaker: Higher frequency value wins");
        lines.add("  biome_frequency: {");
        lines.add("    // \"*:*\": 1.0,                      // Default: 100% spawn rate (implied if not set)");
        lines.add("    // \"minecraft:warm_ocean\": 0.5,     // Warm ocean specifically: 50% spawn rate");
        lines.add("    // \"#minecraft:is_ocean\": 0.2,      // All ocean biomes: 20% spawn rate");
        lines.add("    // \"#terralith:*\": 0.85,            // All Terralith biomes: 85% spawn rate");
        lines.add("  },");
        lines.add("");
    }

    private static void addPlacement(List<String> lines, MinecraftServer server) {
        lines.add("  // v0.4.0: Override structure placement (spacing, separation, salt, spreadType)");
        lines.add("  // Use /mvs config fill-placements to update with current registry values");
        lines.add("  placement: {");

        try {
            var structureSetRegistry = server.registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET);
            ResourceLocation villagesLoc = ResourceLocation.parse(MINECRAFT_VILLAGES_SET);
            var villagesSet = structureSetRegistry.get(villagesLoc);

            if (villagesSet != null && villagesSet.placement() instanceof RandomSpreadStructurePlacement randomSpread) {
                int spacing = randomSpread.spacing();
                int separation = randomSpread.separation();
                String spreadType = randomSpread.spreadType() == RandomSpreadType.TRIANGULAR
                    ? "triangular" : "linear";
                int salt = ((com.rhett.multivillageselector.mixin.StructurePlacementAccessor) randomSpread).invokeSalt();

                lines.add("    \"" + MINECRAFT_VILLAGES_SET + "\": {");
                lines.add("      spacing: " + spacing + ",");
                lines.add("      separation: " + separation + ",");
                lines.add("      salt: " + salt + ",");
                lines.add("      spreadType: \"" + spreadType + "\",");
                lines.add("    },");
            } else {
                lines.add("    \"minecraft:villages\": {},  // Could not read from registry");
            }
        } catch (Exception e) {
            lines.add("    \"minecraft:villages\": {},  // Could not read from registry");
        }

        lines.add("  },");
        lines.add("");
    }

    private static void addRelaxedBiomeValidation(List<String> lines) {
        lines.add("  // v0.4.0: Relaxed biome validation");
        lines.add("  // When false (default): vanilla validates biome at structure's bounding box center.");
        lines.add("  //   Works well for vanilla-sized structures but may reject large mod structures");
        lines.add("  //   (BCA, etc.) whose bounding box center lands in a different biome than chunk center.");
        lines.add("  // When true: bypass vanilla's biome check and trust MVS's chunk-center selection.");
        lines.add("  //   Recommended for modpacks with large village structures (BCA, CTOV large, etc.)");
        lines.add("  relaxed_biome_validation: false,");
        lines.add("");
    }

    private static void addDebugFlags(List<String> lines) {
        boolean isDevEnvironment = "true".equalsIgnoreCase(System.getenv("MVS_DEV"));
        String debugCmdValue = isDevEnvironment ? "true" : "false";
        String debugLoggingValue = isDevEnvironment ? "true" : "false";

        lines.add("  // ## Debugging functionality");
        lines.add("  // Auto-enabled in dev environments (MVS_DEV=true environment variable)");
        lines.add("  debug_cmd: " + debugCmdValue + ",");
        lines.add("  debug_logging: " + debugLoggingValue + ",");
    }
}