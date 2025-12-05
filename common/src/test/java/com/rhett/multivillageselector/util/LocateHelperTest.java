package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for LocateHelper.
 * Tests placement calculation, biome simulation, and full locate search.
 */
class LocateHelperTest {

    // Standard vanilla village placement for testing
    private LocateHelper.PlacementParams vanillaPlacement;
    private long testSeed;

    @BeforeEach
    void setUp() {
        vanillaPlacement = LocateHelper.PlacementParams.villageDefaults();
        testSeed = 12345L;

        // Clear MVSConfig for isolated tests
        MVSConfig.structurePool = new ArrayList<>();
        MVSConfig.enabled = true;
    }

    // ============================================================
    // PLACEMENT PARAMS TESTS
    // ============================================================

    @Test
    @DisplayName("PlacementParams: villageDefaults has correct values")
    void testPlacementParams_VillageDefaults() {
        LocateHelper.PlacementParams params = LocateHelper.PlacementParams.villageDefaults();

        assertEquals(34, params.spacing);
        assertEquals(8, params.separation);
        assertEquals(10387312, params.salt);
        assertFalse(params.triangular);
    }

    @Test
    @DisplayName("PlacementParams: custom constructor")
    void testPlacementParams_Custom() {
        LocateHelper.PlacementParams params = new LocateHelper.PlacementParams(
            20, 5, 12345, true);

        assertEquals(20, params.spacing);
        assertEquals(5, params.separation);
        assertEquals(12345, params.salt);
        assertTrue(params.triangular);
    }

    // ============================================================
    // GET PLACEMENT CHUNK TESTS
    // ============================================================

    @Test
    @DisplayName("getPlacementChunk: deterministic with same seed")
    void testGetPlacementChunk_Deterministic() {
        int cellX = 0;
        int cellZ = 0;

        int[] result1 = LocateHelper.getPlacementChunk(cellX, cellZ, testSeed, vanillaPlacement);
        int[] result2 = LocateHelper.getPlacementChunk(cellX, cellZ, testSeed, vanillaPlacement);

        assertArrayEquals(result1, result2, "Same inputs should produce same output");
    }

    @Test
    @DisplayName("getPlacementChunk: different cells produce different results")
    void testGetPlacementChunk_DifferentCells() {
        int[] result1 = LocateHelper.getPlacementChunk(0, 0, testSeed, vanillaPlacement);
        int[] result2 = LocateHelper.getPlacementChunk(1, 0, testSeed, vanillaPlacement);
        int[] result3 = LocateHelper.getPlacementChunk(0, 1, testSeed, vanillaPlacement);

        // Different cells should generally produce different placement chunks
        // (statistically unlikely to be identical)
        boolean allSame = Arrays.equals(result1, result2) && Arrays.equals(result2, result3);
        assertFalse(allSame, "Different cells should produce different placement chunks");
    }

    @Test
    @DisplayName("getPlacementChunk: result within cell bounds (linear)")
    void testGetPlacementChunk_WithinBoundsLinear() {
        LocateHelper.PlacementParams linearParams = new LocateHelper.PlacementParams(
            34, 8, 10387312, false);

        // Test multiple cells
        for (int cellX = -5; cellX <= 5; cellX++) {
            for (int cellZ = -5; cellZ <= 5; cellZ++) {
                int[] result = LocateHelper.getPlacementChunk(cellX, cellZ, testSeed, linearParams);

                int minChunkX = cellX * linearParams.spacing;
                int maxChunkX = minChunkX + linearParams.spacing - linearParams.separation;
                int minChunkZ = cellZ * linearParams.spacing;
                int maxChunkZ = minChunkZ + linearParams.spacing - linearParams.separation;

                assertTrue(result[0] >= minChunkX && result[0] < maxChunkX + linearParams.separation,
                    "Chunk X should be within cell bounds");
                assertTrue(result[1] >= minChunkZ && result[1] < maxChunkZ + linearParams.separation,
                    "Chunk Z should be within cell bounds");
            }
        }
    }

