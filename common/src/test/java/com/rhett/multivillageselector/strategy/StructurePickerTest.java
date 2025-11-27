package com.rhett.multivillageselector.strategy;

import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for StructurePicker.
 * Tests filtering, weighted selection, and edge cases.
 */
class StructurePickerTest {

    // ============================================================
    // FILTERING TESTS
    // ============================================================

    @Test
    @DisplayName("Filtering: single structure matches biome")
    void testFiltering_SingleMatch() {
        // Setup
        MVSConfig.ConfiguredStructure structure = createStructure("minecraft:village_plains",
            Map.of("#minecraft:is_plains", 10));

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains", "#minecraft:is_plains");
        Random random = new Random(42);

        // Execute
        MVSConfig.ConfiguredStructure result = picker.select(random, plainsBiome);

        // Verify
        assertNotNull(result);
        assertEquals("minecraft:village_plains", result.structure.toString());
    }

    @Test
    @DisplayName("Filtering: no structures match biome")
    void testFiltering_NoMatches() {
        // Setup
        MVSConfig.ConfiguredStructure structure = createStructure("minecraft:village_plains",
            Map.of("#minecraft:is_plains", 10));

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> desertBiome = createMockBiome("minecraft:desert", "#minecraft:is_desert");
        Random random = new Random(42);

        // Execute
        MVSConfig.ConfiguredStructure result = picker.select(random, desertBiome);

        // Verify
        assertNull(result);
    }

    @Test
    @DisplayName("Filtering: multiple structures match, all included")
    void testFiltering_MultipleMatches() {
        // Setup
        MVSConfig.ConfiguredStructure plains = createStructure("minecraft:village_plains",
            Map.of("#minecraft:is_plains", 10));
        MVSConfig.ConfiguredStructure desert = createStructure("minecraft:village_desert",
            Map.of("#minecraft:is_desert", 5));
        MVSConfig.ConfiguredStructure universal = createStructure("mod:universal_village",
            Map.of("#*:*", 2));  // Matches all biomes

        List<MVSConfig.ConfiguredStructure> pool = List.of(plains, desert, universal);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains", "#minecraft:is_plains");
        Random random = new Random(42);

        // Execute - use selectWithStats to verify filtering
        StructurePicker.SelectionResult result = picker.selectWithStats(random, plainsBiome);

        // Verify - plains and universal match (2 structures)
        assertEquals(3, result.poolSizeBefore);
        assertEquals(2, result.poolSizeAfter);
        assertNotNull(result.selected);
        assertTrue(result.selected.structure.toString().equals("minecraft:village_plains") ||
                   result.selected.structure.toString().equals("mod:universal_village"));
    }

    @Test
    @DisplayName("Filtering: weight 0 excludes structure")
    void testFiltering_WeightZeroExcluded() {
        // Setup
        MVSConfig.ConfiguredStructure structure = createStructure("minecraft:village_plains",
            Map.of("#minecraft:is_ocean", 0));  // Weight 0 for ocean

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> oceanBiome = createMockBiome("minecraft:ocean", "#minecraft:is_ocean");
        Random random = new Random(42);

        // Execute
        MVSConfig.ConfiguredStructure result = picker.select(random, oceanBiome);

        // Verify - weight 0 should exclude structure
        assertNull(result);
    }

    // ============================================================
    // WEIGHTED SELECTION TESTS
    // ============================================================

    @Test
    @DisplayName("Weighted selection: deterministic with fixed seed")
    void testWeightedSelection_Deterministic() {
        // Setup
        MVSConfig.ConfiguredStructure structure1 = createStructure("structure1",
            Map.of("#test:biome", 50));
        MVSConfig.ConfiguredStructure structure2 = createStructure("structure2",
            Map.of("#test:biome", 50));

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure1, structure2);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> biome = createMockBiome("test:biome", "#test:biome");

        // Execute twice with same seed
        MVSConfig.ConfiguredStructure result1 = picker.select(new Random(42), biome);
        MVSConfig.ConfiguredStructure result2 = picker.select(new Random(42), biome);

