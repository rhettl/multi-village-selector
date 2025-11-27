package com.rhett.multivillageselector.util;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BiomeTagExpander - pattern expansion and specificity logic.
 * Pure utility class tests - no Minecraft runtime dependencies.
 */
class BiomeTagExpanderTest {

    // ============================================================
    // BASIC EXPANSION TESTS
    // ============================================================

    @Test
    @DisplayName("Expansion: literal tags pass through unchanged")
    void testExpansion_LiteralTags() {
        // Setup
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("#minecraft:is_plains", 10);
        input.put("#minecraft:is_desert", 8);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("minecraft:is_desert"),
            ResourceLocation.parse("minecraft:is_forest")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - literals pass through
        assertEquals(2, result.size());
        assertEquals(10, result.get("#minecraft:is_plains"));
        assertEquals(8, result.get("#minecraft:is_desert"));
    }

    @Test
    @DisplayName("Expansion: wildcard #*:* matches all tags")
    void testExpansion_WildcardAll() {
        // Setup
        Map<String, Integer> input = Map.of("#*:*", 5);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("minecraft:is_desert"),
            ResourceLocation.parse("cobblemon:is_forest")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - all tags matched
        assertEquals(3, result.size());
        assertEquals(5, result.get("#minecraft:is_plains"));
        assertEquals(5, result.get("#minecraft:is_desert"));
        assertEquals(5, result.get("#cobblemon:is_forest"));
    }

    @Test
    @DisplayName("Expansion: namespace wildcard #minecraft:*")
    void testExpansion_NamespaceWildcard() {
        // Setup
        Map<String, Integer> input = Map.of("#minecraft:*", 7);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("minecraft:is_desert"),
            ResourceLocation.parse("cobblemon:is_forest"),
            ResourceLocation.parse("terralith:is_hot")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - only minecraft tags matched
        assertEquals(2, result.size());
        assertEquals(7, result.get("#minecraft:is_plains"));
        assertEquals(7, result.get("#minecraft:is_desert"));
        assertNull(result.get("#cobblemon:is_forest"));
        assertNull(result.get("#terralith:is_hot"));
    }

    @Test
    @DisplayName("Expansion: path wildcard #*:is_hot*")
    void testExpansion_PathWildcard() {
        // Setup
        Map<String, Integer> input = Map.of("#*:is_hot*", 9);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_hot"),
            ResourceLocation.parse("terralith:is_hot_desert"),
            ResourceLocation.parse("cobblemon:is_forest"),
            ResourceLocation.parse("minecraft:is_cold")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - only tags matching *hot* in path
        assertEquals(2, result.size());
        assertEquals(9, result.get("#minecraft:is_hot"));
        assertEquals(9, result.get("#terralith:is_hot_desert"));
        assertNull(result.get("#cobblemon:is_forest"));
    }

    // ============================================================
    // SPECIFICITY OVERRIDE TESTS
    // ============================================================

    @Test
    @DisplayName("Specificity: specific literal overrides vague wildcard")
    void testSpecificity_LiteralOverridesWildcard() {
        // Setup - vague pattern then specific literal
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("#*:*", 5);                      // Vague
        input.put("#minecraft:is_plains", 10);     // Specific

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("minecraft:is_desert")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - specific wins for is_plains, vague applies to is_desert
        assertEquals(2, result.size());
        assertEquals(10, result.get("#minecraft:is_plains"));  // Overridden by specific
        assertEquals(5, result.get("#minecraft:is_desert"));   // From vague wildcard
    }

    @Test
    @DisplayName("Specificity: namespace wildcard overrides #*:*")
    void testSpecificity_NamespaceOverridesAll() {
        // Setup
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("#*:*", 3);                  // Most vague
        input.put("#minecraft:*", 7);          // More specific

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("cobblemon:is_forest")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - minecraft tags use namespace pattern, cobblemon uses #*:*
        assertEquals(2, result.size());
        assertEquals(7, result.get("#minecraft:is_plains"));    // Overridden by #minecraft:*
        assertEquals(3, result.get("#cobblemon:is_forest"));    // From #*:*
    }

