package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.*;

/**
 * Analyzes structure sets for display and testing purposes.
 * Extracted from StructureSetCommands and TestCommands for testability.
 */
public class StructureSetAnalyzer {

    private static final int VANILLA_VILLAGES_SALT = 10387312;

    /**
     * Information about a structure set.
     */
    public static class StructureSetInfo {
        public final String setId;
        public final String placementType;
        public final Integer spacing;
        public final Integer separation;
        public final Integer salt;
        public final boolean matchesVanillaSalt;
        public final List<StructureEntry> structures;
        public final int totalWeight;

        // MVS status
        public final boolean isBlocked;
        public final boolean isIntercepted;

        public StructureSetInfo(String setId, String placementType, Integer spacing,
                               Integer separation, Integer salt, List<StructureEntry> structures,
                               boolean isBlocked, boolean isIntercepted) {
            this.setId = setId;
            this.placementType = placementType;
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
            this.matchesVanillaSalt = salt != null && salt == VANILLA_VILLAGES_SALT;
            this.structures = structures;
            this.totalWeight = structures.stream().mapToInt(e -> e.weight).sum();
            this.isBlocked = isBlocked;
            this.isIntercepted = isIntercepted;
        }

        /**
         * Calculate average chunks per structure spawn attempt.
         */
        public double getAverageChunksPerStructure() {
            if (spacing == null) return 0;
            return (double) spacing * spacing;
        }
    }

    /**
     * A structure entry within a structure set.
     */
    public static class StructureEntry {
        public final String id;
        public final int weight;
        public final double percentage;

        public StructureEntry(String id, int weight, double percentage) {
            this.id = id;
            this.weight = weight;
            this.percentage = percentage;
        }
    }

    /**
     * Analyze a structure set by ID.
     *
     * @param structureSetId The structure set ID to analyze
     * @param registryAccess Registry access for lookups
     * @return StructureSetInfo or null if not found
     */
    public static StructureSetInfo analyze(String structureSetId, RegistryAccess registryAccess) {
        Registry<StructureSet> structureSetRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE_SET);
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        ResourceLocation setLocation = ResourceLocation.parse(structureSetId);
        StructureSet structureSet = structureSetRegistry.get(setLocation);

        if (structureSet == null) {
            return null;
        }

        StructurePlacement placement = structureSet.placement();
        String placementType = placement.type().toString();

        Integer spacing = null;
        Integer separation = null;
        Integer salt = null;

        if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
            spacing = randomSpread.spacing();
            separation = randomSpread.separation();
            salt = ((com.rhett.multivillageselector.mixin.StructurePlacementAccessor) randomSpread).invokeSalt();
        }

        // Collect structure entries
        List<StructureEntry> entries = new ArrayList<>();
        int totalWeight = 0;

        for (var entry : structureSet.structures()) {
            totalWeight += entry.weight();
        }

        for (var entry : structureSet.structures()) {
            var structureHolder = entry.structure();
            var structure = structureHolder.value();
            int weight = entry.weight();

            ResourceLocation structureId = structureRegistry.getKey(structure);
            if (structureId != null) {
                double percentage = totalWeight > 0 ? (weight * 100.0 / totalWeight) : 0;
                entries.add(new StructureEntry(structureId.toString(), weight, percentage));
            }
        }

        // MVS status
        boolean isBlocked = MVSConfig.blockStructureSets.contains(structureSetId);
        boolean isIntercepted = MVSConfig.interceptStructureSets.contains(structureSetId);

        return new StructureSetInfo(structureSetId, placementType, spacing, separation, salt,
            entries, isBlocked, isIntercepted);
    }

    /**
     * Find which structure set contains a given structure.
     *
     * @param structure The structure to find
     * @param registryAccess Registry access for lookups
     * @return Structure set ID or null if not found
     */
    public static String findStructureSetForStructure(Structure structure, RegistryAccess registryAccess) {
        Registry<StructureSet> structureSetRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE_SET);
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        ResourceLocation structureId = structureRegistry.getKey(structure);
        if (structureId == null) return null;

        for (var entry : structureSetRegistry.entrySet()) {
            var structureSet = entry.getValue();
            var structureSetId = entry.getKey().location();

            for (var structureEntry : structureSet.structures()) {
                var entryStructure = structureEntry.structure().value();
                ResourceLocation entryId = structureRegistry.getKey(entryStructure);

                if (entryId != null && entryId.equals(structureId)) {
                    return structureSetId.toString();
                }
            }
        }

        return null;
    }

    /**
     * Test if a structure is MVS-controlled, blacklisted, etc.
     */
    public static class StructureStatus {
        public final String structureId;
        public final boolean exists;
        public final boolean isMVSControlled;
        public final boolean isBlacklisted;
        public final String matchedBlacklistPattern;
        public final String structureSetId;
        public final boolean isSetBlocked;
        public final boolean isSetIntercepted;
        public final boolean willSpawn;

        public StructureStatus(String structureId, boolean exists, boolean isMVSControlled,
                              boolean isBlacklisted, String matchedBlacklistPattern,
                              String structureSetId, boolean isSetBlocked, boolean isSetIntercepted) {
            this.structureId = structureId;
            this.exists = exists;
            this.isMVSControlled = isMVSControlled;
            this.isBlacklisted = isBlacklisted;
            this.matchedBlacklistPattern = matchedBlacklistPattern;
            this.structureSetId = structureSetId;
            this.isSetBlocked = isSetBlocked;
            this.isSetIntercepted = isSetIntercepted;
            this.willSpawn = exists && !isBlacklisted && !isSetBlocked;
        }
    }

    /**
     * Get the status of a structure (MVS control, blacklist, structure set).
     *
     * @param structureId The structure ID to check
     * @param registryAccess Registry access for lookups
     * @return StructureStatus with all relevant info
     */
    public static StructureStatus getStructureStatus(String structureId, RegistryAccess registryAccess) {
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        ResourceLocation structureLoc = ResourceLocation.parse(structureId);
        Structure structure = structureRegistry.get(structureLoc);

        if (structure == null) {
            return new StructureStatus(structureId, false, false, false, null, null, false, false);
        }

        // Check if MVS-controlled
        boolean isMVSControlled = MVSConfig.structurePool.stream()
            .anyMatch(s -> s.structure != null && s.structure.toString().equals(structureId));

        // Check if blacklisted
        String matchedPattern = MVSConfig.blacklistedStructures.stream()
            .filter(pattern -> PatternMatcher.matches(structureId, pattern))
            .findFirst()
            .orElse(null);
        boolean isBlacklisted = matchedPattern != null;

        // Find structure set
        String structureSetId = findStructureSetForStructure(structure, registryAccess);

        boolean isSetBlocked = structureSetId != null && MVSConfig.blockStructureSets.contains(structureSetId);
        boolean isSetIntercepted = structureSetId != null && MVSConfig.interceptStructureSets.contains(structureSetId);

        return new StructureStatus(structureId, true, isMVSControlled, isBlacklisted,
            matchedPattern, structureSetId, isSetBlocked, isSetIntercepted);
    }
}
