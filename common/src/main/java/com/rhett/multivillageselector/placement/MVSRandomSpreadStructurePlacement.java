package com.rhett.multivillageselector.placement;

import com.rhett.multivillageselector.util.LocateHelper;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.Optional;

/**
 * MVS-aware RandomSpreadStructurePlacement that supports custom spread types.
 *
 * This extends vanilla's RandomSpreadStructurePlacement so that mods like
 * Explorer's Compass and Structure Compass recognize it and use our
 * getPotentialStructureChunk() implementation.
 *
 * Supports all MVS spread types:
 * - LINEAR (vanilla default)
 * - TRIANGULAR (vanilla)
 * - EDGE_BIASED (MVS custom)
 * - CORNER_BIASED (MVS custom)
 * - GAUSSIAN (MVS custom)
 * - FIXED_CENTER (MVS custom)
 */
public class MVSRandomSpreadStructurePlacement extends RandomSpreadStructurePlacement {

    private final LocateHelper.SpreadType mvsSpreadType;

    /**
     * Full constructor with all parameters.
     */
    public MVSRandomSpreadStructurePlacement(
            int spacing,
            int separation,
            int salt,
            LocateHelper.SpreadType mvsSpreadType,
            Optional<StructurePlacement.ExclusionZone> exclusionZone
    ) {
        super(
            Vec3i.ZERO,                                    // locateOffset
            FrequencyReductionMethod.DEFAULT,              // frequency reduction
            1.0f,                                          // frequency
            salt,                                          // salt
            exclusionZone,                                 // exclusion zone
            spacing,                                       // spacing
            separation,                                    // separation
            toVanillaSpreadType(mvsSpreadType)            // vanilla spread type (for type() compatibility)
        );
        this.mvsSpreadType = mvsSpreadType;
    }

    /**
     * Simple constructor without exclusion zone.
     */
    public MVSRandomSpreadStructurePlacement(
            int spacing,
            int separation,
            int salt,
            LocateHelper.SpreadType mvsSpreadType
    ) {
        this(spacing, separation, salt, mvsSpreadType, Optional.empty());
    }

    /**
     * Get the MVS spread type.
     */
    public LocateHelper.SpreadType getMvsSpreadType() {
        return mvsSpreadType;
    }

    /**
     * Convert MVS spread type to vanilla spread type.
     * Custom MVS types map to LINEAR (the vanilla default).
     */
    private static RandomSpreadType toVanillaSpreadType(LocateHelper.SpreadType mvsType) {
        return switch (mvsType) {
            case TRIANGULAR -> RandomSpreadType.TRIANGULAR;
            default -> RandomSpreadType.LINEAR;
        };
    }

    /**
     * Override getPotentialStructureChunk to use MVS spread type logic.
     *
     * This is the key method that Explorer's Compass and Structure Compass call
     * to find where structures can spawn. By overriding it, we ensure they
     * use MVS's placement algorithm.
     *
     * Delegates to LocateHelper.calculatePlacementChunk() for canonical implementation.
     */
    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int regionX, int regionZ) {
        // Calculate grid cell
        int cellX = Math.floorDiv(regionX, this.spacing());
        int cellZ = Math.floorDiv(regionZ, this.spacing());

        // Delegate to canonical implementation in LocateHelper
        int[] result = LocateHelper.calculatePlacementChunk(
            cellX, cellZ, seed, this.salt(),
            this.spacing(), this.separation(), mvsSpreadType
        );

        return new ChunkPos(result[0], result[1]);
    }

    /**
     * Create from resolved placement values.
     */
    public static MVSRandomSpreadStructurePlacement fromResolved(
            int spacing,
            int separation,
            int salt,
            LocateHelper.SpreadType spreadType,
            com.rhett.multivillageselector.config.ExclusionZone exclusionZone,
            net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.StructureSet> registry
    ) {
        Optional<StructurePlacement.ExclusionZone> vanillaExclusionZone = Optional.empty();

        // Convert MVS ExclusionZone to vanilla ExclusionZone if present
        if (exclusionZone != null && registry != null) {
            var otherSetKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.STRUCTURE_SET,
                net.minecraft.resources.ResourceLocation.parse(exclusionZone.otherSet)
            );
            var holder = registry.getHolder(otherSetKey);
            if (holder.isPresent()) {
                vanillaExclusionZone = Optional.of(
                    new StructurePlacement.ExclusionZone(holder.get(), exclusionZone.chunkCount)
                );
            }
        }

        return new MVSRandomSpreadStructurePlacement(spacing, separation, salt, spreadType, vanillaExclusionZone);
    }
}