package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.strategy.StructurePicker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
// Note: RandomSpreadStructurePlacement not imported - salt() is protected in parent class
// We use PlacementParams directly instead

import java.util.Random;

/**
 * Pure business logic for MVS-aware structure location.
 * Completely testable - no Minecraft server dependencies beyond interfaces.
 *
 * Unlike vanilla /locate which only checks biome compatibility,
 * mvsLocate() simulates MVS weighted selection to predict where
 * a specific structure WILL spawn (not just COULD spawn).
 */
public class LocateHelper {

    /**
     * Result of mvsLocate operation.
     */
    public static class LocateResult {
        public final boolean found;
        public final BlockPos pos;
        public final int chunkX;
        public final int chunkZ;
        public final String biomeId;
        public final int chunksSearched;
        public final String message;

        private LocateResult(boolean found, BlockPos pos, int chunkX, int chunkZ,
                            String biomeId, int chunksSearched, String message) {
            this.found = found;
            this.pos = pos;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.biomeId = biomeId;
            this.chunksSearched = chunksSearched;
            this.message = message;
        }

        public static LocateResult success(BlockPos pos, int chunkX, int chunkZ,
                                          String biomeId, int chunksSearched) {
            return new LocateResult(true, pos, chunkX, chunkZ, biomeId, chunksSearched,
                "Found at chunk [" + chunkX + ", " + chunkZ + "]");
        }

        public static LocateResult notFound(int chunksSearched, String reason) {
            return new LocateResult(false, null, 0, 0, null, chunksSearched, reason);
        }
    }

    /**
     * Interface for biome sampling - allows testing without full Minecraft context.
     * In production, wraps BiomeSource.getNoiseBiome().
     */
    @FunctionalInterface
    public interface BiomeSampler {
        /**
         * Get biome at the given position.
         * @param blockX Block X coordinate
         * @param blockY Block Y coordinate (typically surface height)
         * @param blockZ Block Z coordinate
         * @return Biome holder at that position
         */
        Holder<Biome> getBiomeAt(int blockX, int blockY, int blockZ);
    }

    /**
     * Interface for placement strategies - allows custom placement patterns.
     *
     * Implementations can provide different placement algorithms:
     * - RandomSpreadPlacement: Vanilla grid-based with random offset (default)
     * - ConcentricRingsPlacement: Structures at specific distances (like strongholds)
     * - RadialDensityPlacement: Density varies with distance from origin
     * - ClusterSpreadPlacement: Structures cluster at grid intersections
     *
     * Future custom patterns just need to implement this interface.
     */
    public interface PlacementStrategy {
        /**
         * Check if this chunk is a valid placement chunk.
         * @param chunkX Chunk X coordinate
         * @param chunkZ Chunk Z coordinate
         * @param seed World seed
         * @return true if structures can spawn at this chunk
         */
        boolean isPlacementChunk(int chunkX, int chunkZ, long seed);

        /**
         * Get the grid spacing for search optimization.
         * Used to calculate how many cells to search per radius increment.
         * @return Approximate spacing in chunks between potential placements
         */
        int getApproximateSpacing();

        /**
         * Iterate potential placement chunks for locate search.
         * Yields [chunkX, chunkZ] pairs in order of distance from start.
         *
         * @param startChunkX Starting chunk X
         * @param startChunkZ Starting chunk Z
         * @param seed World seed
         * @param maxRadiusChunks Maximum search radius
         * @return Iterable of [chunkX, chunkZ] arrays
         */
        Iterable<int[]> iteratePlacements(int startChunkX, int startChunkZ, long seed, int maxRadiusChunks);

