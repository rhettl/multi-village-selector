package com.rhett.multivillageselector.mixin;

import com.rhett.multivillageselector.MultiVillageSelector;
import com.rhett.multivillageselector.MVSConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Random;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    // Track locations we've already processed to avoid duplicate replacements
    // Uses a bounded LRU cache to prevent memory leaks on long-running servers
    private static final java.util.Set<String> processedLocations =
        java.util.Collections.newSetFromMap(new java.util.LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                return size() > 1000;  // Keep max 1000 most recent locations
            }
        });

    // Intercept structure start creation - this is where structures are decided
    @Inject(
        method = "tryGenerateStructure",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
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

        // Only intercept structures in the overworld dimension
        // MVS is specifically designed for village variety in the overworld
        // Supporting nether/end structures (fortresses, bastions, end cities) would be
        // a completely different feature set - a generalized "Multi Structure Selector"
        // For now, we stay focused on our core purpose: village variety in overworld

        // Note: chunk.getLevel() can be null during early chunk generation
        // In that case, skip dimension check (non-overworld structures won't match our patterns anyway)
        var level = chunk.getLevel();
        if (level != null) {
            ResourceLocation dimension = level.dimension().location();
            if (!dimension.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))) {
                return;
            }
        }

        Structure structure = entry.structure().value();
        Registry<Structure> registry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        ResourceLocation structureLocation = registry.getKey(structure);

        if (structureLocation != null) {
            String structureName = structureLocation.toString();

            // DEBUG: Log ALL structure attempts if debug logging is on
            if (MVSConfig.debugLogging && structureName.contains("village")) {
                MultiVillageSelector.LOGGER.info("[MVS-DEBUG] Structure attempt: {} at chunk [{}, {}]",
                    structureName, chunkPos.x, chunkPos.z);
            }

            boolean shouldPrevent = shouldPreventSpawn(structureName);
            boolean shouldReplace = shouldReplaceStructure(structureName);

            BlockPos centerPos = chunkPos.getMiddleBlockPosition(64);
            String locationKey = centerPos.getX() + "," + centerPos.getZ();

            if (shouldPrevent) {
                // Structure is in prevent_spawn list - block it but DON'T debounce the location
                // This allows vanilla villages to still try and be replaced
                if (MVSConfig.debugLogging) {
                    MultiVillageSelector.LOGGER.info("===== MVS: PREVENTED STRUCTURE SPAWN =====");
                    MultiVillageSelector.LOGGER.info("Type: {}", structureLocation);
                    MultiVillageSelector.LOGGER.info("Location: X={}, Z={}", centerPos.getX(), centerPos.getZ());
                    MultiVillageSelector.LOGGER.info("Action: BLOCKED (prevent_spawn)");
                    MultiVillageSelector.LOGGER.info("==========================================");
                }

                // Send in-game message to ops
                sendDebugMessageToOps(
                    chunk,
                    String.format("[MVS] Blocked: %s at [%d, %d] (prevent_spawn)",
                        structureLocation, centerPos.getX(), centerPos.getZ())
                );

                // CANCEL this structure but don't debounce - let vanilla villages try
                cir.setReturnValue(false);
                return;
            }

            if (shouldReplace) {
                // Only process each location once (MC tries multiple village types)
                if (!processedLocations.contains(locationKey)) {
                    processedLocations.add(locationKey);
                    // Structure is in replace_of list - select and place replacement

                    if (MVSConfig.debugLogging) {
                        MultiVillageSelector.LOGGER.info("===== MVS: INTERCEPTED =====");
                        MultiVillageSelector.LOGGER.info("Original: {}", structureLocation);
                        MultiVillageSelector.LOGGER.info("Location: X={}, Z={}", centerPos.getX(), centerPos.getZ());
                    }

                    boolean placed = placeReplacementStructure(
                        structureLocation,
                        centerPos,
                        chunk,
                        chunkPos,
                        sectionPos,
                        registryAccess,
                        structureManager,
                        randomState,
                        templateManager,
                        seed
                    );

                    if (placed) {
                        if (MVSConfig.debugLogging) {
                            MultiVillageSelector.LOGGER.info("===== MVS: SUCCESS =====");
                        }
                    } else {
                        MultiVillageSelector.LOGGER.warn("===== MVS: REPLACEMENT FAILED =====");
                        MultiVillageSelector.LOGGER.warn("Original: {}", structureLocation);
                        MultiVillageSelector.LOGGER.warn("Location: X={}, Z={}", centerPos.getX(), centerPos.getZ());
                        MultiVillageSelector.LOGGER.warn("===================================");
                    }

                    // Return the placement result
                    cir.setReturnValue(placed);
                    return;
                }

                // Already processed this location, skip it
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Stage 2: Place the replacement structure
     */
    private boolean placeReplacementStructure(
            ResourceLocation originalStructure,
            BlockPos centerPos,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            net.minecraft.core.SectionPos sectionPos,
            net.minecraft.core.RegistryAccess registryAccess,
            StructureManager structureManager,
            net.minecraft.world.level.levelgen.RandomState randomState,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager,
            long seed
    ) {
        try {
            // 1. Get the biome at this location from ChunkGenerator's BiomeSource
            // We can't use chunk.getNoiseBiome() because biomes haven't been generated yet
            ChunkGenerator generator = (ChunkGenerator)(Object)this;

            // Get the surface height at this location to sample the correct biome
            // Villages spawn at world surface, not at a fixed Y level
            int surfaceY = generator.getBaseHeight(
                chunkPos.getMiddleBlockX(),
                chunkPos.getMiddleBlockZ(),
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                net.minecraft.world.level.LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth()),
                randomState
            );

            Holder<Biome> biomeHolder = generator.getBiomeSource().getNoiseBiome(
                chunkPos.getMiddleBlockX() >> 2,
                surfaceY >> 2,  // Sample at surface height, not fixed Y=64
                chunkPos.getMiddleBlockZ() >> 2,
                randomState.sampler()
            );

            String biomeCategory = getBiomeCategory(biomeHolder);

            ResourceLocation biomeName = biomeHolder.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);

            if (MVSConfig.debugLogging) {
                MultiVillageSelector.LOGGER.info("  Biome: {} (at Y={}) → Category: {}", biomeName, surfaceY, biomeCategory);
            }

            // 2. Get available structures for this biome category
            java.util.List<MVSConfig.WeightedStructure> availableStructures =
                MVSConfig.biomeReplacements.get(biomeCategory);

            if (availableStructures == null || availableStructures.isEmpty()) {
                // Try DEFAULT
                if (MVSConfig.biomeReplacements.containsKey("DEFAULT")) {
                    availableStructures = MVSConfig.biomeReplacements.get("DEFAULT");
                    if (MVSConfig.debugLogging) {
                        MultiVillageSelector.LOGGER.info("  No structures for {}, using DEFAULT", biomeCategory);
                    }
                }
            }

            if (availableStructures == null || availableStructures.isEmpty()) {
                MultiVillageSelector.LOGGER.warn("  No replacement structures available for biome category: {}", biomeCategory);
                return false;
            }

            // 3. Debug: Log the selection pool
            if (MVSConfig.debugLogging) {
                int totalWeight = availableStructures.stream().mapToInt(s -> s.weight).sum();
                MultiVillageSelector.LOGGER.info("  Selection Pool for {} ({} structures, total weight: {}):",
                    biomeCategory, availableStructures.size(), totalWeight);
                for (MVSConfig.WeightedStructure ws : availableStructures) {
                    double percentage = (ws.weight / (double)totalWeight) * 100;
                    if (ws.isEmpty) {
                        MultiVillageSelector.LOGGER.info("    - (empty - no spawn) (weight: {}, {:.1f}%)",
                            ws.weight, percentage);
                    } else {
                        MultiVillageSelector.LOGGER.info("    - {} (weight: {}, {:.1f}%)",
                            ws.structure, ws.weight, percentage);
                    }
                }
            }

            // 4. Select a replacement structure using weighted random
            Random random = new Random(seed ^ centerPos.asLong());
            int totalWeight = availableStructures.stream().mapToInt(s -> s.weight).sum();
            int roll = random.nextInt(totalWeight);

            if (MVSConfig.debugLogging) {
                MultiVillageSelector.LOGGER.info("  Random Roll: {} out of {} (seed: {})",
                    roll, totalWeight, seed ^ centerPos.asLong());
            }

            // Find the selected structure
            int currentWeight = 0;
            MVSConfig.WeightedStructure selected = null;
            for (MVSConfig.WeightedStructure ws : availableStructures) {
                currentWeight += ws.weight;
                if (roll < currentWeight) {
                    selected = ws;
                    break;
                }
            }

            if (selected == null) {
                selected = availableStructures.get(0); // Fallback
            }

            // Check if empty was selected (weighted no-spawn)
            if (selected.isEmpty) {
                if (MVSConfig.debugLogging) {
                    MultiVillageSelector.LOGGER.info("  SELECTED: (empty - cancelling spawn)");
                    MultiVillageSelector.LOGGER.info("===== MVS: SPAWN CANCELLED (EMPTY) =====");
                }
                return false;
            }

            if (MVSConfig.debugLogging) {
                MultiVillageSelector.LOGGER.info("  SELECTED: {}", selected.structure);
            }

            // 5. Validate structure exists in registry
            Registry<Structure> registry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            Optional<Holder.Reference<Structure>> structureHolder = registry.getHolder(
                ResourceKey.create(Registries.STRUCTURE, selected.structure)
            );

            if (structureHolder.isEmpty()) {
                MultiVillageSelector.LOGGER.error("  ERROR: Replacement structure not found in registry: {}", selected.structure);
                return false;
            }

            // 6. Actually place the replacement structure
            Structure replacementStructure = structureHolder.get().value();

            // Generate the structure start for the replacement
            // (generator already declared earlier in method)
            net.minecraft.world.level.levelgen.structure.StructureStart structureStart =
                replacementStructure.generate(
                    registryAccess,
                    generator,
                    generator.getBiomeSource(),
                    randomState,
                    templateManager,
                    seed,
                    chunkPos,
                    0, // height reference - 0 for surface structures
                    chunk,
                    (structure) -> true // Structure predicate - always true for our replacement
                );

            if (structureStart != null && structureStart.isValid()) {
                // Add the structure start to the chunk
                structureManager.setStartForStructure(
                    sectionPos,
                    replacementStructure,
                    structureStart,
                    chunk
                );

                if (MVSConfig.debugLogging) {
                    MultiVillageSelector.LOGGER.info("  ✅ Successfully placed {} at [{}, {}]",
                        selected.structure, centerPos.getX(), centerPos.getZ());
                }

                // 7. Send in-game message to ops
                sendDebugMessageToOps(
                    chunk,
                    String.format("[MVS] Replaced %s → %s at [%d, %d] (biome: %s)",
                        originalStructure.getPath(),
                        selected.structure.getPath(),
                        centerPos.getX(),
                        centerPos.getZ(),
                        biomeCategory)
                );

                return true;
            } else {
                MultiVillageSelector.LOGGER.warn("  ⚠️ Structure generation returned invalid/null start for {}",
                    selected.structure);
                return false;
            }

        } catch (Exception e) {
            MultiVillageSelector.LOGGER.error("  Error during replacement selection", e);
            return false;
        }
    }

    /**
     * Send debug message to all online operators
     */
    private void sendDebugMessageToOps(ChunkAccess chunk, String message) {
        if (!MVSConfig.debugLogging) {
            return;
        }

        try {
            // We need to get the ServerLevel from the chunk
            // This is tricky during world generation, but we can try
            if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk) {
                if (levelChunk.getLevel() instanceof ServerLevel serverLevel) {
                    // Get all online players with op permission
                    for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                        if (serverLevel.getServer().getPlayerList().isOp(player.getGameProfile())) {
                            player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal(message)
                                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                            );
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail - chunk might not be fully loaded yet
        }
    }

    private boolean shouldPreventSpawn(String structureName) {
        for (String pattern : MVSConfig.preventSpawnPatterns) {
            if (MVSConfig.matchesPattern(structureName, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldReplaceStructure(String structureName) {
        for (String pattern : MVSConfig.replaceOfPatterns) {
            if (MVSConfig.matchesPattern(structureName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Categorizes a biome using multiple methods for maximum compatibility
     * Supports vanilla + modded biomes (BoP, WWOO, Terralith, etc.)
     */
    private String getBiomeCategory(Holder<Biome> biomeHolder) {
        ResourceLocation biomeName = biomeHolder.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);

        if (biomeName == null) {
            return "plains";
        }

        String biomeId = biomeName.toString();
        Biome biome = biomeHolder.value();

        // 1. Check user config overrides first (highest priority)
        if (MVSConfig.biomeCategoryOverrides.containsKey(biomeId)) {
            return MVSConfig.biomeCategoryOverrides.get(biomeId);
        }

        // 2. Check biome tags (most reliable for vanilla + well-made mods)
        if (biomeHolder.is(net.minecraft.tags.BiomeTags.IS_OCEAN)) return "ocean";

        // 3. Enhanced name matching (supports vanilla + many modded biomes)
        String path = biomeName.getPath().toLowerCase();
        String namespace = biomeName.getNamespace().toLowerCase();

        // Jungle variants (check early as they're specific)
        if (path.contains("jungle") || path.contains("tropical") || path.contains("mayan")) {
            return "jungle";
        }

        // Swamp variants
        if (path.contains("swamp") || path.contains("marsh") || path.contains("bog") ||
            path.contains("bayou") || path.contains("wetland") || path.contains("mangrove")) {
            return "swamp";
        }

        // Mushroom variants
        if (path.contains("mushroom") || path.contains("mystic_grove")) {
            return "mushroom";
        }

        // Dark Forest variants
        if (path.contains("dark_forest") || path.contains("dead_forest") || path.contains("ominous")) {
            return "dark_forest";
        }

        // Beach variants
        if (path.contains("beach") || path.contains("shore")) {
            return "beach";
        }

        // Desert variants (includes badlands/mesa)
        if (path.contains("desert") || path.contains("dune") || path.contains("sand") ||
            path.contains("badlands") || path.contains("mesa") || path.contains("outback")) {
            return "desert";
        }

        // Savanna variants
        if (path.contains("savanna") || path.contains("shrubland") || path.contains("prairie") ||
            path.contains("steppe") || path.contains("arid")) {
            return "savanna";
        }

        // Snowy/Cold variants
        if (path.contains("snow") || path.contains("ice") || path.contains("frozen") ||
            path.contains("arctic") || path.contains("tundra") || path.contains("glacier") ||
            path.contains("grove")) {
            return "snowy";
        }

        // Taiga/Coniferous variants
        if (path.contains("taiga") || path.contains("pine") || path.contains("spruce") ||
            path.contains("boreal") || path.contains("conifer")) {
            return "taiga";
        }

        // Ocean/Water variants
        if (path.contains("ocean") || path.contains("sea") || path.contains("river") ||
            path.contains("lake")) {
            return "ocean";
        }

        // Plains variants (check last as it's most common and can be broad)
        if (path.contains("plains") || path.contains("meadow") || path.contains("grassland") ||
            path.contains("field") || path.contains("pasture") || path.contains("forest")) {
            return "plains";
        }

        // 4. Fall back to temperature (works for all biomes)
        if (biome.coldEnoughToSnow(BlockPos.ZERO)) {
            return "snowy";
        }

        float temp = biome.getBaseTemperature();
        if (temp > 1.5f) {
            return "desert";
        }
        if (temp > 1.0f) {
            return "savanna";
        }
        if (temp < 0.3f) {
            return "taiga";
        }

        // 5. Default to plains
        return "plains";
    }
}
