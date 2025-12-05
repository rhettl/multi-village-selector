package com.rhett.multivillageselector.util;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Expands biome patterns/tags to literal biome IDs at world load time.
 * Works for both structure biomes{} (Integer weights) and biome_frequency{} (Double values).
 *
 * Algorithm:
 * 1. Sort patterns by specificity (ascending) - lowest first
 * 2. For each pattern, expand to literal biome IDs
 * 3. Overlay results - later entries (higher specificity) naturally win
 *
 * Uses dynamic programming with caching:
 * - Wildcard tag → literal tags (cached)
 * - Literal tag → biome IDs (cached)
 * - Wildcard ID → biome IDs (cached)
 */
public class BiomePoolExpander {

    // ============ CACHES (cleared on config reload) ============

    // Cache: wildcard tag pattern → Set<literal tag strings>
    // e.g., "#*:is_*" → {"#minecraft:is_plains", "#minecraft:is_ocean", ...}
    private static final Map<String, Set<String>> WILDCARD_TAG_CACHE = new ConcurrentHashMap<>();

    // Cache: literal tag → Set<biome ID strings>
    // e.g., "#minecraft:is_plains" → {"minecraft:plains", "minecraft:sunflower_plains", ...}
    private static final Map<String, Set<String>> TAG_TO_BIOMES_CACHE = new ConcurrentHashMap<>();

    // Cache: wildcard ID pattern → Set<biome ID strings>
    // e.g., "minecraft:*ocean*" → {"minecraft:ocean", "minecraft:deep_ocean", ...}
    private static final Map<String, Set<String>> WILDCARD_ID_CACHE = new ConcurrentHashMap<>();

    /**
     * Clear all caches. Call on config reload or world unload.
     */
    public static void clearCaches() {
        WILDCARD_TAG_CACHE.clear();
        TAG_TO_BIOMES_CACHE.clear();
        WILDCARD_ID_CACHE.clear();
    }

    /**
     * Expand pattern-based biome rules to literal biome IDs.
     * Works with any Number type (Integer for weights, Double for frequency).
     *
     * @param patterns Map of patterns → values (e.g., {"#minecraft:is_plains": 10})
     * @param allBiomes Set of all biome IDs in the game (from registry)
     * @param allTags Set of all biome tag IDs (from registry)
     * @param biomeTags Map of biome ID → set of tags that biome has (with # prefix)
     * @return Map of literal biome ID → value
     */
    public static <T extends Number> Map<String, T> expand(
            Map<String, T> patterns,
            Set<ResourceLocation> allBiomes,
            Set<ResourceLocation> allTags,
            Map<ResourceLocation, Set<String>> biomeTags) {

        if (patterns == null || patterns.isEmpty()) {
            return new HashMap<>();
        }

        // Step 1: Sort patterns by specificity (ascending), then by value (ascending)
        // Lower specificity first → higher specificity overlays later
        // Lower value first → higher value overlays (tie-breaker)
        List<Map.Entry<String, T>> sorted = new ArrayList<>(patterns.entrySet());
        sorted.sort((a, b) -> {
            int specA = PatternMatcher.getSpecificity(a.getKey());
            int specB = PatternMatcher.getSpecificity(b.getKey());
            if (specA != specB) {
                return Integer.compare(specA, specB); // Ascending specificity
            }
            // Same specificity: sort by value ascending (higher value wins = comes last)
            return Double.compare(a.getValue().doubleValue(), b.getValue().doubleValue());
        });

        // Step 2 & 3: Expand each pattern and overlay onto result
        Map<String, T> result = new HashMap<>();

        for (Map.Entry<String, T> entry : sorted) {
            String pattern = entry.getKey();
            T value = entry.getValue();

            // Expand pattern to literal biome IDs
            Set<String> expandedBiomes = expandPattern(pattern, allBiomes, allTags, biomeTags);

            // Overlay: later entries naturally win (higher specificity/value)
            for (String biomeId : expandedBiomes) {
                result.put(biomeId, value);
            }
        }

        return result;
    }

    /**
     * Expand a single pattern to literal biome IDs.
     * Uses caching for performance.
     *
     * Pattern types:
     * - "#*:is_*" → wildcard tag → expand to literal tags → expand to biome IDs
     * - "#minecraft:is_plains" → literal tag → expand to biome IDs
     * - "minecraft:*ocean*" → wildcard ID → expand to biome IDs
     * - "minecraft:plains" → literal ID → just that biome
     */
    private static Set<String> expandPattern(
            String pattern,
            Set<ResourceLocation> allBiomes,
            Set<ResourceLocation> allTags,
            Map<ResourceLocation, Set<String>> biomeTags) {

        // Special case: *:* matches ALL biome IDs directly
        if (pattern.equals("*:*")) {
            Set<String> all = new HashSet<>();
            for (ResourceLocation biome : allBiomes) {
                all.add(biome.toString());
            }
            return all;
        }

        // Special case: #*:* expands all tags → biomes (skips tag pattern matching)
        if (pattern.equals("#*:*")) {
            Set<String> result = new HashSet<>();
            for (ResourceLocation tag : allTags) {
                result.addAll(expandLiteralTag("#" + tag.toString(), allBiomes, biomeTags));
            }
            return result;
        }

        boolean isTag = pattern.startsWith("#");
        boolean isWildcard = pattern.contains("*");

        if (isTag) {
            if (isWildcard) {
                // Wildcard tag: #*:is_* → literal tags → biome IDs
                return expandWildcardTag(pattern, allBiomes, allTags, biomeTags);
            } else {
                // Literal tag: #minecraft:is_plains → biome IDs
                return expandLiteralTag(pattern, allBiomes, biomeTags);
            }
        } else {
            if (isWildcard) {
                // Wildcard ID: minecraft:*ocean* → biome IDs
                return expandWildcardId(pattern, allBiomes);
            } else {
                // Literal ID: minecraft:plains → just that biome (if exists)
                return expandLiteralId(pattern, allBiomes);
            }
        }
    }

