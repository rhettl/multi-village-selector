package com.rhett.multivillageselector;

import com.rhett.multivillageselector.config.MVSConfig;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v0.3.0: Multi-platform entry point using Architectury API
 * Event registration moved to ModEvents.java for cross-platform compatibility
 */
@Mod("multivillageselector")
public class MultiVillageSelector {
    public static final String MOD_ID = "multivillageselector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MultiVillageSelector() {
        // Initialize mod (platform-agnostic)
        init();
    }

    /**
     * Platform-agnostic initialization
     * Called from both NeoForge and (future) Fabric entrypoints
     */
    public static void init() {
        // Load configuration
        MVSConfig.load();

        // Register events (Architectury cross-platform)
        ModEvents.register();

        LOGGER.info("Multi Village Selector (MVS) v0.3.0 initialized");
    }
}
