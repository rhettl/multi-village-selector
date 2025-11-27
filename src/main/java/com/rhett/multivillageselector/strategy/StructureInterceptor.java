package com.rhett.multivillageselector.strategy;

import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.MultiVillageSelector;
import com.rhett.multivillageselector.profiler.ChunkGenerationProfiler;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;

/**
 * Platform-agnostic structure interception coordinator.
 * Pure business logic - no mixin dependencies.
 *
 * Orchestrates:
 * - Block/intercept checks
 * - Placement validation
 * - Strategy routing (MVS vs Vanilla)
 * - Pre-existing structure checks
 */
public class StructureInterceptor {

    /**
     * Callback interface for structure generation.
     * Allows mixin to handle the actual tryGenerateStructure call.
     */
    @FunctionalInterface
    public interface GenerationCallback {
        /**
         * Attempts to generate a structure.
         * @param entry The structure selection entry
         * @return true if generation succeeded, false otherwise
         */
        boolean tryGenerate(
            net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry,
            StructureManager structureManager,
            net.minecraft.core.RegistryAccess registryAccess,
            net.minecraft.world.level.levelgen.RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos
        );
    }

    /**
     * Main interception entry point.
     * Called by mixin to handle structure_set forEach loop.
     *
     * @param structureSetList The list of structure_sets to process
     * @param vanillaConsumer The vanilla consumer (for pass-through)
     * @param generator The ChunkGenerator instance
     * @param registryAccess Registry access
     * @param state Chunk generator structure state
     * @param structureManager Structure manager
     * @param chunk The chunk being generated
     * @param templateManager Template manager
     * @param generationCallback Callback for actual structure generation
     */
    public static void intercept(
            List<?> structureSetList,
            java.util.function.Consumer<Holder<StructureSet>> vanillaConsumer,
            ChunkGenerator generator,
            net.minecraft.core.RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            GenerationCallback generationCallback
    ) {
        if (!MVSConfig.enabled) {
            // Mod disabled - pass through to vanilla
            for (Object obj : structureSetList) {
                long startTime = System.nanoTime();
                vanillaConsumer.accept((Holder<StructureSet>) obj);
                ChunkGenerationProfiler.recordVanillaPassthrough(System.nanoTime() - startTime);
            }
            return;
        }

        ChunkPos chunkPos = chunk.getPos();

        // Process each structure_set
        for (Object obj : structureSetList) {
            Holder<StructureSet> structureSetHolder = (Holder<StructureSet>) obj;

            // Get structure_set ID
            java.util.Optional<net.minecraft.resources.ResourceKey<StructureSet>> keyOpt = structureSetHolder.unwrapKey();
            String structureSetId = keyOpt.map(key -> key.location().toString()).orElse(null);

            // Check if blocked
            if (structureSetId != null && MVSConfig.blockStructureSets.contains(structureSetId)) {
                continue; // Skip entirely (logged at startup)
            }

            // Check if intercepted
            if (structureSetId != null && MVSConfig.interceptStructureSets.contains(structureSetId)) {
                // Check placement rules (spacing/separation)
                StructureSet structureSet = structureSetHolder.value();
                StructurePlacement placement = structureSet.placement();
                if (!placement.isStructureChunk(state, chunkPos.x, chunkPos.z)) {
                    // Don't log every failed spacing check - too noisy
                    continue;
                }

                // Log when we pass spacing check (debug only)
                if (MVSConfig.debugLogging) {
                    int worldX = chunkPos.x * 16;
                    int worldZ = chunkPos.z * 16;
                    MultiVillageSelector.LOGGER.info("[MVS] ⚡ Attempting spawn for '{}' at chunk[{},{}] ~world[{},{}]",
                        structureSetId, chunkPos.x, chunkPos.z, worldX, worldZ);
                }
                ChunkGenerationProfiler.recordMVSSpacingPassed();

                // Check biome frequency (spawn density control)
                if (!rollBiomeFrequency(generator, chunk, registryAccess, state)) {
                    if (MVSConfig.debugLogging) {
                        MultiVillageSelector.LOGGER.info("[MVS]   ✗ Frequency check failed - no spawn");
                    }
                    ChunkGenerationProfiler.recordMVSFrequencyFailure();
                    continue; // Failed frequency roll
                }

                // Route to MVS strategy
                handleIntercepted(
                    structureSetHolder,
                    generator,
                    registryAccess,
                    state,
                    structureManager,
                    chunk,
                    templateManager,
                    generationCallback
                );
                ChunkGenerationProfiler.recordMVSSelection();

            } else {
                // Not intercepted - pass through to vanilla
                long startTime = System.nanoTime();
                vanillaConsumer.accept(structureSetHolder);
                ChunkGenerationProfiler.recordVanillaPassthrough(System.nanoTime() - startTime);
            }
        }
    }

