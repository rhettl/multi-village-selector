package com.rhett.multivillageselector.strategy;

import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.util.BiomeRules;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.*;

/**
 * Pure business logic for structure selection.
 * Completely testable - no static state, no logging, no side effects.
 *
 * Takes a list of configured structures and a biome, returns a weighted selection.
 */
public class StructurePicker {

    private final List<MVSConfig.ConfiguredStructure> pool;

    public StructurePicker(List<MVSConfig.ConfiguredStructure> pool) {
        this.pool = pool;
    }

    /**
     * Internal weighted structure wrapper for filtered pool.
     */
    private static class WeightedStructure {
        final MVSConfig.ConfiguredStructure structure;
        final int weight;

        WeightedStructure(MVSConfig.ConfiguredStructure structure, int weight) {
            this.structure = structure;
            this.weight = weight;
        }
    }

    /**
     * Selects a structure using MVS filter-first strategy.
     * Filters pool by biome tags, then does weighted random selection.
     *
     * @param random Random source (for deterministic testing)
     * @param biomeHolder The biome to spawn in
     * @return Selected structure, or null if no structures match
     */
    public MVSConfig.ConfiguredStructure select(Random random, Holder<Biome> biomeHolder) {
        // Phase 1: Filter by biome tags
        List<WeightedStructure> filteredPool = new ArrayList<>();

        for (MVSConfig.ConfiguredStructure configured : pool) {
            // Use BiomeRules for weight calculation (eliminates duplicate logic)
            BiomeRules rules = BiomeRules.fromMVSConfig(configured);
            int weight = rules.getWeightForBiome(biomeHolder);

            if (weight > 0) {
                filteredPool.add(new WeightedStructure(configured, weight));
            }
        }

        // No matches?
        if (filteredPool.isEmpty()) {
            return null;
        }

        // Phase 2: Weighted random selection
        return weightedSelect(filteredPool, random);
    }

    /**
     * Performs weighted random selection from a filtered pool.
     * Pure function - easy to test with fixed Random seed.
     *
     * @param pool Filtered pool with weights
     * @param random Random source
     * @return Selected structure
     */
    private MVSConfig.ConfiguredStructure weightedSelect(
            List<WeightedStructure> pool,
            Random random) {

        // Calculate total weight
        int totalWeight = pool.stream().mapToInt(ws -> ws.weight).sum();

        if (totalWeight == 0) {
            // All weights are 0? Pick randomly
            return pool.get(random.nextInt(pool.size())).structure;
        }

        // Roll random number
        int roll = random.nextInt(totalWeight);

        // Find selected structure
        int accumulated = 0;
        for (WeightedStructure weighted : pool) {
            accumulated += weighted.weight;
            if (roll < accumulated) {
                return weighted.structure;
            }
        }

        // Fallback (shouldn't happen)
        return pool.get(pool.size() - 1).structure;
    }

    /**
     * Selection result with diagnostics.
     * Useful for logging without coupling the picker to logging.
     */
    public static class SelectionResult {
        public final MVSConfig.ConfiguredStructure selected;
        public final int poolSizeBefore;
        public final int poolSizeAfter;
        public final int totalWeight;

        public SelectionResult(
                MVSConfig.ConfiguredStructure selected,
                int poolSizeBefore,
                int poolSizeAfter,
                int totalWeight) {
            this.selected = selected;
            this.poolSizeBefore = poolSizeBefore;
            this.poolSizeAfter = poolSizeAfter;
            this.totalWeight = totalWeight;
        }
    }

    /**
     * Select with diagnostics (for debugging/logging).
     */
    public SelectionResult selectWithStats(Random random, Holder<Biome> biomeHolder) {
        int sizeBefore = pool.size();

        // Filter
        List<WeightedStructure> filteredPool = new ArrayList<>();
        for (MVSConfig.ConfiguredStructure configured : pool) {
            // Use BiomeRules for weight calculation (eliminates duplicate logic)
            BiomeRules rules = BiomeRules.fromMVSConfig(configured);
            int weight = rules.getWeightForBiome(biomeHolder);

            if (weight > 0) {
                filteredPool.add(new WeightedStructure(configured, weight));
            }
        }

        int sizeAfter = filteredPool.size();
        int totalWeight = filteredPool.stream().mapToInt(ws -> ws.weight).sum();

        MVSConfig.ConfiguredStructure selected = filteredPool.isEmpty() ?
            null : weightedSelect(filteredPool, random);

        return new SelectionResult(selected, sizeBefore, sizeAfter, totalWeight);
    }
}
