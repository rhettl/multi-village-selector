package com.rhett.multivillageselector.util;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable data class representing the biome rules for a structure.
 * Can be constructed from MVS config or vanilla registry.
 *
 * Used for:
 * - Validation (does structure match biome?)
 * - Debugging (what biomes are allowed?)
 * - Testing (pure data, easy to inspect)
 */
public class BiomeRules {
    public enum Source {
        MVS_CONFIG,      // From MVS structure_pool
        VANILLA_REGISTRY, // From Structure.biomes()
        EMPTY            // Not found
    }

    public final Source source;
    public final List<String> tags;           // ["#minecraft:is_plains", ...]
    public final List<String> directBiomes;   // ["minecraft:plains", ...] (from vanilla Direct sets)
    public final Map<String, Integer> weights; // Tag/biome -> weight mapping

    private BiomeRules(Source source,
                       List<String> tags,
                       List<String> directBiomes,
                       Map<String, Integer> weights) {
        this.source = source;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        this.directBiomes = Collections.unmodifiableList(new ArrayList<>(directBiomes));
        this.weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
    }

    /**
     * Factory: Create BiomeRules from MVS ConfiguredStructure
     */
    public static BiomeRules fromMVSConfig(com.rhett.multivillageselector.config.MVSConfig.ConfiguredStructure config) {
        return new BiomeRules(
            Source.MVS_CONFIG,
            new ArrayList<>(config.biomes.keySet()),
            List.of(),
            config.biomes
        );
    }

    /**
     * Factory: Create BiomeRules from vanilla Structure's biomes() HolderSet
     */
    public static BiomeRules fromVanillaStructure(net.minecraft.world.level.levelgen.structure.Structure structure,
                                                    net.minecraft.core.RegistryAccess registryAccess) {
        var biomeHolderSet = structure.biomes();
        var unwrapped = biomeHolderSet.unwrap();

        // Either.left = TagKey (Named set), Either.right = List<Holder> (Direct set)
        return unwrapped.map(
            // Left: Named set (single tag)
            tagKey -> {
                String tagString = "#" + tagKey.location().toString();
                return new BiomeRules(
                    Source.VANILLA_REGISTRY,
                    List.of(tagString),
                    List.of(),
                    Map.of() // Vanilla doesn't have weights
                );
            },
            // Right: Direct set (list of biome holders)
            holders -> {
                List<String> biomeIds = new ArrayList<>();
                Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

                for (Holder<Biome> holder : holders) {
                    holder.unwrapKey().ifPresent(key ->
                        biomeIds.add(key.location().toString())
                    );
                }

                return new BiomeRules(
                    Source.VANILLA_REGISTRY,
                    List.of(),
                    biomeIds,
                    Map.of() // Vanilla doesn't have weights
                );
            }
        );
    }

    /**
     * Factory: Empty rules (structure not found)
     */
    public static BiomeRules empty() {
        return new BiomeRules(Source.EMPTY, List.of(), List.of(), Map.of());
    }

    /**
     * Check if a biome matches these rules.
     * Uses unified pattern matching to support:
     * - Literal tags: "#minecraft:is_plains"
     * - Tag patterns: "#*:*ocean*"
     * - Literal biome IDs: "minecraft:plains"
     * - Biome ID patterns: "*:*ocean*"
     */
    public boolean matches(Holder<Biome> biomeHolder) {
        // Use getWeightForBiome which has full pattern matching support
        // If weight > 0, the biome matches
        return getWeightForBiome(biomeHolder) > 0;
    }

    /**
     * Get the matching pattern/tag for a biome (for debugging)
     * Returns null if no match.
     * Supports patterns via PatternMatcher.
     */
    public String getMatchingTag(Holder<Biome> biomeHolder) {
        // Get biome ID and tags for pattern matching
        String biomeId = biomeHolder.unwrapKey()
            .map(k -> k.location().toString())
            .orElse(null);

        java.util.Set<String> biomeStrings = new java.util.HashSet<>();
        if (biomeId != null) {
            biomeStrings.add(biomeId);
        }
        try {
            for (var tagKey : biomeHolder.tags().toList()) {
                biomeStrings.add("#" + tagKey.location().toString());
            }
        } catch (IllegalStateException e) {
            // Stream already closed
        }

        // Find matching pattern with highest specificity
        String bestMatch = null;
        int bestSpecificity = Integer.MIN_VALUE;

        for (String pattern : weights.keySet()) {
            for (String biomeString : biomeStrings) {
                if (PatternMatcher.matches(biomeString, pattern)) {
                    int specificity = PatternMatcher.getSpecificity(pattern);
                    if (specificity > bestSpecificity) {
                        bestSpecificity = specificity;
                        bestMatch = pattern;
                    }
                    break;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Get weight for a specific tag/biome (from config)
     */
    public int getWeight(String tagOrBiome) {
        return weights.getOrDefault(tagOrBiome, 0);
    }

    /**
     * Get the weight for this structure in a specific biome.
     *
     * v0.4.0: First tries O(1) lookup (biome patterns pre-expanded at config load).
     * Falls back to pattern matching for backward compatibility with tests
     * and any code that uses tag-based weights directly.
     *
     * @param biomeHolder The biome to check
     * @return Weight (0 if no match)
     */
    public int getWeightForBiome(Holder<Biome> biomeHolder) {
        // Get biome ID for direct lookup
        String biomeId = biomeHolder.unwrapKey()
            .map(k -> k.location().toString())
            .orElse(null);

        if (biomeId == null) {
            return 0;
        }

        // First try O(1) direct lookup (v0.4.0 pre-expanded biome IDs)
        Integer weight = weights.get(biomeId);
        if (weight != null) {
            return weight;
        }

        // Fallback to pattern matching (for tests and backward compatibility)
        return PatternMatcher.getValueForBiome(weights, biomeHolder, 0);
    }

    /**
     * Check if these rules are from MVS config
     */
    public boolean isMVSControlled() {
        return source == Source.MVS_CONFIG;
    }

    /**
     * Check if these rules are empty (structure not found)
     */
    public boolean isEmpty() {
        return source == Source.EMPTY;
    }

    @Override
    public String toString() {
        return String.format("BiomeRules{source=%s, tags=%d, directBiomes=%d, weights=%d}",
            source, tags.size(), directBiomes.size(), weights.size());
    }
}