        /**
         * Get the world position to report for /locate at a given chunk.
         * This applies the locateOffset from the structure placement.
         *
         * Default implementation returns chunk origin (minBlockX, 0, minBlockZ).
         * Vanilla villages use (0, 0, 0) offset so this reports chunk origin.
         * Mods can customize this offset.
         *
         * @param chunkX Chunk X coordinate
         * @param chunkZ Chunk Z coordinate
         * @return BlockPos to report for /locate command
         */
        default BlockPos getLocatePos(int chunkX, int chunkZ) {
            // Default: chunk origin (minBlockX, 0, minBlockZ)
            return new BlockPos(chunkX << 4, 0, chunkZ << 4);
        }
    }

    /**
     * Spread type determines how structures are distributed within each grid cell.
     * Vanilla Minecraft only supports LINEAR and TRIANGULAR.
     * MVS adds additional spread types for more control.
     */
    public enum SpreadType {
        /** Uniform random distribution (vanilla default) */
        LINEAR,
        /** Bell curve toward cell center (vanilla triangular) */
        TRIANGULAR,
        /** Biased toward cell edges */
        EDGE_BIASED,
        /** Biased toward cell corners */
        CORNER_BIASED,
        /** Gaussian distribution centered in cell */
        GAUSSIAN,
        /** Always at cell center (deterministic grid) */
        FIXED_CENTER
    }

    /**
     * Vanilla-style random spread placement (grid with random offset).
     * This is the default placement used by minecraft:villages.
     *
     * Supports multiple spread types for different distribution patterns.
     */
    public static class RandomSpreadPlacement implements PlacementStrategy {
        public final int spacing;
        public final int separation;
        public final int salt;
        public final SpreadType spreadType;
        public final Vec3i locateOffset; // Offset for /locate position reporting

        // Legacy field for backwards compatibility
        public final boolean triangular;

        /**
         * Full constructor with locateOffset support.
         */
        public RandomSpreadPlacement(int spacing, int separation, int salt, SpreadType spreadType, Vec3i locateOffset) {
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
            this.spreadType = spreadType;
            this.locateOffset = locateOffset != null ? locateOffset : Vec3i.ZERO;
            this.triangular = (spreadType == SpreadType.TRIANGULAR);
        }

        public RandomSpreadPlacement(int spacing, int separation, int salt, SpreadType spreadType) {
            this(spacing, separation, salt, spreadType, Vec3i.ZERO);
        }

        /** Legacy constructor for backwards compatibility */
        public RandomSpreadPlacement(int spacing, int separation, int salt, boolean triangular) {
            this(spacing, separation, salt, triangular ? SpreadType.TRIANGULAR : SpreadType.LINEAR, Vec3i.ZERO);
        }

        /**
         * Default vanilla village placement (linear spread).
         */
        public static RandomSpreadPlacement villageDefaults() {
            return new RandomSpreadPlacement(34, 8, 10387312, SpreadType.LINEAR, Vec3i.ZERO);
        }

        /**
         * Create with specific spread type.
         */
        public static RandomSpreadPlacement withSpread(int spacing, int separation, int salt, SpreadType spread) {
            return new RandomSpreadPlacement(spacing, separation, salt, spread, Vec3i.ZERO);
        }

        /**
         * Get the world position for /locate output at a given chunk.
         * Uses locateOffset from the structure placement definition.
         *
         * This matches vanilla's StructurePlacement.getLocatePos():
         * new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ()).offset(locateOffset)
         */
        @Override
        public BlockPos getLocatePos(int chunkX, int chunkZ) {
            int minBlockX = chunkX << 4; // chunkX * 16
            int minBlockZ = chunkZ << 4;
            return new BlockPos(
                minBlockX + locateOffset.getX(),
                locateOffset.getY(),
                minBlockZ + locateOffset.getZ()
            );
        }

        @Override
        public boolean isPlacementChunk(int chunkX, int chunkZ, long seed) {
            int cellX = Math.floorDiv(chunkX, spacing);
            int cellZ = Math.floorDiv(chunkZ, spacing);
            int[] actual = getPlacementChunkForCell(cellX, cellZ, seed);
            return actual[0] == chunkX && actual[1] == chunkZ;
        }

        @Override
        public int getApproximateSpacing() {
            return spacing;
        }

