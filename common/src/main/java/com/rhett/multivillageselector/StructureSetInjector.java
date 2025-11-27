package com.rhett.multivillageselector;

import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Injects a minimal empty entry into minecraft:villages structure_set if it's missing vanilla structures.
 * This ensures the structure_set is always called, allowing MVS to intercept and provide variety.
 */
public class StructureSetInjector {

    private static final ResourceLocation VILLAGES_SET = ResourceLocation.fromNamespaceAndPath("minecraft", "villages");

    /**
     * Called via Architectury LifecycleEvent.SERVER_BEFORE_START
     * No @SubscribeEvent annotation needed - registered in ModEvents.java
     */
    public static void onServerAboutToStart(MinecraftServer server) {
        if (!MVSConfig.enabled) {
            return;
        }

        try {
            Registry<StructureSet> structureSetRegistry = server
                .registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET);

            // Check if minecraft:villages exists
            StructureSet villagesSet = structureSetRegistry.get(VILLAGES_SET);
            if (villagesSet == null) {
                MVSCommon.LOGGER.warn("[MVS] minecraft:villages structure_set not found - cannot inject empty entry");
                return;
            }

            // Check if the structure_set is completely empty
            // Only inject if there are NO structures at all
            int structureCount = villagesSet.structures().size();

            if (structureCount == 0) {
                MVSCommon.LOGGER.warn("===========================================");
                MVSCommon.LOGGER.warn("[MVS] DETECTED: minecraft:villages structure_set is EMPTY!");
                MVSCommon.LOGGER.warn("[MVS] This can happen when mods replace structure_sets then get disabled via config.");
                MVSCommon.LOGGER.warn("[MVS] Empty structure_sets won't be called during world gen.");
                MVSCommon.LOGGER.warn("[MVS] Current structures in set: {}", structureCount);

                // Log what's in the set
                for (var entry : villagesSet.structures()) {
                    var structureHolder = entry.structure();
                    var keyOpt = structureHolder.unwrapKey();
                    if (keyOpt.isPresent()) {
                        MVSCommon.LOGGER.warn("[MVS]   - {} (weight: {})",
                            keyOpt.get().location(), entry.weight());
                    } else {
                        MVSCommon.LOGGER.warn("[MVS]   - (empty) (weight: {})", entry.weight());
                    }
                }

                // Inject vanilla structure to force structure_set to be called
                MVSCommon.LOGGER.warn("[MVS] Attempting to inject vanilla village into structure_set...");

                try {
                    // Get vanilla village_plains structure
                    Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
                        server.registryAccess().registryOrThrow(Registries.STRUCTURE);

                    ResourceLocation vanillaVillage = ResourceLocation.fromNamespaceAndPath("minecraft", "village_plains");
                    var vanillaStructure = structureRegistry.getHolder(
                        net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, vanillaVillage)
                    );

                    if (vanillaStructure.isEmpty()) {
                        MVSCommon.LOGGER.error("[MVS] ❌ Could not find minecraft:village_plains structure!");
                        MVSCommon.LOGGER.warn("===========================================");
                        return;
                    }

                    // Create a new structures list with existing + vanilla entry
                    var existingStructures = villagesSet.structures();
                    var newStructures = new java.util.ArrayList<>(existingStructures);

                    // Add vanilla village with weight 1 (minimal but forces the set to be called)
                    var vanillaEntry = new StructureSet.StructureSelectionEntry(
                        vanillaStructure.get(),
                        1 // Minimal weight - our mixin will replace it anyway
                    );
                    newStructures.add(vanillaEntry);

                    // Create new StructureSet with modified structures list
                    var newVillagesSet = new StructureSet(
                        newStructures,
                        villagesSet.placement()
                    );

                    // Replace the structure_set in the registry using reflection
                    try {
                        // Unfreeze the registry temporarily
                        var registryClass = structureSetRegistry.getClass();
                        var frozenField = registryClass.getDeclaredField("frozen");
                        frozenField.setAccessible(true);
                        frozenField.setBoolean(structureSetRegistry, false);

                        // Register the modified structure_set
                        Registry.register(structureSetRegistry, VILLAGES_SET, newVillagesSet);

                        // Re-freeze the registry
                        frozenField.setBoolean(structureSetRegistry, true);

                        MVSCommon.LOGGER.warn("[MVS] ✅ Successfully injected minecraft:village_plains (weight: 1)");
                        MVSCommon.LOGGER.warn("[MVS] minecraft:villages structure_set will now be called during world gen");
                        MVSCommon.LOGGER.warn("[MVS] MVS mixin will intercept village_plains attempts and provide full variety");
                        MVSCommon.LOGGER.warn("[MVS] This fixes the corner-case where structure_sets lose vanilla entries");
                        MVSCommon.LOGGER.warn("===========================================");
                    } catch (Exception registryEx) {
                        MVSCommon.LOGGER.error("[MVS] ❌ Failed to modify registry: " + registryEx.getMessage(), registryEx);
                        MVSCommon.LOGGER.warn("===========================================");
                    }

                } catch (Exception e) {
                    MVSCommon.LOGGER.error("[MVS] ❌ Failed to inject vanilla village: " + e.getMessage(), e);
                    MVSCommon.LOGGER.warn("===========================================");
                }
            } else {
                MVSCommon.LOGGER.info("[MVS] minecraft:villages structure_set has {} structures - OK", structureCount);
            }

        } catch (Exception e) {
            MVSCommon.LOGGER.error("[MVS] Error checking structure_set", e);
        }
    }
}
