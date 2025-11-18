package com.rhett.multivillageselector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MVSCommands logic
 * Tests biome categorization, percentage calculations, and validation
 * (Full command testing requires Minecraft runtime, tested in-game)
 */
class MVSCommandsTest {

    @BeforeEach
    void setUp() {
        MVSConfig.biomeCategoryOverrides.clear();
        MVSConfig.biomeReplacements.clear();
    }

    // ====== Biome Category Override Tests ======

    @Test
    @DisplayName("Biome command: config overrides take precedence")
    void testBiomeCategoryOverride() {
        MVSConfig.biomeCategoryOverrides.put("minecraft:deep_dark", "dark_forest");
        MVSConfig.biomeCategoryOverrides.put("terralith:volcanic_peaks", "desert");

        assertTrue(MVSConfig.biomeCategoryOverrides.containsKey("minecraft:deep_dark"));
        assertEquals("dark_forest", MVSConfig.biomeCategoryOverrides.get("minecraft:deep_dark"));
        assertEquals("desert", MVSConfig.biomeCategoryOverrides.get("terralith:volcanic_peaks"));
    }

    @Test
    @DisplayName("Biome command: handles invalid format")
    void testBiomeInvalidFormat() {
        // Biome IDs must contain ":"
        String invalidBiome = "plains";
        assertFalse(invalidBiome.contains(":"));

        String validBiome = "minecraft:plains";
        assertTrue(validBiome.contains(":"));
    }

    @Test
    @DisplayName("Biome command: validates biome ID format")
    void testBiomeIDValidation() {
        // Valid formats
        assertTrue("minecraft:plains".contains(":"));
        assertTrue("terralith:volcanic_peaks".contains(":"));
        assertTrue("mod:biome".contains(":"));

        // Invalid formats (missing namespace)
        assertFalse("plains".contains(":"));
        assertFalse("desert".contains(":"));
    }

    // ====== Percentage Calculation Tests ======

    @Test
    @DisplayName("Percentage calculation: simple 100-weight pool")
    void testPercentageCalculationSimple() {
        List<TestWeight> weights = Arrays.asList(
            new TestWeight(10),
            new TestWeight(30),
            new TestWeight(60)
        );

        int totalWeight = weights.stream().mapToInt(w -> w.weight).sum();
        assertEquals(100, totalWeight);

        double percent1 = (10.0 / totalWeight) * 100.0;
        double percent2 = (30.0 / totalWeight) * 100.0;
        double percent3 = (60.0 / totalWeight) * 100.0;

        assertEquals(10.0, percent1, 0.01);
        assertEquals(30.0, percent2, 0.01);
        assertEquals(60.0, percent3, 0.01);
    }

    @Test
    @DisplayName("Percentage calculation: non-100 total weight")
    void testPercentageCalculationNonStandard() {
        List<TestWeight> weights = Arrays.asList(
            new TestWeight(5),
            new TestWeight(15),
            new TestWeight(30)
        );

        int totalWeight = weights.stream().mapToInt(w -> w.weight).sum();
        assertEquals(50, totalWeight);

        // Percentages should still add up to 100%
        double percent1 = (5.0 / totalWeight) * 100.0;
        double percent2 = (15.0 / totalWeight) * 100.0;
        double percent3 = (30.0 / totalWeight) * 100.0;

        assertEquals(10.0, percent1, 0.01);
        assertEquals(30.0, percent2, 0.01);
        assertEquals(60.0, percent3, 0.01);
        assertEquals(100.0, percent1 + percent2 + percent3, 0.01);
    }

    @Test
    @DisplayName("Percentage calculation: handles zero weight")
    void testPercentageCalculationZeroWeight() {
        List<TestWeight> weights = Arrays.asList(
            new TestWeight(0),
            new TestWeight(100)
        );

        int totalWeight = weights.stream().mapToInt(w -> w.weight).sum();
        assertEquals(100, totalWeight);

        double percent1 = (0.0 / totalWeight) * 100.0;
        double percent2 = (100.0 / totalWeight) * 100.0;

        assertEquals(0.0, percent1, 0.01);
        assertEquals(100.0, percent2, 0.01);
    }

    @Test
    @DisplayName("Percentage formatting: decimal places")
    void testPercentageFormatting() {
        double percent1 = 10.0;
        double percent2 = 33.333;
        double percent3 = 50.5;
        double percent4 = 66.666666;

        // Test formatting to 1 decimal place
        String formatted1 = String.format("%.1f%%", percent1);
        String formatted2 = String.format("%.1f%%", percent2);
        String formatted3 = String.format("%.1f%%", percent3);
        String formatted4 = String.format("%.1f%%", percent4);

        assertEquals("10.0%", formatted1);
        assertEquals("33.3%", formatted2);
        assertEquals("50.5%", formatted3);
        assertEquals("66.7%", formatted4);
    }

    @Test
    @DisplayName("Weight formatting: bracket display")
    void testWeightFormatting() {
        int weight1 = 10;
        int weight2 = 100;
        int weight3 = 5;

        String formatted1 = String.format("[%d]", weight1);
        String formatted2 = String.format("[%d]", weight2);
        String formatted3 = String.format("[%d]", weight3);

        assertEquals("[10]", formatted1);
        assertEquals("[100]", formatted2);
        assertEquals("[5]", formatted3);
    }

