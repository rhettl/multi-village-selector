package com.rhett.multivillageselector.yacl;

import com.rhett.multivillageselector.MVSCommon;

/**
 * Helper class to detect and handle YACL presence.
 * Uses reflection to avoid hard dependencies on YACL.
 */
public class YACLHelper {

    private static Boolean yaclAvailable = null;

    /**
     * Checks if YACL is present in the classpath.
     * Result is cached after first check.
     *
     * @return true if YACL is available, false otherwise
     */
    public static boolean isYACLAvailable() {
        if (yaclAvailable == null) {
            try {
                // Try to load YACL's main config builder class
                Class.forName("dev.isxander.yacl3.api.YetAnotherConfigLib");
                yaclAvailable = true;
                MVSCommon.LOGGER.info("MVS: YACL detected! Config GUI will be available.");
            } catch (ClassNotFoundException e) {
                yaclAvailable = false;
                MVSCommon.LOGGER.info("MVS: YACL not found. Config GUI disabled (manual JSON5 editing only).");
            }
        }
        return yaclAvailable;
    }

    /**
     * Resets the cached availability check.
     * Useful for testing.
     */
    public static void resetCache() {
        yaclAvailable = null;
    }
}
