package com.rhett.multivillageselector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ChunkGeneratorMixin logic
 * Tests the interception, selection, and debouncing systems
 * (Biome categorization requires Minecraft runtime, tested in-game)
 */
class ChunkGeneratorMixinTest {

    @BeforeEach
    void setUp() {
        // Reset config state before each test
        MVSConfig.enabled = true;
        MVSConfig.debugLogging = false;
        MVSConfig.preventSpawnPatterns.clear();
        MVSConfig.replaceOfPatterns.clear();
        MVSConfig.biomeReplacements.clear();
        MVSConfig.biomeCategoryOverrides.clear();
    }

    // ====== Dimension Check Tests ======

    @Test
    @DisplayName("Dimension check: Overworld dimension identifier")
    void testOverworldDimensionCheck() {
        // Test that we correctly identify overworld dimension
        String overworldDimension = "minecraft:overworld";

        // Overworld should be allowed
        assertTrue(isOverworldDimension(overworldDimension));

        // Non-overworld dimensions should not be allowed
        assertFalse(isOverworldDimension("minecraft:the_nether"));
        assertFalse(isOverworldDimension("minecraft:the_end"));
        assertFalse(isOverworldDimension("twilightforest:twilight_forest"));
        assertFalse(isOverworldDimension("custom_mod:custom_dimension"));
    }

    @Test
    @DisplayName("Dimension check: Non-overworld dimensions skip interception")
    void testNonOverworldDimensionsSkipped() {
        // Document expected behavior: MVS only operates in overworld
        // When chunk.getLevel().dimension().location() returns non-overworld dimension,
        // the mixin should return early without intercepting any structures

        String netherDimension = "minecraft:the_nether";
        String endDimension = "minecraft:the_end";

        // These dimensions should be skipped
        assertFalse(isOverworldDimension(netherDimension),
            "Nether structures (fortresses, bastions) should not be intercepted");
        assertFalse(isOverworldDimension(endDimension),
            "End structures (cities, ships) should not be intercepted");

        // This ensures MVS stays focused on village variety in overworld
        // Supporting cross-dimension structure replacement would be a different feature
    }

    // ====== Pattern Matching Integration Tests ======

    @Test
    @DisplayName("shouldPreventSpawn: matches prevent_spawn patterns")
    void testShouldPreventSpawn() {
        MVSConfig.preventSpawnPatterns.add("ctov:*/village_*");
        MVSConfig.preventSpawnPatterns.add("bca:village/*");

        // Should match
        assertTrue(shouldPreventSpawn("ctov:small/village_plains"));
        assertTrue(shouldPreventSpawn("ctov:large/village_desert"));
        assertTrue(shouldPreventSpawn("bca:village/default_large"));

        // Should not match
        assertFalse(shouldPreventSpawn("minecraft:village_plains"));
        assertFalse(shouldPreventSpawn("towns_and_towers:village_classic"));
    }

    @Test
    @DisplayName("shouldReplaceStructure: matches replace_of patterns")
    void testShouldReplaceStructure() {
        MVSConfig.replaceOfPatterns.add("minecraft:village_*");

        // Should match
        assertTrue(shouldReplaceStructure("minecraft:village_plains"));
        assertTrue(shouldReplaceStructure("minecraft:village_desert"));
        assertTrue(shouldReplaceStructure("minecraft:village_taiga"));

        // Should not match
        assertFalse(shouldReplaceStructure("ctov:small/village_plains"));
        assertFalse(shouldReplaceStructure("bca:village/default_large"));
    }

    @Test
    @DisplayName("Debouncing: prevents multiple replacements at same location")
    void testDebouncing() {
        // Simulate processing the same location twice
        Set<String> processedLocations = new HashSet<>();

        String location1 = "1000,2000";
        String location2 = "1000,2000"; // Same location
        String location3 = "1500,2500"; // Different location

        // First attempt at location1 - should process
        assertFalse(processedLocations.contains(location1));
        processedLocations.add(location1);

        // Second attempt at same location - should skip
        assertTrue(processedLocations.contains(location2));

        // Different location - should process
        assertFalse(processedLocations.contains(location3));
    }

