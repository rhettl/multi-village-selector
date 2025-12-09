package com.rhett.multivillageselector.util;

import net.minecraft.core.RegistryAccess;

/**
 * Duck interface for accessing MVS-injected fields on Structure instances.
 * StructureMixin implements this interface, making these methods available
 * on all Structure instances at runtime.
 *
 * Usage: ((MVSStructureAccessor) structure).mvs$setStructureId(id, registryAccess);
 */
public interface MVSStructureAccessor {

    /**
     * Set the structure ID on this instance.
     * Called from ModEvents during registry iteration.
     */
    void mvs$setStructureId(String id, RegistryAccess registryAccess);

    /**
     * Get the structure ID (for debugging/testing).
     */
    String mvs$getStructureId();

    /**
     * Clear cached biome data. Call when config reloads.
     */
    void mvs$clearCache();
}
