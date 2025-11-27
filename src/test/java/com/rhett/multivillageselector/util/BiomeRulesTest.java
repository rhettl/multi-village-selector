package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for BiomeRules immutable data class.
 * Tests factory methods, validation, and weight resolution.
 */
class BiomeRulesTest {

    // ============================================================
    // FACTORY METHOD TESTS
    // ============================================================

    @Test
    @DisplayName("Factory: fromMVSConfig creates MVS_CONFIG source")
    void testFactoryFromMVSConfig() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        biomes.put("#minecraft:is_desert", 5);

        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);

        // Execute
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        // Verify
        assertEquals(BiomeRules.Source.MVS_CONFIG, rules.source);
        assertEquals(2, rules.tags.size());
        assertTrue(rules.tags.contains("#minecraft:is_plains"));
        assertTrue(rules.tags.contains("#minecraft:is_desert"));
        assertEquals(0, rules.directBiomes.size());
        assertEquals(10, rules.weights.get("#minecraft:is_plains"));
        assertEquals(5, rules.weights.get("#minecraft:is_desert"));
    }

    @Test
    @DisplayName("Factory: empty creates EMPTY source")
    void testFactoryEmpty() {
        // Execute
        BiomeRules rules = BiomeRules.empty();

        // Verify
        assertEquals(BiomeRules.Source.EMPTY, rules.source);
        assertTrue(rules.tags.isEmpty());
        assertTrue(rules.directBiomes.isEmpty());
        assertTrue(rules.weights.isEmpty());
        assertTrue(rules.isEmpty());
    }

    // ============================================================
    // MATCHES METHOD TESTS
    // ============================================================

    @Test
    @DisplayName("matches: biome with matching tag")
    void testMatches_TagMatch() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains",
            "#minecraft:is_plains", "#minecraft:has_structure/village_plains");

        // Execute & Verify
        assertTrue(rules.matches(plainsBiome));
    }

    @Test
    @DisplayName("matches: biome without matching tag")
    void testMatches_NoTagMatch() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> desertBiome = createMockBiome("minecraft:desert",
            "#minecraft:is_desert");

        // Execute & Verify
        assertFalse(rules.matches(desertBiome));
    }

    @Test
    @DisplayName("matches: biome with multiple tags, any match succeeds")
    void testMatches_MultipleTagsAnyMatch() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        biomes.put("#minecraft:is_forest", 5);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> biome = createMockBiome("minecraft:flower_forest",
            "#minecraft:is_forest", "#minecraft:has_flowers");

        // Execute & Verify
        assertTrue(rules.matches(biome));
    }

    @Test
    @DisplayName("matches: empty rules never match")
    void testMatches_EmptyRules() {
        // Setup
        BiomeRules rules = BiomeRules.empty();
        Holder<Biome> biome = createMockBiome("minecraft:plains", "#minecraft:is_plains");

        // Execute & Verify
        assertFalse(rules.matches(biome));
    }

    @Test
    @DisplayName("matches: invalid tag string doesn't crash")
    void testMatches_InvalidTag() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("invalid_tag_no_hash", 10);  // Invalid (no #)
        biomes.put("#minecraft:is_plains", 5);   // Valid
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains",
            "#minecraft:is_plains");

        // Execute & Verify - should still match valid tag
        assertTrue(rules.matches(plainsBiome));
    }

    // ============================================================
    // GET MATCHING TAG TESTS
    // ============================================================

    @Test
    @DisplayName("getMatchingTag: returns matching tag string")
    void testGetMatchingTag_Match() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        biomes.put("#minecraft:is_desert", 5);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains",
            "#minecraft:is_plains");

        // Execute
        String matchingTag = rules.getMatchingTag(plainsBiome);

        // Verify
        assertEquals("#minecraft:is_plains", matchingTag);
    }

    @Test
    @DisplayName("getMatchingTag: returns null when no match")
    void testGetMatchingTag_NoMatch() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> desertBiome = createMockBiome("minecraft:desert",
            "#minecraft:is_desert");

        // Execute
        String matchingTag = rules.getMatchingTag(desertBiome);

        // Verify
        assertNull(matchingTag);
    }

    @Test
    @DisplayName("getMatchingTag: returns first matching tag")
    void testGetMatchingTag_FirstMatch() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        biomes.put("#minecraft:has_structure/village_plains", 8);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains",
            "#minecraft:is_plains", "#minecraft:has_structure/village_plains");

        // Execute
        String matchingTag = rules.getMatchingTag(plainsBiome);

        // Verify - returns first match (insertion order)
        assertEquals("#minecraft:is_plains", matchingTag);
    }

    // ============================================================
    // GET WEIGHT TESTS
    // ============================================================

    @Test
    @DisplayName("getWeight: returns weight for existing tag")
    void testGetWeight_ExistingTag() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        biomes.put("#minecraft:is_desert", 5);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        // Execute & Verify
        assertEquals(10, rules.getWeight("#minecraft:is_plains"));
        assertEquals(5, rules.getWeight("#minecraft:is_desert"));
    }

    @Test
    @DisplayName("getWeight: returns 0 for non-existent tag")
    void testGetWeight_NonExistentTag() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        // Execute & Verify
        assertEquals(0, rules.getWeight("#minecraft:is_ocean"));
    }

    // ============================================================
    // GET WEIGHT FOR BIOME TESTS (uses PatternMatcher)
    // ============================================================

    @Test
    @DisplayName("getWeightForBiome: returns weight from matching tag")
    void testGetWeightForBiome_TagMatch() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains",
            "#minecraft:is_plains");

        // Execute
        int weight = rules.getWeightForBiome(plainsBiome);

        // Verify
        assertEquals(10, weight);
    }

    @Test
    @DisplayName("getWeightForBiome: returns 0 when no match")
    void testGetWeightForBiome_NoMatch() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> desertBiome = createMockBiome("minecraft:desert",
            "#minecraft:is_desert");

        // Execute
        int weight = rules.getWeightForBiome(desertBiome);

        // Verify
        assertEquals(0, weight);
    }

    @Test
    @DisplayName("getWeightForBiome: direct biome ID beats tag")
    void testGetWeightForBiome_DirectIDBeatstag() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("minecraft:plains", 15);         // Direct ID (specificity 25)
        biomes.put("#minecraft:is_plains", 10);     // Tag (specificity 24)
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains",
            "#minecraft:is_plains");

        // Execute
        int weight = rules.getWeightForBiome(plainsBiome);

        // Verify - direct ID has higher specificity
        assertEquals(15, weight);
    }

    @Test
    @DisplayName("getWeightForBiome: specific tag beats wildcard")
    void testGetWeightForBiome_SpecificTagBeatsWildcard() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        biomes.put("#minecraft:*", 5);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains",
            "#minecraft:is_plains", "#minecraft:overworld");

        // Execute
        int weight = rules.getWeightForBiome(plainsBiome);

        // Verify - specific tag has higher specificity
        assertEquals(10, weight);
    }

    // ============================================================
    // STATUS CHECK TESTS
    // ============================================================

    @Test
    @DisplayName("isMVSControlled: true for MVS_CONFIG source")
    void testIsMVSControlled_True() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        // Verify
        assertTrue(rules.isMVSControlled());
    }

    @Test
    @DisplayName("isMVSControlled: false for EMPTY source")
    void testIsMVSControlled_FalseForEmpty() {
        // Setup
        BiomeRules rules = BiomeRules.empty();

        // Verify
        assertFalse(rules.isMVSControlled());
    }

    @Test
    @DisplayName("isEmpty: true for EMPTY source")
    void testIsEmpty_True() {
        // Setup
        BiomeRules rules = BiomeRules.empty();

        // Verify
        assertTrue(rules.isEmpty());
    }

    @Test
    @DisplayName("isEmpty: false for MVS_CONFIG source")
    void testIsEmpty_FalseForMVSConfig() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        // Verify
        assertFalse(rules.isEmpty());
    }

    // ============================================================
    // IMMUTABILITY TESTS
    // ============================================================

    @Test
    @DisplayName("Immutability: modifying source map doesn't affect rules")
    void testImmutability_SourceMap() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        // Modify original map
        biomes.put("#minecraft:is_desert", 5);
        biomes.put("#minecraft:is_plains", 999);

        // Verify - rules unchanged
        assertEquals(1, rules.tags.size());
        assertEquals(10, rules.getWeight("#minecraft:is_plains"));
        assertEquals(0, rules.getWeight("#minecraft:is_desert"));
    }

    @Test
    @DisplayName("Immutability: can't modify tags list")
    void testImmutability_TagsList() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        // Verify - list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            rules.tags.add("#minecraft:is_desert");
        });
    }

    @Test
    @DisplayName("Immutability: can't modify directBiomes list")
    void testImmutability_DirectBiomesList() {
        // Setup
        BiomeRules rules = BiomeRules.empty();

        // Verify - list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            rules.directBiomes.add("minecraft:plains");
        });
    }

    @Test
    @DisplayName("Immutability: can't modify weights map")
    void testImmutability_WeightsMap() {
        // Setup
        Map<String, Integer> biomes = new LinkedHashMap<>();
        biomes.put("#minecraft:is_plains", 10);
        MVSConfig.ConfiguredStructure config = createMockConfiguredStructure(biomes);
        BiomeRules rules = BiomeRules.fromMVSConfig(config);

        // Verify - map is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            rules.weights.put("#minecraft:is_desert", 5);
        });
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

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

        // Mock tags
        Stream<TagKey<Biome>> tagStream = Stream.of(tags)
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
            });
        when(holder.tags()).thenReturn(tagStream);

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

    private MVSConfig.ConfiguredStructure createMockConfiguredStructure(Map<String, Integer> biomes) {
        net.minecraft.resources.ResourceLocation dummyStructure =
            net.minecraft.resources.ResourceLocation.parse("test:structure");
        return new MVSConfig.ConfiguredStructure(
            dummyStructure,
            biomes,  // originalBiomeTags
            biomes   // expandedBiomeTags (same for tests)
        );
    }
}
