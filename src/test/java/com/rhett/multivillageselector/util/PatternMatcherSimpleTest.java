package com.rhett.multivillageselector.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests for PatternMatcher that don't require Minecraft classes.
 * Tests pattern matching and specificity scoring logic.
 */
class PatternMatcherSimpleTest {

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
        assertEquals(18, PatternMatcher.getSpecificity("*:village"));  // Has 2+ chars after colon (+2)
    }

    @Test
    @DisplayName("Specificity: ultimate fallbacks")
    void testSpecificityUltimateFallbacks() {
        assertEquals(-10, PatternMatcher.getSpecificity("*:*"));
        assertEquals(-20, PatternMatcher.getSpecificity("#*:*"));
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

        // Verify ordering
        assertTrue(literalID > tag);
        assertTrue(tag > wildcardLiteral);
        assertTrue(wildcardLiteral > wildcardTag);
        assertTrue(wildcardTag > allWildcard);
        assertTrue(allWildcard > allWildcardTag);
    }
}
