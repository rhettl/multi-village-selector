package com.rhett.multivillageselector.util;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BiomePoolExpander - expands biome patterns/tags to literal biome IDs at load time.
 *
 * Algorithm:
 * 1. Sort by specificity (ascending) - lowest first
 * 2. Expand each pattern to literal biome IDs
 * 3. Overlay results - later entries (higher specificity) naturally win
 *
 * Specificity (from PatternMatcher.getSpecificity):
 * - Literal biome ID (e.g., "minecraft:plains") = 25 (most specific)
 * - Literal tag (e.g., "#minecraft:is_plains") = 24
 * - Wildcard patterns lose specificity per wildcard (-5 each)
 * - "#*:*" = -20 (least specific, ultimate fallback)
 *
 * Tie-breaker: Same specificity → higher value wins (sorted ascending, last wins)
 */
@DisplayName("BiomePoolExpander Tests")
public class BiomePoolExpanderTest {

    // Test biomes (simulating registry)
    private Set<ResourceLocation> allBiomes;

    // Test tags (simulating tag registry)
    private Set<ResourceLocation> allTags;

    // Map: biome ID → set of tags it belongs to (with # prefix)
    private Map<ResourceLocation, Set<String>> biomeTags;

    @BeforeEach
    void setUp() {
        BiomePoolExpander.clearCaches();

        // Set up test biomes
        allBiomes = new HashSet<>();
        allBiomes.add(ResourceLocation.parse("minecraft:plains"));
        allBiomes.add(ResourceLocation.parse("minecraft:sunflower_plains"));
        allBiomes.add(ResourceLocation.parse("minecraft:desert"));
        allBiomes.add(ResourceLocation.parse("minecraft:ocean"));
        allBiomes.add(ResourceLocation.parse("minecraft:deep_ocean"));
        allBiomes.add(ResourceLocation.parse("minecraft:cold_ocean"));
        allBiomes.add(ResourceLocation.parse("minecraft:snowy_plains"));
        allBiomes.add(ResourceLocation.parse("terralith:volcanic_peaks"));
        allBiomes.add(ResourceLocation.parse("cobblemon:pokemon_forest"));

        // Set up available tags
        allTags = new HashSet<>();
        allTags.add(ResourceLocation.parse("minecraft:is_plains"));
        allTags.add(ResourceLocation.parse("minecraft:is_desert"));
        allTags.add(ResourceLocation.parse("minecraft:is_ocean"));
        allTags.add(ResourceLocation.parse("minecraft:is_deep_ocean"));
        allTags.add(ResourceLocation.parse("minecraft:is_overworld"));
        allTags.add(ResourceLocation.parse("minecraft:has_structure/igloo"));
        allTags.add(ResourceLocation.parse("minecraft:has_structure/village_desert"));
        allTags.add(ResourceLocation.parse("terralith:is_volcanic"));
        allTags.add(ResourceLocation.parse("cobblemon:has_pokemon"));

        // Set up biome → tags mapping
        biomeTags = new HashMap<>();

        // Plains biomes
        addBiomeTags("minecraft:plains", "#minecraft:is_plains", "#minecraft:is_overworld");
        addBiomeTags("minecraft:sunflower_plains", "#minecraft:is_plains", "#minecraft:is_overworld");
        addBiomeTags("minecraft:snowy_plains", "#minecraft:is_plains", "#minecraft:is_overworld", "#minecraft:has_structure/igloo");

        // Desert
        addBiomeTags("minecraft:desert", "#minecraft:is_desert", "#minecraft:is_overworld", "#minecraft:has_structure/village_desert");

        // Ocean biomes
        addBiomeTags("minecraft:ocean", "#minecraft:is_ocean", "#minecraft:is_overworld");
        addBiomeTags("minecraft:deep_ocean", "#minecraft:is_ocean", "#minecraft:is_deep_ocean", "#minecraft:is_overworld");
        addBiomeTags("minecraft:cold_ocean", "#minecraft:is_ocean", "#minecraft:is_overworld");

        // Modded biomes
        addBiomeTags("terralith:volcanic_peaks", "#terralith:is_volcanic"); // No is_overworld for this test!
        addBiomeTags("cobblemon:pokemon_forest", "#cobblemon:has_pokemon"); // No is_overworld!
    }

    private void addBiomeTags(String biomeId, String... tags) {
        ResourceLocation loc = ResourceLocation.parse(biomeId);
        Set<String> tagSet = new HashSet<>(Arrays.asList(tags));
        biomeTags.put(loc, tagSet);
    }

    // ========== SPECIFICITY RULE TESTS ==========