        @Override
        public Iterable<int[]> iteratePlacements(int startChunkX, int startChunkZ, long seed, int maxRadiusChunks) {
            return () -> new java.util.Iterator<int[]>() {
                int startCellX = Math.floorDiv(startChunkX, spacing);
                int startCellZ = Math.floorDiv(startChunkZ, spacing);
                int radius = 0;
                int maxCellRadius = maxRadiusChunks / spacing + 1;
                int dx = 0, dz = 0;
                boolean hasNext = true;

                @Override
                public boolean hasNext() {
                    return hasNext && radius <= maxCellRadius;
                }

                @Override
                public int[] next() {
                    int cellX = startCellX + dx;
                    int cellZ = startCellZ + dz;
                    int[] result = getPlacementChunkForCell(cellX, cellZ, seed);

                    // Advance to next position in spiral
                    advanceSpiral();

                    return result;
                }

                private void advanceSpiral() {
                    if (radius == 0) {
                        radius = 1;
                        dx = -1;
                        dz = -1;
                        return;
                    }

                    // Move around perimeter
                    if (dz == -radius && dx < radius) {
                        dx++;
                    } else if (dx == radius && dz < radius) {
                        dz++;
                    } else if (dz == radius && dx > -radius) {
                        dx--;
                    } else if (dx == -radius && dz > -radius) {
                        dz--;
                        if (dz == -radius) {
                            radius++;
                            dx = -radius;
                            dz = -radius;
                        }
                    }

                    if (radius > maxCellRadius) {
                        hasNext = false;
                    }
                }
            };
        }

        /**
         * Calculate placement chunk for a specific grid cell.
         * Uses the configured spread type to determine offset distribution.
         */
        public int[] getPlacementChunkForCell(int cellX, int cellZ, long seed) {
            long cellSeed = (long)cellX * 341873128712L + (long)cellZ * 132897987541L + seed + (long)salt;
            Random random = new Random(cellSeed);

            int maxOffset = spacing - separation;
            int offsetX, offsetZ;

            switch (spreadType) {
                case TRIANGULAR:
                    // Bell curve toward center (sum of two randoms)
                    offsetX = (random.nextInt(maxOffset) + random.nextInt(maxOffset)) / 2;
                    offsetZ = (random.nextInt(maxOffset) + random.nextInt(maxOffset)) / 2;
                    break;

                case EDGE_BIASED:
                    // Biased toward edges (inverse of triangular)
                    int rawX = random.nextInt(maxOffset);
                    int rawZ = random.nextInt(maxOffset);
                    // Push toward edges by taking max/min of two samples
                    int altX = random.nextInt(maxOffset);
                    int altZ = random.nextInt(maxOffset);
                    offsetX = (rawX > maxOffset/2) ? Math.max(rawX, altX) : Math.min(rawX, altX);
                    offsetZ = (rawZ > maxOffset/2) ? Math.max(rawZ, altZ) : Math.min(rawZ, altZ);
                    break;

                case CORNER_BIASED:
                    // Biased toward corners (both axes pushed to extremes)
                    int x1 = random.nextInt(maxOffset);
                    int x2 = random.nextInt(maxOffset);
                    int z1 = random.nextInt(maxOffset);
                    int z2 = random.nextInt(maxOffset);
                    // Take the more extreme value for each axis
                    offsetX = (Math.abs(x1 - maxOffset/2) > Math.abs(x2 - maxOffset/2)) ? x1 : x2;
                    offsetZ = (Math.abs(z1 - maxOffset/2) > Math.abs(z2 - maxOffset/2)) ? z1 : z2;
                    break;

                case GAUSSIAN:
                    // Gaussian distribution centered in cell
                    double gaussX = random.nextGaussian() * (maxOffset / 6.0) + (maxOffset / 2.0);
                    double gaussZ = random.nextGaussian() * (maxOffset / 6.0) + (maxOffset / 2.0);
                    offsetX = Math.max(0, Math.min(maxOffset - 1, (int) gaussX));
                    offsetZ = Math.max(0, Math.min(maxOffset - 1, (int) gaussZ));
                    break;

                case FIXED_CENTER:
                    // Always at cell center (deterministic)
                    offsetX = maxOffset / 2;
                    offsetZ = maxOffset / 2;
                    break;

                case LINEAR:
                default:
                    // Uniform random (vanilla default)
                    offsetX = random.nextInt(maxOffset);
                    offsetZ = random.nextInt(maxOffset);
                    break;
            }

            return new int[] { cellX * spacing + offsetX, cellZ * spacing + offsetZ };
        }
    }

