package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for BiomeRuleResolver - precedence and structure lookup logic.
 */
class BiomeRuleResolverTest {

    private List<MVSConfig.ConfiguredStructure> originalPool;
    private RegistryAccess mockRegistryAccess;
    @SuppressWarnings("unchecked")
    private Registry<Structure> mockStructureRegistry;

    @BeforeEach
    void setUp() {
        // Save original pool
        originalPool = new ArrayList<>(MVSConfig.structurePool);

        // Clear pool for tests
        MVSConfig.structurePool.clear();

        // Setup mocks
        mockRegistryAccess = mock(RegistryAccess.class);
        mockStructureRegistry = mock(Registry.class);
        when(mockRegistryAccess.registryOrThrow(Registries.STRUCTURE)).thenReturn(mockStructureRegistry);
    }

    @AfterEach
    void tearDown() {
        // Restore original pool
        MVSConfig.structurePool.clear();
        MVSConfig.structurePool.addAll(originalPool);
    }

    // ============================================================
    // PRECEDENCE TESTS
    // ============================================================

    @Test
    @DisplayName("Precedence: MVS config takes priority over vanilla registry")
    void testPrecedence_MVSConfigWins() {
        // Setup MVS config
        Map<String, Integer> mvsWeights = new LinkedHashMap<>();
        mvsWeights.put("#mvs:custom_tag", 10);

        MVSConfig.ConfiguredStructure mvsStructure = createMVSStructure(
            "minecraft:village_plains",
            mvsWeights
        );
        MVSConfig.structurePool.add(mvsStructure);

        // Note: We don't mock Structure here - just verify registry is never queried
        // when MVS config exists (MVS config takes precedence)

        // Execute
        BiomeRules rules = BiomeRuleResolver.getEffectiveRules(
            "minecraft:village_plains",
            mockRegistryAccess
        );

        // Verify - MVS config used, NOT vanilla
        assertEquals(BiomeRules.Source.MVS_CONFIG, rules.source);
        assertEquals(1, rules.tags.size());
        assertEquals("#mvs:custom_tag", rules.tags.get(0));
        assertEquals(10, rules.weights.get("#mvs:custom_tag"));

        // Vanilla registry should NOT be queried
        verify(mockStructureRegistry, never()).get(any(ResourceLocation.class));
    }

    @Test
    @DisplayName("Precedence: Vanilla registry queried when MVS config absent")
    void testPrecedence_VanillaFallback() {
        // No MVS config
        MVSConfig.structurePool.clear();

        // Note: We can't mock Structure.biomes() in Architectury common module
        // because Mockito cannot mock sealed Minecraft classes.
        // Instead, we verify the registry IS queried when MVS config is absent.
        // Registry returns null -> empty rules (structure not found path)
        when(mockStructureRegistry.get(ResourceLocation.parse("minecraft:village_plains")))
            .thenReturn(null);

        // Execute
        BiomeRules rules = BiomeRuleResolver.getEffectiveRules(
            "minecraft:village_plains",
            mockRegistryAccess
        );

        // Verify - vanilla registry WAS queried (unlike testPrecedence_MVSConfigWins)
        verify(mockStructureRegistry).get(ResourceLocation.parse("minecraft:village_plains"));

        // With null structure, we get empty rules
        // This verifies the fallback path is taken, even though we can't test
        // the full Structure.biomes() parsing in unit tests
        assertEquals(BiomeRules.Source.EMPTY, rules.source);
        assertTrue(rules.isEmpty());
    }

    @Test
    @DisplayName("Precedence: Empty rules when structure not found")
    void testPrecedence_NotFound() {
        // No MVS config
        MVSConfig.structurePool.clear();

        // Vanilla registry returns null
        when(mockStructureRegistry.get(any(ResourceLocation.class))).thenReturn(null);

        // Execute
        BiomeRules rules = BiomeRuleResolver.getEffectiveRules(
            "unknown:structure",
            mockRegistryAccess
        );

        // Verify - empty rules
        assertEquals(BiomeRules.Source.EMPTY, rules.source);
        assertTrue(rules.tags.isEmpty());
        assertTrue(rules.directBiomes.isEmpty());
        assertTrue(rules.weights.isEmpty());
        assertTrue(rules.isEmpty());
    }

    // ============================================================
    // MVS CONTROLLED TESTS
    // ============================================================

    @Test
    @DisplayName("isMVSControlled: returns true when structure in pool")
    void testIsMVSControlled_True() {
        // Setup MVS config
        MVSConfig.ConfiguredStructure structure = createMVSStructure(
            "minecraft:village_plains",
            Map.of("#minecraft:is_plains", 10)
        );
        MVSConfig.structurePool.add(structure);

        // Execute & Verify
        assertTrue(BiomeRuleResolver.isMVSControlled("minecraft:village_plains"));
    }

