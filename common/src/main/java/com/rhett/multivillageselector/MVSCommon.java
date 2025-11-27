package com.rhett.multivillageselector;

import com.rhett.multivillageselector.config.MVSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point for Multi Village Selector.
 * Platform-agnostic initialization called from NeoForge/Fabric entry points.
 */
public class MVSCommon {
    public static final String MOD_ID = "multivillageselector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Platform-agnostic initialization.
     * Called from both NeoForge and Fabric entrypoints.
     */
    public static void init() {
        // Load configuration
        MVSConfig.load();

        // Register events (Architectury cross-platform)
        ModEvents.register();

        LOGGER.info("Multi Village Selector (MVS) v0.3.0 initialized");
    }
}
