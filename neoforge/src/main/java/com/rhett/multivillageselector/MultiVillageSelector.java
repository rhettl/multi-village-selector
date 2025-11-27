package com.rhett.multivillageselector;

import net.neoforged.fml.common.Mod;

/**
 * NeoForge entry point for Multi Village Selector.
 * All logic lives in common/ module (MVSCommon).
 */
@Mod(MVSCommon.MOD_ID)
public class MultiVillageSelector {
    public MultiVillageSelector() {
        MVSCommon.init();
    }
}
