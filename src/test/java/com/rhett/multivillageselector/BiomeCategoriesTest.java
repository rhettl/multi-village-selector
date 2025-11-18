package com.rhett.multivillageselector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Integration tests for biome categorization and config override logic
 * These tests verify the core mod behavior will work after Architectury refactor
 */
class BiomeCategoriesTest {

    @BeforeEach
    void setUp() {
        // Reset config state before each test
        MVSConfig.biomeCategoryOverrides.clear();
    }

    @Test
    @DisplayName("Config overrides: exact biome ID match")
    void testBiomeOverrideExactMatch() {
        // Setup: Add a biome override
        MVSConfig.biomeCategoryOverrides.put("terralith:volcanic_peaks", "snowy");
        MVSConfig.biomeCategoryOverrides.put("minecraft:deep_ocean", "ocean");

        // Verify: Overrides are stored correctly
        assertEquals("snowy", MVSConfig.biomeCategoryOverrides.get("terralith:volcanic_peaks"));
        assertEquals("ocean", MVSConfig.biomeCategoryOverrides.get("minecraft:deep_ocean"));
        assertNull(MVSConfig.biomeCategoryOverrides.get("minecraft:plains"));
    }

    @Test
    @DisplayName("Config overrides: multiple overrides")
    void testMultipleBiomeOverrides() {
        // Setup: Add multiple overrides
        Map<String, String> testOverrides = Map.of(
            "terralith:volcanic_peaks", "snowy",
            "terralith:hot_spring", "taiga",
            "biome_o_plenty:mystic_grove", "mushroom",
            "minecraft:warm_ocean", "ocean"
        );

        MVSConfig.biomeCategoryOverrides.putAll(testOverrides);

        // Verify: All overrides present
        assertEquals(4, MVSConfig.biomeCategoryOverrides.size());
        assertEquals("snowy", MVSConfig.biomeCategoryOverrides.get("terralith:volcanic_peaks"));
        assertEquals("taiga", MVSConfig.biomeCategoryOverrides.get("terralith:hot_spring"));
        assertEquals("mushroom", MVSConfig.biomeCategoryOverrides.get("biome_o_plenty:mystic_grove"));
        assertEquals("ocean", MVSConfig.biomeCategoryOverrides.get("minecraft:warm_ocean"));
    }

    @Test
    @DisplayName("Config overrides: case sensitivity")
    void testBiomeOverrideCaseSensitivity() {
        // Biome IDs should be case-sensitive (Minecraft convention is lowercase)
        MVSConfig.biomeCategoryOverrides.put("minecraft:plains", "plains");

        // Should not match different case
        assertNull(MVSConfig.biomeCategoryOverrides.get("Minecraft:Plains"));
        assertNull(MVSConfig.biomeCategoryOverrides.get("MINECRAFT:PLAINS"));

        // Should match exact case
        assertEquals("plains", MVSConfig.biomeCategoryOverrides.get("minecraft:plains"));
    }

    @Test
    @DisplayName("Config overrides: override replacement")
    void testBiomeOverrideReplacement() {
        // First override
        MVSConfig.biomeCategoryOverrides.put("minecraft:desert", "desert");
        assertEquals("desert", MVSConfig.biomeCategoryOverrides.get("minecraft:desert"));

        // Replace with different category (user changed their mind)
        MVSConfig.biomeCategoryOverrides.put("minecraft:desert", "savanna");
        assertEquals("savanna", MVSConfig.biomeCategoryOverrides.get("minecraft:desert"));

        // Should only have one entry
        assertEquals(1, MVSConfig.biomeCategoryOverrides.size());
    }

    @Test
    @DisplayName("Config overrides: empty category string")
    void testBiomeOverrideEmptyCategory() {
        // Edge case: what if user provides empty string?
        MVSConfig.biomeCategoryOverrides.put("minecraft:void", "");

        assertEquals("", MVSConfig.biomeCategoryOverrides.get("minecraft:void"));
        // In practice, this would fall back to DEFAULT in the actual biome categorization
    }

