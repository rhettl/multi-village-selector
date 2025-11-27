package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.util.PatternMatcher;

import com.rhett.multivillageselector.util.PatternMatcher;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Pure utility class for expanding biome tag patterns.
 * Completely testable - no dependencies, no side effects, no logging.
 *
 * Takes patterns like "#*:*" or "#minecraft:is_*" and expands them
 * against a set of available biome tags.
 */
public class BiomeTagExpander {

    /**
     * Expands biome tag patterns to concrete tags.
     *
     * @param biomes Map of tag patterns → weights (e.g., {"#*:*": 5, "#minecraft:is_plains": 10})
     * @param allBiomeTags Set of all available biome tags in the registry
     * @return Expanded map with patterns replaced by concrete tags
     *
     * Example:
     *   Input:  {"#*:*": 5, "#minecraft:is_plains": 10}
     *   Tags:   [minecraft:is_plains, minecraft:is_desert, cobblemon:is_forest]
     *   Output: {"#minecraft:is_plains": 10, "#minecraft:is_desert": 5, "#cobblemon:is_forest": 5}
     *
     * Note: More specific patterns override vague ones (specificity order).
     */
    public static Map<String, Integer> expand(
            Map<String, Integer> biomes,
            Set<ResourceLocation> allBiomeTags) {

        Map<String, Integer> expanded = new HashMap<>();

        // Sort entries by specificity (vague → specific)
        // This ensures more specific entries override vague ones
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(biomes.entrySet());
        sortedEntries.sort((a, b) -> {
            int specA = getPatternSpecificity(a.getKey());
            int specB = getPatternSpecificity(b.getKey());
            return Integer.compare(specA, specB); // Lower specificity first
        });

        // Process each entry in specificity order
        for (Map.Entry<String, Integer> entry : sortedEntries) {
            String tagPattern = entry.getKey();
            int tagWeight = entry.getValue();

            // Check if it's a pattern (contains *)
            if (tagPattern.contains("*")) {
                // Only expand patterns that start with # (tag patterns)
                // Patterns without # are for direct biome IDs and should NOT expand to tags
                if (tagPattern.startsWith("#")) {
                    String patternWithoutHash = tagPattern.substring(1);

                    for (ResourceLocation biomeTag : allBiomeTags) {
                        if (PatternMatcher.matches(biomeTag.toString(), patternWithoutHash)) {
                            expanded.put("#" + biomeTag.toString(), tagWeight);
                        }
                    }
                } else {
                    // Non-# pattern - keep as-is for runtime matching against biome IDs
                    expanded.put(tagPattern, tagWeight);
                }
            } else {
                // Literal tag - add/override directly
                expanded.put(tagPattern, tagWeight);
            }
        }

        return expanded;
    }

    /**
     * Calculate specificity of a biome tag pattern.
     * Lower number = more vague (apply first)
     * Higher number = more specific (apply last, overrides)
     *
     * Specificity levels:
     * 0 = #*:* (most vague - matches everything)
     * 1 = patterns with wildcards (e.g., #minecraft:*, #*:plains*)
     * 2 = literals (e.g., #minecraft:is_plains)
     *
     * @param pattern The biome tag pattern
     * @return Specificity level (0-2)
     */
    public static int getPatternSpecificity(String pattern) {
        // Remove # prefix for analysis
        String cleaned = pattern.startsWith("#") ? pattern.substring(1) : pattern;

        // Check for #*:* (most vague)
        if (cleaned.equals("*:*")) {
            return 0; // #*:* - most vague
        }

        // Check if contains wildcard
        if (cleaned.contains("*")) {
            return 1; // Pattern with wildcards
        }

        // Literal - most specific
        return 2;
    }

    /**
     * Result class containing expansion statistics.
     * Useful for logging/debugging without coupling the expander to logging.
     */
    public static class ExpansionResult {
        public final Map<String, Integer> expandedTags;
        public final Map<String, Integer> expansionCounts; // pattern → count of tags it matched

        public ExpansionResult(Map<String, Integer> expandedTags, Map<String, Integer> expansionCounts) {
            this.expandedTags = expandedTags;
            this.expansionCounts = expansionCounts;
        }
    }

    /**
     * Expands patterns and returns detailed statistics.
     * Use this version when you need to log expansion details.
     */
    public static ExpansionResult expandWithStats(
            Map<String, Integer> biomes,
            Set<ResourceLocation> allBiomeTags) {

        Map<String, Integer> expanded = new HashMap<>();
        Map<String, Integer> expansionCounts = new HashMap<>();

        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(biomes.entrySet());
        sortedEntries.sort((a, b) -> {
            int specA = getPatternSpecificity(a.getKey());
            int specB = getPatternSpecificity(b.getKey());
            return Integer.compare(specA, specB);
        });

        for (Map.Entry<String, Integer> entry : sortedEntries) {
            String tagPattern = entry.getKey();
            int tagWeight = entry.getValue();

            if (tagPattern.contains("*")) {
                // Only expand patterns that start with # (tag patterns)
                // Patterns without # are for direct biome IDs and should NOT expand to tags
                if (tagPattern.startsWith("#")) {
                    String patternWithoutHash = tagPattern.substring(1);

                    int matchCount = 0;
                    for (ResourceLocation biomeTag : allBiomeTags) {
                        if (PatternMatcher.matches(biomeTag.toString(), patternWithoutHash)) {
                            expanded.put("#" + biomeTag.toString(), tagWeight);
                            matchCount++;
                        }
                    }

                    expansionCounts.put(tagPattern, matchCount);
                } else {
                    // Non-# pattern - keep as-is for runtime matching against biome IDs
                    expanded.put(tagPattern, tagWeight);
                    expansionCounts.put(tagPattern, 1); // Kept as-is counts as 1
                }
            } else {
                expanded.put(tagPattern, tagWeight);
                expansionCounts.put(tagPattern, 1); // Literal counts as 1
            }
        }

        return new ExpansionResult(expanded, expansionCounts);
    }
}
