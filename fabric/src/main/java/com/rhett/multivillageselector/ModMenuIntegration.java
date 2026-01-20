package com.rhett.multivillageselector;

import com.rhett.multivillageselector.yacl.YACLHelper;
import com.rhett.multivillageselector.yacl.MVSConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration for Fabric.
 * Provides the config screen button in Mod Menu (if both Mod Menu and YACL are present).
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Only register config screen if YACL is present
        if (YACLHelper.isYACLAvailable()) {
            return parent -> {
                try {
                    return MVSConfigScreen.createScreen(parent);
                } catch (Exception e) {
                    MVSCommon.LOGGER.error("MVS: Failed to create config screen", e);
                    return null;
                }
            };
        }

        // YACL not present - no config screen available
        return parent -> null;
    }
}