    /**
     * Expand wildcard tag pattern to biome IDs.
     * Step 1: Wildcard tag → literal tags (cached)
     * Step 2: Literal tags → biome IDs (cached per tag)
     */
    private static Set<String> expandWildcardTag(
            String pattern,
            Set<ResourceLocation> allBiomes,
            Set<ResourceLocation> allTags,
            Map<ResourceLocation, Set<String>> biomeTags) {

        // Step 1: Get literal tags from cache or compute
        Set<String> literalTags = WILDCARD_TAG_CACHE.computeIfAbsent(pattern, p -> {
            Set<String> tags = new HashSet<>();
            String patternWithoutHash = p.substring(1); // Remove #

            for (ResourceLocation tagLoc : allTags) {
                String tagString = "#" + tagLoc.toString();
                // Check if this tag matches the wildcard pattern
                if (PatternMatcher.matches(tagString, p)) {
                    tags.add(tagString);
                }
            }
            return tags;
        });

        // Step 2: Expand each literal tag to biome IDs
        Set<String> result = new HashSet<>();
        for (String literalTag : literalTags) {
            result.addAll(expandLiteralTag(literalTag, allBiomes, biomeTags));
        }

        return result;
    }

    /**
     * Expand literal tag to biome IDs (cached).
     */
    private static Set<String> expandLiteralTag(
            String tag,
            Set<ResourceLocation> allBiomes,
            Map<ResourceLocation, Set<String>> biomeTags) {

        return TAG_TO_BIOMES_CACHE.computeIfAbsent(tag, t -> {
            Set<String> biomes = new HashSet<>();

            for (ResourceLocation biome : allBiomes) {
                Set<String> tags = biomeTags.getOrDefault(biome, Collections.emptySet());
                if (tags.contains(t)) {
                    biomes.add(biome.toString());
                }
            }

            return biomes;
        });
    }

    /**
     * Expand wildcard ID pattern to biome IDs (cached).
     */
    private static Set<String> expandWildcardId(
            String pattern,
            Set<ResourceLocation> allBiomes) {

        return WILDCARD_ID_CACHE.computeIfAbsent(pattern, p -> {
            Set<String> biomes = new HashSet<>();

            for (ResourceLocation biome : allBiomes) {
                if (PatternMatcher.matches(biome.toString(), p)) {
                    biomes.add(biome.toString());
                }
            }

            return biomes;
        });
    }

    /**
     * Expand literal ID - just returns that biome if it exists.
     */
    private static Set<String> expandLiteralId(
            String biomeId,
            Set<ResourceLocation> allBiomes) {

        Set<String> result = new HashSet<>();
        ResourceLocation loc = ResourceLocation.parse(biomeId);

        if (allBiomes.contains(loc)) {
            result.add(biomeId);
        }

        return result;
    }

    // ============ CONVENIENCE METHODS ============

    /**
     * Expand with statistics for debugging/logging.
     */
    public static <T extends Number> ExpansionResult<T> expandWithStats(
            Map<String, T> patterns,
            Set<ResourceLocation> allBiomes,
            Set<ResourceLocation> allTags,
            Map<ResourceLocation, Set<String>> biomeTags) {

        Map<String, T> expanded = expand(patterns, allBiomes, allTags, biomeTags);

        // Collect stats: how many biomes each pattern matched
        Map<String, Integer> patternMatchCounts = new LinkedHashMap<>();
        for (String pattern : patterns.keySet()) {
            Set<String> matches = expandPattern(pattern, allBiomes, allTags, biomeTags);
            patternMatchCounts.put(pattern, matches.size());
        }

        return new ExpansionResult<>(expanded, patternMatchCounts);
    }

    /**
     * Result class with expansion statistics.
     */
    public static class ExpansionResult<T extends Number> {
        public final Map<String, T> expandedBiomes;
        public final Map<String, Integer> patternMatchCounts;

        public ExpansionResult(Map<String, T> expandedBiomes, Map<String, Integer> patternMatchCounts) {
            this.expandedBiomes = expandedBiomes;
            this.patternMatchCounts = patternMatchCounts;
        }
    }
}