    /**
     * Handles intercepted structure_set using MVS filter-first strategy.
     */
    private static void handleIntercepted(
            Holder<StructureSet> structureSetHolder,
            ChunkGenerator generator,
            net.minecraft.core.RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            GenerationCallback generationCallback
    ) {
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.bottomOf(chunk);

        // === SELECTION PHASE (timed separately) ===
        long selectionStart = System.nanoTime();
        MVSStrategyHandler.Result result = MVSStrategyHandler.handle(
            structureSetHolder,
            registryAccess,
            state,
            structureManager,
            chunk,
            generator
        );
        ChunkGenerationProfiler.recordMVSSelectionTime(System.nanoTime() - selectionStart);

        // Log selection result (only if debug logging enabled)
        if (MVSConfig.debugLogging) {
            if (result.shouldGenerate) {
                MultiVillageSelector.LOGGER.info("[MVS]   ✓ Selected '{}' → will attempt generation", result.structureId);
            } else {
                MultiVillageSelector.LOGGER.info("[MVS]   ✗ Selection failed: {}", result.message);
            }
        }

        if (result.shouldGenerate) {
            // Create entry and attempt generation
            Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            Holder<Structure> structureHolder = structureRegistry.getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,
                    net.minecraft.resources.ResourceLocation.parse(result.structureId))
            );

            net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry =
                new net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry(
                    structureHolder, 1
                );

            // === GENERATION PHASE (timed separately) ===
            long generationStart = System.nanoTime();
            boolean generated = generationCallback.tryGenerate(
                entry, structureManager, registryAccess, state.randomState(),
                templateManager, state.getLevelSeed(), chunk, chunkPos, sectionPos
            );
            ChunkGenerationProfiler.recordMVSGenerationTime(System.nanoTime() - generationStart);

            if (generated) {
                if (MVSConfig.debugLogging) {
                    int worldX = chunkPos.x * 16;
                    int worldZ = chunkPos.z * 16;
                    MultiVillageSelector.LOGGER.info("[MVS]   ✓ Generation SUCCEEDED for '{}' at chunk[{},{}] ~world[{},{}]",
                        result.structureId, chunkPos.x, chunkPos.z, worldX, worldZ);
                }
                ChunkGenerationProfiler.recordMVSGenerationSuccess();
            } else {
                if (MVSConfig.debugLogging) {
                    MultiVillageSelector.LOGGER.info("[MVS]   ✗ Generation FAILED for '{}' (biome validation or placement issue)",
                        result.structureId);
                }
            }
        }
    }

    /**
     * Rolls biome frequency check for spawn density control.
     * Returns true if frequency roll passes (spawn should proceed).
     * Returns false if frequency roll fails (skip this spawn attempt).
     *
     * Uses specificity-based pattern matching:
     * - #*:* (least specific)
     * - #minecraft:* (medium)
     * - #minecraft:is_plains (most specific wins)
     */
    private static boolean rollBiomeFrequency(
            ChunkGenerator generator,
            ChunkAccess chunk,
            net.minecraft.core.RegistryAccess registryAccess,
            ChunkGeneratorStructureState state) {

        // If no biome_frequency configured, always pass (default 100%)
        if (MVSConfig.biomeFrequency.isEmpty()) {
            return true;
        }

        ChunkPos chunkPos = chunk.getPos();

        // Get biome at chunk center (same logic as MVSStrategyHandler)
        int centerX = chunkPos.getMinBlockX() + 8;
        int centerZ = chunkPos.getMinBlockZ() + 8;
        int surfaceY = generator.getBaseHeight(
            centerX, centerZ,
            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
            chunk.getHeightAccessorForGeneration(),
            state.randomState()
        );

        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder =
            generator.getBiomeSource().getNoiseBiome(
                centerX >> 2, surfaceY >> 2, centerZ >> 2,
                state.randomState().sampler()
            );

        // Use unified pattern matcher to get frequency
        // Supports direct biome IDs, tags, and patterns with specificity resolution
        double frequency = com.rhett.multivillageselector.util.PatternMatcher.getValueForBiome(
            MVSConfig.biomeFrequency,
            biomeHolder,
            1.0  // Default: 100% spawn rate
        );

        // Roll random (deterministic per chunk)
        java.util.Random random = new java.util.Random(
            state.getLevelSeed() + chunkPos.x * 341873128712L + chunkPos.z * 132897987541L
        );

        double roll = random.nextDouble();
        return roll < frequency;
    }
}
