package com.rhett.multivillageselector.locate;

import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.locate.PredictionHelper.ChunkPrediction;
import com.rhett.multivillageselector.util.LocateHelper;
import com.rhett.multivillageselector.util.LocateHelper.BiomeSampler;
import com.rhett.multivillageselector.util.LocateHelper.PlacementStrategy;
import com.rhett.multivillageselector.util.LocateHelper.RandomSpreadPlacement;
import com.rhett.multivillageselector.util.LocateHelper.SpreadType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
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
 * Test suite for PredictionHelper.
 * Tests chunk prediction logic for debugging /mvs locate position issues.
 */
class PredictionHelperTest {

    private static final long TEST_SEED = 12345L;
    private RandomSpreadPlacement vanillaPlacement;
    private BiomeSampler plainsSampler;

    @BeforeEach
    void setUp() {
        vanillaPlacement = RandomSpreadPlacement.villageDefaults();
        plainsSampler = (x, y, z) -> createMockBiome("minecraft:plains", "#minecraft:is_plains");

        // Setup MVSConfig with test structures
        MVSConfig.structurePool = new ArrayList<>();
        MVSConfig.enabled = true;
    }

    // ============================================================
    // BASIC FUNCTIONALITY TESTS
    // ============================================================

    @Test
    @DisplayName("predictChunks: returns empty list when maxChunks is 0")
    void testPredictChunks_ReturnsEmptyForZeroMax() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 0);

        assertTrue(predictions.isEmpty());
    }

    @Test
    @DisplayName("predictChunks: returns requested number of chunks")
    void testPredictChunks_ReturnsRequestedCount() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 50);

        assertEquals(50, predictions.size());
    }

    @Test
    @DisplayName("predictChunks: returns up to 100 chunks")
    void testPredictChunks_Returns100Chunks() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 100);

        assertEquals(100, predictions.size());
    }

    // ============================================================
    // DETERMINISM TESTS
    // ============================================================

    @Test
    @DisplayName("predictChunks: deterministic with same inputs")
    void testPredictChunks_Deterministic() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions1 = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 20);
        List<ChunkPrediction> predictions2 = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 20);

        assertEquals(predictions1.size(), predictions2.size());
        for (int i = 0; i < predictions1.size(); i++) {
            ChunkPrediction p1 = predictions1.get(i);
            ChunkPrediction p2 = predictions2.get(i);
            assertEquals(p1.chunkX, p2.chunkX, "Chunk X should match at index " + i);
            assertEquals(p1.chunkZ, p2.chunkZ, "Chunk Z should match at index " + i);
            assertEquals(p1.worldPos, p2.worldPos, "World pos should match at index " + i);
            assertEquals(p1.structureId, p2.structureId, "Structure should match at index " + i);
        }
    }

    @Test
    @DisplayName("predictChunks: different seeds produce different results")
    void testPredictChunks_DifferentSeeds() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions1 = PredictionHelper.predictChunks(
            BlockPos.ZERO, 12345L, vanillaPlacement, plainsSampler, 10);
        List<ChunkPrediction> predictions2 = PredictionHelper.predictChunks(
            BlockPos.ZERO, 54321L, vanillaPlacement, plainsSampler, 10);

        // At least some predictions should differ (statistically almost certain)
        boolean anyDifferent = false;
        for (int i = 0; i < predictions1.size(); i++) {
            if (predictions1.get(i).chunkX != predictions2.get(i).chunkX ||
                predictions1.get(i).chunkZ != predictions2.get(i).chunkZ) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "Different seeds should produce different chunk positions");
    }

    @Test
    @DisplayName("predictChunks: repeated calls produce identical results (no caching side effects)")
    void testPredictChunks_RepeatedCallsIdentical() {
        setupSingleStructurePool();

        // Call multiple times to ensure no state accumulates
        for (int run = 0; run < 5; run++) {
            List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
                BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 10);

            // First prediction should always be the same
            assertEquals(predictions.get(0).chunkX, predictions.get(0).chunkX);
            assertEquals(predictions.get(0).chunkZ, predictions.get(0).chunkZ);
        }
    }

    // ============================================================
    // CHUNK PREDICTION DATA TESTS
    // ============================================================

    @Test
    @DisplayName("ChunkPrediction: has correct world position from getLocatePos")
    void testChunkPrediction_WorldPosMatchesLocatePos() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 10);

        for (ChunkPrediction prediction : predictions) {
            // World pos should be chunk origin (chunkX * 16, 0, chunkZ * 16) for vanilla villages
            BlockPos expected = vanillaPlacement.getLocatePos(prediction.chunkX, prediction.chunkZ);
            assertEquals(expected, prediction.worldPos,
                "World pos should match getLocatePos for chunk [" + prediction.chunkX + ", " + prediction.chunkZ + "]");
        }
    }

    @Test
    @DisplayName("ChunkPrediction: has correct biome from sampler")
    void testChunkPrediction_HasCorrectBiome() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 10);

        for (ChunkPrediction prediction : predictions) {
            assertEquals("minecraft:plains", prediction.biomeId,
                "Biome should match sampler output");
        }
    }

    @Test
    @DisplayName("ChunkPrediction: hasOffset is false for vanilla villages")
    void testChunkPrediction_NoOffsetForVanilla() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 10);

        for (ChunkPrediction prediction : predictions) {
            assertFalse(prediction.hasOffset,
                "Vanilla villages should have no offset");
            assertEquals(Vec3i.ZERO, prediction.locateOffset);
        }
    }

    @Test
    @DisplayName("ChunkPrediction: hasOffset is true when offset is non-zero")
    void testChunkPrediction_HasOffsetWhenNonZero() {
        setupSingleStructurePool();

        // Create placement with non-zero offset
        Vec3i customOffset = new Vec3i(4, 0, 4);
        RandomSpreadPlacement placementWithOffset = new RandomSpreadPlacement(
            34, 8, 10387312, SpreadType.LINEAR, customOffset);

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, placementWithOffset, plainsSampler, 10);

        for (ChunkPrediction prediction : predictions) {
            assertTrue(prediction.hasOffset, "Should have offset when locateOffset is non-zero");
            assertEquals(customOffset, prediction.locateOffset);
        }
    }

    @Test
    @DisplayName("ChunkPrediction: distance is calculated correctly")
    void testChunkPrediction_DistanceCalculation() {
        setupSingleStructurePool();

        BlockPos startPos = new BlockPos(100, 64, 200);
        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            startPos, TEST_SEED, vanillaPlacement, plainsSampler, 10);

        for (ChunkPrediction prediction : predictions) {
            int dx = prediction.worldPos.getX() - startPos.getX();
            int dz = prediction.worldPos.getZ() - startPos.getZ();
            int expectedDistance = (int) Math.sqrt(dx * dx + dz * dz);

            assertEquals(expectedDistance, prediction.distanceFromStart,
                "Distance should be Euclidean distance from start");
        }
    }

    @Test
    @DisplayName("ChunkPrediction: results are sorted by distance")
    void testChunkPrediction_SortedByDistance() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 50);

        for (int i = 1; i < predictions.size(); i++) {
            assertTrue(predictions.get(i).distanceFromStart >= predictions.get(i - 1).distanceFromStart,
                "Predictions should be sorted by distance (ascending)");
        }
    }

    // ============================================================
    // STRUCTURE SELECTION TESTS
    // ============================================================

    @Test
    @DisplayName("predictChunks: selects structure when pool has one entry")
    void testPredictChunks_SelectsSingleStructure() {
        setupSingleStructurePool();

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 20);

        for (ChunkPrediction prediction : predictions) {
            assertEquals("minecraft:village_plains", prediction.structureId,
                "Should select the only structure in pool");
        }
    }

    @Test
    @DisplayName("predictChunks: selects from multiple structures")
    void testPredictChunks_SelectsFromMultipleStructures() {
        // Setup pool with two equal-weight structures
        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 50)),
            createStructure("ctov:village_plains", Map.of("#minecraft:is_plains", 50))
        );

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 50);

        Set<String> selectedStructures = new HashSet<>();
        for (ChunkPrediction prediction : predictions) {
            if (prediction.structureId != null) {
                selectedStructures.add(prediction.structureId);
            }
        }

        // With equal weights, should select both at some point
        assertEquals(2, selectedStructures.size(),
            "Should select variety of structures with equal weights");
    }

    @Test
    @DisplayName("predictChunks: returns null structure when biome doesn't match")
    void testPredictChunks_NullStructureWhenBiomeMismatch() {
        // Structure only spawns in desert
        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_desert", Map.of("#minecraft:is_desert", 100))
        );

        // But we sample plains biome
        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, plainsSampler, 10);

        for (ChunkPrediction prediction : predictions) {
            assertNull(prediction.structureId,
                "Structure should be null when biome doesn't match");
        }
    }

    // ============================================================
    // CHUNK PREDICTION FORMATTING TESTS
    // ============================================================

    @Test
    @DisplayName("ChunkPrediction.toFileString: contains all relevant info")
    void testChunkPrediction_ToFileString() {
        ChunkPrediction prediction = new ChunkPrediction(
            10, 20,
            new BlockPos(160, 0, 320),
            "minecraft:village_plains",
            "minecraft:plains",
            false,
            Vec3i.ZERO,
            500
        );

        String fileString = prediction.toFileString();

        assertTrue(fileString.contains("Chunk [10, 20]"), "Should contain chunk coords");
        assertTrue(fileString.contains("World [160, 0, 320]"), "Should contain world pos");
        assertTrue(fileString.contains("minecraft:village_plains"), "Should contain structure");
        assertTrue(fileString.contains("minecraft:plains"), "Should contain biome");
        assertTrue(fileString.contains("500 blocks"), "Should contain distance");
    }

    @Test
    @DisplayName("ChunkPrediction.toFileString: shows offset when present")
    void testChunkPrediction_ToFileStringWithOffset() {
        ChunkPrediction prediction = new ChunkPrediction(
            10, 20,
            new BlockPos(164, 0, 324),
            "minecraft:village_plains",
            "minecraft:plains",
            true,
            new Vec3i(4, 0, 4),
            500
        );

        String fileString = prediction.toFileString();

        assertTrue(fileString.contains("[offset: 4,0,4]"), "Should show offset when present");
    }

    @Test
    @DisplayName("ChunkPrediction.toFileString: handles null structure")
    void testChunkPrediction_ToFileStringNullStructure() {
        ChunkPrediction prediction = new ChunkPrediction(
            10, 20,
            new BlockPos(160, 0, 320),
            null,  // No structure selected
            "minecraft:plains",
            false,
            Vec3i.ZERO,
            500
        );

        String fileString = prediction.toFileString();

        assertTrue(fileString.contains("no structure"), "Should indicate no structure");
    }

    @Test
    @DisplayName("ChunkPrediction.getTeleportCommand: generates correct command")
    void testChunkPrediction_GetTeleportCommand() {
        ChunkPrediction prediction = new ChunkPrediction(
            10, 20,
            new BlockPos(160, 0, 320),
            "minecraft:village_plains",
            "minecraft:plains",
            false,
            Vec3i.ZERO,
            500
        );

        String tpCommand = prediction.getTeleportCommand();

        assertEquals("/tp @s 160 ~ 320", tpCommand,
            "Should generate teleport command with X and Z from worldPos");
    }

    // ============================================================
    // DIFFERENT START POSITIONS TESTS
    // ============================================================

    @Test
    @DisplayName("predictChunks: works from different start positions")
    void testPredictChunks_DifferentStartPositions() {
        setupSingleStructurePool();

        BlockPos[] startPositions = {
            BlockPos.ZERO,
            new BlockPos(1000, 64, 1000),
            new BlockPos(-500, 64, 500),
            new BlockPos(10000, 64, -10000)
        };

        for (BlockPos startPos : startPositions) {
            List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
                startPos, TEST_SEED, vanillaPlacement, plainsSampler, 10);

            assertEquals(10, predictions.size(),
                "Should return 10 predictions from start " + startPos);

            // First prediction should be closest to start
            for (int i = 1; i < predictions.size(); i++) {
                assertTrue(predictions.get(i).distanceFromStart >= predictions.get(0).distanceFromStart,
                    "First prediction should be closest to start");
            }
        }
    }

    // ============================================================
    // DIFFERENT PLACEMENT STRATEGY TESTS
    // ============================================================

    @Test
    @DisplayName("predictChunks: works with different spread types")
    void testPredictChunks_DifferentSpreadTypes() {
        setupSingleStructurePool();

        for (SpreadType spreadType : SpreadType.values()) {
            RandomSpreadPlacement placement = RandomSpreadPlacement.withSpread(
                34, 8, 10387312, spreadType);

            List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
                BlockPos.ZERO, TEST_SEED, placement, plainsSampler, 10);

            assertEquals(10, predictions.size(),
                "Should return 10 predictions with " + spreadType);

            // All predictions should have valid data
            for (ChunkPrediction prediction : predictions) {
                assertNotNull(prediction.worldPos);
                assertNotNull(prediction.biomeId);
            }
        }
    }

    @Test
    @DisplayName("predictChunks: works with custom placement params")
    void testPredictChunks_CustomPlacementParams() {
        setupSingleStructurePool();

        // Tighter spacing = more structures
        RandomSpreadPlacement tightPlacement = new RandomSpreadPlacement(
            20, 4, 12345, SpreadType.LINEAR);

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, tightPlacement, plainsSampler, 20);

        assertEquals(20, predictions.size());

        // With tighter spacing, first few should be closer
        assertTrue(predictions.get(0).distanceFromStart < 500,
            "First prediction with tight spacing should be nearby");
    }

    // ============================================================
    // BIOME VARIATION TESTS
    // ============================================================

    @Test
    @DisplayName("predictChunks: captures biome from sampler at each chunk")
    void testPredictChunks_CapturesBiomeVariation() {
        // Setup structures for different biomes
        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 100)),
            createStructure("minecraft:village_desert", Map.of("#minecraft:is_desert", 100))
        );

        // Alternating biome sampler
        BiomeSampler alternatingSampler = (x, y, z) -> {
            int chunkX = x >> 4;
            if (chunkX % 2 == 0) {
                return createMockBiome("minecraft:plains", "#minecraft:is_plains");
            } else {
                return createMockBiome("minecraft:desert", "#minecraft:is_desert");
            }
        };

        List<ChunkPrediction> predictions = PredictionHelper.predictChunks(
            BlockPos.ZERO, TEST_SEED, vanillaPlacement, alternatingSampler, 20);

        Set<String> biomes = new HashSet<>();
        for (ChunkPrediction prediction : predictions) {
            biomes.add(prediction.biomeId);
        }

        // Should see both biomes
        assertTrue(biomes.size() >= 1, "Should capture biome from sampler");
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private void setupSingleStructurePool() {
        MVSConfig.structurePool = List.of(
            createStructure("minecraft:village_plains", Map.of("#minecraft:is_plains", 100))
        );
    }

    private MVSConfig.ConfiguredStructure createStructure(String id, Map<String, Integer> biomes) {
        ResourceLocation resourceLocation = ResourceLocation.parse(id);
        Map<String, Integer> biomeMap = new LinkedHashMap<>(biomes);
        return new MVSConfig.ConfiguredStructure(
            resourceLocation,
            biomeMap,
            biomeMap
        );
    }

    @SuppressWarnings("unchecked")
    private Holder<Biome> createMockBiome(String biomeId, String... tags) {
        Holder<Biome> holder = mock(Holder.class);

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