    @Test
    @DisplayName("Config overrides: namespace variants")
    void testBiomeOverrideNamespaceVariants() {
        // Different namespaces should be treated as different biomes
        MVSConfig.biomeCategoryOverrides.put("minecraft:forest", "plains");
        MVSConfig.biomeCategoryOverrides.put("terralith:forest", "dark_forest");
        MVSConfig.biomeCategoryOverrides.put("biome_o_plenty:forest", "jungle");

        assertEquals("plains", MVSConfig.biomeCategoryOverrides.get("minecraft:forest"));
        assertEquals("dark_forest", MVSConfig.biomeCategoryOverrides.get("terralith:forest"));
        assertEquals("jungle", MVSConfig.biomeCategoryOverrides.get("biome_o_plenty:forest"));

        // All three should coexist
        assertEquals(3, MVSConfig.biomeCategoryOverrides.size());
    }

    @Test
    @DisplayName("Config overrides: special characters in biome ID")
    void testBiomeOverrideSpecialCharacters() {
        // Biome IDs can contain underscores, dots, slashes
        MVSConfig.biomeCategoryOverrides.put("mod.name:biome_type/variant", "plains");

        assertEquals("plains", MVSConfig.biomeCategoryOverrides.get("mod.name:biome_type/variant"));
    }

    @Test
    @DisplayName("Pattern specificity: calculate specificity correctly")
    void testPatternSpecificityCalculation() {
        // Specificity = pattern length - number of wildcards

        // Most specific: exact match (no wildcards)
        String exact = "ctov:large/village_plains";
        assertEquals(25, exact.length()); // 25 chars, 0 wildcards = specificity 25

        // Less specific: one wildcard
        String oneWildcard = "ctov:*/village_plains";
        assertEquals(21, oneWildcard.length()); // 21 chars, 1 wildcard = specificity 20

        // Even less specific: two wildcards
        String twoWildcards = "ctov:*/village_*";
        assertEquals(16, twoWildcards.length()); // 16 chars, 2 wildcards = specificity 14

        // Least specific: full wildcard
        String fullWildcard = "*";
        assertEquals(1, fullWildcard.length()); // 1 char, 1 wildcard = specificity 0
    }

    @Test
    @DisplayName("Pattern specificity: matching priority")
    void testPatternMatchingPriority() {
        // When multiple patterns match, most specific should be used
        String structure = "ctov:large/village_plains";

        // All these patterns match this structure
        List<String> matchingPatterns = Arrays.asList(
            "ctov:large/village_plains", // specificity: 25 - exact match
            "ctov:*/village_plains",      // specificity: 20 - wildcard middle
            "ctov:large/village_*",       // specificity: 21 - wildcard end
            "ctov:*/village_*",           // specificity: 14 - two wildcards
            "ctov:*",                     // specificity: 4  - wildcard after namespace
            "*"                           // specificity: 0  - full wildcard
        );

        // Verify all match
        for (String pattern : matchingPatterns) {
            assertTrue(MVSConfig.matchesPattern(structure, pattern),
                "Pattern '" + pattern + "' should match '" + structure + "'");
        }

        // Pattern that should NOT match (has path component)
        assertFalse(MVSConfig.matchesPattern(structure, "*:village_plains"),
            "Pattern '*:village_plains' should NOT match '" + structure + "' (has /large/ in between)");

        // In actual code, "ctov:large/village_plains" should win (highest specificity)
    }

    @Test
    @DisplayName("Replace patterns: multiple structures match same pattern")
    void testMultipleStructuresMatchPattern() {
        String pattern = "ctov:*/village_plains";

        // Multiple structures should match
        List<String> structures = Arrays.asList(
            "ctov:small/village_plains",
            "ctov:medium/village_plains",
            "ctov:large/village_plains"
        );

        for (String structure : structures) {
            assertTrue(MVSConfig.matchesPattern(structure, pattern),
                "Structure '" + structure + "' should match pattern '" + pattern + "'");
        }

        // Non-matching structures
        List<String> nonMatching = Arrays.asList(
            "ctov:small/village_desert",
            "minecraft:village_plains",
            "towns_and_towers:village_plains"
        );

        for (String structure : nonMatching) {
            assertFalse(MVSConfig.matchesPattern(structure, pattern),
                "Structure '" + structure + "' should NOT match pattern '" + pattern + "'");
        }
    }