    @Test
    @DisplayName("Specificity: multiple overrides cascade")
    void testSpecificity_MultipleLevels() {
        // Setup - 3 levels of specificity
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("#*:*", 1);                      // Least specific
        input.put("#minecraft:*", 5);              // More specific
        input.put("#minecraft:is_plains", 10);     // Most specific

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("minecraft:is_desert"),
            ResourceLocation.parse("cobblemon:is_forest")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - each tag gets most specific weight
        assertEquals(3, result.size());
        assertEquals(10, result.get("#minecraft:is_plains"));   // Most specific
        assertEquals(5, result.get("#minecraft:is_desert"));    // #minecraft:*
        assertEquals(1, result.get("#cobblemon:is_forest"));    // #*:*
    }

    // ============================================================
    // SPECIFICITY SCORING TESTS
    // ============================================================

    @Test
    @DisplayName("getPatternSpecificity: literal tags most specific")
    void testPatternSpecificity_Literal() {
        assertEquals(2, BiomeTagExpander.getPatternSpecificity("#minecraft:is_plains"));
        assertEquals(2, BiomeTagExpander.getPatternSpecificity("#cobblemon:is_forest"));
        assertEquals(2, BiomeTagExpander.getPatternSpecificity("minecraft:is_desert")); // Without #
    }

    @Test
    @DisplayName("getPatternSpecificity: patterns with wildcards")
    void testPatternSpecificity_Patterns() {
        assertEquals(1, BiomeTagExpander.getPatternSpecificity("#minecraft:*"));
        assertEquals(1, BiomeTagExpander.getPatternSpecificity("#*:is_hot*"));
        assertEquals(1, BiomeTagExpander.getPatternSpecificity("*:plains"));
    }

    @Test
    @DisplayName("getPatternSpecificity: #*:* most vague")
    void testPatternSpecificity_AllWildcard() {
        assertEquals(0, BiomeTagExpander.getPatternSpecificity("#*:*"));
        assertEquals(0, BiomeTagExpander.getPatternSpecificity("*:*")); // Without #
    }

    // ============================================================
    // EDGE CASES
    // ============================================================

    @Test
    @DisplayName("Edge case: empty input map")
    void testEdgeCase_EmptyInput() {
        Map<String, Integer> input = Map.of();
        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains")
        );

        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Edge case: empty tag set")
    void testEdgeCase_EmptyTagSet() {
        Map<String, Integer> input = Map.of("#*:*", 5);
        Set<ResourceLocation> allTags = Set.of();

        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Edge case: pattern matches no tags")
    void testEdgeCase_NoMatches() {
        Map<String, Integer> input = Map.of("#nonexistent:*", 5);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("cobblemon:is_forest")
        );

        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Edge case: pattern without # prefix (kept as-is for runtime biome ID matching)")
    void testEdgeCase_NoHashPrefix() {
        // Setup - pattern WITHOUT # should NOT expand to tags
        // It's meant for direct biome ID matching at runtime (e.g., minecraft:plains)
        Map<String, Integer> input = Map.of("minecraft:*", 7);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("minecraft:is_desert")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - pattern kept as-is, NOT expanded to tag patterns
        assertEquals(1, result.size());
        assertEquals(7, result.get("minecraft:*"));
        assertNull(result.get("#minecraft:is_plains")); // Should NOT be added
    }

    // ============================================================
    // EXPANSION WITH STATS TESTS
    // ============================================================

    @Test
    @DisplayName("expandWithStats: returns expansion counts")
    void testExpandWithStats_Counts() {
        // Setup
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("#*:*", 1);
        input.put("#minecraft:*", 5);
        input.put("#minecraft:is_plains", 10);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("minecraft:is_desert"),
            ResourceLocation.parse("cobblemon:is_forest")
        );

        // Execute
        BiomeTagExpander.ExpansionResult result =
            BiomeTagExpander.expandWithStats(input, allTags);

        // Verify - expansion counts tracked
        assertEquals(3, result.expansionCounts.get("#*:*"));      // Matched 3 tags
        assertEquals(2, result.expansionCounts.get("#minecraft:*")); // Matched 2 tags
        assertEquals(1, result.expansionCounts.get("#minecraft:is_plains")); // Literal = 1

        // Verify - expanded tags correct
        assertEquals(3, result.expandedTags.size());
        assertEquals(10, result.expandedTags.get("#minecraft:is_plains"));
        assertEquals(5, result.expandedTags.get("#minecraft:is_desert"));
        assertEquals(1, result.expandedTags.get("#cobblemon:is_forest"));
    }

