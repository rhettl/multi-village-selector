package com.rhett.multivillageselector.strategy;

import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.MultiVillageSelector;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.Random;

/**
 * Platform-agnostic MVS strategy handler.
 * Pure business logic - no mixin dependencies.
 * Filter-first: filters pool by biome tags, then does weighted selection.
 */
public class MVSStrategyHandler {

    /**
     * Result of MVS strategy execution.
     */
    public static class Result {
        public final boolean shouldGenerate;
        public final Structure structure;
        public final String structureId;
        public final String message; // For logging

        private Result(boolean shouldGenerate, Structure structure, String structureId, String message) {
            this.shouldGenerate = shouldGenerate;
            this.structure = structure;
            this.structureId = structureId;
            this.message = message;
        }

        public static Result noGenerate(String reason) {
            return new Result(false, null, null, reason);
        }

        public static Result generate(Structure structure, String structureId) {
            return new Result(true, structure, structureId, "Selected: " + structureId);
        }
    }

    /**
     * Handles MVS strategy selection.
     * Returns what structure to generate (if any).
     *
     * @return Result indicating whether to generate and what structure
     */
    public static Result handle(
            Holder<StructureSet> structureSetHolder,
            net.minecraft.core.RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            StructureManager structureManager,
            ChunkAccess chunk,
            ChunkGenerator generator
    ) {
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.bottomOf(chunk);

        // Check if any MVS structure already exists at this location
        if (anyMVSStructureExists(structureManager, registryAccess, sectionPos, chunk)) {
            return Result.noGenerate("MVS structure already exists at chunk [" + chunkPos.x + "," + chunkPos.z + "]");
        }

        // Get biome at surface level for filtering
        int worldX = chunkPos.getMinBlockX() + 8;
        int worldZ = chunkPos.getMinBlockZ() + 8;
        int surfaceY = generator.getBaseHeight(
            worldX, worldZ,
            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
            chunk.getHeightAccessorForGeneration(),
            state.randomState()
        );

        // Get biome from BiomeSource
        Holder<Biome> biomeHolder = generator.getBiomeSource().getNoiseBiome(
            worldX >> 2, surfaceY >> 2, worldZ >> 2, state.randomState().sampler()
        );

        // Use MVS to filter by biome tags and select structure
        Random random = new Random(
            state.getLevelSeed() + chunkPos.x * 341873128712L + chunkPos.z * 132897987541L
        );

        MVSConfig.ConfiguredStructure selected = MVSConfig.selectStructure(random, biomeHolder);

        // Handle null or empty selection
        if (selected == null || selected.isEmpty) {
            String biomeName = biomeHolder.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");

            if (selected == null) {
                return Result.noGenerate("No structures match biome " + biomeName + " at chunk [" + chunkPos.x + "," + chunkPos.z + "]");
            } else {
                return Result.noGenerate("Empty entry selected for biome " + biomeName + " at chunk [" + chunkPos.x + "," + chunkPos.z + "]");
            }
        }

        // Get the structure from registry
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        Structure structure = structureRegistry.get(selected.structure);

        if (structure == null) {
            return Result.noGenerate("Structure not found in registry: " + selected.structure);
        }

        // DEBUG: Log detected biome on successful selection (helps debug biome filtering issues)
        if (MVSConfig.debugLogging) {
            String biomeName = biomeHolder.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
            MultiVillageSelector.LOGGER.info("[MVS] Debug: Biome at chunk [{},{}]: {}",
                chunkPos.x, chunkPos.z, biomeName);
        }

        return Result.generate(structure, selected.structure.toString());
    }

    /**
     * Checks if any MVS structure already exists at this location.
     * Prevents duplicate villages at same coordinates.
     */
    private static boolean anyMVSStructureExists(
            StructureManager structureManager,
            net.minecraft.core.RegistryAccess registryAccess,
            SectionPos sectionPos,
            ChunkAccess chunk
    ) {
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        for (MVSConfig.ConfiguredStructure configured : MVSConfig.structurePool) {
            if (configured.isEmpty) continue;

            Structure structure = structureRegistry.get(configured.structure);
            if (structure == null) continue;

            net.minecraft.world.level.levelgen.structure.StructureStart existingStart =
                structureManager.getStartForStructure(sectionPos, structure, chunk);

            if (existingStart != null && existingStart.isValid()) {
                return true;
            }
        }

        return false;
    }
}
