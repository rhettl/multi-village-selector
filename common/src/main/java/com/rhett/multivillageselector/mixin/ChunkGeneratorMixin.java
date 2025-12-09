package com.rhett.multivillageselector.mixin;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.strategy.StructureInterceptor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Mixin for structure interception (shared by NeoForge and Fabric).
 * Thin wrapper that delegates to platform-agnostic StructureInterceptor.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    /**
     * Shadow method for tryGenerateStructure.
     * Allows StructureInterceptor callbacks to trigger actual generation.
     */
    @org.spongepowered.asm.mixin.Shadow
    protected abstract boolean tryGenerateStructure(
        net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry,
        net.minecraft.world.level.StructureManager structureManager,
        net.minecraft.core.RegistryAccess registryAccess,
        net.minecraft.world.level.levelgen.RandomState randomState,
        StructureTemplateManager templateManager,
        long seed,
        ChunkAccess chunk,
        ChunkPos chunkPos,
        net.minecraft.core.SectionPos sectionPos
    );

    /**
     * v0.3.0: Structure-set level interception (main entry point).
     * Redirects forEach loop to delegate to StructureInterceptor.
     */
    @Redirect(
        method = "createStructures",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
            remap = false
        )
    )
    private void redirectStructureSetForEach(
            List<?> structureSetList,
            java.util.function.Consumer<Holder<StructureSet>> vanillaConsumer,
            net.minecraft.core.RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            net.minecraft.world.level.StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager
    ) {
        // Delegate to platform-agnostic interceptor
        StructureInterceptor.intercept(
            structureSetList,
            vanillaConsumer,
            (ChunkGenerator)(Object)this,  // Pass generator instance
            registryAccess,
            state,
            structureManager,
            chunk,
            templateManager,
            // Generation callback - calls Shadow method
            (entry, sm, ra, rs, tm, sd, ch, cp, sp) ->
                this.tryGenerateStructure(entry, sm, ra, rs, tm, sd, ch, cp, sp)
        );
    }

    /**
     * v0.3.0: Structure-level blacklist enforcement.
     * Prevents blacklisted structures from spawning (even from non-intercepted sources).
     */
    @Inject(
        method = "tryGenerateStructure",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onTryGenerateStructure(
            net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry,
            net.minecraft.world.level.StructureManager structureManager,
            net.minecraft.core.RegistryAccess registryAccess,
            net.minecraft.world.level.levelgen.RandomState randomState,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager,
            long seed,
            net.minecraft.world.level.chunk.ChunkAccess chunk,
            ChunkPos chunkPos,
            net.minecraft.core.SectionPos sectionPos,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir
    ) {
        if (!MVSConfig.enabled) {
            return;
        }

        // Check blacklist
        Structure structure = entry.structure().value();
        Registry<Structure> registry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        ResourceLocation structureLocation = registry.getKey(structure);

        if (structureLocation != null && shouldPreventSpawn(structureLocation.toString())) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Checks if a structure should be prevented from spawning.
     * Matches against blacklisted_structures list (supports wildcards).
     */
    private boolean shouldPreventSpawn(String structureName) {
        for (String blacklisted : MVSConfig.blacklistedStructures) {
            if (com.rhett.multivillageselector.util.PatternMatcher.matches(structureName, blacklisted)) {
                if (MVSConfig.debugLogging) {
                    MVSCommon.LOGGER.info("  [MVS] Blocking blacklisted structure: {}", structureName);
                }
                return true;
            }
        }
        return false;
    }

    // v0.4.0: Biome validation moved to StructureMixin.biomes() intercept
    // This catches ALL callers of structure.biomes(), not just tryGenerateStructure().
    // See StructureMixin.java for implementation.

    // v0.4.0: getStructurePresence debug intercept removed
    // Method signature mapping issues on Fabric - would need intermediary names
    // The main StructureMixin.biomes() intercept IS working for both loaders
}