    @Test
    @DisplayName("expandWithStats: literal tags count as 1")
    void testExpandWithStats_LiteralCounts() {
        // Setup
        Map<String, Integer> input = Map.of(
            "#minecraft:is_plains", 10,
            "#cobblemon:is_forest", 8
        );

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains")
        );

        // Execute
        BiomeTagExpander.ExpansionResult result =
            BiomeTagExpander.expandWithStats(input, allTags);

        // Verify - literals count as 1 each
        assertEquals(1, result.expansionCounts.get("#minecraft:is_plains"));
        assertEquals(1, result.expansionCounts.get("#cobblemon:is_forest"));

        // Verify - both literals present (even if not in tag set)
        assertEquals(2, result.expandedTags.size());
    }

    @Test
    @DisplayName("expandWithStats: zero matches tracked")
    void testExpandWithStats_ZeroMatches() {
        // Setup - pattern that matches nothing
        Map<String, Integer> input = Map.of("#nonexistent:*", 5);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains")
        );

        // Execute
        BiomeTagExpander.ExpansionResult result =
            BiomeTagExpander.expandWithStats(input, allTags);

        // Verify - zero matches tracked
        assertEquals(0, result.expansionCounts.get("#nonexistent:*"));
        assertTrue(result.expandedTags.isEmpty());
    }

    // ============================================================
    // COMPLEX PATTERNS
    // ============================================================

    @Test
    @DisplayName("Complex: multiple wildcards in path")
    void testComplex_MultipleWildcardsInPath() {
        // Setup
        Map<String, Integer> input = Map.of("#*:*hot*desert*", 12);

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_hot_desert"),
            ResourceLocation.parse("terralith:has_hot_sandy_desert"),
            ResourceLocation.parse("minecraft:is_cold_desert"), // No "hot"
            ResourceLocation.parse("cobblemon:is_hot") // No "desert"
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - only tags with both "hot" and "desert" matched
        assertEquals(2, result.size());
        assertEquals(12, result.get("#minecraft:is_hot_desert"));
        assertEquals(12, result.get("#terralith:has_hot_sandy_desert"));
    }

    @Test
    @DisplayName("Complex: mixed patterns and literals")
    void testComplex_MixedPatternsAndLiterals() {
        // Setup - realistic config scenario
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("#*:*", 1);                              // Default for all
        input.put("#minecraft:*", 5);                      // Minecraft tags boosted
        input.put("#minecraft:is_plains", 15);             // Plains extra boost
        input.put("#cobblemon:*", 8);                      // Cobblemon tags boosted

        Set<ResourceLocation> allTags = Set.of(
            ResourceLocation.parse("minecraft:is_plains"),
            ResourceLocation.parse("minecraft:is_desert"),
            ResourceLocation.parse("cobblemon:is_forest"),
            ResourceLocation.parse("terralith:is_hot")
        );

        // Execute
        Map<String, Integer> result = BiomeTagExpander.expand(input, allTags);

        // Verify - each tag gets most specific weight
        assertEquals(4, result.size());
        assertEquals(15, result.get("#minecraft:is_plains"));     // Most specific
        assertEquals(5, result.get("#minecraft:is_desert"));      // #minecraft:*
        assertEquals(8, result.get("#cobblemon:is_forest"));      // #cobblemon:*
        assertEquals(1, result.get("#terralith:is_hot"));         // #*:* (default)
    }
}
