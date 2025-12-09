package com.rhett.multivillageselector.mixin;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.util.BiomeRuleResolver;
import com.rhett.multivillageselector.util.BiomeRules;
import com.rhett.multivillageselector.util.MVSStructureAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.datafixers.util.Either;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Mixin to intercept Structure.biomes() globally.
 *
 * This replaces the call-site specific @Redirect in ChunkGeneratorMixin with a single
 * intercept at the source. ALL callers of structure.biomes() now get MVS-aware results:
 * - tryGenerateStructure (chunk generation)
 * - getStructurePresence (compass mods, explorer maps)
 * - Any other mod calling biomes()
 *
 * We inject a structureId field directly into Structure instances, set during registry
 * iteration at server start.
 */
@Mixin(Structure.class)
public abstract class StructureMixin implements MVSStructureAccessor {

    /**
     * Injected field to store structure ID directly on the Structure instance.
     * Set during SERVER_BEFORE_START registry iteration.
     * Avoids external Map lookup - state lives on the object.
     */
    @Unique
    private String mvs$structureId = null;

    /**
     * Cached registry access for biome resolution.
     * Set alongside structureId during initialization.
     */
    @Unique
    private RegistryAccess mvs$registryAccess = null;

    /**
     * Cached biome rules resolved from MVS config.
     * Lazily initialized on first biomes() call.
     */
    @Unique
    private BiomeRules mvs$cachedRules = null;

    /**
     * Cached HolderSet for MVS biome rules.
     * Lazily created, reused for all subsequent calls.
     */
    @Unique
    private HolderSet<Biome> mvs$cachedHolderSet = null;

    /**
     * Debug: Track if we've printed a stack trace for Explorer's Compass yet.
     */
    @Unique
    private static boolean mvs$printedCompassStack = false;

    /**
     * Debug: Counter for contains() calls to help track search progress.
     */
    @Unique
    private static int mvs$containsCallCount = 0;

    /**
     * Set the structure ID on this instance.
     * Called from ModEvents during registry iteration.
     */
    @Override
    @Unique
    public void mvs$setStructureId(String id, RegistryAccess registryAccess) {
        this.mvs$structureId = id;
        this.mvs$registryAccess = registryAccess;
        // Clear caches when ID changes
        this.mvs$cachedRules = null;
        this.mvs$cachedHolderSet = null;
    }

    /**
     * Get the structure ID (for debugging/testing).
     */
    @Override
    @Unique
    public String mvs$getStructureId() {
        return this.mvs$structureId;
    }

    /**
     * Clear cached biome data. Call when config reloads.
     */
    @Override
    @Unique
    public void mvs$clearCache() {
        this.mvs$cachedRules = null;
        this.mvs$cachedHolderSet = null;
    }

    /**
     * Intercept biomes() to return MVS-aware HolderSet for controlled structures.
     */
    @Inject(method = "biomes", at = @At("HEAD"), cancellable = true)
    private void onBiomes(CallbackInfoReturnable<HolderSet<Biome>> cir) {
        // TEMP DEBUG: Log all biomes() calls
        if (MVSConfig.debugLogging) {
            MVSCommon.LOGGER.info("[MVS] biomes() called, structureId={}", mvs$structureId);
        }

        if (!MVSConfig.enabled) {
            return; // MVS disabled, use vanilla
        }

        if (mvs$structureId == null) {
            return; // ID not set, use vanilla
        }

        // Check if this structure is in MVS pool
        boolean inPool = MVSConfig.structurePool.stream()
            .anyMatch(s -> s.structure != null && s.structure.toString().equals(mvs$structureId));

        if (!inPool) {
            // TEMP DEBUG
            if (MVSConfig.debugLogging && mvs$structureId.contains("village")) {
                MVSCommon.LOGGER.info("[MVS] {} NOT in pool. Pool has {} entries. First few: {}",
                    mvs$structureId,
                    MVSConfig.structurePool.size(),
                    MVSConfig.structurePool.stream()
                        .limit(3)
                        .map(s -> s.structure != null ? s.structure.toString() : "null")
                        .toList());
            }
            return; // Not MVS-controlled, use vanilla
        }

        // Return cached HolderSet or create new one
        if (mvs$cachedHolderSet == null) {
            mvs$cachedHolderSet = mvs$createMVSBiomeHolderSet();
        }

        if (MVSConfig.debugLogging) {
            MVSCommon.LOGGER.info("[MVS] Structure.biomes() INTERCEPTED for {}", mvs$structureId);
        }

        cir.setReturnValue(mvs$cachedHolderSet);
    }

