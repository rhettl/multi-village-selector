package com.rhett.multivillageselector;

import com.rhett.multivillageselector.yacl.YACLHelper;
import com.rhett.multivillageselector.yacl.MVSConfigScreen;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge entry point for Multi Village Selector.
 * All logic lives in common/ module (MVSCommon).
 */
@Mod(MVSCommon.MOD_ID)
public class MultiVillageSelector {
    public MultiVillageSelector() {
        MVSCommon.init();

        // Register config screen factory if YACL is present
        if (YACLHelper.isYACLAvailable()) {
            ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (client, parent) -> {
                    try {
                        return MVSConfigScreen.createScreen(parent);
                    } catch (Exception e) {
                        MVSCommon.LOGGER.error("MVS: Failed to create config screen", e);
                        return null;
                    }
                }
            );
            MVSCommon.LOGGER.info("MVS: Config screen registered (YACL present)");
        } else {
            MVSCommon.LOGGER.info("MVS: Config screen not registered (YACL not present)");
        }
    }
}
