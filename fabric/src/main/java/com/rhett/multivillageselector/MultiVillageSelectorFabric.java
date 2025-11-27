package com.rhett.multivillageselector;

import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point for Multi Village Selector.
 * All logic lives in common/ module (MVSCommon).
 */
public class MultiVillageSelectorFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MVSCommon.init();
    }
}
