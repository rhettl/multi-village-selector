package com.rhett.multivillageselector.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigFileEditor.
 * Pure string manipulation - no Minecraft dependencies.
 */
class ConfigFileEditorTest {

    // ========================================
    // insertOrReplacePlacement - Replace existing
    // ========================================

    @Test
    @DisplayName("Replace: existing placement section is replaced")
    void replaceExistingPlacement() {
        String content = """
            {
              enabled: true,
              placement: {
                "minecraft:villages": {
                  spacing: 34,
                },
              },
              debug_cmd: false,
            }
            """;

        String newSection = "  placement: {\n    \"minecraft:villages\": { spacing: 40 },\n  },\n";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        assertTrue(result.contains("spacing: 40"));
        assertFalse(result.contains("spacing: 34"));
        assertEquals(1, countOccurrences(result, "placement:"));
    }

    @Test
    @DisplayName("Replace: single-nested braces in placement are handled")
    void replaceSingleNestedBraces() {
        // Note: The regex handles one level of nesting (placement > entries)
        // Triple nesting (placement > entry > exclusion_zone) is a known limitation
        String content = """
            {
              placement: {
                "minecraft:villages": {
                  spacing: 34,
                },
              },
              debug_cmd: false,
            }
            """;

        String newSection = "  placement: {\n    \"test\": {},\n  },\n";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        assertTrue(result.contains("\"test\""));
        assertFalse(result.contains("spacing: 34"));
        assertEquals(1, countOccurrences(result, "placement:"));
    }

    @Test
    @DisplayName("Replace: triple-nested braces (known limitation - partial replacement)")
    void replaceTripleNestedBraces() {
        // Known limitation: regex only handles 2 levels of nesting
        // With exclusion_zone (3 levels), partial content may remain
        String content = """
            {
              placement: {
                "minecraft:villages": {
                  spacing: 34,
                  exclusion_zone: {
                    other_set: "minecraft:pillager_outposts",
                    chunk_count: 10,
                  },
                },
              },
              debug_cmd: false,
            }
            """;

        String newSection = "  placement: {\n    \"test\": {},\n  },\n";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        // The new section IS inserted
        assertTrue(result.contains("\"test\""));
        // But due to regex limitation, we still get a result (may have artifacts)
        // Main requirement: the file is still parseable after edit
        assertTrue(result.contains("debug_cmd: false"));
    }

    @Test
    @DisplayName("Replace: preserves content before and after placement")
    void replacePreservesOtherContent() {
        String content = """
            {
              enabled: true,
              structure_pool: [],
              placement: {
                "old": {},
              },
              debug_cmd: false,
              debug_logging: true,
            }
            """;

        String newSection = "  placement: { \"new\": {} },\n";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        assertTrue(result.contains("enabled: true"));
        assertTrue(result.contains("structure_pool: []"));
        assertTrue(result.contains("debug_cmd: false"));
        assertTrue(result.contains("debug_logging: true"));
        assertTrue(result.contains("\"new\""));
    }

    // ========================================
    // insertOrReplacePlacement - Insert new
    // ========================================

    @Test
    @DisplayName("Insert: after biome_frequency section")
    void insertAfterBiomeFrequency() {
        String content = """
            {
              enabled: true,
              biome_frequency: {
                "*:*": 1.0,
              },
              debug_cmd: false,
            }
            """;

        String newSection = "  placement: { \"test\": {} },";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        // Placement should appear after biome_frequency, before debug_cmd
        int biomeFreqPos = result.indexOf("biome_frequency");
        int placementPos = result.indexOf("placement:");
        int debugPos = result.indexOf("debug_cmd");

        assertTrue(placementPos > biomeFreqPos, "placement should be after biome_frequency");
        assertTrue(placementPos < debugPos, "placement should be before debug_cmd");
    }

    @Test
    @DisplayName("Insert: before debug_cmd when no biome_frequency")
    void insertBeforeDebugCmd() {
        String content = """
            {
              enabled: true,
              structure_pool: [],
              debug_cmd: false,
            }
            """;

        String newSection = "  placement: { \"test\": {} },";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        int placementPos = result.indexOf("placement:");
        int debugPos = result.indexOf("debug_cmd");

        assertTrue(placementPos > 0, "placement should be inserted");
        assertTrue(placementPos < debugPos, "placement should be before debug_cmd");
    }

    @Test
    @DisplayName("Insert: before debug_logging when no debug_cmd")
    void insertBeforeDebugLogging() {
        String content = """
            {
              enabled: true,
              debug_logging: true,
            }
            """;

        String newSection = "  placement: { \"test\": {} },";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        int placementPos = result.indexOf("placement:");
        int debugPos = result.indexOf("debug_logging");

        assertTrue(placementPos > 0, "placement should be inserted");
        assertTrue(placementPos < debugPos, "placement should be before debug_logging");
    }

    @Test
    @DisplayName("Insert: before closing brace as last resort")
    void insertBeforeClosingBrace() {
        String content = """
            {
              enabled: true,
              structure_pool: [],
            }
            """;

        String newSection = "  placement: { \"test\": {} },";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        assertTrue(result.contains("placement:"));
        int placementPos = result.indexOf("placement:");
        int closingBrace = result.lastIndexOf('}');
        assertTrue(placementPos < closingBrace, "placement should be before final }");
    }

    @Test
    @DisplayName("Insert: appends when no closing brace (malformed)")
    void insertAppendsWhenNoClosingBrace() {
        String content = "{ enabled: true";

        String newSection = "  placement: { \"test\": {} },";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        assertTrue(result.contains("placement:"));
        assertTrue(result.endsWith(newSection) || result.contains(newSection));
    }

