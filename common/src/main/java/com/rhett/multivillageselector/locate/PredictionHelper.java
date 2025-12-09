package com.rhett.multivillageselector.locate;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.util.LocateHelper;
import com.rhett.multivillageselector.util.LocateHelper.BiomeSampler;
import com.rhett.multivillageselector.util.LocateHelper.PlacementStrategy;
import com.rhett.multivillageselector.util.LocateHelper.RandomSpreadPlacement;
import com.rhett.multivillageselector.util.PlacementResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug helper for predicting structure placements at chunk candidates.
 * Used by /mvs debug predict to compare predicted vs actual structure locations.
 */
public class PredictionHelper {

    /**
     * Prediction data for a single chunk candidate.
     */
    public static class ChunkPrediction {
        public final int chunkX;
        public final int chunkZ;
        public final BlockPos worldPos;           // Position reported by getLocatePos()
        public final String structureId;          // Which structure wins selection (null = none)
        public final String biomeId;              // Biome at this chunk
        public final boolean hasOffset;           // True if locateOffset is non-zero
        public final Vec3i locateOffset;          // The actual offset value
        public final int distanceFromStart;       // Block distance from start position

        public ChunkPrediction(int chunkX, int chunkZ, BlockPos worldPos, String structureId,
                               String biomeId, boolean hasOffset, Vec3i locateOffset, int distanceFromStart) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.worldPos = worldPos;
            this.structureId = structureId;
            this.biomeId = biomeId;
            this.hasOffset = hasOffset;
            this.locateOffset = locateOffset;
            this.distanceFromStart = distanceFromStart;
        }

        /**
         * Format for file output.
         */
        public String toFileString() {
            // Note: null structureId can mean:
            // 1. No structures in pool match this biome
            // 2. Biome frequency roll failed (spawn density reduction)
            String structureStr = structureId != null ? structureId : "(no structure - freq/biome)";
            String offsetStr = hasOffset ? String.format(" [offset: %d,%d,%d]",
                locateOffset.getX(), locateOffset.getY(), locateOffset.getZ()) : "";

            return String.format("Chunk [%d, %d] -> World [%d, %d, %d]%s\n" +
                                 "  Structure: %s\n" +
                                 "  Biome: %s\n" +
                                 "  Distance: %d blocks",
                chunkX, chunkZ,
                worldPos.getX(), worldPos.getY(), worldPos.getZ(),
                offsetStr,
                structureStr,
                biomeId,
                distanceFromStart);
        }

