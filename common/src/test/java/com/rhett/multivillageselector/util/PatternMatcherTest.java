package com.rhett.multivillageselector.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for PatternMatcher utility class.
 * Tests pattern matching, specificity scoring, and biome value resolution.
 */
class PatternMatcherTest {

    @BeforeEach
    void setUp() {
        // Clear caches before each test to ensure mock biomes get fresh tag sets
        PatternMatcher.clearCaches();
    }

    // ============================================================
    // PATTERN MATCHING TESTS
    // ============================================================

    @Test
    @DisplayName("Pattern matching: exact match")
    void testMatchesExact() {
        assertTrue(PatternMatcher.matches("minecraft:plains", "minecraft:plains"));
        assertFalse(PatternMatcher.matches("minecraft:plains", "minecraft:desert"));
    }

    @Test
    @DisplayName("Pattern matching: wildcard before colon")
    void testMatchesWildcardBeforeColon() {
        assertTrue(PatternMatcher.matches("minecraft:plains", "*:plains"));
        assertTrue(PatternMatcher.matches("ctov:village_plains", "*:village_plains"));
        assertFalse(PatternMatcher.matches("minecraft:desert", "*:plains"));
    }

    @Test
    @DisplayName("Pattern matching: wildcard after colon")
    void testMatchesWildcardAfterColon() {
        assertTrue(PatternMatcher.matches("minecraft:village_plains", "minecraft:village_*"));
        assertTrue(PatternMatcher.matches("minecraft:village_desert", "minecraft:village_*"));
        assertTrue(PatternMatcher.matches("minecraft:anything", "minecraft:*"));
        assertFalse(PatternMatcher.matches("ctov:village_plains", "minecraft:village_*"));
    }

    @Test
    @DisplayName("Pattern matching: multiple wildcards")
    void testMatchesMultipleWildcards() {
        assertTrue(PatternMatcher.matches("minecraft:village_plains", "*:*"));
        assertTrue(PatternMatcher.matches("ctov:small/village_plains", "*:*"));
        assertTrue(PatternMatcher.matches("minecraft:village_plains", "*craft*:*village*"));
        assertFalse(PatternMatcher.matches("bca:town_desert", "*craft*:*village*"));
    }

    @Test
    @DisplayName("Pattern matching: tag patterns")
    void testMatchesTagPatterns() {
        assertTrue(PatternMatcher.matches("#minecraft:is_plains", "#minecraft:is_plains"));
        assertTrue(PatternMatcher.matches("#minecraft:is_plains", "#minecraft:*"));
        assertTrue(PatternMatcher.matches("#minecraft:is_plains", "#*:*"));
        assertFalse(PatternMatcher.matches("#minecraft:is_plains", "minecraft:*"));
    }

    @Test
    @DisplayName("Pattern matching: dot escaping")
    void testMatchesDotEscaping() {
        // Dots in patterns should be treated as literals, not regex wildcards
        assertFalse(PatternMatcher.matches("minecraftXplains", "minecraft.plains"));
        assertTrue(PatternMatcher.matches("some.name.with.dots", "some.name.with.dots"));
    }

    // ============================================================
    // SPECIFICITY SCORING TESTS
    // ============================================================

    @Test
    @DisplayName("Specificity: literal biome ID (most specific)")
    void testSpecificityLiteralBiomeID() {
        assertEquals(25, PatternMatcher.getSpecificity("minecraft:plains"));
        assertEquals(25, PatternMatcher.getSpecificity("ctov:village_plains"));
    }

    @Test
    @DisplayName("Specificity: biome tags")
    void testSpecificityBiomeTags() {
        assertEquals(24, PatternMatcher.getSpecificity("#minecraft:is_plains"));
        assertEquals(24, PatternMatcher.getSpecificity("#minecraft:has_structure/village_plains"));
    }