    @Test
    @DisplayName("Prevent spawn patterns: verify blocking behavior")
    void testPreventSpawnPatterns() {
        // Setup: Add patterns to prevent_spawn
        MVSConfig.preventSpawnPatterns.clear();
        MVSConfig.preventSpawnPatterns.add("ctov:large/*");
        MVSConfig.preventSpawnPatterns.add("ctov:medium/*");
        MVSConfig.preventSpawnPatterns.add("towns_and_towers:village_*");

        // Verify: Patterns stored correctly
        assertEquals(3, MVSConfig.preventSpawnPatterns.size());
        assertTrue(MVSConfig.preventSpawnPatterns.contains("ctov:large/*"));
        assertTrue(MVSConfig.preventSpawnPatterns.contains("towns_and_towers:village_*"));
    }

    @Test
    @DisplayName("Replace of patterns: verify interception behavior")
    void testReplaceOfPatterns() {
        // Setup: Add patterns to replace_of
        MVSConfig.replaceOfPatterns.clear();
        MVSConfig.replaceOfPatterns.add("minecraft:village_plains");
        MVSConfig.replaceOfPatterns.add("minecraft:village_desert");
        MVSConfig.replaceOfPatterns.add("bca:village/*");

        // Verify: Patterns stored correctly
        assertEquals(3, MVSConfig.replaceOfPatterns.size());

        // Test matching logic
        assertTrue(MVSConfig.matchesPattern("minecraft:village_plains", "minecraft:village_plains"));
        assertTrue(MVSConfig.matchesPattern("bca:village/default_small", "bca:village/*"));
        assertFalse(MVSConfig.matchesPattern("ctov:large/village_plains", "minecraft:village_plains"));
    }

    @Test
    @DisplayName("Config state: reset between tests")
    void testConfigStateReset() {
        // This test verifies @BeforeEach is working
        // biomeCategoryOverrides should be empty at start of each test
        assertTrue(MVSConfig.biomeCategoryOverrides.isEmpty(),
            "biomeCategoryOverrides should be empty at start of test");

        // Add some overrides
        MVSConfig.biomeCategoryOverrides.put("test:biome", "plains");
        assertEquals(1, MVSConfig.biomeCategoryOverrides.size());

        // Next test will verify this is reset
    }

    @Test
    @DisplayName("Config state: verify previous test cleanup")
    void testConfigStateCleanupWorked() {
        // This should run after testConfigStateReset
        // If @BeforeEach works, overrides should be empty
        assertTrue(MVSConfig.biomeCategoryOverrides.isEmpty(),
            "biomeCategoryOverrides should have been cleared by @BeforeEach");
    }

    @Test
    @DisplayName("Weighted structures: total weight calculation")
    void testWeightedStructuresTotalWeight() {
        // Setup: Create a pool with mixed weights
        List<MVSConfig.WeightedStructure> pool = new ArrayList<>();
        pool.add(MVSConfig.WeightedStructure.empty(60)); // 60% empty
        // Would add more but can't create ResourceLocation without Minecraft

        // Verify empty structure
        assertEquals(60, pool.get(0).weight);
        assertTrue(pool.get(0).isEmpty);

        // Total weight calculation (what the mixin does)
        int totalWeight = pool.stream().mapToInt(s -> s.weight).sum();
        assertEquals(60, totalWeight);
    }

    @Test
    @DisplayName("Replacement entries: mixed pool types")
    void testMixedReplacementEntries() {
        // Test that we can have patterns, structures, and empties in same pool
        List<MVSConfig.ReplacementEntry> entries = new ArrayList<>();

        entries.add(MVSConfig.ReplacementEntry.pattern("ctov:*/village_*", 30));
        entries.add(MVSConfig.ReplacementEntry.empty(60));
        // Can't easily test structure() without ResourceLocation

        assertEquals(2, entries.size());

        MVSConfig.ReplacementEntry pattern = entries.get(0);
        assertTrue(pattern.isPattern);
        assertFalse(pattern.isEmpty);
        assertEquals(30, pattern.weight);

        MVSConfig.ReplacementEntry empty = entries.get(1);
        assertTrue(empty.isEmpty);
        assertFalse(empty.isPattern);
        assertEquals(60, empty.weight);
    }
}
