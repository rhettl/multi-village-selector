package com.rhett.multivillageselector.mixin;

import com.rhett.multivillageselector.profiler.ChunkGenerationProfiler;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Performance profiling mixin for chunk structure generation.
 *
 * This mixin is INDEPENDENT of MVSConfig.enabled - it always profiles
 * so you can compare performance with MVS enabled vs disabled.
 *
 * Logs averages every 5 minutes to help diagnose performance issues.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorProfilerMixin {

    /**
     * Start timing at the beginning of createStructures.
     */
    @Inject(
        method = "createStructures",
        at = @At("HEAD"),
        remap = false
    )
    private void onCreateStructuresStart(
            net.minecraft.core.RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            net.minecraft.world.level.StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            CallbackInfo ci
    ) {
        ChunkGenerationProfiler.startChunkTiming();
    }

    /**
     * End timing at the end of createStructures.
     */
    @Inject(
        method = "createStructures",
        at = @At("RETURN"),
        remap = false
    )
    private void onCreateStructuresEnd(
            net.minecraft.core.RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            net.minecraft.world.level.StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            CallbackInfo ci
    ) {
        ChunkGenerationProfiler.endChunkTiming();
    }
}