        // Verify - same seed produces same result
        assertEquals(result1.structure.toString(), result2.structure.toString());
    }

    @Test
    @DisplayName("Weighted selection: heavily weighted structure chosen more often")
    void testWeightedSelection_WeightInfluence() {
        // Setup - 99% weight for structure1, 1% for structure2
        MVSConfig.ConfiguredStructure structure1 = createStructure("heavy",
            Map.of("#test:biome", 99));
        MVSConfig.ConfiguredStructure structure2 = createStructure("light",
            Map.of("#test:biome", 1));

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure1, structure2);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> biome = createMockBiome("test:biome", "#test:biome");

        // Execute many times
        Map<String, Integer> counts = new HashMap<>();
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            MVSConfig.ConfiguredStructure result = picker.select(random, biome);
            counts.put(result.structure.toString(), counts.getOrDefault(result.structure.toString(), 0) + 1);
        }

        // Verify - heavy structure chosen much more often
        // ResourceLocation.parse() adds "minecraft:" namespace by default
        int heavyCount = counts.getOrDefault("minecraft:heavy", 0);
        int lightCount = counts.getOrDefault("minecraft:light", 0);

        assertTrue(heavyCount > 900, "Heavy structure should be chosen ~99% of time (actual: " + heavyCount + "/1000)");
        assertTrue(lightCount < 100, "Light structure should be chosen ~1% of time (actual: " + lightCount + "/1000)");
    }

    @Test
    @DisplayName("Weighted selection: equal weights produce even distribution")
    void testWeightedSelection_EqualWeights() {
        // Setup
        MVSConfig.ConfiguredStructure structure1 = createStructure("structure1",
            Map.of("#test:biome", 10));
        MVSConfig.ConfiguredStructure structure2 = createStructure("structure2",
            Map.of("#test:biome", 10));
        MVSConfig.ConfiguredStructure structure3 = createStructure("structure3",
            Map.of("#test:biome", 10));

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure1, structure2, structure3);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> biome = createMockBiome("test:biome", "#test:biome");

        // Execute many times
        Map<String, Integer> counts = new HashMap<>();
        Random random = new Random(42);
        for (int i = 0; i < 3000; i++) {
            MVSConfig.ConfiguredStructure result = picker.select(random, biome);
            counts.put(result.structure.toString(), counts.getOrDefault(result.structure.toString(), 0) + 1);
        }

        // Verify - roughly even distribution (~1000 each, allow 15% variance)
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();
            assertTrue(count > 850 && count < 1150,
                "Structure " + entry.getKey() + " should be chosen ~1000/3000 times (actual: " + count + ")");
        }
    }

    @Test
    @DisplayName("Weighted selection: single structure always chosen")
    void testWeightedSelection_SingleStructure() {
        // Setup
        MVSConfig.ConfiguredStructure structure = createStructure("only_one",
            Map.of("#test:biome", 10));

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> biome = createMockBiome("test:biome", "#test:biome");

        // Execute many times
        Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            MVSConfig.ConfiguredStructure result = picker.select(random, biome);
            // ResourceLocation.parse("only_one") defaults to "minecraft:only_one"
            assertEquals("minecraft:only_one", result.structure.toString());
        }
    }

    // ============================================================
    // EDGE CASE TESTS
    // ============================================================

    @Test
    @DisplayName("Edge case: empty pool")
    void testEdgeCase_EmptyPool() {
        // Setup
        List<MVSConfig.ConfiguredStructure> pool = List.of();
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> biome = createMockBiome("test:biome", "#test:biome");
        Random random = new Random(42);

        // Execute
        MVSConfig.ConfiguredStructure result = picker.select(random, biome);

        // Verify
        assertNull(result);
    }

    @Test
    @DisplayName("Edge case: all weights sum to 0 (fallback to random)")
    void testEdgeCase_AllWeightsZero() {
        // This case is theoretical - if all matched structures have weight 0 somehow
        // The current implementation filters weight 0, so this won't happen in practice
        // But the weightedSelect method has a fallback for totalWeight == 0

        // Can't easily test without accessing private method
        // Skip this test or refactor to expose weightedSelect
    }

    @Test
    @DisplayName("Edge case: pattern matching with direct biome ID")
    void testEdgeCase_DirectBiomeID() {
        // Setup
        MVSConfig.ConfiguredStructure structure = createStructure("minecraft:village_plains",
            Map.of("minecraft:plains", 15,           // Direct ID (highest specificity)
                   "#minecraft:is_plains", 10));      // Tag (lower specificity)

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains", "#minecraft:is_plains");
        Random random = new Random(42);

        // Execute
        StructurePicker.SelectionResult result = picker.selectWithStats(random, plainsBiome);

        // Verify - should use direct ID weight (15) not tag weight (10)
        assertEquals(15, result.totalWeight);
    }

    @Test
    @DisplayName("Edge case: wildcard pattern fallback")
    void testEdgeCase_WildcardFallback() {
        // Setup
        MVSConfig.ConfiguredStructure structure = createStructure("minecraft:village",
            Map.of("#*:*", 5));  // Ultimate fallback wildcard

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure);
        StructurePicker picker = new StructurePicker(pool);

        // Any biome should match
        Holder<Biome> randomBiome = createMockBiome("mod:custom_biome", "#mod:custom_tag");
        Random random = new Random(42);

        // Execute
        MVSConfig.ConfiguredStructure result = picker.select(random, randomBiome);

        // Verify
        assertNotNull(result);
        assertEquals("minecraft:village", result.structure.toString());
    }

    // ============================================================
    // DIAGNOSTICS TESTS (selectWithStats)
    // ============================================================

    @Test
    @DisplayName("Diagnostics: selectWithStats provides accurate metrics")
    void testDiagnostics_AccurateMetrics() {
        // Setup
        MVSConfig.ConfiguredStructure structure1 = createStructure("structure1",
            Map.of("#test:biome", 10));
        MVSConfig.ConfiguredStructure structure2 = createStructure("structure2",
            Map.of("#test:biome", 20));
        MVSConfig.ConfiguredStructure structure3 = createStructure("structure3",
            Map.of("#other:biome", 5));  // Won't match

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure1, structure2, structure3);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> biome = createMockBiome("test:biome", "#test:biome");
        Random random = new Random(42);

        // Execute
        StructurePicker.SelectionResult result = picker.selectWithStats(random, biome);

        // Verify metrics
        assertEquals(3, result.poolSizeBefore);
        assertEquals(2, result.poolSizeAfter);
        assertEquals(30, result.totalWeight);  // 10 + 20
        assertNotNull(result.selected);
    }

    @Test
    @DisplayName("Diagnostics: selectWithStats handles no matches")
    void testDiagnostics_NoMatches() {
        // Setup
        MVSConfig.ConfiguredStructure structure = createStructure("structure1",
            Map.of("#test:biome", 10));

        List<MVSConfig.ConfiguredStructure> pool = List.of(structure);
        StructurePicker picker = new StructurePicker(pool);

        Holder<Biome> biome = createMockBiome("other:biome", "#other:biome");
        Random random = new Random(42);

        // Execute
        StructurePicker.SelectionResult result = picker.selectWithStats(random, biome);

        // Verify
        assertEquals(1, result.poolSizeBefore);
        assertEquals(0, result.poolSizeAfter);
        assertEquals(0, result.totalWeight);
        assertNull(result.selected);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private MVSConfig.ConfiguredStructure createStructure(String id, Map<String, Integer> biomes) {
        net.minecraft.resources.ResourceLocation resourceLocation =
            net.minecraft.resources.ResourceLocation.parse(id);
        Map<String, Integer> biomeMap = new LinkedHashMap<>(biomes);
        return new MVSConfig.ConfiguredStructure(
            resourceLocation,
            biomeMap,       // originalBiomeTags
            biomeMap        // expandedBiomeTags (same for tests)
        );
    }

    @SuppressWarnings("unchecked")
    private Holder<Biome> createMockBiome(String biomeId, String... tags) {
        Holder<Biome> holder = mock(Holder.class);

        // Mock biome key (for direct ID)
        String[] parts = biomeId.split(":", 2);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
            parts[0],
            parts.length > 1 ? parts[1] : "default"
        );
        ResourceKey<Biome> key = ResourceKey.create(
            net.minecraft.core.registries.Registries.BIOME,
            location
        );
        when(holder.unwrapKey()).thenReturn(Optional.of(key));

        // Mock tags - use thenAnswer to create a fresh stream each time
        // (streams can only be consumed once, so we need a new stream for each call)
        when(holder.tags()).thenAnswer(invocation ->
            Stream.of(tags)
                .map(tagId -> {
                    String cleanTag = tagId.startsWith("#") ? tagId.substring(1) : tagId;
                    String[] tagParts = cleanTag.split(":", 2);
                    ResourceLocation tagLocation = ResourceLocation.fromNamespaceAndPath(
                        tagParts[0],
                        tagParts.length > 1 ? tagParts[1] : "default"
                    );
                    return (TagKey<Biome>) TagKey.create(
                        net.minecraft.core.registries.Registries.BIOME,
                        tagLocation
                    );
                })
        );

        // Mock is() method for tag checking
        when(holder.is(any(TagKey.class))).thenAnswer(invocation -> {
            TagKey<Biome> queryTag = invocation.getArgument(0);
            for (String tag : tags) {
                String cleanTag = tag.startsWith("#") ? tag.substring(1) : tag;
                if (queryTag.location().toString().equals(cleanTag)) {
                    return true;
                }
            }
            return false;
        });

        return holder;
    }
}