    @Test
    @DisplayName("Literal biome ID beats tag pattern")
    void literalBiomeIdBeatsTag() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#minecraft:is_plains", 5);      // Specificity 24
        patterns.put("minecraft:plains", 10);         // Specificity 25 (wins)

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // minecraft:plains should get 10 (literal wins over tag)
        assertEquals(10, expanded.get("minecraft:plains"));
        // sunflower_plains only matches tag, gets 5
        assertEquals(5, expanded.get("minecraft:sunflower_plains"));
    }

    @Test
    @DisplayName("Specific tag beats wildcard tag")
    void specificTagBeatsWildcard() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 1);                      // Specificity -20
        patterns.put("#minecraft:is_plains", 10);    // Specificity 24 (wins)

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Plains biomes get 10 from specific tag
        assertEquals(10, expanded.get("minecraft:plains"));
        assertEquals(10, expanded.get("minecraft:sunflower_plains"));
        // Other biomes get 1 from fallback
        assertEquals(1, expanded.get("minecraft:desert"));
        assertEquals(1, expanded.get("minecraft:ocean"));
    }

    @Test
    @DisplayName("Namespace wildcard beats global wildcard")
    void namespaceWildcardBeatsGlobal() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 1);                      // Specificity -20
        patterns.put("#minecraft:is_*", 5);          // Specificity ~19 (matches is_plains, is_ocean, etc.)
        patterns.put("#terralith:*", 8);             // Specificity ~17

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Biomes with #minecraft:is_* tags get 5
        assertEquals(5, expanded.get("minecraft:plains")); // has #minecraft:is_plains
        assertEquals(5, expanded.get("minecraft:ocean"));  // has #minecraft:is_ocean
        // Terralith biomes get 8
        assertEquals(8, expanded.get("terralith:volcanic_peaks"));
        // Cobblemon only has #cobblemon:has_pokemon (no minecraft tag), falls back to #*:*
        assertEquals(1, expanded.get("cobblemon:pokemon_forest"));
    }

    @Test
    @DisplayName("Same specificity: higher weight wins")
    void sameSpecificityHigherWeightWins() {
        // Two literal tags with same specificity
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#minecraft:is_plains", 5);     // Specificity 24, applies to plains
        patterns.put("#minecraft:is_overworld", 10); // Specificity 24, also applies to plains

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Plains has both tags - higher weight (10) should win due to sort order
        assertEquals(10, expanded.get("minecraft:plains"));
    }

    @Test
    @DisplayName("Direct biome ID wildcard expands correctly")
    void directBiomeWildcardExpands() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("minecraft:*ocean*", 8);        // Matches ocean, deep_ocean, cold_ocean

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        assertEquals(8, expanded.get("minecraft:ocean"));
        assertEquals(8, expanded.get("minecraft:deep_ocean"));
        assertEquals(8, expanded.get("minecraft:cold_ocean"));
        // Non-ocean biomes not in result
        assertNull(expanded.get("minecraft:plains"));
    }

    // ========== EXPANSION COVERAGE TESTS ==========

    @Test
    @DisplayName("Fallback #*:* expands to all biomes")
    void globalWildcardExpandsToAll() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 5);

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // All biomes should be in the result (any biome with at least one tag)
        // cobblemon:pokemon_forest only has cobblemon tag, but #*:* matches it
        assertTrue(expanded.containsKey("minecraft:plains"));
        assertTrue(expanded.containsKey("minecraft:desert"));
        assertTrue(expanded.containsKey("terralith:volcanic_peaks"));
        assertTrue(expanded.containsKey("cobblemon:pokemon_forest"));

        for (String biome : expanded.keySet()) {
            assertEquals(5, expanded.get(biome));
        }
    }

    @Test
    @DisplayName("Non-matching pattern produces no entries")
    void nonMatchingPatternProducesNoEntries() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#nonexistent:tag", 10);

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // No biomes have this tag
        assertTrue(expanded.isEmpty());
    }

    @Test
    @DisplayName("Literal biome ID only matches that biome")
    void literalBiomeIdOnlyMatchesThatBiome() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("minecraft:plains", 10);

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Only minecraft:plains should be in result
        assertEquals(1, expanded.size());
        assertEquals(10, expanded.get("minecraft:plains"));
    }

    @Test
    @DisplayName("Biome ID wildcard expands to matching biomes")
    void biomeIdWildcardExpands() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("minecraft:*ocean*", 7);

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Should match ocean, deep_ocean, cold_ocean
        assertEquals(3, expanded.size());
        assertEquals(7, expanded.get("minecraft:ocean"));
        assertEquals(7, expanded.get("minecraft:deep_ocean"));
        assertEquals(7, expanded.get("minecraft:cold_ocean"));
    }

    @Test
    @DisplayName("Tag pattern expands to biomes with that tag")
    void tagPatternExpandsToBiomesWithTag() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#minecraft:is_plains", 6);

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Should match plains, sunflower_plains, snowy_plains
        assertEquals(3, expanded.size());
        assertEquals(6, expanded.get("minecraft:plains"));
        assertEquals(6, expanded.get("minecraft:sunflower_plains"));
        assertEquals(6, expanded.get("minecraft:snowy_plains"));
    }

    // ========== COMPLEX SCENARIO TESTS ==========

    @Test
    @DisplayName("Real-world config: village structure with multiple rules")
    void realWorldVillageConfig() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 0);                              // -20: Default exclude
        patterns.put("#minecraft:is_overworld", 1);           // 24: Low weight in overworld
        patterns.put("#minecraft:is_plains", 10);             // 24: Higher in plains (same spec, higher wins)
        patterns.put("minecraft:plains", 15);                 // 25: Highest for exact biome

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // minecraft:plains = 15 (literal wins)
        assertEquals(15, expanded.get("minecraft:plains"));
        // sunflower_plains has is_plains (10) and is_overworld (1), is_plains processed after is_overworld
        // But both have same specificity (24), so higher weight (10) wins
        assertEquals(10, expanded.get("minecraft:sunflower_plains"));
        // desert = has is_overworld, so gets 1 (no is_plains)
        assertEquals(1, expanded.get("minecraft:desert"));
        // terralith = only has #terralith:is_volcanic, matches #*:*, gets 0
        assertEquals(0, expanded.get("terralith:volcanic_peaks"));
        // cobblemon = only has #cobblemon:has_pokemon, matches #*:*, gets 0
        assertEquals(0, expanded.get("cobblemon:pokemon_forest"));
    }

    @Test
    @DisplayName("Order independence: same result regardless of pattern order")
    void orderIndependence() {
        Map<String, Integer> patterns1 = new LinkedHashMap<>();
        patterns1.put("#*:*", 1);
        patterns1.put("#minecraft:is_plains", 10);
        patterns1.put("minecraft:plains", 15);

        Map<String, Integer> patterns2 = new LinkedHashMap<>();
        patterns2.put("minecraft:plains", 15);           // Different order
        patterns2.put("#*:*", 1);
        patterns2.put("#minecraft:is_plains", 10);

        Map<String, Integer> expanded1 = BiomePoolExpander.expand(patterns1, allBiomes, allTags, biomeTags);
        Map<String, Integer> expanded2 = BiomePoolExpander.expand(patterns2, allBiomes, allTags, biomeTags);

        // Results should be identical regardless of input order
        assertEquals(expanded1, expanded2);
    }

    @Test
    @DisplayName("Empty pattern map returns empty result")
    void emptyPatternMapReturnsEmpty() {
        Map<String, Integer> patterns = new LinkedHashMap<>();

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        assertTrue(expanded.isEmpty());
    }

    @Test
    @DisplayName("Zero weight is preserved (can be used to exclude)")
    void zeroWeightIsPreserved() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 5);
        patterns.put("#minecraft:is_ocean", 0);  // Explicitly exclude ocean

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Ocean biomes should have weight 0 (is_ocean is more specific than *:*)
        assertEquals(0, expanded.get("minecraft:ocean"));
        assertEquals(0, expanded.get("minecraft:deep_ocean"));
        // Non-ocean biomes should have weight 5
        assertEquals(5, expanded.get("minecraft:plains"));
    }

    // ========== DOUBLE VALUE TESTS (for biome_frequency) ==========

    @Test
    @DisplayName("Works with Double values for biome_frequency")
    void worksWithDoubleValues() {
        Map<String, Double> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 0.5);
        patterns.put("#minecraft:is_plains", 1.0);

        Map<String, Double> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        assertEquals(1.0, expanded.get("minecraft:plains"), 0.001);
        assertEquals(0.5, expanded.get("minecraft:desert"), 0.001);
    }

    // ========== CACHING TESTS ==========

    @Test
    @DisplayName("Caching: second expansion is faster (same patterns)")
    void cachingWorks() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 1);
        patterns.put("#minecraft:is_plains", 10);

        // First expansion - populates cache
        Map<String, Integer> expanded1 = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Second expansion - should use cache
        Map<String, Integer> expanded2 = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Results should be identical
        assertEquals(expanded1, expanded2);
    }

    // ========== ADDITIONAL PATTERN TYPE TESTS ==========

    @Test
    @DisplayName("Tag suffix wildcard: #minecraft:has_structure/village_*")
    void tagSuffixWildcard() {
        // Add village_plains tag to allTags for this test
        allTags.add(ResourceLocation.parse("minecraft:has_structure/village_plains"));
        allTags.add(ResourceLocation.parse("minecraft:has_structure/village_desert"));
        allTags.add(ResourceLocation.parse("minecraft:has_structure/village_taiga"));

        // Add these tags to biomes
        biomeTags.get(ResourceLocation.parse("minecraft:plains"))
            .add("#minecraft:has_structure/village_plains");
        biomeTags.get(ResourceLocation.parse("minecraft:desert"))
            .add("#minecraft:has_structure/village_desert");

        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 1);                                    // Specificity -20
        patterns.put("#minecraft:has_structure/village_*", 10);    // Specificity ~19

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Biomes with village_* tags get 10
        assertEquals(10, expanded.get("minecraft:plains"));   // has village_plains
        assertEquals(10, expanded.get("minecraft:desert"));   // has village_desert
        // Other biomes fall back to #*:*
        assertEquals(1, expanded.get("minecraft:ocean"));
    }

    @Test
    @DisplayName("Multi-wildcard tag: #*:has_structure/village_*")
    void multiWildcardTag() {
        // Add modded village tags
        allTags.add(ResourceLocation.parse("minecraft:has_structure/village_plains"));
        allTags.add(ResourceLocation.parse("ctov:has_structure/village_cherry"));

        biomeTags.get(ResourceLocation.parse("minecraft:plains"))
            .add("#minecraft:has_structure/village_plains");
        // Add cherry biome with ctov tag
        allBiomes.add(ResourceLocation.parse("minecraft:cherry_grove"));
        biomeTags.put(ResourceLocation.parse("minecraft:cherry_grove"),
            new HashSet<>(Arrays.asList("#ctov:has_structure/village_cherry", "#minecraft:is_overworld")));

        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 1);                               // Specificity -20
        patterns.put("#*:has_structure/village_*", 8);        // Specificity ~14 (multi-wildcard)

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // Both minecraft and ctov village tags match
        assertEquals(8, expanded.get("minecraft:plains"));      // has minecraft:has_structure/village_plains
        assertEquals(8, expanded.get("minecraft:cherry_grove")); // has ctov:has_structure/village_cherry
        // Other biomes fall back
        assertEquals(1, expanded.get("minecraft:ocean"));
    }

    @Test
    @DisplayName("Multi-wildcard biome ID: *:*plains*")
    void multiWildcardBiomeId() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 1);              // Specificity -20 (fallback via tags)
        patterns.put("*:*plains*", 7);        // Specificity ~13 (multi-wildcard ID)

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // All biomes with "plains" in ID get 7
        assertEquals(7, expanded.get("minecraft:plains"));
        assertEquals(7, expanded.get("minecraft:sunflower_plains"));
        assertEquals(7, expanded.get("minecraft:snowy_plains"));
        // Non-plains biomes get fallback
        assertEquals(1, expanded.get("minecraft:desert"));
        assertEquals(1, expanded.get("minecraft:ocean"));
    }

    @Test
    @DisplayName("Global biome ID wildcard: *:* (non-tag)")
    void globalBiomeIdWildcard() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("*:*", 5);  // Specificity -10 (matches all biome IDs directly)

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // All biomes should match via ID wildcard
        assertEquals(5, expanded.get("minecraft:plains"));
        assertEquals(5, expanded.get("minecraft:desert"));
        assertEquals(5, expanded.get("minecraft:ocean"));
        assertEquals(5, expanded.get("terralith:volcanic_peaks"));
        assertEquals(5, expanded.get("cobblemon:pokemon_forest"));
        // Should match all biomes in registry
        assertEquals(allBiomes.size(), expanded.size());
    }

    @Test
    @DisplayName("Global biome *:* beats global tag #*:* (higher specificity)")
    void globalBiomeBeatsGlobalTag() {
        Map<String, Integer> patterns = new LinkedHashMap<>();
        patterns.put("#*:*", 1);  // Specificity -20 (lowest)
        patterns.put("*:*", 5);   // Specificity -10 (higher than tag)

        Map<String, Integer> expanded = BiomePoolExpander.expand(patterns, allBiomes, allTags, biomeTags);

        // *:* (-10) should win over #*:* (-20)
        assertEquals(5, expanded.get("minecraft:plains"));
        assertEquals(5, expanded.get("minecraft:desert"));
    }

    // ========== SPECIFICITY CALCULATION TESTS ==========

    @Test
    @DisplayName("PatternMatcher.getSpecificity returns expected values")
    void specificityCalculation() {
        assertEquals(25, PatternMatcher.getSpecificity("minecraft:plains"));      // Literal ID
        assertEquals(24, PatternMatcher.getSpecificity("#minecraft:is_plains")); // Tag literal
        assertEquals(-10, PatternMatcher.getSpecificity("*:*"));                  // Global wildcard
        assertEquals(-20, PatternMatcher.getSpecificity("#*:*"));                 // Global tag wildcard

        // Wildcard patterns
        assertTrue(PatternMatcher.getSpecificity("minecraft:*") > PatternMatcher.getSpecificity("*:*"));
        assertTrue(PatternMatcher.getSpecificity("#minecraft:*") > PatternMatcher.getSpecificity("#*:*"));
    }
}