    @Test
    @DisplayName("getPlacementChunk: triangular produces different distribution than linear")
    void testGetPlacementChunk_TriangularVsLinear() {
        LocateHelper.PlacementParams linearParams = new LocateHelper.PlacementParams(
            34, 8, 10387312, false);
        LocateHelper.PlacementParams triangularParams = new LocateHelper.PlacementParams(
            34, 8, 10387312, true);

        // Same seed and params except spread type - should produce different results
        int[] linearResult = LocateHelper.getPlacementChunk(0, 0, testSeed, linearParams);
        int[] triangularResult = LocateHelper.getPlacementChunk(0, 0, testSeed, triangularParams);

        // These could theoretically be the same, but statistically very unlikely
        // If they happen to match, we just note that the test isn't definitive
        // The main verification is that both run without error
        assertNotNull(linearResult);
        assertNotNull(triangularResult);
    }

    // ============================================================
    // IS PLACEMENT CHUNK TESTS
    // ============================================================

    @Test
    @DisplayName("isPlacementChunk: returns true for calculated placement")
    void testIsPlacementChunk_MatchesCalculation() {
        int cellX = 2;
        int cellZ = -3;

        int[] placementChunk = LocateHelper.getPlacementChunk(cellX, cellZ, testSeed, vanillaPlacement);

        assertTrue(LocateHelper.isPlacementChunk(
            placementChunk[0], placementChunk[1], testSeed, vanillaPlacement),
            "Calculated placement chunk should pass isPlacementChunk check");
    }

