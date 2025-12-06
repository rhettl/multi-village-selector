package com.rhett.multivillageselector.mixin;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.config.PlacementRule;
import com.rhett.multivillageselector.placement.MVSRandomSpreadStructurePlacement;
import com.rhett.multivillageselector.util.LocateHelper;
import com.rhett.multivillageselector.util.PlacementResolver;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

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

    @Shadow
    private List<Holder<StructureSet>> possibleStructureSets;

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
        // Resolve placement from MVS config with possibleStructureSets fallback
        PlacementResolver.ResolvedPlacement resolved = resolvePlacementWithFallback(structureSetId);

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

    /**
     * Intercept getPlacementsForStructure to return MVS placement for structures in the pool.
     *
     * This is critical for mod compatibility - mods like Explorer's Compass and Structure Compass
     * use this method to get placement information. By returning our MVSRandomSpreadStructurePlacement,
     * these mods will use MVS's placement algorithm for searching.
     *
     * Flow:
     *   Explorer's Compass → getPlacementsForStructure() → returns MVSRandomSpreadStructurePlacement
     *   → calls getPotentialStructureChunk() → uses MVS spread type logic
     */
    @Inject(
        method = "getPlacementsForStructure",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onGetPlacementsForStructure(
            Holder<Structure> structureHolder,
            CallbackInfoReturnable<List<StructurePlacement>> cir
    ) {
        if (!MVSConfig.enabled) {
            return; // Let vanilla handle it
        }

        // Get structure ID
        String structureId = structureHolder.unwrapKey()
            .map(k -> k.location().toString())
            .orElse(null);

        if (structureId == null) {
            return; // Can't identify, let vanilla handle
        }

        // Check if this structure is in MVS pool
        boolean inPool = MVSConfig.structurePool.stream()
            .anyMatch(s -> s.structure != null && s.structure.toString().equals(structureId));

        if (!inPool) {
            return; // Not in MVS pool, let vanilla handle
        }

        // Get the structure set this structure belongs to
        String structureSetId = getStructureSetForStructure(structureId);
        if (structureSetId == null) {
            return; // Can't determine structure set
        }

        // Resolve placement from MVS config with possibleStructureSets fallback
        PlacementResolver.ResolvedPlacement resolved = resolvePlacementWithFallback(structureSetId);

        // Create MVS placement
        MVSRandomSpreadStructurePlacement mvsPlacement = new MVSRandomSpreadStructurePlacement(
            resolved.spacing,
            resolved.separation,
            resolved.salt,
            resolved.spreadType
        );

        if (MVSConfig.debugLogging) {
            MVSCommon.LOGGER.info(
                "[MVS] getPlacementsForStructure({}) → MVS placement (spacing={}, sep={}, salt={}, spread={})",
                structureId, resolved.spacing, resolved.separation, resolved.salt, resolved.spreadType
            );
        }

        cir.setReturnValue(List.of(mvsPlacement));
    }

    /**
     * Get the structure set ID for a given structure.
     * For now, uses the first intercepted structure set.
     * TODO: Could be enhanced to look up actual structure → structure_set mapping.
     */
    private String getStructureSetForStructure(String structureId) {
        if (!MVSConfig.interceptStructureSets.isEmpty()) {
            return MVSConfig.interceptStructureSets.get(0);
        }
        return null;
    }

    /**
     * Resolve placement using MVSConfig with fallback to possibleStructureSets (registry values).
     * This ensures we use the same placement values as LocateCommands which has registry access.
     *
     * Priority: MVSConfig.placement > Registry (from possibleStructureSets) > Defaults
     */
    private PlacementResolver.ResolvedPlacement resolvePlacementWithFallback(String structureSetId) {
        // Try to find the structure set in possibleStructureSets for registry fallback
        RandomSpreadStructurePlacement registryPlacement = null;
        for (Holder<StructureSet> holder : possibleStructureSets) {
            String id = holder.unwrapKey().map(k -> k.location().toString()).orElse(null);
            if (structureSetId.equals(id)) {
                StructurePlacement placement = holder.value().placement();
                if (placement instanceof RandomSpreadStructurePlacement rsp) {
                    registryPlacement = rsp;
                }
                break;
            }
        }

        // Get config values (may be null if not configured)
        var configPlacement = MVSConfig.placement.get(structureSetId);

        // Resolve each value: config > registry > default
        int spacing;
        int separation;
        int salt;
        LocateHelper.SpreadType spreadType;
        String spacingSource, separationSource, saltSource, spreadTypeSource;

        if (configPlacement != null && configPlacement.spacing != null) {
            spacing = configPlacement.spacing;
            spacingSource = "config";
        } else if (registryPlacement != null) {
            spacing = registryPlacement.spacing();
            spacingSource = "registry";
        } else {
            spacing = PlacementResolver.DEFAULT_SPACING;
            spacingSource = "default";
        }

        if (configPlacement != null && configPlacement.separation != null) {
            separation = configPlacement.separation;
            separationSource = "config";
        } else if (registryPlacement != null) {
            separation = registryPlacement.separation();
            separationSource = "registry";
        } else {
            separation = PlacementResolver.DEFAULT_SEPARATION;
            separationSource = "default";
        }

        if (configPlacement != null && configPlacement.salt != null) {
            salt = configPlacement.salt;
            saltSource = "config";
        } else if (registryPlacement != null) {
            // Use mixin accessor to get protected salt value
            salt = ((StructurePlacementAccessor) registryPlacement).invokeSalt();
            saltSource = "registry";
        } else {
            salt = PlacementResolver.DEFAULT_SALT;
            saltSource = "default";
        }

        if (configPlacement != null && configPlacement.spreadType != null) {
            // Config stores spread type as string, need to convert
            spreadType = configPlacement.resolveSpreadType(PlacementResolver.DEFAULT_SPREAD_TYPE);
            spreadTypeSource = "config";
        } else if (registryPlacement != null) {
            RandomSpreadType vanillaType = registryPlacement.spreadType();
            spreadType = vanillaType == RandomSpreadType.TRIANGULAR
                ? LocateHelper.SpreadType.TRIANGULAR
                : LocateHelper.SpreadType.LINEAR;
            spreadTypeSource = "registry";
        } else {
            spreadType = PlacementResolver.DEFAULT_SPREAD_TYPE;
            spreadTypeSource = "default";
        }

        return new PlacementResolver.ResolvedPlacement(
            spacing, separation, salt, spreadType, "random_spread",
            net.minecraft.core.Vec3i.ZERO, null,
            spacingSource, separationSource, saltSource, spreadTypeSource, null
        );
    }
}