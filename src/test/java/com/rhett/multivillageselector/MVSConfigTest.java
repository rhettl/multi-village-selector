package com.rhett.multivillageselector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MVSConfig pattern matching and utility methods
 */
class MVSConfigTest {

    @Test
    @DisplayName("Pattern matching: exact match without wildcards")
    void testExactMatch() {
        assertTrue(MVSConfig.matchesPattern(
            "minecraft:village_plains",
            "minecraft:village_plains"
        ));

        assertFalse(MVSConfig.matchesPattern(
            "minecraft:village_desert",
            "minecraft:village_plains"
        ));
    }

    @Test
    @DisplayName("Pattern matching: single wildcard in middle")
    void testWildcardInMiddle() {
        // Pattern: ctov:*/village_plains
        String pattern = "ctov:*/village_plains";

        assertTrue(MVSConfig.matchesPattern("ctov:small/village_plains", pattern));
        assertTrue(MVSConfig.matchesPattern("ctov:medium/village_plains", pattern));
        assertTrue(MVSConfig.matchesPattern("ctov:large/village_plains", pattern));

        // Should not match different ending
        assertFalse(MVSConfig.matchesPattern("ctov:small/village_desert", pattern));

        // Should not match different namespace
        assertFalse(MVSConfig.matchesPattern("minecraft:small/village_plains", pattern));
    }

    @Test
    @DisplayName("Pattern matching: wildcard at end")
    void testWildcardAtEnd() {
        // Pattern: towns_and_towers:village_*
        String pattern = "towns_and_towers:village_*";

        assertTrue(MVSConfig.matchesPattern("towns_and_towers:village_plains", pattern));
        assertTrue(MVSConfig.matchesPattern("towns_and_towers:village_desert", pattern));
        assertTrue(MVSConfig.matchesPattern("towns_and_towers:village_ocean", pattern));
        assertTrue(MVSConfig.matchesPattern("towns_and_towers:village_anything", pattern));

        // Should not match without village_ prefix
        assertFalse(MVSConfig.matchesPattern("towns_and_towers:something_else", pattern));
    }

    @Test
    @DisplayName("Pattern matching: wildcard at start")
    void testWildcardAtStart() {
        // Pattern: *:village_ocean
        String pattern = "*:village_ocean";

        assertTrue(MVSConfig.matchesPattern("joshie:village_ocean", pattern));
        assertTrue(MVSConfig.matchesPattern("towns_and_towers:village_ocean", pattern));
        assertTrue(MVSConfig.matchesPattern("anymod:village_ocean", pattern));
        assertTrue(MVSConfig.matchesPattern("minecraft:village_ocean", pattern));

        // Should not match different structure name
        assertFalse(MVSConfig.matchesPattern("joshie:village_plains", pattern));
    }

    @Test
    @DisplayName("Pattern matching: multiple wildcards")
    void testMultipleWildcards() {
        // Pattern: ctov:*/village_*
        String pattern = "ctov:*/village_*";

        assertTrue(MVSConfig.matchesPattern("ctov:small/village_plains", pattern));
        assertTrue(MVSConfig.matchesPattern("ctov:medium/village_desert", pattern));
        assertTrue(MVSConfig.matchesPattern("ctov:large/village_taiga", pattern));

        // Should not match different namespace
        assertFalse(MVSConfig.matchesPattern("minecraft:small/village_plains", pattern));

        // Should not match missing middle part
        assertFalse(MVSConfig.matchesPattern("ctov:village_plains", pattern));
    }

    @Test
    @DisplayName("Pattern matching: full wildcard")
    void testFullWildcard() {
        // Pattern: *
        String pattern = "*";

        assertTrue(MVSConfig.matchesPattern("minecraft:village_plains", pattern));
        assertTrue(MVSConfig.matchesPattern("ctov:small/village_desert", pattern));
        assertTrue(MVSConfig.matchesPattern("anything", pattern));
    }

