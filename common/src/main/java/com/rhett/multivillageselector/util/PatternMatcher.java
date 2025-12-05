package com.rhett.multivillageselector.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Pure utility class for wildcard pattern matching with biome support.
 * Handles unified pattern matching for both biomes and biome_frequency.
 * Completely testable - no side effects.
 *
 * Supports:
 * - Direct biome IDs: "minecraft:plains"
 * - Biome tags: "#minecraft:is_plains"
 * - Patterns with wildcards: "minecraft:*", "#*:*", "*craft*:*village*"
 *
 * Specificity scoring (higher = more specific, wins conflicts):
 * - Score = 20 - (wildcard_count × 5) + (1 if no # prefix)
 * - Tie-breaker: Higher value wins
 */
public class PatternMatcher {

    // Cache for compiled regex patterns (pattern string -> compiled Pattern)
    private static final ConcurrentHashMap<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

    // Cache for biome tag sets (biome ResourceLocation -> set of strings to check)
    private static final ConcurrentHashMap<ResourceLocation, Set<String>> BIOME_TAGS_CACHE = new ConcurrentHashMap<>();

    /**
     * Clear all caches. Call on config reload or world unload.
     */
    public static void clearCaches() {
        REGEX_CACHE.clear();
        BIOME_TAGS_CACHE.clear();
    }

    /**
     * Get the value for a biome from a pattern map.
     * Checks both the biome's direct ID and all its tags.
     * Returns the value from the most specific matching pattern.
     * If multiple patterns have same specificity, returns the highest value.
     *
     * @param patternMap Map of patterns to values (e.g., {"minecraft:plains": 10, "#minecraft:*": 5})
     * @param biomeHolder The biome to check
     * @param defaultValue Value to return if no patterns match
     * @return Value from most specific matching pattern, or defaultValue if none match
     */
    public static <T extends Number> T getValueForBiome(
            Map<String, T> patternMap,
            Holder<Biome> biomeHolder,
            T defaultValue) {

        if (patternMap == null || patternMap.isEmpty()) {
            return defaultValue;
        }

        // Get biome key for cache lookup
        var biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) {
            return defaultValue;
        }

        ResourceLocation biomeLocation = biomeKey.get().location();

        // Use cached biome tags if available, otherwise compute and cache
        Set<String> stringsToCheck = BIOME_TAGS_CACHE.computeIfAbsent(biomeLocation, loc -> {
            Set<String> strings = new HashSet<>();

            // Add direct biome ID
            strings.add(loc.toString());

            // Add all biome tags (with # prefix)
            // Materialize stream immediately to avoid "stream already closed" errors
            try {
                for (var tagKey : biomeHolder.tags().toList()) {
                    strings.add("#" + tagKey.location().toString());
                }
            } catch (IllegalStateException e) {
                // Stream already closed - this can happen with mocked biomes in tests
                // Direct biome ID matching will still work, just without tag matching
            }

            return strings;
        });

        // Find best match
        T bestValue = defaultValue;
        int maxSpecificity = Integer.MIN_VALUE;  // Allow negative specificities (e.g., "*:*" = -10)

        // Note: Debug logging removed from hot loop in v0.4.0 (caused 30-second freezes)
        // Pattern matching is now only used as fallback; main path uses O(1) lookup

        for (Map.Entry<String, T> entry : patternMap.entrySet()) {
            String pattern = entry.getKey();
            T value = entry.getValue();

            // Check if pattern matches any of the biome's strings
            for (String biomeString : stringsToCheck) {
                if (matches(biomeString, pattern)) {
                    int specificity = getSpecificity(pattern);

                    // Higher specificity wins
                    if (specificity > maxSpecificity) {
                        maxSpecificity = specificity;
                        bestValue = value;
                    }
                    // Same specificity: higher value wins (tie-breaker)
                    else if (specificity == maxSpecificity && value.doubleValue() > bestValue.doubleValue()) {
                        bestValue = value;
                    }

                    break; // Pattern matched, no need to check other strings
                }
            }
        }

        return bestValue;
    }

    /**
     * Calculate specificity score for a pattern.
     * Higher score = more specific.
     *
     * Scoring rules:
     * - Base: 20
     * - No # prefix: +1
     * - 2+ literal chars before colon: +2
     * - 2+ literal chars after colon: +2
     * - Each wildcard: -5
     * - Special cases: "*:*" = -10, "#*:*" = -20 (ultimate fallbacks)
     *
     * Examples:
     * - "minecraft:plains" = 25 (literal ID, most specific)
     * - "#minecraft:has_structure/village_plains" = 24 (long tag)
     * - "#minecraft:is_plains" = 24 (tag)
     * - "minecraft:*" = 18 (1 wildcard, literal)
     * - "#minecraft:*" = 17 (1 wildcard, tag)
     * - "#*craft*:*village*" = 4 (has literal content)
     * - "*:*" = -10 (pure wildcard, literal)
     * - "#*:*" = -20 (pure wildcard, tag, lowest)
     */
    public static int getSpecificity(String pattern) {
        // Special cases for ultimate fallbacks
        if (pattern.equals("*:*")) return -10;
        if (pattern.equals("#*:*")) return -20;

        int score = 20;

        // No # prefix: +1
        if (!pattern.startsWith("#")) score += 1;

        // Split on colon
        String[] parts = pattern.split(":", 2);
        String afterColon = parts.length > 1 ? parts[1] : "";

        // 2+ literal chars before colon: +2
        if (parts[0].matches(".*[a-z0-9_/-]{2,}.*")) score += 2;

        // 2+ literal chars after colon: +2
        if (afterColon.matches(".*[a-z0-9_/-]{2,}.*")) score += 2;

        // Each wildcard: -5
        score -= (pattern.chars().filter(c -> c == '*').count() * 5);

        return score;
    }

    /**
     * Checks if a string matches a wildcard pattern.
     *
     * @param input The string to test (e.g., "minecraft:village_plains" or "#minecraft:is_ocean")
     * @param pattern The pattern to match against (e.g., "minecraft:village_*" or "#*:is_ocean")
     * @return true if input matches pattern
     *
     * Important: # prefix must match!
     *   - Pattern without # only matches inputs without #
     *   - Pattern with # only matches inputs with #
     *
     * Examples:
     *   matches("minecraft:village_plains", "minecraft:village_*") → true
     *   matches("minecraft:cold_ocean", "*:*ocean*") → true
     *   matches("#minecraft:is_ocean", "#*:*ocean*") → true
     *   matches("#minecraft:is_ocean", "*:*ocean*") → FALSE (# mismatch)
     *   matches("minecraft:beach", "#*:is_beach") → FALSE (# mismatch)
     */
    public static boolean matches(String input, String pattern) {
        // # prefix must match: non-# patterns don't match # inputs and vice versa
        boolean inputIsTag = input.startsWith("#");
        boolean patternIsTag = pattern.startsWith("#");
        if (inputIsTag != patternIsTag) {
            return false;
        }

        Pattern regex = wildcardToRegex(pattern);
        return regex.matcher(input).matches();
    }

    /**
     * Converts a wildcard pattern to a regular expression.
     * Results are cached for performance.
     *
     * @param wildcardPattern Pattern with * wildcards
     * @return Compiled regex pattern (cached)
     *
     * Examples:
     *   wildcardToRegex("minecraft:*") → ^minecraft:.*$
     *   wildcardToRegex("*:village_*") → ^.*:village_.*$
     */
    private static Pattern wildcardToRegex(String wildcardPattern) {
        return REGEX_CACHE.computeIfAbsent(wildcardPattern, pattern -> {
            String regex = pattern
                    .replace(".", "\\.")  // Escape dots
                    .replace("*", ".*");  // Convert * to .*
            return Pattern.compile("^" + regex + "$");
        });
    }
}