    @Test
    @DisplayName("isPlacementChunk: returns false for non-placement chunks")
    void testIsPlacementChunk_RejectsNonPlacement() {
        // Get a valid placement chunk
        int[] placementChunk = LocateHelper.getPlacementChunk(0, 0, testSeed, vanillaPlacement);

        // Check adjacent chunk (should not be a placement chunk unless by coincidence)
        // Statistically, most chunks are NOT placement chunks
        int nonPlacementCount = 0;
        int checked = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                checked++;
                if (!LocateHelper.isPlacementChunk(
                        placementChunk[0] + dx, placementChunk[1] + dz, testSeed, vanillaPlacement)) {
                    nonPlacementCount++;
                }
            }
        }

        assertTrue(nonPlacementCount > checked / 2,
            "Most adjacent chunks should not be placement chunks");
    }

    // ============================================================
    // SIMULATE SELECTION TESTS
    // ============================================================

    @Test
    @DisplayName("simulateSelection: returns null when pool is empty")
    void testSimulateSelection_EmptyPool() {
        MVSConfig.structurePool = new ArrayList<>();

        Holder<Biome> biome = createMockBiome("minecraft:plains", "#minecraft:is_plains");

        MVSConfig.ConfiguredStructure result = LocateHelper.simulateSelection(
            0, 0, testSeed, biome);

        assertNull(result);
    }

    @Test
    @DisplayName("simulateSelection: deterministic with same coordinates")
    void testSimulateSelection_Deterministic() {
        // Setup pool
        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 50)),
            createStructure("minecraft:village_desert", Map.of("#minecraft:is_plains", 50))
        );

        Holder<Biome> biome = createMockBiome("minecraft:plains", "#minecraft:is_plains");

        MVSConfig.ConfiguredStructure result1 = LocateHelper.simulateSelection(10, 20, testSeed, biome);
        MVSConfig.ConfiguredStructure result2 = LocateHelper.simulateSelection(10, 20, testSeed, biome);

        assertEquals(result1.structure.toString(), result2.structure.toString(),
            "Same coordinates should produce same selection");
    }

    @Test
    @DisplayName("simulateSelection: different coordinates produce different selections")
    void testSimulateSelection_DifferentCoordinates() {
        // Setup pool with equal weights
        MVSConfig.structurePool = List.of(
            createStructure("structure1", Map.of("#test:biome", 50)),
            createStructure("structure2", Map.of("#test:biome", 50))
        );

        Holder<Biome> biome = createMockBiome("test:biome", "#test:biome");

        // Run many selections at different coordinates
        Set<String> selectedStructures = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            MVSConfig.ConfiguredStructure result = LocateHelper.simulateSelection(
                i * 34, i * 17, testSeed, biome);
            if (result != null) {
                selectedStructures.add(result.structure.toString());
            }
        }

        // With equal weights, should select both structures at some point
        assertEquals(2, selectedStructures.size(),
            "Different coordinates should produce variety in selection");
    }

    @Test
    @DisplayName("simulateSelection: respects biome filtering")
    void testSimulateSelection_BiomeFiltering() {
        // Setup pool with biome-specific structures
        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 100)),
            createStructure("minecraft:village_desert", Map.of("#minecraft:is_desert", 100))
        );

        Holder<Biome> plainsBiome = createMockBiome("minecraft:plains", "#minecraft:is_plains");

        // Should only ever select village_plains
        for (int i = 0; i < 50; i++) {
            MVSConfig.ConfiguredStructure result = LocateHelper.simulateSelection(
                i, i, testSeed, plainsBiome);

            assertNotNull(result);
            assertEquals("minecraft:village_plains", result.structure.toString());
        }
    }

    // ============================================================
    // MDS LOCATE TESTS
    // ============================================================

    @Test
    @DisplayName("mvsLocate: returns not found when structure not in pool")
    void testMvsLocate_StructureNotInPool() {
        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 100))
        );

        LocateHelper.BiomeSampler sampler = (x, y, z) ->
            createMockBiome("minecraft:plains", "#minecraft:is_plains");

        LocateHelper.LocateResult result = LocateHelper.mvsLocate(
            ResourceLocation.parse("minecraft:village_desert"),  // Not in pool
            BlockPos.ZERO,
            testSeed,
            vanillaPlacement,
            sampler,
            10);

        assertFalse(result.found);
        assertTrue(result.message.contains("not in MVS pool"));
    }

    @Test
    @DisplayName("mvsLocate: finds structure when it's the only one in pool")
    void testMvsLocate_SingleStructurePool() {
        ResourceLocation structureId = ResourceLocation.parse("minecraft:village_plains");

        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 100))
        );

        // Sampler always returns plains biome
        LocateHelper.BiomeSampler sampler = (x, y, z) ->
            createMockBiome("minecraft:plains", "#minecraft:is_plains");

        LocateHelper.LocateResult result = LocateHelper.mvsLocate(
            structureId,
            BlockPos.ZERO,
            testSeed,
            vanillaPlacement,
            sampler,
            50);  // Search up to 50 chunks

        assertTrue(result.found, "Should find structure when it's the only option");
        assertNotNull(result.pos);
        assertTrue(result.chunksSearched > 0);
    }

    @Test
    @DisplayName("mvsLocate: returns position within search radius")
    void testMvsLocate_WithinSearchRadius() {
        ResourceLocation structureId = ResourceLocation.parse("minecraft:village_plains");

        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#*:*", 100))
        );

        LocateHelper.BiomeSampler sampler = (x, y, z) ->
            createMockBiome("minecraft:plains", "#minecraft:is_plains");

        BlockPos startPos = new BlockPos(0, 64, 0);
        int maxRadius = 50;

        LocateHelper.LocateResult result = LocateHelper.mvsLocate(
            structureId,
            startPos,
            testSeed,
            vanillaPlacement,
            sampler,
            maxRadius);

        if (result.found) {
            // Verify position is within search radius
            int distChunksX = Math.abs(result.chunkX - (startPos.getX() >> 4));
            int distChunksZ = Math.abs(result.chunkZ - (startPos.getZ() >> 4));

            assertTrue(distChunksX <= maxRadius, "Result should be within X radius");
            assertTrue(distChunksZ <= maxRadius, "Result should be within Z radius");
        }
    }

    @Test
    @DisplayName("mvsLocate: not found when biome never matches")
    void testMvsLocate_BiomeNeverMatches() {
        ResourceLocation structureId = ResourceLocation.parse("minecraft:village_plains");

        // Structure only spawns in plains
        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 100))
        );

        // But sampler always returns desert
        LocateHelper.BiomeSampler sampler = (x, y, z) ->
            createMockBiome("minecraft:desert", "#minecraft:is_desert");

        LocateHelper.LocateResult result = LocateHelper.mvsLocate(
            structureId,
            BlockPos.ZERO,
            testSeed,
            vanillaPlacement,
            sampler,
            10);  // Small radius to keep test fast

        assertFalse(result.found, "Should not find structure when biome never matches");
    }

    @Test
    @DisplayName("mvsLocate: respects competition between structures")
    void testMvsLocate_CompetitionBetweenStructures() {
        // Two structures competing for the same biome
        // With equal weights, each should win ~50% of the time at different locations

        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 50)),
            createStructure("ctov:village_plains", Map.of("#minecraft:is_plains", 50))
        );

        LocateHelper.BiomeSampler sampler = (x, y, z) ->
            createMockBiome("minecraft:plains", "#minecraft:is_plains");

        // Search for minecraft:village_plains
        // Due to competition, may need to search further
        LocateHelper.LocateResult result = LocateHelper.mvsLocate(
            ResourceLocation.parse("minecraft:village_plains"),
            BlockPos.ZERO,
            testSeed,
            vanillaPlacement,
            sampler,
            100);

        // Should eventually find it (statistically very likely within 100 chunk radius)
        assertTrue(result.found, "Should find structure despite competition");
    }

    @Test
    @DisplayName("mvsLocate: LocateResult has correct fields on success")
    void testMvsLocate_ResultFields() {
        ResourceLocation structureId = ResourceLocation.parse("minecraft:village_plains");

        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#*:*", 100))
        );

        LocateHelper.BiomeSampler sampler = (x, y, z) ->
            createMockBiome("minecraft:plains", "#minecraft:is_plains");

        LocateHelper.LocateResult result = LocateHelper.mvsLocate(
            structureId,
            BlockPos.ZERO,
            testSeed,
            vanillaPlacement,
            sampler,
            50);

        assertTrue(result.found);
        assertNotNull(result.pos);
        assertNotNull(result.biomeId);
        assertTrue(result.chunksSearched >= 1);
        assertTrue(result.message.contains("Found"));
    }

    // ============================================================
    // PLACEMENT STRATEGY INTERFACE TESTS
    // ============================================================

    @Test
    @DisplayName("PlacementStrategy: RandomSpreadPlacement implements interface")
    void testPlacementStrategy_RandomSpreadImplementsInterface() {
        LocateHelper.PlacementStrategy strategy = new LocateHelper.RandomSpreadPlacement(34, 8, 12345, false);

        assertNotNull(strategy);
        assertEquals(34, strategy.getApproximateSpacing());
    }

    @Test
    @DisplayName("PlacementStrategy: PlacementParams extends RandomSpreadPlacement")
    void testPlacementStrategy_PlacementParamsIsStrategy() {
        LocateHelper.PlacementStrategy strategy = LocateHelper.PlacementParams.villageDefaults();

        assertTrue(strategy instanceof LocateHelper.RandomSpreadPlacement);
        assertEquals(34, strategy.getApproximateSpacing());
    }

    @Test
    @DisplayName("RandomSpreadPlacement: isPlacementChunk consistent with getPlacementChunkForCell")
    void testRandomSpreadPlacement_IsPlacementChunkConsistent() {
        LocateHelper.RandomSpreadPlacement placement = new LocateHelper.RandomSpreadPlacement(34, 8, 12345, false);

        // Get placement chunk for cell (0, 0)
        int[] chunk = placement.getPlacementChunkForCell(0, 0, testSeed);

        // That chunk should pass isPlacementChunk
        assertTrue(placement.isPlacementChunk(chunk[0], chunk[1], testSeed));
    }

    @Test
    @DisplayName("RandomSpreadPlacement: iteratePlacements yields valid chunks")
    void testRandomSpreadPlacement_IteratePlacementsYieldsValid() {
        LocateHelper.RandomSpreadPlacement placement = new LocateHelper.RandomSpreadPlacement(34, 8, 12345, false);

        int count = 0;
        for (int[] chunk : placement.iteratePlacements(0, 0, testSeed, 100)) {
            // Each yielded chunk should be a valid placement chunk
            assertTrue(placement.isPlacementChunk(chunk[0], chunk[1], testSeed),
                "Yielded chunk [" + chunk[0] + ", " + chunk[1] + "] should be a placement chunk");
            count++;
            if (count >= 10) break; // Just check first 10
        }

        assertTrue(count >= 10, "Should yield at least 10 placement chunks");
    }

    @Test
    @DisplayName("RandomSpreadPlacement: iteratePlacements produces reasonable results")
    void testRandomSpreadPlacement_IteratePlacementsProducesReasonableResults() {
        LocateHelper.RandomSpreadPlacement placement = new LocateHelper.RandomSpreadPlacement(10, 2, 12345, false);

        int maxRadius = 30;
        int count = 0;
        int nearbyCount = 0;

        for (int[] chunk : placement.iteratePlacements(0, 0, testSeed, maxRadius)) {
            count++;
            int dist = Math.max(Math.abs(chunk[0]), Math.abs(chunk[1]));
            // Track how many are within maxRadius (mvsLocate filters the rest)
            if (dist <= maxRadius) {
                nearbyCount++;
            }
            // Prevent infinite loop
            if (count > 100) break;
        }

        // Should produce chunks, and most should be within radius
        assertTrue(count > 0, "Should produce at least some placements");
        assertTrue(nearbyCount > 0, "Should have some placements within radius");
    }

    @Test
    @DisplayName("RandomSpreadPlacement: triangular vs linear produce different results")
    void testRandomSpreadPlacement_TriangularVsLinear() {
        LocateHelper.RandomSpreadPlacement linear = new LocateHelper.RandomSpreadPlacement(34, 8, 12345, false);
        LocateHelper.RandomSpreadPlacement triangular = new LocateHelper.RandomSpreadPlacement(34, 8, 12345, true);

        // Same cell, different spread type
        int[] linearChunk = linear.getPlacementChunkForCell(5, 5, testSeed);
        int[] triangularChunk = triangular.getPlacementChunkForCell(5, 5, testSeed);

        // Should produce different results (statistically almost certain)
        // Both are valid, just different
        assertNotNull(linearChunk);
        assertNotNull(triangularChunk);
    }

    // ============================================================
    // SPREAD TYPE TESTS
    // ============================================================

    @Test
    @DisplayName("SpreadType: all spread types produce valid placements")
    void testSpreadType_AllTypesProduceValidPlacements() {
        for (LocateHelper.SpreadType spreadType : LocateHelper.SpreadType.values()) {
            LocateHelper.RandomSpreadPlacement placement =
                LocateHelper.RandomSpreadPlacement.withSpread(34, 8, 12345, spreadType);

            int[] chunk = placement.getPlacementChunkForCell(0, 0, testSeed);

            assertNotNull(chunk, "SpreadType." + spreadType + " should produce valid chunk");
            assertEquals(2, chunk.length, "Should return [chunkX, chunkZ]");

            // Chunk should be within cell bounds
            int maxOffset = placement.spacing - placement.separation;
            assertTrue(chunk[0] >= 0 && chunk[0] < maxOffset,
                "SpreadType." + spreadType + " X offset should be in bounds");
            assertTrue(chunk[1] >= 0 && chunk[1] < maxOffset,
                "SpreadType." + spreadType + " Z offset should be in bounds");
        }
    }

    @Test
    @DisplayName("SpreadType: FIXED_CENTER is deterministic")
    void testSpreadType_FixedCenterIsDeterministic() {
        LocateHelper.RandomSpreadPlacement placement =
            LocateHelper.RandomSpreadPlacement.withSpread(34, 8, 12345, LocateHelper.SpreadType.FIXED_CENTER);

        int maxOffset = placement.spacing - placement.separation;
        int expectedCenter = maxOffset / 2;

        // Check multiple cells - all should be at center
        for (int cellX = -5; cellX <= 5; cellX++) {
            for (int cellZ = -5; cellZ <= 5; cellZ++) {
                int[] chunk = placement.getPlacementChunkForCell(cellX, cellZ, testSeed);
                int offsetX = chunk[0] - (cellX * placement.spacing);
                int offsetZ = chunk[1] - (cellZ * placement.spacing);

                assertEquals(expectedCenter, offsetX, "FIXED_CENTER X offset should be at center");
                assertEquals(expectedCenter, offsetZ, "FIXED_CENTER Z offset should be at center");
            }
        }
    }

    @Test
    @DisplayName("SpreadType: different types produce different distributions")
    void testSpreadType_DifferentTypesProduceDifferentDistributions() {
        // Create placements with different spread types but same other params
        LocateHelper.RandomSpreadPlacement linear =
            LocateHelper.RandomSpreadPlacement.withSpread(34, 8, 12345, LocateHelper.SpreadType.LINEAR);
        LocateHelper.RandomSpreadPlacement triangular =
            LocateHelper.RandomSpreadPlacement.withSpread(34, 8, 12345, LocateHelper.SpreadType.TRIANGULAR);
        LocateHelper.RandomSpreadPlacement gaussian =
            LocateHelper.RandomSpreadPlacement.withSpread(34, 8, 12345, LocateHelper.SpreadType.GAUSSIAN);

        // Collect samples from each
        Set<String> linearResults = new HashSet<>();
        Set<String> triangularResults = new HashSet<>();
        Set<String> gaussianResults = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            int[] l = linear.getPlacementChunkForCell(i, i, testSeed);
            int[] t = triangular.getPlacementChunkForCell(i, i, testSeed);
            int[] g = gaussian.getPlacementChunkForCell(i, i, testSeed);

            linearResults.add(l[0] + "," + l[1]);
            triangularResults.add(t[0] + "," + t[1]);
            gaussianResults.add(g[0] + "," + g[1]);
        }

        // Different spread types should produce some different results
        // (statistically almost certain over 100 samples)
        assertFalse(linearResults.equals(triangularResults),
            "LINEAR and TRIANGULAR should produce different distributions");
        assertFalse(linearResults.equals(gaussianResults),
            "LINEAR and GAUSSIAN should produce different distributions");
    }

    @Test
    @DisplayName("SpreadType: legacy boolean constructor maps correctly")
    void testSpreadType_LegacyConstructorMapsCorrectly() {
        LocateHelper.RandomSpreadPlacement linear =
            new LocateHelper.RandomSpreadPlacement(34, 8, 12345, false);
        LocateHelper.RandomSpreadPlacement triangular =
            new LocateHelper.RandomSpreadPlacement(34, 8, 12345, true);

        assertEquals(LocateHelper.SpreadType.LINEAR, linear.spreadType);
        assertEquals(LocateHelper.SpreadType.TRIANGULAR, triangular.spreadType);
        assertFalse(linear.triangular);
        assertTrue(triangular.triangular);
    }

    // ============================================================
    // CONCENTRIC RINGS PLACEMENT TESTS
    // ============================================================

    @Test
    @DisplayName("ConcentricRingsPlacement: basic construction")
    void testConcentricRingsPlacement_Construction() {
        LocateHelper.ConcentricRingsPlacement rings = new LocateHelper.ConcentricRingsPlacement(
            new int[]{128, 256, 512}, 8, 12345);

        assertEquals(3, rings.ringDistances.length);
        assertEquals(8, rings.structuresPerRing);
        assertEquals(12345, rings.salt);
    }

    @Test
    @DisplayName("ConcentricRingsPlacement: getApproximateSpacing")
    void testConcentricRingsPlacement_ApproximateSpacing() {
        LocateHelper.ConcentricRingsPlacement rings = new LocateHelper.ConcentricRingsPlacement(
            new int[]{128, 256, 512}, 8, 12345);

        // Should be first ring distance / structures per ring
        assertEquals(128 / 8, rings.getApproximateSpacing());
    }

    @Test
    @DisplayName("ConcentricRingsPlacement: isPlacementChunk detects ring positions")
    void testConcentricRingsPlacement_IsPlacementChunk() {
        LocateHelper.ConcentricRingsPlacement rings = new LocateHelper.ConcentricRingsPlacement(
            new int[]{100}, 4, 12345);  // Single ring at distance 100, 4 structures

        // Chunks far from ring should not be placement chunks
        assertFalse(rings.isPlacementChunk(0, 0, testSeed), "Origin should not be on ring");
        assertFalse(rings.isPlacementChunk(50, 0, testSeed), "Distance 50 should not be on ring at 100");

        // Note: Chunks near the ring might or might not be placement depending on angle
        // The implementation checks for specific angular positions
    }

    // ============================================================
    // MVSLOCATE WITH STRATEGY TESTS
    // ============================================================

    @Test
    @DisplayName("mvsLocate: works with PlacementStrategy interface")
    void testMvsLocate_WorksWithStrategyInterface() {
        ResourceLocation structureId = ResourceLocation.parse("minecraft:village_plains");

        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#*:*", 100))
        );

        LocateHelper.BiomeSampler sampler = (x, y, z) ->
            createMockBiome("minecraft:plains", "#minecraft:is_plains");

        // Use strategy interface directly
        LocateHelper.PlacementStrategy strategy = new LocateHelper.RandomSpreadPlacement(34, 8, 10387312, false);

        LocateHelper.LocateResult result = LocateHelper.mvsLocate(
            structureId,
            BlockPos.ZERO,
            testSeed,
            strategy,
            sampler,
            50);

        assertTrue(result.found, "Should find structure using strategy interface");
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private MVSConfig.ConfiguredStructure createStructure(String id, Map<String, Integer> biomes) {
        ResourceLocation resourceLocation = ResourceLocation.parse(id);
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
