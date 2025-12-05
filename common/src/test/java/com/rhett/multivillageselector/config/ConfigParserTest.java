package com.rhett.multivillageselector.config;

import com.rhett.multivillageselector.config.MVSConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigParser - JSON5 parsing and validation logic.
 * Pure logic tests - no file I/O, just string → ConfigState transformation.
 */
class ConfigParserTest {

    // ============================================================
    // VALID CONFIG TESTS (HAPPY PATH)
    // ============================================================

    @Test
    @DisplayName("Parse: minimal valid config")
    void testParse_MinimalValid() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ]
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertNotNull(result);
        assertTrue(result.enabled); // Default
        assertFalse(result.debugLogging); // Default
        assertEquals(1, result.interceptStructureSets.size());
        assertEquals("minecraft:villages", result.interceptStructureSets.get(0));
        assertEquals(1, result.structurePoolRaw.size());
    }

    @Test
    @DisplayName("Parse: full config with all optional fields")
    void testParse_FullConfig() throws ConfigParser.ConfigParseException {
        String json = """
            {
              enabled: true,
              debug_logging: true,
              debug_cmd: true,
              show_launch_message: false,
              block_structure_sets: ["minecraft:pillager_outposts"],
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} },
                { structure: "minecraft:village_desert", biomes: {"#minecraft:is_desert": 8} }
              ],
              blacklisted_structures: ["bca:village/witch_hut"],
              biome_frequency: {
                "#*:*": 0.5,
                "#minecraft:is_plains": 1.0
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertNotNull(result);
        assertTrue(result.enabled);
        assertTrue(result.debugLogging);
        assertTrue(result.debugCmd);
        assertFalse(result.showLaunchMessage);
        assertEquals(1, result.blockStructureSets.size());
        assertEquals(1, result.interceptStructureSets.size());
        assertEquals(2, result.structurePoolRaw.size());
        assertEquals(1, result.blacklistedStructures.size());
        assertEquals(2, result.biomeFrequency.size());
    }

    @Test
    @DisplayName("Parse: JSON5 features (single quotes, trailing commas, comments)")
    void testParse_Json5Features() throws ConfigParser.ConfigParseException {
        String json = """
            {
              // This is a comment
              'intercept_structure_sets': ['minecraft:villages'], // Trailing comma OK
              structure_pool: [
                { structure: 'minecraft:village_plains', biomes: {'#minecraft:is_plains': 10}, }, // Trailing comma
              ], // Trailing comma at end
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertNotNull(result);
        assertEquals(1, result.interceptStructureSets.size());
        assertEquals(1, result.structurePoolRaw.size());
    }

    // ============================================================
    // MISSING REQUIRED FIELDS
    // ============================================================

    @Test
    @DisplayName("Error: missing intercept_structure_sets")
    void testError_MissingInterceptStructureSets() {
        String json = """
            {
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ]
            }
            """;

        ConfigParser.ConfigParseException ex = assertThrows(
            ConfigParser.ConfigParseException.class,
            () -> ConfigParser.parse(json)
        );

        assertTrue(ex.getMessage().contains("intercept_structure_sets"));
    }

    @Test
    @DisplayName("Error: missing structure_pool")
    void testError_MissingStructurePool() {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"]
            }
            """;

        ConfigParser.ConfigParseException ex = assertThrows(
            ConfigParser.ConfigParseException.class,
            () -> ConfigParser.parse(json)
        );

        assertTrue(ex.getMessage().contains("structure_pool"));
    }

    @Test
    @DisplayName("Error: empty structure_pool array")
    void testError_EmptyStructurePool() {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: []
            }
            """;

        ConfigParser.ConfigParseException ex = assertThrows(
            ConfigParser.ConfigParseException.class,
            () -> ConfigParser.parse(json)
        );

        assertTrue(ex.getMessage().contains("empty"));
    }

    // ============================================================
    // INVALID FIELD TYPES
    // ============================================================

    @Test
    @DisplayName("Error: intercept_structure_sets is not array")
    void testError_InterceptStructureSetsNotArray() {
        String json = """
            {
              intercept_structure_sets: "minecraft:villages",
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ]
            }
            """;

        assertThrows(
            ConfigParser.ConfigParseException.class,
            () -> ConfigParser.parse(json)
        );
    }

    @Test
    @DisplayName("Error: structure_pool is not array")
    void testError_StructurePoolNotArray() {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: { structure: "minecraft:village_plains" }
            }
            """;

        assertThrows(
            ConfigParser.ConfigParseException.class,
            () -> ConfigParser.parse(json)
        );
    }

    // ============================================================
    // MALFORMED JSON5
    // ============================================================

    @Test
    @DisplayName("Error: completely invalid JSON")
    void testError_InvalidJson() {
        String json = "{ this is not valid json }";

        assertThrows(
            ConfigParser.ConfigParseException.class,
            () -> ConfigParser.parse(json)
        );
    }

    @Test
    @DisplayName("Error: missing closing brace")
    void testError_MissingClosingBrace() {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ]
            """; // Missing }

        assertThrows(
            ConfigParser.ConfigParseException.class,
            () -> ConfigParser.parse(json)
        );
    }

    @Test
    @DisplayName("Error: empty string")
    void testError_EmptyString() {
        assertThrows(
            ConfigParser.ConfigParseException.class,
            () -> ConfigParser.parse("")
        );
    }

    // ============================================================
    // STRUCTURE POOL PARSING
    // ============================================================

    @Test
    @DisplayName("Structure pool: multiple structures")
    void testStructurePool_Multiple() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} },
                { structure: "minecraft:village_desert", biomes: {"#minecraft:is_desert": 8} },
                { structure: "ctov:small/village_oak", biomes: {"#minecraft:is_forest": 5} }
              ]
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertEquals(3, result.structurePoolRaw.size());
        assertEquals("minecraft:village_plains", result.structurePoolRaw.get(0).structure);
        assertEquals("minecraft:village_desert", result.structurePoolRaw.get(1).structure);
        assertEquals("ctov:small/village_oak", result.structurePoolRaw.get(2).structure);
    }

    @Test
    @DisplayName("Structure pool: pattern entry")
    void testStructurePool_Pattern() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "ctov:*/village_*", biomes: {"#*:*": 10} }
              ]
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertEquals(1, result.structurePoolRaw.size());
        assertTrue(result.structurePoolRaw.get(0).structure.contains("*"));
    }

    @Test
    @DisplayName("Structure pool: multiple biome tags with weights")
    void testStructurePool_MultipleBiomeTags() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                {
                  structure: "minecraft:village_plains",
                  biomes: {
                    "#minecraft:is_plains": 10,
                    "#minecraft:is_beach": 5,
                    "#minecraft:is_mountain": 2
                  }
                }
              ]
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertEquals(1, result.structurePoolRaw.size());
        MVSConfig.RawConfigEntry entry = result.structurePoolRaw.get(0);
        assertEquals(3, entry.biomes.size());
        assertEquals(10, entry.biomes.get("#minecraft:is_plains"));
        assertEquals(5, entry.biomes.get("#minecraft:is_beach"));
        assertEquals(2, entry.biomes.get("#minecraft:is_mountain"));
    }

    // ============================================================
    // BIOME FREQUENCY VALIDATION
    // ============================================================

    @Test
    @DisplayName("Biome frequency: valid values (0.0 to 1.0)")
    void testBiomeFrequency_ValidValues() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              biome_frequency: {
                "#*:*": 0.5,
                "#minecraft:is_plains": 1.0,
                "#minecraft:is_ocean": 0.0
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertEquals(3, result.biomeFrequency.size());
        assertEquals(0.5, result.biomeFrequency.get("#*:*"));
        assertEquals(1.0, result.biomeFrequency.get("#minecraft:is_plains"));
        assertEquals(0.0, result.biomeFrequency.get("#minecraft:is_ocean"));
        assertTrue(result.validationWarnings.isEmpty()); // No warnings
    }

    @Test
    @DisplayName("Biome frequency: out-of-range values produce warnings")
    void testBiomeFrequency_OutOfRangeWarnings() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              biome_frequency: {
                "#minecraft:is_hot": 1.5,
                "#minecraft:is_cold": -0.2
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        // Invalid values should be skipped
        assertFalse(result.biomeFrequency.containsKey("#minecraft:is_hot"));
        assertFalse(result.biomeFrequency.containsKey("#minecraft:is_cold"));

        // Should have warnings
        assertFalse(result.validationWarnings.isEmpty());
        assertTrue(result.validationWarnings.stream()
            .anyMatch(w -> w.contains("1.5") || w.contains("out of range")));
    }

    // ============================================================
    // LEGACY FIELD SUPPORT
    // ============================================================

    @Test
    @DisplayName("Legacy: show_first_launch_message supported")
    void testLegacy_ShowFirstLaunchMessage() throws ConfigParser.ConfigParseException {
        String json = """
            {
              show_first_launch_message: true,
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ]
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.showLaunchMessage);
    }

    @Test
    @DisplayName("Legacy: show_launch_message takes priority over show_first_launch_message")
    void testLegacy_ShowLaunchMessagePriority() throws ConfigParser.ConfigParseException {
        String json = """
            {
              show_launch_message: false,
              show_first_launch_message: true,
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ]
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertFalse(result.showLaunchMessage); // show_launch_message wins
    }

    // ============================================================
    // EDGE CASES
    // ============================================================

    @Test
    @DisplayName("Edge case: empty blacklist array")
    void testEdgeCase_EmptyBlacklist() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              blacklisted_structures: []
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.blacklistedStructures.isEmpty());
    }

    @Test
    @DisplayName("Edge case: empty intercept_structure_sets array (valid but unusual)")
    void testEdgeCase_EmptyInterceptSets() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: [],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ]
            }
            """;

        // Should parse successfully (valid but mod won't intercept anything)
        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.interceptStructureSets.isEmpty());
    }

    @Test
    @DisplayName("Edge case: structure with single biome")
    void testEdgeCase_SingleBiome() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ]
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertEquals(1, result.structurePoolRaw.size());
        assertEquals(1, result.structurePoolRaw.get(0).biomes.size());
    }

    // ============================================================
    // PLACEMENT CONFIG TESTS
    // ============================================================

    @Test
    @DisplayName("Placement: empty object inherits everything")
    void testPlacement_EmptyObject() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {}
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertEquals(1, result.placement.size());
        assertTrue(result.placement.containsKey("minecraft:villages"));
        PlacementRule rule = result.placement.get("minecraft:villages");
        assertTrue(rule.isFullyInherited());
    }

    @Test
    @DisplayName("Placement: full config with all fields")
    void testPlacement_FullConfig() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  spacing: 34,
                  separation: 8,
                  salt: 10387312,
                  spreadType: "linear",
                  strategy: "random_spread"
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        PlacementRule rule = result.placement.get("minecraft:villages");
        assertNotNull(rule);
        assertEquals(34, rule.spacing);
        assertEquals(8, rule.separation);
        assertEquals(10387312, rule.salt);
        assertEquals("linear", rule.spreadType);
        assertEquals("random_spread", rule.strategy);
    }

    @Test
    @DisplayName("Placement: partial config inherits missing fields")
    void testPlacement_PartialConfig() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  spacing: 20
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        PlacementRule rule = result.placement.get("minecraft:villages");
        assertEquals(20, rule.spacing);
        assertNull(rule.separation);  // Inherited
        assertNull(rule.salt);        // Inherited
        assertNull(rule.spreadType);  // Inherited
    }

    @Test
    @DisplayName("Placement: invalid spacing generates warning")
    void testPlacement_InvalidSpacing() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  spacing: 0
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.validationWarnings.stream()
            .anyMatch(w -> w.contains("spacing") && w.contains("invalid")));
        // Spacing should be null (inherited) since 0 is invalid
        assertNull(result.placement.get("minecraft:villages").spacing);
    }

    @Test
    @DisplayName("Placement: negative separation generates warning")
    void testPlacement_NegativeSeparation() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  separation: -1
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.validationWarnings.stream()
            .anyMatch(w -> w.contains("separation") && w.contains("invalid")));
        assertNull(result.placement.get("minecraft:villages").separation);
    }

    @Test
    @DisplayName("Placement: separation >= spacing skips entry")
    void testPlacement_SeparationGteSpacing() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  spacing: 10,
                  separation: 10
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.validationWarnings.stream()
            .anyMatch(w -> w.contains("separation") && w.contains("less than") && w.contains("spacing")));
        // Entry should be skipped entirely
        assertFalse(result.placement.containsKey("minecraft:villages"));
    }

    @Test
    @DisplayName("Placement: empty string salt generates warning")
    void testPlacement_EmptySalt() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  salt: ""
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.validationWarnings.stream()
            .anyMatch(w -> w.contains("salt") && w.contains("empty")));
        assertNull(result.placement.get("minecraft:villages").salt);
    }

    @Test
    @DisplayName("Placement: invalid spreadType generates warning")
    void testPlacement_InvalidSpreadType() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  spreadType: "invalid_type"
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.validationWarnings.stream()
            .anyMatch(w -> w.contains("spreadType") && w.contains("not valid")));
        assertNull(result.placement.get("minecraft:villages").spreadType);
    }

    @Test
    @DisplayName("Placement: valid spreadType values accepted")
    void testPlacement_ValidSpreadTypes() throws ConfigParser.ConfigParseException {
        String[] validTypes = {"linear", "triangular", "edge_biased", "corner_biased", "gaussian", "fixed_center"};

        for (String spreadType : validTypes) {
            String json = String.format("""
                {
                  intercept_structure_sets: ["minecraft:villages"],
                  structure_pool: [
                    { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
                  ],
                  placement: {
                    "minecraft:villages": {
                      spreadType: "%s"
                    }
                  }
                }
                """, spreadType);

            ConfigState result = ConfigParser.parse(json);
            assertEquals(spreadType, result.placement.get("minecraft:villages").spreadType,
                "SpreadType " + spreadType + " should be accepted");
        }
    }

    @Test
    @DisplayName("Placement: invalid strategy generates warning")
    void testPlacement_InvalidStrategy() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  strategy: "invalid_strategy"
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertTrue(result.validationWarnings.stream()
            .anyMatch(w -> w.contains("strategy") && w.contains("not valid")));
        assertNull(result.placement.get("minecraft:villages").strategy);
    }

    @Test
    @DisplayName("Placement: multiple structure sets")
    void testPlacement_MultipleStructureSets() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": { spacing: 34 },
                "bca:villages": { spacing: 40 },
                "minecraft:pillager_outposts": { spacing: 64 }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertEquals(3, result.placement.size());
        assertEquals(34, result.placement.get("minecraft:villages").spacing);
        assertEquals(40, result.placement.get("bca:villages").spacing);
        assertEquals(64, result.placement.get("minecraft:pillager_outposts").spacing);
    }

    @Test
    @DisplayName("Placement: spreadType case insensitive")
    void testPlacement_SpreadTypeCaseInsensitive() throws ConfigParser.ConfigParseException {
        String json = """
            {
              intercept_structure_sets: ["minecraft:villages"],
              structure_pool: [
                { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
              ],
              placement: {
                "minecraft:villages": {
                  spreadType: "TRIANGULAR"
                }
              }
            }
            """;

        ConfigState result = ConfigParser.parse(json);

        assertEquals("triangular", result.placement.get("minecraft:villages").spreadType);
    }
}