        /**
         * Generate teleport command for this position.
         */
        public String getTeleportCommand() {
            return String.format("/tp @s %d ~ %d", worldPos.getX(), worldPos.getZ());
        }
    }

    /**
     * Predict structures for the closest N placement chunks from a starting position.
     * This is the core debugging function to validate locate position calculation.
     *
     * @param startPos Starting position to search from
     * @param seed World seed
     * @param strategy Placement strategy (with offset info)
     * @param biomeSampler Interface to sample biomes
     * @param maxChunks Maximum number of chunks to predict (e.g., 100)
     * @return List of predictions sorted by distance from start
     */
    public static List<ChunkPrediction> predictChunks(
            BlockPos startPos,
            long seed,
            PlacementStrategy strategy,
            BiomeSampler biomeSampler,
            int maxChunks) {

        List<ChunkPrediction> predictions = new ArrayList<>();

        int startChunkX = startPos.getX() >> 4;
        int startChunkZ = startPos.getZ() >> 4;

        // Extract offset info if RandomSpreadPlacement
        Vec3i locateOffset = Vec3i.ZERO;
        if (strategy instanceof RandomSpreadPlacement rsp) {
            locateOffset = rsp.locateOffset;
        }
        boolean hasOffset = !locateOffset.equals(Vec3i.ZERO);

        // Iterate placement chunks (search up to 300 chunk radius to get enough candidates)
        // 300 chunks covers ~5 cells with spacing=60 (Better Villages), ~9 cells with spacing=34 (vanilla)
        for (int[] placementChunk : strategy.iteratePlacements(startChunkX, startChunkZ, seed, 300)) {
            if (predictions.size() >= maxChunks) {
                break;
            }

            int chunkX = placementChunk[0];
            int chunkZ = placementChunk[1];

            // Get world position using strategy's locate calculation
            BlockPos worldPos = strategy.getLocatePos(chunkX, chunkZ);

            // Calculate distance from start
            int dx = worldPos.getX() - startPos.getX();
            int dz = worldPos.getZ() - startPos.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);

            // Get biome at chunk NW corner (placement anchor point)
            // Must match MVSStrategyHandler which samples at NW corner, not center
            // The BiomeSampler uses noise-based height estimation, so Y parameter is ignored
            int biomeX = chunkX << 4;
            int biomeZ = chunkZ << 4;
            Holder<Biome> biomeHolder = biomeSampler.getBiomeAt(biomeX, 0, biomeZ);

            String biomeId = biomeHolder.unwrapKey()
                .map(k -> k.location().toString())
                .orElse("unknown");

            // Simulate MVS selection at this chunk
            MVSConfig.ConfiguredStructure selected = LocateHelper.simulateSelection(chunkX, chunkZ, seed, biomeHolder);

            String structureId = null;
            if (selected != null && !selected.isEmpty && selected.structure != null) {
                structureId = selected.structure.toString();
            }

            predictions.add(new ChunkPrediction(
                chunkX, chunkZ, worldPos, structureId, biomeId,
                hasOffset, locateOffset, distance
            ));
        }

        // Sort by distance (should already be roughly sorted by spiral, but ensure it)
        predictions.sort((a, b) -> Integer.compare(a.distanceFromStart, b.distanceFromStart));

        return predictions;
    }

    /**
     * Predict chunks using config-aware placement resolution.
     * Convenience method for use from commands.
     *
     * @param structureSetId The structure set ID (e.g., "minecraft:villages")
     * @param startPos Starting position to search from
     * @param seed World seed
     * @param biomeSampler Interface to sample biomes
     * @param structureSetRegistry Registry for placement lookup
     * @param maxChunks Maximum number of chunks to predict
     * @return List of predictions sorted by distance from start
     */
    public static List<ChunkPrediction> predictChunksWithConfig(
            String structureSetId,
            BlockPos startPos,
            long seed,
            BiomeSampler biomeSampler,
            Registry<StructureSet> structureSetRegistry,
            int maxChunks) {

        PlacementStrategy strategy = LocateHelper.getConfiguredPlacement(structureSetId, structureSetRegistry);
        return predictChunks(startPos, seed, strategy, biomeSampler, maxChunks);
    }

    // ============================================================
    // STRUCTURE-SPECIFIC SEARCH (used by /mvs locate)
    // ============================================================

    /**
     * Find chunks where a specific structure will spawn.
     * Used by /mvs locate to find spawn locations for a target structure.
     *
     * @param targetStructureId The structure to find (e.g., "minecraft:village_plains")
     * @param startPos Starting position to search from
     * @param seed World seed
     * @param strategy Placement strategy
     * @param biomeSampler Interface to sample biomes
     * @param maxResults Maximum number of results to return
     * @param maxRadiusChunks Maximum search radius in chunks
     * @return List of predictions where the target structure spawns, sorted by distance
     */
    public static List<ChunkPrediction> findChunksForStructure(
            String targetStructureId,
            BlockPos startPos,
            long seed,
            PlacementStrategy strategy,
            BiomeSampler biomeSampler,
            int maxResults,
            int maxRadiusChunks) {

        List<ChunkPrediction> results = new ArrayList<>();

        // Search for at least 3 to ensure we find the actual closest
        // (spiral iteration is by cell, not exact block distance)
        //
        // EDGE CASE (accepted): If player is near cell edge and the target structure
        // wins selection in all 8 surrounding cells, we might miss the true closest.
        // This is negligible in practice because:
        // 1. We're searching for a SPECIFIC structure competing with others
        // 2. Finding 3 matches typically requires checking 30+ placement chunks
        // 3. By then we've covered multiple cells in every direction
        // Tradeoff: O(cells_to_find_3) vs O(all_cells_in_radius) for <1% edge case
        int searchCount = Math.max(3, maxResults);

        int startChunkX = startPos.getX() >> 4;
        int startChunkZ = startPos.getZ() >> 4;

        // Extract offset info if RandomSpreadPlacement
        Vec3i locateOffset = Vec3i.ZERO;
        if (strategy instanceof RandomSpreadPlacement rsp) {
            locateOffset = rsp.locateOffset;
        }
        boolean hasOffset = !locateOffset.equals(Vec3i.ZERO);

        for (int[] placementChunk : strategy.iteratePlacements(startChunkX, startChunkZ, seed, maxRadiusChunks)) {
            int chunkX = placementChunk[0];
            int chunkZ = placementChunk[1];

            // Check distance
            int distX = Math.abs(chunkX - startChunkX);
            int distZ = Math.abs(chunkZ - startChunkZ);
            if (distX > maxRadiusChunks || distZ > maxRadiusChunks) {
                continue;
            }

            // Get world position
            BlockPos worldPos = strategy.getLocatePos(chunkX, chunkZ);

            // Calculate distance from start
            int dx = worldPos.getX() - startPos.getX();
            int dz = worldPos.getZ() - startPos.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);

            // Get biome at chunk NW corner (placement anchor point)
            // Must match MVSStrategyHandler which samples at NW corner, not center
            int biomeX = chunkX << 4;
            int biomeZ = chunkZ << 4;
            Holder<Biome> biomeHolder = biomeSampler.getBiomeAt(biomeX, 0, biomeZ);

            // Simulate selection
            MVSConfig.ConfiguredStructure selected = LocateHelper.simulateSelection(chunkX, chunkZ, seed, biomeHolder);

            // Check if target structure was selected
            if (selected != null && !selected.isEmpty &&
                selected.structure != null && selected.structure.toString().equals(targetStructureId)) {

                String biomeId = biomeHolder.unwrapKey()
                    .map(k -> k.location().toString())
                    .orElse("unknown");

                // Enhanced debug logging - matches generation format for comparison
                if (MVSConfig.debugLogging) {
                    long randomSeed = seed + chunkX * 341873128712L + chunkZ * 132897987541L;
                    MVSCommon.LOGGER.info("[MVS] === PREDICTION at chunk [{}, {}] world [{}, {}] ===",
                        chunkX, chunkZ, biomeX, biomeZ);
                    MVSCommon.LOGGER.info("[MVS]   Biome: {} (biomeQuartX={}, biomeQuartZ={})",
                        biomeId, biomeX >> 2, biomeZ >> 2);
                    MVSCommon.LOGGER.info("[MVS]   Random: seed={}, chunkX={}, chunkZ={}, combined={}",
                        seed, chunkX, chunkZ, randomSeed);
                    MVSCommon.LOGGER.info("[MVS]   Selected: {}", targetStructureId);
                }

                results.add(new ChunkPrediction(
                    chunkX, chunkZ, worldPos, targetStructureId, biomeId,
                    hasOffset, locateOffset, distance
                ));

                if (results.size() >= searchCount) {
                    break;
                }
            }
        }

        // Sort by distance and trim to requested count
        results.sort((a, b) -> Integer.compare(a.distanceFromStart, b.distanceFromStart));
        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }

        return results;
    }

    /**
     * Find chunks for a structure using config-aware placement resolution.
     * Convenience method for use from /mvs locate command.
     */
    public static List<ChunkPrediction> findChunksForStructureWithConfig(
            String targetStructureId,
            String structureSetId,
            BlockPos startPos,
            long seed,
            BiomeSampler biomeSampler,
            Registry<StructureSet> structureSetRegistry,
            int maxResults,
            int maxRadiusChunks) {

        PlacementStrategy strategy = LocateHelper.getConfiguredPlacement(structureSetId, structureSetRegistry);
        return findChunksForStructure(targetStructureId, startPos, seed, strategy, biomeSampler, maxResults, maxRadiusChunks);
    }
}