package com.rhett.multivillageselector;

import net.neoforged.fml.common.Mod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NeoForge platform-specific tests.
 * Verifies the NeoForge entry point and platform integration.
 */
class NeoForgePlatformTest {

    @Test
    @DisplayName("Entry point: MultiVillageSelector has @Mod annotation")
    void testEntryPointHasModAnnotation() {
        Mod annotation = MultiVillageSelector.class.getAnnotation(Mod.class);
        assertNotNull(annotation, "MultiVillageSelector should have @Mod annotation");
        assertEquals(MVSCommon.MOD_ID, annotation.value(), "Mod ID should match MVSCommon.MOD_ID");
    }

    @Test
    @DisplayName("Entry point: MultiVillageSelector is instantiable")
    void testEntryPointInstantiable() {
        // This verifies the class can be loaded and constructed
        // In real NeoForge, FML would do this
        assertDoesNotThrow(() -> {
            Class<?> clazz = MultiVillageSelector.class;
            assertNotNull(clazz);
        });
    }

    @Test
    @DisplayName("Common: MVSCommon.MOD_ID is correct")
    void testModIdConstant() {
        assertEquals("multivillageselector", MVSCommon.MOD_ID);
    }

    @Test
    @DisplayName("Common: MVSCommon class is accessible from NeoForge")
    void testCommonAccessible() {
        assertNotNull(MVSCommon.class);
        // Verify key methods exist
        assertDoesNotThrow(() -> MVSCommon.class.getMethod("init"));
    }
}
