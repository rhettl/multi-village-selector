package com.rhett.multivillageselector;

import net.fabricmc.api.ModInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fabric platform-specific tests.
 * Verifies the Fabric entry point and platform integration.
 */
class FabricPlatformTest {

    @Test
    @DisplayName("Entry point: MultiVillageSelectorFabric implements ModInitializer")
    void testEntryPointImplementsModInitializer() {
        assertTrue(
            ModInitializer.class.isAssignableFrom(MultiVillageSelectorFabric.class),
            "MultiVillageSelectorFabric should implement ModInitializer"
        );
    }

    @Test
    @DisplayName("Entry point: MultiVillageSelectorFabric is instantiable")
    void testEntryPointInstantiable() {
        assertDoesNotThrow(() -> {
            MultiVillageSelectorFabric instance = new MultiVillageSelectorFabric();
            assertNotNull(instance);
        });
    }

    @Test
    @DisplayName("Entry point: onInitialize method exists")
    void testOnInitializeExists() {
        assertDoesNotThrow(() -> {
            MultiVillageSelectorFabric.class.getMethod("onInitialize");
        });
    }

    @Test
    @DisplayName("Common: MVSCommon.MOD_ID is correct")
    void testModIdConstant() {
        assertEquals("multivillageselector", MVSCommon.MOD_ID);
    }

    @Test
    @DisplayName("Common: MVSCommon class is accessible from Fabric")
    void testCommonAccessible() {
        assertNotNull(MVSCommon.class);
        // Verify key methods exist
        assertDoesNotThrow(() -> MVSCommon.class.getMethod("init"));
    }
}
