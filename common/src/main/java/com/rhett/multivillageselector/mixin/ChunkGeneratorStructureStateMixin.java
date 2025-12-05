package com.rhett.multivillageselector.mixin;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.util.LocateHelper;
import com.rhett.multivillageselector.util.PlacementResolver;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for ChunkGeneratorStructureState to make hasStructureChunkInRange() MVS-aware.
 *
 * Problem: When structures like pillager_outposts check exclusion zones via
 * hasStructureChunkInRange("minecraft:villages", ...), they use vanilla's placement.
 * If MVS uses different placement values, the check looks in wrong places.
 *
 * Solution: Intercept hasStructureChunkInRange() and use MVS placement values
 * when checking for MVS-intercepted structure sets.
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin {

    @Shadow
    public abstract long getLevelSeed();

    /**
     * Intercept hasStructureChunkInRange to use MVS placement for intercepted structure sets.
     *
     * This ensures exclusion zones (like pillager outposts avoiding villages) work correctly
     * when MVS changes village placement parameters.
     */
    @Inject(
        method = "hasStructureChunkInRange",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onHasStructureChunkInRange(
            Holder<StructureSet> structureSetHolder,
            int chunkX,
            int chunkZ,
            int chunkRange,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!MVSConfig.enabled) {
            return; // Let vanilla handle it
        }

        // Get structure set ID
        ResourceLocation structureSetId = structureSetHolder.unwrapKey()
            .map(k -> k.location())
            .orElse(null);

        if (structureSetId == null) {
            return; // Can't identify, let vanilla handle
        }

        String structureSetIdString = structureSetId.toString();

        // If the set being checked is blocked, it won't generate - no structures nearby
        if (MVSConfig.blockStructureSets.contains(structureSetIdString)) {
            if (MVSConfig.debugLogging) {
                MVSCommon.LOGGER.info(
                    "[MVS] hasStructureChunkInRange({}) = false (blocked set)",
                    structureSetIdString
                );
            }
            cir.setReturnValue(false);
            return;
        }

        // Check if this structure set is MVS-intercepted
        if (!MVSConfig.interceptStructureSets.contains(structureSetIdString)) {
            return; // Not intercepted, let vanilla handle
        }

        // MVS-intercepted structure set - use MVS placement logic
        boolean result = hasStructureChunkInRangeMVS(structureSetIdString, chunkX, chunkZ, chunkRange);

        if (MVSConfig.debugLogging) {
            MVSCommon.LOGGER.info(
                "[MVS] hasStructureChunkInRange({}, chunk[{},{}], range={}) = {} (MVS placement)",
                structureSetIdString, chunkX, chunkZ, chunkRange, result
            );
        }

        cir.setReturnValue(result);
    }

    /**
     * Check if there's an MVS-managed structure within range using MVS placement.
     *
     * This mirrors vanilla's logic but uses MVS placement values:
     * - Resolve placement from config + registry
     * - Loop through chunks in range
     * - Check if each chunk is a placement chunk using MVS formula
     */
    private boolean hasStructureChunkInRangeMVS(String structureSetId, int centerChunkX, int centerChunkZ, int chunkRange) {
        // Resolve placement from MVS config (with registry fallback)
        // Note: We don't have registry access here, so we rely on config or defaults
        PlacementResolver.ResolvedPlacement resolved = PlacementResolver.resolve(structureSetId, null);

        // Create placement strategy
        LocateHelper.RandomSpreadPlacement placement = new LocateHelper.RandomSpreadPlacement(
            resolved.spacing,
            resolved.separation,
            resolved.salt,
            resolved.spreadType
        );

        long seed = this.getLevelSeed();

        // Loop through all chunks in range (same as vanilla)
        for (int x = centerChunkX - chunkRange; x <= centerChunkX + chunkRange; x++) {
            for (int z = centerChunkZ - chunkRange; z <= centerChunkZ + chunkRange; z++) {
                if (placement.isPlacementChunk(x, z, seed)) {
                    return true;
                }
            }
        }

        return false;
    }
}