package com.rhett.multivillageselector.mixin;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.strategy.StructureInterceptor;
import com.rhett.multivillageselector.util.BiomeRuleResolver;
import com.rhett.multivillageselector.util.BiomeRules;
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

    /**
     * v0.3.0: Override vanilla biome validation with MVS biomes{} validation.
     * Redirects structure.biomes() call to return MVS-aware HolderSet.
     */
    @Redirect(
        method = "tryGenerateStructure",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/Structure;biomes()Lnet/minecraft/core/HolderSet;"
        )
    )
    private net.minecraft.core.HolderSet<net.minecraft.world.level.biome.Biome> redirectStructureBiomes(
            Structure structure,
            net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry,
            net.minecraft.world.level.StructureManager structureManager,
            net.minecraft.core.RegistryAccess registryAccess
    ) {
        if (!MVSConfig.enabled) {
            return structure.biomes(); // Original behavior
        }

        // Get structure ID
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        ResourceLocation structureId = structureRegistry.getKey(structure);

        if (structureId == null) {
            return structure.biomes(); // Original behavior
        }

        // Check if this structure is in MVS structure_pool (stateless check, no race conditions)
        String structureIdString = structureId.toString();
        boolean isMVSControlled = MVSConfig.structurePool.stream()
            .anyMatch(s -> s.structure != null && s.structure.toString().equals(structureIdString));

        if (!isMVSControlled) {
            return structure.biomes(); // Not MVS-controlled, use vanilla
        }

        // MVS-controlled: Return custom HolderSet that validates against biomes{}
        return createMVSBiomeHolderSet(structureIdString, registryAccess);
    }

    /**
     * Creates a HolderSet that validates biomes against MVS biomes{} config.
     * Uses BiomeRuleResolver for clean, testable logic.
     *
     * Returns a wrapper that delegates to empty HolderSet but overrides contains().
     */
    @SuppressWarnings("unchecked")
    private net.minecraft.core.HolderSet<net.minecraft.world.level.biome.Biome> createMVSBiomeHolderSet(
            String structureId,
            net.minecraft.core.RegistryAccess registryAccess) {

        // Get biome rules using centralized resolver
        final BiomeRules rules = BiomeRuleResolver.getEffectiveRules(structureId, registryAccess);

        if (rules.isEmpty()) {
            // Structure not found - fallback to empty (allow all)
            return net.minecraft.core.HolderSet.direct();
        }

        final net.minecraft.core.HolderSet<net.minecraft.world.level.biome.Biome> baseSet =
            net.minecraft.core.HolderSet.direct();

        // Create wrapper via dynamic proxy
        return (net.minecraft.core.HolderSet<net.minecraft.world.level.biome.Biome>) java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.core.HolderSet.class.getClassLoader(),
            new Class<?>[] { net.minecraft.core.HolderSet.class },
            (proxy, method, args) -> {
                // Override contains() method
                if (method.getName().equals("contains") && args != null && args.length == 1) {
                    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder =
                        (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>) args[0];

                    // Delegate to BiomeRules.matches() - clean, testable logic!
                    return rules.matches(biomeHolder);
                }

                // Delegate all other methods to base HolderSet
                return method.invoke(baseSet, args);
            }
        );
    }
}