    /**
     * Concentric rings placement (like strongholds).
     * Structures spawn in rings at specific distances from world origin.
     *
     * Future implementation - placeholder for extensibility demonstration.
     */
    public static class ConcentricRingsPlacement implements PlacementStrategy {
        public final int[] ringDistances; // Distance of each ring in chunks
        public final int structuresPerRing;
        public final int salt;

        public ConcentricRingsPlacement(int[] ringDistances, int structuresPerRing, int salt) {
            this.ringDistances = ringDistances;
            this.structuresPerRing = structuresPerRing;
            this.salt = salt;
        }

        @Override
        public boolean isPlacementChunk(int chunkX, int chunkZ, long seed) {
            // Calculate distance from origin
            double dist = Math.sqrt(chunkX * chunkX + chunkZ * chunkZ);

            // Check if near any ring
            for (int ringDist : ringDistances) {
                if (Math.abs(dist - ringDist) < 2) { // Within 2 chunks of ring
                    // Check if this is a valid angle position on the ring
                    double angle = Math.atan2(chunkZ, chunkX);
                    double angleStep = 2 * Math.PI / structuresPerRing;

                    Random random = new Random(seed + salt + ringDist);
                    double startAngle = random.nextDouble() * angleStep;

                    for (int i = 0; i < structuresPerRing; i++) {
                        double targetAngle = startAngle + i * angleStep;
                        if (Math.abs(normalizeAngle(angle - targetAngle)) < 0.1) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private double normalizeAngle(double angle) {
            while (angle > Math.PI) angle -= 2 * Math.PI;
            while (angle < -Math.PI) angle += 2 * Math.PI;
            return angle;
        }

        @Override
        public int getApproximateSpacing() {
            return ringDistances.length > 0 ? ringDistances[0] / structuresPerRing : 32;
        }

        @Override
        public Iterable<int[]> iteratePlacements(int startChunkX, int startChunkZ, long seed, int maxRadiusChunks) {
            // Implementation would iterate through ring positions sorted by distance
            // Placeholder - returns empty for now
            return java.util.Collections.emptyList();
        }
    }

    // Legacy alias for backwards compatibility
    public static class PlacementParams extends RandomSpreadPlacement {
        public PlacementParams(int spacing, int separation, int salt, boolean triangular) {
            super(spacing, separation, salt, triangular);
        }

        public static PlacementParams villageDefaults() {
            return new PlacementParams(34, 8, 10387312, false);
        }
    }

    // Maximum search radius in chunks (matches vanilla /locate)
    public static final int MAX_SEARCH_RADIUS_CHUNKS = 100;

    // Default surface Y for biome sampling
    public static final int DEFAULT_SURFACE_Y = 64;

    /**
     * Locate the nearest position where a specific structure WILL spawn.
     *
     * Unlike vanilla /locate which only checks biome compatibility,
     * this simulates MVS weighted selection to find where this specific
     * structure would actually be selected (accounting for competition
     * from other structures in the pool).
     *
     * @param structureId The structure to find (e.g., "minecraft:village_plains")
     * @param startPos Starting position to search from
     * @param seed World seed
     * @param strategy Placement strategy (RandomSpreadPlacement, ConcentricRingsPlacement, etc.)
     * @param biomeSampler Interface to sample biomes at positions
     * @param maxRadiusChunks Maximum search radius in chunks (default: 100)
     * @return LocateResult with position if found, or reason if not
     */
    public static LocateResult mvsLocate(
            ResourceLocation structureId,
            BlockPos startPos,
            long seed,
            PlacementStrategy strategy,
            BiomeSampler biomeSampler,
            int maxRadiusChunks) {

        // Validate structure is in MVS pool
        boolean inPool = MVSConfig.structurePool.stream()
            .anyMatch(s -> s.structure != null && s.structure.equals(structureId));

        if (!inPool) {
            return LocateResult.notFound(0,
                "Structure '" + structureId + "' is not in MVS pool");
        }

        // Calculate starting chunk
        int startChunkX = startPos.getX() >> 4;
        int startChunkZ = startPos.getZ() >> 4;

        // Use strategy's placement iterator
        int chunksSearched = 0;

        for (int[] placementChunk : strategy.iteratePlacements(startChunkX, startChunkZ, seed, maxRadiusChunks)) {
            int chunkX = placementChunk[0];
            int chunkZ = placementChunk[1];

            // Check distance from start (in chunks)
            int distChunksX = Math.abs(chunkX - startChunkX);
            int distChunksZ = Math.abs(chunkZ - startChunkZ);
            if (distChunksX > maxRadiusChunks || distChunksZ > maxRadiusChunks) {
                continue; // Outside search radius
            }

            chunksSearched++;

            // Get biome at chunk center (for biome sampling)
            // Note: Biome sampling uses chunk center, but locate output uses locateOffset
            // Sample biome at chunk NW corner (placement anchor point)
            // Structure starter piece is placed here, then expands in random direction based on rotation
            int biomeX = chunkX << 4;
            int biomeZ = chunkZ << 4;
            Holder<Biome> biomeHolder = biomeSampler.getBiomeAt(
                biomeX, DEFAULT_SURFACE_Y, biomeZ);

            // Simulate MVS selection at this location
            MVSConfig.ConfiguredStructure selected = simulateSelection(
                chunkX, chunkZ, seed, biomeHolder);

            // Check if our target structure was selected
            if (selected != null && !selected.isEmpty &&
                selected.structure != null && selected.structure.equals(structureId)) {

                String biomeId = biomeHolder.unwrapKey()
                    .map(k -> k.location().toString())
                    .orElse("unknown");

                // Use strategy's getLocatePos() for the reported position
                // This applies the locateOffset from the structure placement
                BlockPos locatePos = strategy.getLocatePos(chunkX, chunkZ);

                return LocateResult.success(
                    locatePos,
                    chunkX, chunkZ, biomeId, chunksSearched);
            }
        }

        return LocateResult.notFound(chunksSearched,
            "No spawn point found within " + maxRadiusChunks + " chunks");
    }

    /**
     * Overload with default max radius.
     */
    public static LocateResult mvsLocate(
            ResourceLocation structureId,
            BlockPos startPos,
            long seed,
            PlacementStrategy strategy,
            BiomeSampler biomeSampler) {
        return mvsLocate(structureId, startPos, seed, strategy, biomeSampler, MAX_SEARCH_RADIUS_CHUNKS);
    }

    /**
     * Legacy overload for backwards compatibility with PlacementParams.
     */
    public static LocateResult mvsLocate(
            ResourceLocation structureId,
            BlockPos startPos,
            long seed,
            PlacementParams placement,
            BiomeSampler biomeSampler,
            int maxRadiusChunks) {
        return mvsLocate(structureId, startPos, seed, (PlacementStrategy) placement, biomeSampler, maxRadiusChunks);
    }

    /**
     * Legacy overload for backwards compatibility with PlacementParams.
     */
    public static LocateResult mvsLocate(
            ResourceLocation structureId,
            BlockPos startPos,
            long seed,
            PlacementParams placement,
            BiomeSampler biomeSampler) {
        return mvsLocate(structureId, startPos, seed, (PlacementStrategy) placement, biomeSampler, MAX_SEARCH_RADIUS_CHUNKS);
    }

    /**
     * Calculate the placement chunk within a grid cell.
     * Convenience method for RandomSpreadPlacement.
     *
     * @param cellX Grid cell X
     * @param cellZ Grid cell Z
     * @param seed World seed
     * @param placement RandomSpreadPlacement parameters
     * @return [chunkX, chunkZ] of placement position within this cell
     */
    public static int[] getPlacementChunk(int cellX, int cellZ, long seed, RandomSpreadPlacement placement) {
        return placement.getPlacementChunkForCell(cellX, cellZ, seed);
    }

    /**
     * Legacy overload for backwards compatibility.
     */
    public static int[] getPlacementChunk(int cellX, int cellZ, long seed, PlacementParams placement) {
        return placement.getPlacementChunkForCell(cellX, cellZ, seed);
    }

    /**
     * Simulate MVS weighted selection at a specific chunk.
     * Uses the same deterministic random formula as actual chunk generation.
     * Includes biome_frequency simulation for accurate predictions.
     *
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param seed World seed
     * @param biomeHolder Biome at this location
     * @return Selected structure, or null if none match biome or frequency check fails
     */
    public static MVSConfig.ConfiguredStructure simulateSelection(
            int chunkX, int chunkZ, long seed, Holder<Biome> biomeHolder) {

        // Same random formula as StructureInterceptor and MVSStrategyHandler
        Random random = new Random(seed + chunkX * 341873128712L + chunkZ * 132897987541L);

        // Check biome_frequency first (same as StructureInterceptor.rollBiomeFrequency)
        if (!simulateBiomeFrequency(random, biomeHolder)) {
            return null; // Frequency check failed - no spawn at this location
        }

        // Use StructurePicker for consistent selection logic
        StructurePicker picker = new StructurePicker(MVSConfig.structurePool);
        return picker.select(random, biomeHolder);
    }

    /**
     * Simulate biome frequency roll for predictions.
     * Matches the logic in StructureInterceptor.rollBiomeFrequency().
     *
     * @param random Random instance (already seeded for this chunk)
     * @param biomeHolder Biome at this location
     * @return true if frequency roll passes, false if spawn should be skipped
     */
    private static boolean simulateBiomeFrequency(Random random, Holder<Biome> biomeHolder) {
        // If no biome_frequency configured, always pass (default 100%)
        if (MVSConfig.biomeFrequency.isEmpty()) {
            return true;
        }

        // Use unified pattern matcher to get frequency
        // Supports direct biome IDs, tags, and patterns with specificity resolution
        double frequency = PatternMatcher.getValueForBiome(
            MVSConfig.biomeFrequency,
            biomeHolder,
            1.0  // Default: 100% spawn rate
        );

        // Roll random and check against frequency
        double roll = random.nextDouble();
        return roll < frequency;
    }

    /**
     * Check if a specific chunk is a placement chunk.
     * Works with any PlacementStrategy implementation.
     *
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param seed World seed
     * @param strategy Placement strategy
     * @return true if this chunk is a valid placement chunk
     */
    public static boolean isPlacementChunk(int chunkX, int chunkZ, long seed, PlacementStrategy strategy) {
        return strategy.isPlacementChunk(chunkX, chunkZ, seed);
    }

    /**
     * Legacy overload for backwards compatibility.
     */
    public static boolean isPlacementChunk(int chunkX, int chunkZ, long seed, PlacementParams placement) {
        return placement.isPlacementChunk(chunkX, chunkZ, seed);
    }

    /**
     * Create a BiomeSampler from a BiomeSource and Climate.Sampler.
     * For use in actual Minecraft context.
     */
    public static BiomeSampler createBiomeSampler(BiomeSource biomeSource, Climate.Sampler climateSampler) {
        return (blockX, blockY, blockZ) ->
            biomeSource.getNoiseBiome(blockX >> 2, blockY >> 2, blockZ >> 2, climateSampler);
    }

    /**
     * Create a surface-aware biome sampler that estimates surface height using noise.
     * This avoids cave biomes AND handles 3D biome mods like Terralith correctly.
     *
     * @param biomeSource The biome source
     * @param climateSampler Climate sampler for biome lookup
     * @param generator ChunkGenerator for height estimation
     * @param heightAccessor Level height accessor
     * @param randomState Random state for noise-based height lookup
     * @return BiomeSampler that samples at estimated surface level (ignores Y parameter)
     */
    public static BiomeSampler createSurfaceAwareBiomeSampler(
            BiomeSource biomeSource,
            Climate.Sampler climateSampler,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor heightAccessor,
            net.minecraft.world.level.levelgen.RandomState randomState) {

        return (blockX, blockY, blockZ) -> {
            // Estimate surface height using noise (no chunk loading required)
            // getBaseHeight() returns Y of first AIR block above surface (not solid block)
            // This equals getFirstFreeHeight() which vanilla uses for structure placement
            int surfaceY = generator.getBaseHeight(
                blockX, blockZ,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                heightAccessor,
                randomState);

            // Sample biome at surfaceY (no offset needed)
            // getBaseHeight() already returns the Y where structures sit (first air above ground)
            int structureY = surfaceY;
            int quartX = blockX >> 2;
            int quartY = structureY >> 2;
            int quartZ = blockZ >> 2;
            var biome = biomeSource.getNoiseBiome(quartX, quartY, quartZ, climateSampler);

            return biome;
        };
    }

    // ============================================================
    // CONFIG-AWARE METHODS
    // ============================================================

    /**
     * Locate using placement from MVSConfig.placement (with registry fallback).
     * This is the recommended method for commands and runtime use.
     *
     * @param structureId The structure to find
     * @param structureSetId The structure set (for placement lookup, e.g., "minecraft:villages")
     * @param startPos Starting position to search from
     * @param seed World seed
     * @param biomeSampler Interface to sample biomes
     * @param structureSetRegistry Registry for inheritance (can be null)
     * @param maxRadiusChunks Maximum search radius
     * @return LocateResult with position if found
     */
    public static LocateResult mvsLocateWithConfig(
            ResourceLocation structureId,
            String structureSetId,
            BlockPos startPos,
            long seed,
            BiomeSampler biomeSampler,
            net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.StructureSet> structureSetRegistry,
            int maxRadiusChunks) {

        // Resolve placement from config + registry
        PlacementResolver.ResolvedPlacement resolved = PlacementResolver.resolve(structureSetId, structureSetRegistry);
        PlacementStrategy strategy = resolved.toStrategy();

        return mvsLocate(structureId, startPos, seed, strategy, biomeSampler, maxRadiusChunks);
    }

    /**
     * Locate using placement from MVSConfig.placement (with registry fallback).
     * Uses default max search radius.
     */
    public static LocateResult mvsLocateWithConfig(
            ResourceLocation structureId,
            String structureSetId,
            BlockPos startPos,
            long seed,
            BiomeSampler biomeSampler,
            net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.StructureSet> structureSetRegistry) {
        return mvsLocateWithConfig(structureId, structureSetId, startPos, seed, biomeSampler, structureSetRegistry, MAX_SEARCH_RADIUS_CHUNKS);
    }

    /**
     * Get resolved placement strategy for a structure set from config.
     *
     * @param structureSetId The structure set ID
     * @param structureSetRegistry Registry for inheritance (can be null)
     * @return PlacementStrategy resolved from config + registry
     */
    public static PlacementStrategy getConfiguredPlacement(
            String structureSetId,
            net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.StructureSet> structureSetRegistry) {
        PlacementResolver.ResolvedPlacement resolved = PlacementResolver.resolve(structureSetId, structureSetRegistry);
        return resolved.toStrategy();
    }

}