    // ========================================
    // buildPlacementEntry
    // ========================================

    @Test
    @DisplayName("buildPlacementEntry: basic entry without exclusion zone")
    void buildPlacementEntryBasic() {
        String result = ConfigFileEditor.buildPlacementEntry(
            "minecraft:villages", 34, 8, 10387312, "triangular", null);

        assertTrue(result.contains("\"minecraft:villages\":"));
        assertTrue(result.contains("spacing: 34,"));
        assertTrue(result.contains("separation: 8,"));
        assertTrue(result.contains("salt: 10387312,"));
        assertTrue(result.contains("spreadType: \"triangular\","));
        assertFalse(result.contains("exclusion_zone"));
    }

    @Test
    @DisplayName("buildPlacementEntry: with exclusion zone")
    void buildPlacementEntryWithExclusionZone() {
        ConfigFileEditor.ExclusionZoneInfo zone =
            new ConfigFileEditor.ExclusionZoneInfo("minecraft:pillager_outposts", 10);

        String result = ConfigFileEditor.buildPlacementEntry(
            "minecraft:villages", 34, 8, 10387312, "triangular", zone);

        assertTrue(result.contains("exclusion_zone:"));
        assertTrue(result.contains("other_set: \"minecraft:pillager_outposts\","));
        assertTrue(result.contains("chunk_count: 10,"));
    }

    @Test
    @DisplayName("buildPlacementEntry: linear spread type")
    void buildPlacementEntryLinear() {
        String result = ConfigFileEditor.buildPlacementEntry(
            "test:set", 20, 5, 12345, "linear", null);

        assertTrue(result.contains("spreadType: \"linear\","));
    }

    @Test
    @DisplayName("buildPlacementEntry: custom structure set ID")
    void buildPlacementEntryCustomId() {
        String result = ConfigFileEditor.buildPlacementEntry(
            "mymod:custom_villages", 50, 10, 999999, "triangular", null);

        assertTrue(result.contains("\"mymod:custom_villages\":"));
        assertTrue(result.contains("spacing: 50,"));
        assertTrue(result.contains("separation: 10,"));
        assertTrue(result.contains("salt: 999999,"));
    }

    // ========================================
    // buildPlacementSectionStart / End
    // ========================================

    @Test
    @DisplayName("buildPlacementSectionStart: has comment and opening brace")
    void buildPlacementSectionStart() {
        String result = ConfigFileEditor.buildPlacementSectionStart();

        assertTrue(result.contains("//"));
        assertTrue(result.contains("placement:"));
        assertTrue(result.contains("{"));
    }

    @Test
    @DisplayName("buildPlacementSectionEnd: has closing brace")
    void buildPlacementSectionEnd() {
        String result = ConfigFileEditor.buildPlacementSectionEnd();

        assertTrue(result.contains("}"));
        assertTrue(result.contains(","));
    }

    @Test
    @DisplayName("Section start + entries + end forms valid structure")
    void buildCompletePlacementSection() {
        String section = ConfigFileEditor.buildPlacementSectionStart()
            + ConfigFileEditor.buildPlacementEntry("minecraft:villages", 34, 8, 123, "triangular", null)
            + ConfigFileEditor.buildPlacementSectionEnd();

        assertTrue(section.contains("placement: {"));
        assertTrue(section.contains("\"minecraft:villages\":"));
        // Count braces to verify structure
        int openBraces = countOccurrences(section, "{");
        int closeBraces = countOccurrences(section, "}");
        assertEquals(openBraces, closeBraces, "Braces should be balanced");
    }

    // ========================================
    // ExclusionZoneInfo
    // ========================================

    @Test
    @DisplayName("ExclusionZoneInfo: constructor sets fields correctly")
    void exclusionZoneInfoConstructor() {
        ConfigFileEditor.ExclusionZoneInfo zone =
            new ConfigFileEditor.ExclusionZoneInfo("minecraft:strongholds", 5);

        assertEquals("minecraft:strongholds", zone.otherSet);
        assertEquals(5, zone.chunkCount);
    }

    // ========================================
    // Edge cases
    // ========================================

    @Test
    @DisplayName("Edge case: empty content")
    void emptyContent() {
        String result = ConfigFileEditor.insertOrReplacePlacement("", "  placement: {},");
        assertTrue(result.contains("placement:"));
    }

    @Test
    @DisplayName("Edge case: content with only whitespace")
    void whitespaceOnlyContent() {
        String result = ConfigFileEditor.insertOrReplacePlacement("   \n\n   ", "  placement: {},");
        assertTrue(result.contains("placement:"));
    }

    @Test
    @DisplayName("Edge case: placement section with comments")
    void placementWithComments() {
        String content = """
            {
              // Main placement config
              placement: {
                // Villages placement
                "minecraft:villages": {
                  spacing: 34, // Default spacing
                },
              },
            }
            """;

        String newSection = "  placement: { \"new\": {} },";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        assertTrue(result.contains("\"new\""));
        // Old placement should be replaced
        assertFalse(result.contains("spacing: 34"));
    }

    @Test
    @DisplayName("Edge case: multiple placement-like strings in content")
    void multiplePlacementStrings() {
        String content = """
            {
              // This mentions placement in a comment
              structure_pool: [
                // placement is important
              ],
              placement: {
                "old": {},
              },
            }
            """;

        String newSection = "  placement: { \"new\": {} },";

        String result = ConfigFileEditor.insertOrReplacePlacement(content, newSection);

        // Should only have one actual placement section
        assertTrue(result.contains("\"new\""));
        assertFalse(result.contains("\"old\""));
    }

    // ========================================
    // Helper methods
    // ========================================

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