    @Test
    @DisplayName("isMVSControlled: returns false when structure not in pool")
    void testIsMVSControlled_False() {
        // Empty pool
        MVSConfig.structurePool.clear();

        // Execute & Verify
        assertFalse(BiomeRuleResolver.isMVSControlled("minecraft:village_plains"));
    }

    @Test
    @DisplayName("isMVSControlled: returns false for null structure ID")
    void testIsMVSControlled_NullStructure() {
        // Setup - structure with null ResourceLocation
        MVSConfig.ConfiguredStructure structure = new MVSConfig.ConfiguredStructure(
            null, // null structure
            Map.of("#test:tag", 10),
            Map.of("#test:tag", 10)
        );
        MVSConfig.structurePool.add(structure);

        // Execute & Verify - should not crash, should return false
        assertFalse(BiomeRuleResolver.isMVSControlled("minecraft:village_plains"));
    }

    // ============================================================
    // REGISTRY ERROR HANDLING
    // ============================================================

    @Test
    @DisplayName("Error handling: invalid structure ID")
    void testErrorHandling_InvalidStructureId() {
        // No MVS config
        MVSConfig.structurePool.clear();

        // Registry throws on invalid ID
        when(mockStructureRegistry.get(any(ResourceLocation.class)))
            .thenThrow(new IllegalArgumentException("Invalid resource location"));

        // Execute - should not crash
        BiomeRules rules = BiomeRuleResolver.getEffectiveRules(
            "invalid::#@!structure",
            mockRegistryAccess
        );

        // Verify - returns empty rules
        assertEquals(BiomeRules.Source.EMPTY, rules.source);
        assertTrue(rules.isEmpty());
    }

    @Test
    @DisplayName("Error handling: registry access throws")
    void testErrorHandling_RegistryThrows() {
        // No MVS config
        MVSConfig.structurePool.clear();

        // Registry access throws
        when(mockRegistryAccess.registryOrThrow(Registries.STRUCTURE))
            .thenThrow(new IllegalStateException("Registry not available"));

        // Execute - should not crash
        BiomeRules rules = BiomeRuleResolver.getEffectiveRules(
            "minecraft:village_plains",
            mockRegistryAccess
        );

        // Verify - returns empty rules
        assertEquals(BiomeRules.Source.EMPTY, rules.source);
        assertTrue(rules.isEmpty());
    }

    // ============================================================
    // MULTIPLE STRUCTURES IN POOL
    // ============================================================

    @Test
    @DisplayName("Pool lookup: finds correct structure among multiple")
    void testPoolLookup_MultipleStructures() {
        // Setup multiple structures
        MVSConfig.structurePool.add(createMVSStructure(
            "minecraft:village_plains",
            Map.of("#minecraft:is_plains", 10)
        ));
        MVSConfig.structurePool.add(createMVSStructure(
            "minecraft:village_desert",
            Map.of("#minecraft:is_desert", 8)
        ));
        MVSConfig.structurePool.add(createMVSStructure(
            "minecraft:village_taiga",
            Map.of("#minecraft:is_taiga", 12)
        ));

        // Execute - lookup middle structure
        BiomeRules rules = BiomeRuleResolver.getEffectiveRules(
            "minecraft:village_desert",
            mockRegistryAccess
        );

        // Verify - found correct structure
        assertEquals(BiomeRules.Source.MVS_CONFIG, rules.source);
        assertEquals(1, rules.tags.size());
        assertEquals("#minecraft:is_desert", rules.tags.get(0));
        assertEquals(8, rules.weights.get("#minecraft:is_desert"));
    }

    @Test
    @DisplayName("Pool lookup: returns first match if duplicates exist")
    void testPoolLookup_Duplicates() {
        // Setup duplicate structures (same ID, different weights)
        MVSConfig.structurePool.add(createMVSStructure(
            "minecraft:village_plains",
            Map.of("#minecraft:is_plains", 10)
        ));
        MVSConfig.structurePool.add(createMVSStructure(
            "minecraft:village_plains",
            Map.of("#minecraft:is_plains", 99) // Different weight
        ));

        // Execute
        BiomeRules rules = BiomeRuleResolver.getEffectiveRules(
            "minecraft:village_plains",
            mockRegistryAccess
        );

        // Verify - first match used
        assertEquals(BiomeRules.Source.MVS_CONFIG, rules.source);
        assertEquals(10, rules.weights.get("#minecraft:is_plains")); // First one (10, not 99)
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private MVSConfig.ConfiguredStructure createMVSStructure(String id, Map<String, Integer> weights) {
        ResourceLocation location = ResourceLocation.parse(id);
        return new MVSConfig.ConfiguredStructure(
            location,
            new LinkedHashMap<>(weights),
            new LinkedHashMap<>(weights)
        );
    }
}