    /**
     * Create a HolderSet that properly implements all methods with MVS biome rules.
     */
    @Unique
    private HolderSet<Biome> mvs$createMVSBiomeHolderSet() {
        // Get/cache biome rules
        if (mvs$cachedRules == null && mvs$registryAccess != null) {
            mvs$cachedRules = BiomeRuleResolver.getEffectiveRules(mvs$structureId, mvs$registryAccess);
        }

        final BiomeRules rules = mvs$cachedRules;
        final String structureId = mvs$structureId;
        final RegistryAccess registryAccess = mvs$registryAccess;

        // Get the actual biome holders for stream/iterator
        final List<Holder<Biome>> resolvedBiomes = mvs$resolveBiomeHolders(rules, registryAccess);

        // Create a proper HolderSet implementation
        return new HolderSet<Biome>() {
            @Override
            public Stream<Holder<Biome>> stream() {
                return resolvedBiomes.stream();
            }

            @Override
            public int size() {
                return resolvedBiomes.size();
            }

            @Override
            public boolean contains(Holder<Biome> holder) {
                if (MVSConfig.relaxedBiomeValidation) {
                    // Relaxed mode: MVS already validated at chunk center
                    if (MVSConfig.debugLogging) {
                        MVSCommon.LOGGER.info("[MVS] contains({}) for {} → TRUE (relaxed)",
                            holder.unwrapKey().map(k -> k.location().toString()).orElse("?"), structureId);
                    }
                    return true;
                }
                // Strict mode: validate against rules
                boolean result = rules == null || rules.isEmpty() || rules.matches(holder);
                if (MVSConfig.debugLogging) {
                    String biomeId = holder.unwrapKey().map(k -> k.location().toString()).orElse("?");

                    // Extract caller from stack trace
                    String caller = "unknown";
                    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                    boolean isCompass = false;
                    for (int i = 0; i < stack.length && i < 20; i++) {
                        String className = stack[i].getClassName().toLowerCase();
                        if (className.contains("explorerscompass") || className.contains("searchworker")) {
                            caller = "Explorer's Compass";
                            isCompass = true;
                            break;
                        } else if (className.contains("chunkgenerator")) {
                            caller = "ChunkGenerator";
                            break;
                        } else if (className.contains("structurecheck")) {
                            caller = "StructureCheck";
                            break;
                        }
                    }

                    // Print full stack trace ONCE for Explorer's Compass to see call chain
                    if (isCompass && !mvs$printedCompassStack) {
                        mvs$printedCompassStack = true;
                        MVSCommon.LOGGER.info("[MVS] === EXPLORER'S COMPASS STACK TRACE (one-time) ===");
                        for (int i = 0; i < Math.min(stack.length, 25); i++) {
                            MVSCommon.LOGGER.info("[MVS]   {}: {}", i, stack[i]);
                        }
                        MVSCommon.LOGGER.info("[MVS] === END STACK TRACE ===");
                    }

                    MVSCommon.LOGGER.info("[MVS] contains({}) for {} → {} [caller: {}]",
                        biomeId, structureId, result, caller);
                }
                return result;
            }

            @Override
            public Holder<Biome> get(int index) {
                return resolvedBiomes.get(index);
            }

            @Override
            public Optional<net.minecraft.tags.TagKey<Biome>> unwrapKey() {
                return Optional.empty(); // We're not a tag-based set
            }

            @Override
            public Either<net.minecraft.tags.TagKey<Biome>, List<Holder<Biome>>> unwrap() {
                return Either.right(resolvedBiomes);
            }

            @Override
            public java.util.Iterator<Holder<Biome>> iterator() {
                return resolvedBiomes.iterator();
            }

            @Override
            public boolean canSerializeIn(HolderOwner<Biome> owner) {
                // We can always serialize - we're a direct holder set
                return true;
            }

            @Override
            public Optional<Holder<Biome>> getRandomElement(net.minecraft.util.RandomSource random) {
                if (resolvedBiomes.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(resolvedBiomes.get(random.nextInt(resolvedBiomes.size())));
            }
        };
    }

    /**
     * Resolve biome rules to actual Holder<Biome> instances for stream/iterator.
     */
    @Unique
    private List<Holder<Biome>> mvs$resolveBiomeHolders(BiomeRules rules, RegistryAccess registryAccess) {
        if (rules == null || registryAccess == null) {
            return Collections.emptyList();
        }

        try {
            var biomeRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);

            // Get all biomes that match our rules
            return biomeRegistry.holders()
                .filter(holder -> rules.matches(holder))
                .map(holder -> (Holder<Biome>) holder)
                .toList();
        } catch (Exception e) {
            MVSCommon.LOGGER.warn("[MVS] Failed to resolve biome holders for {}: {}", mvs$structureId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