    @Test
    @DisplayName("Specificity: patterns with single wildcard")
    void testSpecificitySingleWildcard() {
        assertEquals(18, PatternMatcher.getSpecificity("minecraft:*"));
        assertEquals(17, PatternMatcher.getSpecificity("#minecraft:*"));
        // "*:village" = 20 (base) + 1 (no #) + 0 (no literal before) + 2 (literal after) - 5 (1 wildcard) = 18
        assertEquals(18, PatternMatcher.getSpecificity("*:village"));
    }

    @Test
    @DisplayName("Specificity: patterns with multiple wildcards")
    void testSpecificityMultipleWildcards() {
        // "*craft*:*village*" = 20 (base) + 1 (no #) + 2 (has "craft") + 2 (has "village") - 20 (4 wildcards) = 5
        assertEquals(5, PatternMatcher.getSpecificity("*craft*:*village*"));

        // "*:*" has 2 wildcards (-10) and no literal content → special case -10
        assertEquals(-10, PatternMatcher.getSpecificity("*:*"));
    }

    @Test
    @DisplayName("Specificity: ultimate fallbacks")
    void testSpecificityUltimateFallbacks() {
        assertEquals(-10, PatternMatcher.getSpecificity("*:*"));
        assertEquals(-20, PatternMatcher.getSpecificity("#*:*"));
    }

    @Test
    @DisplayName("Specificity: patterns with literal content")
    void testSpecificityLiteralContent() {
        // "minecraft:*" has literal namespace
        int score1 = PatternMatcher.getSpecificity("minecraft:*");

        // "*:plains" has literal path
        int score2 = PatternMatcher.getSpecificity("*:plains");

        // Both have 1 wildcard (-5), but different literal bonuses
        assertTrue(score1 > 0);
        assertTrue(score2 > 0);
    }

    @Test
    @DisplayName("Specificity: ordering verification")
    void testSpecificityOrdering() {
        int literalID = PatternMatcher.getSpecificity("minecraft:plains");
        int tag = PatternMatcher.getSpecificity("#minecraft:is_plains");
        int wildcardLiteral = PatternMatcher.getSpecificity("minecraft:*");
        int wildcardTag = PatternMatcher.getSpecificity("#minecraft:*");
        int allWildcard = PatternMatcher.getSpecificity("*:*");
        int allWildcardTag = PatternMatcher.getSpecificity("#*:*");

        // Verify ordering: literal > tag > wildcard > all-wildcard > all-wildcard-tag
        assertTrue(literalID > tag);
        assertTrue(tag > wildcardLiteral);
        assertTrue(wildcardLiteral > wildcardTag);
        assertTrue(wildcardTag > allWildcard);
        assertTrue(allWildcard > allWildcardTag);
    }

    // ============================================================
    // GET VALUE FOR BIOME TESTS (with mocks)
    // ============================================================

    @Test
    @DisplayName("getValueForBiome: exact biome ID match")
    void testGetValueForBiome_ExactMatch() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("minecraft:plains",
            "#minecraft:has_structure/village_plains", "#minecraft:is_plains");

        Map<String, Integer> patternMap = new HashMap<>();
        patternMap.put("minecraft:plains", 15);
        patternMap.put("#minecraft:is_plains", 10);

        // Execute
        Integer result = PatternMatcher.getValueForBiome(patternMap, biomeHolder, 0);