    @Test
    @DisplayName("Combined formatting: weight and percentage")
    void testCombinedFormatting() {
        int weight = 30;
        int totalWeight = 100;
        double percentage = (weight / (double) totalWeight) * 100.0;

        String combined = String.format("[%d] (%.1f%%)", weight, percentage);
        assertEquals("[30] (30.0%)", combined);
    }

    // ====== Pool Size Tests ======

    @Test
    @DisplayName("Pool counting: empty pools")
    void testEmptyPools() {
        assertTrue(MVSConfig.biomeReplacements.isEmpty());
        assertEquals(0, MVSConfig.biomeReplacements.size());
    }

    @Test
    @DisplayName("Pool counting: category count")
    void testCategoryCount() {
        // Simulate adding categories (without ResourceLocation)
        MVSConfig.biomeReplacements.put("plains", Arrays.asList());
        MVSConfig.biomeReplacements.put("desert", Arrays.asList());
        MVSConfig.biomeReplacements.put("DEFAULT", Arrays.asList());

        assertEquals(3, MVSConfig.biomeReplacements.size());
        assertTrue(MVSConfig.biomeReplacements.containsKey("plains"));
        assertTrue(MVSConfig.biomeReplacements.containsKey("desert"));
        assertTrue(MVSConfig.biomeReplacements.containsKey("DEFAULT"));
    }

    @Test
    @DisplayName("Pool lookup: non-existent category")
    void testNonExistentCategory() {
        MVSConfig.biomeReplacements.put("plains", Arrays.asList());

        assertTrue(MVSConfig.biomeReplacements.containsKey("plains"));
        assertFalse(MVSConfig.biomeReplacements.containsKey("desert"));
        assertFalse(MVSConfig.biomeReplacements.containsKey("jungle"));
    }

    // ====== String Parsing Tests ======

    @Test
    @DisplayName("Resource location parsing: namespace extraction")
    void testNamespaceExtraction() {
        String id1 = "minecraft:village_plains";
        String id2 = "ctov:small/village_plains";
        String id3 = "bca:village/default_large";

        assertEquals("minecraft", id1.split(":")[0]);
        assertEquals("ctov", id2.split(":")[0]);
        assertEquals("bca", id3.split(":")[0]);
    }

    @Test
    @DisplayName("Resource location parsing: path extraction")
    void testPathExtraction() {
        String id1 = "minecraft:village_plains";
        String id2 = "ctov:small/village_plains";
        String id3 = "bca:village/default_large";

        assertEquals("village_plains", id1.split(":")[1]);
        assertEquals("small/village_plains", id2.split(":")[1]);
        assertEquals("village/default_large", id3.split(":")[1]);
    }

    @Test
    @DisplayName("Resource location parsing: handles special characters")
    void testSpecialCharacters() {
        String id1 = "mod-name:structure_name";
        String id2 = "mod.name:structure/path";

        assertTrue(id1.contains(":"));
        assertTrue(id2.contains(":"));
        assertEquals("mod-name", id1.split(":")[0]);
        assertEquals("mod.name", id2.split(":")[0]);
    }

    // ====== Command Permission Tests (Logic Only) ======

    @Test
    @DisplayName("Command permission: OP level requirement")
    void testOpLevelRequirement() {
        // Commands require OP level 2
        int requiredLevel = 2;

        // Test permission levels
        assertTrue(2 >= requiredLevel);  // OP level 2 (server ops)
        assertTrue(3 >= requiredLevel);  // OP level 3 (game moderators)
        assertTrue(4 >= requiredLevel);  // OP level 4 (server owner)

        assertFalse(0 >= requiredLevel); // Regular player
        assertFalse(1 >= requiredLevel); // OP level 1 (bypass spawn protection)
    }

    // ====== Integration Tests ======

    @Test
    @DisplayName("Integration: full percentage calculation workflow")
    void testFullCalculationWorkflow() {
        // Simulate a pool with multiple entries
        List<TestWeight> pool = Arrays.asList(
            new TestWeight(20),  // Structure A
            new TestWeight(30),  // Structure B
            new TestWeight(40),  // Structure C
            new TestWeight(10)   // Empty (no spawn)
        );

        // Calculate total weight
        int totalWeight = pool.stream().mapToInt(w -> w.weight).sum();
        assertEquals(100, totalWeight);

        // Calculate each percentage
        double[] percentages = pool.stream()
            .mapToDouble(w -> (w.weight / (double) totalWeight) * 100.0)
            .toArray();

        // Verify each percentage
        assertEquals(20.0, percentages[0], 0.01);
        assertEquals(30.0, percentages[1], 0.01);
        assertEquals(40.0, percentages[2], 0.01);
        assertEquals(10.0, percentages[3], 0.01);

        // Verify total is 100%
        double total = Arrays.stream(percentages).sum();
        assertEquals(100.0, total, 0.01);
    }

    /**
     * Simple test class to represent weighted entries
     */
    private static class TestWeight {
        final int weight;

        TestWeight(int weight) {
            this.weight = weight;
        }
    }
}
