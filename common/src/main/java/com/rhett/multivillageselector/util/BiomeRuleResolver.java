package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Central API for resolving biome rules for structures.
 *
 * Pure function approach:
 * - No side effects
 * - Testable
 * - Single source of truth
 *
 * Usage:
 *   BiomeRules rules = BiomeRuleResolver.getEffectiveRules(structureId, registryAccess);
 *   boolean canSpawn = rules.matches(biomeHolder);
 *   String matchedTag = rules.getMatchingTag(biomeHolder);
 */
public class BiomeRuleResolver {

    /**
     * Get the effective biome rules for a structure.
     *
     * Precedence:
     * 1. MVS config (if structure is in structure_pool)
     * 2. Vanilla registry (Structure.biomes())
     * 3. Empty (structure not found)
     *
     * @param structureId Structure ID (e.g., "minecraft:village_plains")
     * @param registryAccess Registry access (for looking up structures/biomes)
     * @return BiomeRules (never null, may be empty)
     */
    public static BiomeRules getEffectiveRules(String structureId,
                                                net.minecraft.core.RegistryAccess registryAccess) {
        // Step 1: Check MVS config first
        MVSConfig.ConfiguredStructure mvsConfig = findInMVSConfig(structureId);
        if (mvsConfig != null) {
            return BiomeRules.fromMVSConfig(mvsConfig);
        }

        // Step 2: Fallback to vanilla registry
        Structure structure = getStructureFromRegistry(structureId, registryAccess);
        if (structure != null) {
            return BiomeRules.fromVanillaStructure(structure, registryAccess);
        }

        // Step 3: Not found
        return BiomeRules.empty();
    }

    /**
     * Find structure in MVS structure_pool
     */
    private static MVSConfig.ConfiguredStructure findInMVSConfig(String structureId) {
        for (MVSConfig.ConfiguredStructure configured : MVSConfig.structurePool) {
            if (configured.structure != null &&
                configured.structure.toString().equals(structureId)) {
                return configured;
            }
        }
        return null;
    }

    /**
     * Get structure from vanilla registry
     */
    private static Structure getStructureFromRegistry(String structureId,
                                                       net.minecraft.core.RegistryAccess registryAccess) {
        try {
            ResourceLocation location = ResourceLocation.parse(structureId);
            Registry<Structure> registry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            return registry.get(location);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a structure is MVS-controlled (in structure_pool)
     */
    public static boolean isMVSControlled(String structureId) {
        return findInMVSConfig(structureId) != null;
    }
}