        // Verify - exact biome ID (specificity 25) beats tag (specificity 24)
        assertEquals(15, result);
    }

    @Test
    @DisplayName("getValueForBiome: tag match")
    void testGetValueForBiome_TagMatch() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("minecraft:desert",
            "#minecraft:has_structure/village_desert", "#minecraft:is_desert");

        Map<String, Integer> patternMap = new HashMap<>();
        patternMap.put("#minecraft:is_desert", 8);
        patternMap.put("#minecraft:*", 2);

        // Execute
        Integer result = PatternMatcher.getValueForBiome(patternMap, biomeHolder, 0);

        // Verify - specific tag beats wildcard tag
        assertEquals(8, result);
    }

    @Test
    @DisplayName("getValueForBiome: wildcard fallback")
    void testGetValueForBiome_WildcardFallback() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("terralith:volcanic_peaks",
            "#terralith:hot_biomes");

        Map<String, Integer> patternMap = new HashMap<>();
        patternMap.put("minecraft:*", 5);
        patternMap.put("*:*", 1);

        // Execute
        Integer result = PatternMatcher.getValueForBiome(patternMap, biomeHolder, 0);

        // Verify - *:* wildcard matches (no minecraft:* match)
        assertEquals(1, result);
    }

    @Test
    @DisplayName("getValueForBiome: same specificity, higher value wins")
    void testGetValueForBiome_TieBreaker() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("minecraft:plains",
            "#minecraft:is_plains", "#minecraft:has_structure/village_plains");

        Map<String, Integer> patternMap = new HashMap<>();
        // Both are tags with same specificity (24)
        patternMap.put("#minecraft:is_plains", 5);
        patternMap.put("#minecraft:has_structure/village_plains", 10);

        // Execute
        Integer result = PatternMatcher.getValueForBiome(patternMap, biomeHolder, 0);

        // Verify - higher value wins tie-breaker
        assertEquals(10, result);
    }

    @Test
    @DisplayName("getValueForBiome: no match returns default")
    void testGetValueForBiome_NoMatch() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("minecraft:ocean",
            "#minecraft:is_ocean");

        Map<String, Integer> patternMap = new HashMap<>();
        patternMap.put("minecraft:plains", 10);
        patternMap.put("#minecraft:is_forest", 5);

        // Execute
        Integer result = PatternMatcher.getValueForBiome(patternMap, biomeHolder, 99);

        // Verify - no match, returns default
        assertEquals(99, result);
    }

    @Test
    @DisplayName("getValueForBiome: empty map returns default")
    void testGetValueForBiome_EmptyMap() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("minecraft:plains", "#minecraft:is_plains");
        Map<String, Integer> patternMap = new HashMap<>();

        // Execute
        Integer result = PatternMatcher.getValueForBiome(patternMap, biomeHolder, 42);

        // Verify
        assertEquals(42, result);
    }

    @Test
    @DisplayName("getValueForBiome: null map returns default")
    void testGetValueForBiome_NullMap() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("minecraft:plains", "#minecraft:is_plains");

        // Execute
        Integer result = PatternMatcher.getValueForBiome(null, biomeHolder, 42);

        // Verify
        assertEquals(42, result);
    }

    @Test
    @DisplayName("getValueForBiome: works with Double values")
    void testGetValueForBiome_DoubleValues() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("minecraft:plains", "#minecraft:is_plains");

        Map<String, Double> patternMap = new HashMap<>();
        patternMap.put("minecraft:plains", 0.75);
        patternMap.put("#*:*", 0.5);

        // Execute
        Double result = PatternMatcher.getValueForBiome(patternMap, biomeHolder, 0.0);

        // Verify
        assertEquals(0.75, result);
    }

    @Test
    @DisplayName("getValueForBiome: complex pattern priority")
    void testGetValueForBiome_ComplexPatternPriority() {
        // Setup
        Holder<Biome> biomeHolder = createMockBiome("minecraft:warm_ocean",
            "#minecraft:is_ocean", "#minecraft:is_warm");

        Map<String, Integer> patternMap = new HashMap<>();
        patternMap.put("*:*", 50);              // specificity -10
        patternMap.put("minecraft:*", 85);      // specificity 18
        patternMap.put("#minecraft:*", 75);     // specificity 17
        patternMap.put("#minecraft:is_ocean", 20); // specificity 24
        patternMap.put("minecraft:warm_ocean", 100); // specificity 25 (should win)

        // Execute
        Integer result = PatternMatcher.getValueForBiome(patternMap, biomeHolder, 0);

        // Verify - exact ID has highest specificity
        assertEquals(100, result);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Creates a mock Holder<Biome> with specified biome ID and tags.
     */
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

        return holder;
    }
}
