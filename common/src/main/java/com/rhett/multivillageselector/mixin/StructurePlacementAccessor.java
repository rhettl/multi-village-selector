package com.rhett.multivillageselector.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/**
 * Mixin accessor to expose protected methods from StructurePlacement.
 * This is cleaner than reflection and works cross-platform (Fabric + NeoForge).
 */
@Mixin(StructurePlacement.class)
public interface StructurePlacementAccessor {

    /**
     * Invokes the protected salt() method on StructurePlacement.
     * Usage: ((StructurePlacementAccessor) placement).invokeSalt()
     *
     * @return The salt value used for structure placement randomization
     */
    @Invoker("salt")
    int invokeSalt();

    /**
     * Invokes the protected locateOffset() method on StructurePlacement.
     * Usage: ((StructurePlacementAccessor) placement).invokeLocateOffset()
     *
     * The locate offset is added to the chunk origin when /locate reports position.
     * Most structures use Vec3i.ZERO, but mods can customize this.
     *
     * @return The offset vector to add to chunk origin for locate results
     */
    @Invoker("locateOffset")
    Vec3i invokeLocateOffset();

    /**
     * Invokes the protected exclusionZone() method on StructurePlacement.
     * Usage: ((StructurePlacementAccessor) placement).invokeExclusionZone()
     *
     * The exclusion zone defines which other structure set this placement avoids.
     * Example: Pillager outposts have exclusion_zone referencing villages.
     *
     * @return Optional containing the exclusion zone, or empty if none defined
     */
    @Invoker("exclusionZone")
    Optional<StructurePlacement.ExclusionZone> invokeExclusionZone();
}