    @Test
    @DisplayName("Pattern matching: dots in resource location")
    void testDotsInResourceLocation() {
        // Dots should be treated literally, not as regex wildcards
        String pattern = "mod.name:village_*";

        assertTrue(MVSConfig.matchesPattern("mod.name:village_plains", pattern));

        // Should NOT match - dots are literal
        assertFalse(MVSConfig.matchesPattern("modXname:village_plains", pattern));
    }

    @Test
    @DisplayName("Pattern matching: case sensitivity")
    void testCaseSensitivity() {
        // Minecraft resource locations are lowercase by convention
        String pattern = "minecraft:village_plains";

        assertTrue(MVSConfig.matchesPattern("minecraft:village_plains", pattern));

        // Case should matter (though in practice Minecraft normalizes to lowercase)
        assertFalse(MVSConfig.matchesPattern("Minecraft:Village_Plains", pattern));
    }

    @Test
    @DisplayName("Pattern matching: empty strings")
    void testEmptyStrings() {
        // Edge case: empty pattern should only match empty string
        assertTrue(MVSConfig.matchesPattern("", ""));
        assertFalse(MVSConfig.matchesPattern("minecraft:village_plains", ""));
        assertFalse(MVSConfig.matchesPattern("", "minecraft:village_plains"));
    }

    @Test
    @DisplayName("Pattern specificity: specific structure vs wildcard")
    void testPatternSpecificity() {
        // When multiple patterns match, most specific should win
        // This is tested in discoverStructures(), but we can verify pattern matching works correctly

        String structure = "ctov:large/village_plains";

        // All these patterns should match
        assertTrue(MVSConfig.matchesPattern(structure, "ctov:*/village_*"));
        assertTrue(MVSConfig.matchesPattern(structure, "ctov:*/village_plains"));
        assertTrue(MVSConfig.matchesPattern(structure, "ctov:large/village_plains"));
        assertTrue(MVSConfig.matchesPattern(structure, "*"));

        // Specificity is determined by: pattern length - number of wildcards
        // "ctov:large/village_plains" (25 chars, 0 wildcards) = specificity 25
        // "ctov:*/village_plains" (21 chars, 1 wildcard) = specificity 20
        // "ctov:*/village_*" (16 chars, 2 wildcards) = specificity 14
        // "*" (1 char, 1 wildcard) = specificity 0
    }

    @Test
    @DisplayName("Pattern matching: special regex characters escaped")
    void testSpecialCharactersEscaped() {
        // Other special regex characters should be treated literally
        // Only * should be treated as wildcard

        // Dots are already tested above

        // In practice, Minecraft resource locations don't contain these,
        // but the pattern matcher should handle them correctly
        String pattern = "mod-name:village_plains";
        assertTrue(MVSConfig.matchesPattern("mod-name:village_plains", pattern));
    }

    @Test
    @DisplayName("Replacement entry: pattern creation")
    void testReplacementEntryPattern() {
        MVSConfig.ReplacementEntry entry = MVSConfig.ReplacementEntry.pattern("ctov:*/village_*", 30);

        assertEquals("ctov:*/village_*", entry.pattern);
        assertEquals(30, entry.weight);
        assertTrue(entry.isPattern);
        assertFalse(entry.isEmpty);
    }

    @Test
    @DisplayName("Replacement entry: empty creation")
    void testReplacementEntryEmpty() {
        MVSConfig.ReplacementEntry entry = MVSConfig.ReplacementEntry.empty(60);

        assertEquals(60, entry.weight);
        assertTrue(entry.isEmpty);
        assertFalse(entry.isPattern);
    }

    @Test
    @DisplayName("Weighted structure: empty creation")
    void testWeightedStructureEmpty() {
        MVSConfig.WeightedStructure ws = MVSConfig.WeightedStructure.empty(90);

        assertEquals(90, ws.weight);
        assertTrue(ws.isEmpty);
    }
}