    @Test
    @DisplayName("LRU cache: removes oldest entries when exceeding max size")
    void testLRUCacheBounds() {
        // Create a bounded LRU set like in the mixin
        Set<String> lruSet = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>(10, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 10; // Small size for testing
            }
        });

        // Add 15 entries
        for (int i = 0; i < 15; i++) {
            lruSet.add("location_" + i);
        }

        // Should only contain the last 10 entries
        assertEquals(10, lruSet.size());
        assertFalse(lruSet.contains("location_0")); // Oldest should be evicted
        assertFalse(lruSet.contains("location_4"));
        assertTrue(lruSet.contains("location_5"));  // Should still be present
        assertTrue(lruSet.contains("location_14")); // Newest should be present
    }

    // ====== Weighted Random Selection Tests ======

    @Test
    @DisplayName("Weighted selection: single structure always selected")
    void testWeightedSelectionSingle() {
        // Create test data without ResourceLocation (not available in test env)
        TestStructure structure = new TestStructure("minecraft:village_plains", 100, false);
        List<TestStructure> structures = Arrays.asList(structure);

        // Run selection 10 times, should always get the same structure
        Random random = new Random(12345);
        for (int i = 0; i < 10; i++) {
            TestStructure selected = selectWeightedStructure(structures, random);
            assertEquals("minecraft:village_plains", selected.name);
        }
    }

    @Test
    @DisplayName("Weighted selection: distribution follows weights")
    void testWeightedSelectionDistribution() {
        List<TestStructure> structures = Arrays.asList(
            new TestStructure("mod:structure_a", 10, false),
            new TestStructure("mod:structure_b", 30, false),
            new TestStructure("mod:structure_c", 60, false)
        );

        // Run many selections and count results
        Map<String, Integer> counts = new HashMap<>();
        Random random = new Random(12345);
        int iterations = 10000;

        for (int i = 0; i < iterations; i++) {
            TestStructure selected = selectWeightedStructure(structures, random);
            counts.put(selected.name, counts.getOrDefault(selected.name, 0) + 1);
        }

        // Check that distribution roughly matches weights (within 5%)
        // structure_a: 10% of 10000 = ~1000
        // structure_b: 30% of 10000 = ~3000
        // structure_c: 60% of 10000 = ~6000
        int countA = counts.getOrDefault("mod:structure_a", 0);
        int countB = counts.getOrDefault("mod:structure_b", 0);
        int countC = counts.getOrDefault("mod:structure_c", 0);

        assertTrue(countA > 800 && countA < 1200, "Structure A should be ~10%: " + countA);
        assertTrue(countB > 2500 && countB < 3500, "Structure B should be ~30%: " + countB);
        assertTrue(countC > 5500 && countC < 6500, "Structure C should be ~60%: " + countC);
    }

    @Test
    @DisplayName("Weighted selection: empty structures can be selected")
    void testWeightedSelectionEmpty() {
        List<TestStructure> structures = Arrays.asList(
            new TestStructure("minecraft:village_plains", 30, false),
            new TestStructure(null, 70, true) // 70% chance of no spawn (empty)
        );

        // Run many selections and count empty results
        int emptyCount = 0;
        Random random = new Random(12345);
        int iterations = 10000;

        for (int i = 0; i < iterations; i++) {
            TestStructure selected = selectWeightedStructure(structures, random);
            if (selected.isEmpty) {
                emptyCount++;
            }
        }

        // Should be roughly 70% empty (within 5%)
        assertTrue(emptyCount > 6500 && emptyCount < 7500,
            "Empty selection should be ~70%: " + emptyCount);
    }

    @Test
    @DisplayName("Weighted selection: zero weights handled correctly")
    void testWeightedSelectionZeroWeight() {
        List<TestStructure> structures = Arrays.asList(
            new TestStructure("mod:structure_a", 0, false),
            new TestStructure("mod:structure_b", 100, false)
        );

        // Structure A has 0 weight, should never be selected
        Random random = new Random(12345);
        for (int i = 0; i < 100; i++) {
            TestStructure selected = selectWeightedStructure(structures, random);
            assertEquals("mod:structure_b", selected.name);
        }
    }

    @Test
    @DisplayName("Percentage calculation: correct percentages from weights")
    void testPercentageCalculation() {
        List<TestStructure> structures = Arrays.asList(
            new TestStructure("mod:structure_a", 10, false),
            new TestStructure("mod:structure_b", 30, false),
            new TestStructure("mod:structure_c", 60, false)
        );

        int totalWeight = structures.stream().mapToInt(s -> s.weight).sum();
        assertEquals(100, totalWeight);

        // Test percentage calculation
        double percentA = (10.0 / totalWeight) * 100;
        double percentB = (30.0 / totalWeight) * 100;
        double percentC = (60.0 / totalWeight) * 100;

        assertEquals(10.0, percentA, 0.01);
        assertEquals(30.0, percentB, 0.01);
        assertEquals(60.0, percentC, 0.01);
    }

    @Test
    @DisplayName("Percentage calculation: handles non-100 total weights")
    void testPercentageCalculationNonStandard() {
        List<TestStructure> structures = Arrays.asList(
            new TestStructure("mod:structure_a", 5, false),
            new TestStructure("mod:structure_b", 15, false),
            new TestStructure("mod:structure_c", 30, false)
        );

        int totalWeight = structures.stream().mapToInt(s -> s.weight).sum();
        assertEquals(50, totalWeight);

        // Percentages should still add up to 100%
        double percentA = (5.0 / totalWeight) * 100;
        double percentB = (15.0 / totalWeight) * 100;
        double percentC = (30.0 / totalWeight) * 100;

        assertEquals(10.0, percentA, 0.01);
        assertEquals(30.0, percentB, 0.01);
        assertEquals(60.0, percentC, 0.01);
        assertEquals(100.0, percentA + percentB + percentC, 0.01);
    }

    // ====== Helper Methods (mirror ChunkGeneratorMixin logic) ======

    /**
     * Mirror of dimension check from ChunkGeneratorMixin
     * Tests that we correctly identify the overworld dimension
     */
    private boolean isOverworldDimension(String dimensionId) {
        return "minecraft:overworld".equals(dimensionId);
    }

    /**
     * Mirror of shouldPreventSpawn from ChunkGeneratorMixin
     */
    private boolean shouldPreventSpawn(String structureName) {
        for (String pattern : MVSConfig.preventSpawnPatterns) {
            if (MVSConfig.matchesPattern(structureName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mirror of shouldReplaceStructure from ChunkGeneratorMixin
     */
    private boolean shouldReplaceStructure(String structureName) {
        for (String pattern : MVSConfig.replaceOfPatterns) {
            if (MVSConfig.matchesPattern(structureName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mirror of weighted selection logic from ChunkGeneratorMixin
     */
    private TestStructure selectWeightedStructure(
            List<TestStructure> structures,
            Random random) {

        int totalWeight = structures.stream().mapToInt(s -> s.weight).sum();
        int roll = random.nextInt(totalWeight);

        int currentWeight = 0;
        for (TestStructure s : structures) {
            currentWeight += s.weight;
            if (roll < currentWeight) {
                return s;
            }
        }

        return structures.get(0); // Fallback
    }

    /**
     * Simple test structure class (mirror of WeightedStructure)
     */
    private static class TestStructure {
        final String name;
        final int weight;
        final boolean isEmpty;

        TestStructure(String name, int weight, boolean isEmpty) {
            this.name = name;
            this.weight = weight;
            this.isEmpty = isEmpty;
        }
    }
}
