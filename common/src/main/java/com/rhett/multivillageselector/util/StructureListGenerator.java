package com.rhett.multivillageselector.util;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Generates structure list reports from registry data.
 * Extracted from StructureCommands.executeList for testability.
 */
public class StructureListGenerator {

    /**
     * Entry for a structure with its metadata.
     */
    public static class StructureEntry {
        public final String id;
        public final String biomeTag;
        public final Integer weight;
        public final String namespace;
        public final String structureSetId;

        public StructureEntry(String id, String biomeTag, Integer weight, String namespace, String structureSetId) {
            this.id = id;
            this.biomeTag = biomeTag;
            this.weight = weight;
            this.namespace = namespace;
            this.structureSetId = structureSetId;
        }
    }

    /**
     * Result of structure scan.
     */
    public static class ScanResult {
        public final List<StructureEntry> structures;
        public final int totalCount;
        public final int filteredCount;
        public final Map<String, Map<String, List<StructureEntry>>> groupedByNamespace;

        public ScanResult(List<StructureEntry> structures, int totalCount, int filteredCount,
                         Map<String, Map<String, List<StructureEntry>>> groupedByNamespace) {
            this.structures = structures;
            this.totalCount = totalCount;
            this.filteredCount = filteredCount;
            this.groupedByNamespace = groupedByNamespace;
        }
    }

    /**
     * Scan all structures in the registry and group by namespace/structure_set.
     *
     * @param registryAccess Registry access for lookups
     * @param filter Optional pattern filter (null for no filter)
     * @return ScanResult with all structures and grouping
     */
    public static ScanResult scanStructures(RegistryAccess registryAccess, String filter) {
        Registry<StructureSet> structureSetRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE_SET);
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        // Build biome tag map for all structures
        Map<ResourceLocation, String> structureToBiomeTag = new HashMap<>();
        for (var entry : structureRegistry.entrySet()) {
            ResourceLocation structureId = entry.getKey().location();
            Structure structure = entry.getValue();

            try {
                var biomeHolderSet = structure.biomes();
                var tagKey = biomeHolderSet.unwrapKey();
                if (tagKey.isPresent()) {
                    structureToBiomeTag.put(structureId, "#" + tagKey.get().location().toString());
                } else {
                    structureToBiomeTag.put(structureId, "(direct biomes)");
                }
            } catch (Exception e) {
                structureToBiomeTag.put(structureId, "(error)");
            }
        }

        // Track structure_set membership
        Map<ResourceLocation, String> structureToSet = new HashMap<>();
        for (var entry : structureSetRegistry.entrySet()) {
            ResourceLocation setId = entry.getKey().location();
            String setIdStr = setId.toString();
            var structureSet = entry.getValue();

            for (var selectionEntry : structureSet.structures()) {
                var structureKey = selectionEntry.structure().unwrapKey();
                if (structureKey.isPresent()) {
                    ResourceLocation structureId = structureKey.get().location();
                    structureToSet.put(structureId, setIdStr);
                }
            }
        }

        // Build complete structure list
        List<StructureEntry> allStructures = new ArrayList<>();
        Map<String, Map<String, List<StructureEntry>>> namespaceMap = new TreeMap<>();
        int totalCount = 0;
        int filteredCount = 0;

        for (var entry : structureRegistry.entrySet()) {
            ResourceLocation structureId = entry.getKey().location();
            String structureIdStr = structureId.toString();
            String namespace = structureId.getNamespace();
            String biomeTag = structureToBiomeTag.getOrDefault(structureId, "(unknown)");
            String setId = structureToSet.getOrDefault(structureId, "(none)");

            totalCount++;

            // Apply filter if provided
            if (filter != null && !filter.isEmpty()) {
                if (!PatternMatcher.matches(structureIdStr, filter)) {
                    continue;
                }
            }

            filteredCount++;

            // Get weight from structure_set
            Integer weight = getStructureWeight(structureId, setId, structureSetRegistry);

            StructureEntry structureEntry = new StructureEntry(
                structureIdStr, biomeTag, weight, namespace, setId
            );
            allStructures.add(structureEntry);

            // Add to namespace map
            namespaceMap
                .computeIfAbsent(namespace, k -> new TreeMap<>())
                .computeIfAbsent(setId, k -> new ArrayList<>())
                .add(structureEntry);
        }

        return new ScanResult(allStructures, totalCount, filteredCount, namespaceMap);
    }

    private static Integer getStructureWeight(ResourceLocation structureId, String setIdStr,
                                              Registry<StructureSet> structureSetRegistry) {
        if (setIdStr.equals("(none)")) return null;

        try {
            ResourceLocation setLoc = ResourceLocation.parse(setIdStr);
            var setHolder = structureSetRegistry.getHolder(ResourceKey.create(Registries.STRUCTURE_SET, setLoc));
            if (setHolder.isPresent()) {
                for (var sel : setHolder.get().value().structures()) {
                    if (sel.structure().unwrapKey().map(k -> k.location().equals(structureId)).orElse(false)) {
                        return sel.weight();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Generate text report lines from scan result.
     *
     * @param scanResult The scan result
     * @param filter The filter used (null if none)
     * @return List of lines for the report file
     */
    public static List<String> generateReport(ScanResult scanResult, String filter) {
        List<String> lines = new ArrayList<>();

        lines.add("=".repeat(80));
        lines.add("MVS Structure List - All Structures in Game");
        lines.add("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        if (filter != null && !filter.isEmpty()) {
            lines.add("Filter: " + filter);
            lines.add("Matched: " + scanResult.filteredCount + " / " + scanResult.totalCount + " structures");
        } else {
            lines.add("Total: " + scanResult.totalCount + " structures");
        }
        lines.add("=".repeat(80));
        lines.add("");

        // Output grouped by namespace
        for (var namespaceEntry : scanResult.groupedByNamespace.entrySet()) {
            String namespace = namespaceEntry.getKey();
            var setMap = namespaceEntry.getValue();

            int namespaceCount = setMap.values().stream().mapToInt(List::size).sum();
            lines.add("=== " + namespace + " (" + namespaceCount + " structures) ===");
            lines.add("");

            for (var setEntry : setMap.entrySet()) {
                String setId = setEntry.getKey();
                var structures = setEntry.getValue();

                // Calculate normalized weights for this structure_set (target avg: 25)
                int totalWeight = 0;
                int weightedCount = 0;
                for (var s : structures) {
                    if (s.weight != null) {
                        totalWeight += s.weight;
                        weightedCount++;
                    }
                }
                double multiplier = (weightedCount > 0 && totalWeight > 0)
                    ? 25.0 / (totalWeight / (double) weightedCount)
                    : 1.0;

                lines.add("  [" + setId + "]");

                // Sort structures alphabetically
                structures.sort(Comparator.comparing(s -> s.id));

                for (var struct : structures) {
                    String weightStr;
                    if (struct.weight != null) {
                        int normalized = (int) Math.round(struct.weight * multiplier);
                        weightStr = String.format("weight: %d (normalized: %d)", struct.weight, normalized);
                    } else {
                        weightStr = "weight: (none)";
                    }
                    lines.add("    " + struct.id);
                    lines.add("      Biomes: " + struct.biomeTag);
                    lines.add("      " + weightStr);
                }
                lines.add("");
            }
        }

        return lines;
    }
}
