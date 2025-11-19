package com.rhett.multivillageselector;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class VillageReplacementHandler {

    // Track villages we've already logged to avoid spam
    private final Set<String> loggedVillages = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerStart(ServerAboutToStartEvent event) {
        if (!MVSConfig.enabled) {
            return;
        }

        MultiVillageSelector.LOGGER.info("===========================================");
        MultiVillageSelector.LOGGER.info("MVS: Server starting - RUNTIME INTERCEPTION MODE");
        MultiVillageSelector.LOGGER.info("Replace patterns: {}", MVSConfig.replaceOfPatterns);
        MultiVillageSelector.LOGGER.info("Prevent spawn patterns: {}", MVSConfig.preventSpawnPatterns);
        MultiVillageSelector.LOGGER.info("Biome categories configured: {}", MVSConfig.biomeReplacements.keySet());
        MultiVillageSelector.LOGGER.info("===========================================");
        MultiVillageSelector.LOGGER.info("Stage 1: Detect & Cancel - Using Mixin");
        MultiVillageSelector.LOGGER.info("Stage 2: Replacement Placement - TODO");
        MultiVillageSelector.LOGGER.info("===========================================");

        // Register commands
        MultiVillageSelector.LOGGER.info("Registering MVS commands...");
        try {
            MVSCommands.register(event.getServer().getCommands().getDispatcher());
            MultiVillageSelector.LOGGER.info("MVS commands registered successfully");
        } catch (Exception e) {
            MultiVillageSelector.LOGGER.error("Failed to register MVS commands", e);
        }

        // Show launch message if enabled
        if (MVSConfig.showLaunchMessage) {
            MultiVillageSelector.LOGGER.warn("╔════════════════════════════════════════════════════════╗");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("║          Multi Village Selector - Quick Start         ║");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("╠════════════════════════════════════════════════════════╣");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("║  Quick Start Guide:                                    ║");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("║  1. Run in-game:  /mvs generate                        ║");
            MultiVillageSelector.LOGGER.warn("║     (Requires OP level 2)                              ║");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("║  2. Review the generated config file                   ║");
            MultiVillageSelector.LOGGER.warn("║     Click the link shown in chat                       ║");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("║  3. Copy file to: config/multivillageselector.json5    ║");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("║  4. Restart Minecraft to apply changes                 ║");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("╠════════════════════════════════════════════════════════╣");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("║  📖 Documentation:                                     ║");
            MultiVillageSelector.LOGGER.warn("║     github.com/RhettL/multi-village-selector/docs     ║");
            MultiVillageSelector.LOGGER.warn("║                                                        ║");
            MultiVillageSelector.LOGGER.warn("╚════════════════════════════════════════════════════════╝");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkEvent.Load event) {
        // This event fires when chunks are loaded
        // We'll use this as a hook point for now
        if (!MVSConfig.enabled) {
            return;
        }

        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Cast to ServerLevel to access structure manager
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        ChunkPos chunkPos = event.getChunk().getPos();

        // Check if there are any structures in this chunk
        if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk) {
            var structureManager = serverLevel.structureManager();
            var structureStarts = structureManager.startsForStructure(chunkPos, structure -> {
                // Check all structures
                return true;
            });

            structureStarts.forEach(structureStart -> {
                if (!structureStart.isValid()) {
                    return;
                }

                Structure structure = structureStart.getStructure();
                Registry<Structure> registry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
                ResourceLocation structureLocation = registry.getKey(structure);

                if (structureLocation != null && shouldReplaceStructure(structureLocation)) {
                    BlockPos pos = structureStart.getBoundingBox().getCenter();
                    String villageKey = structureLocation + "@" + pos.toShortString();

                    // Only log each unique village once
                    if (!loggedVillages.contains(villageKey)) {
                        loggedVillages.add(villageKey);

                        String biomeCategory = getBiomeCategoryAtPos(serverLevel, pos);

                        // Log to console
                        MultiVillageSelector.LOGGER.warn("===== MVS: Vanilla Village Found =====");
                        MultiVillageSelector.LOGGER.warn("Type: {}", structureLocation);
                        MultiVillageSelector.LOGGER.warn("Location: X={}, Y={}, Z={}", pos.getX(), pos.getY(), pos.getZ());
                        MultiVillageSelector.LOGGER.warn("Biome Category: {}", biomeCategory);
                        MultiVillageSelector.LOGGER.warn("Status: NOT CANCELED - Village already generated!");
                        MultiVillageSelector.LOGGER.warn("INFO: Villages must be canceled during generation, not after.");
                        MultiVillageSelector.LOGGER.warn("INFO: To test cancellation, create a NEW world.");
                        MultiVillageSelector.LOGGER.warn("======================================");

                        // Notify nearby players
                        notifyNearbyPlayers(serverLevel, pos, structureLocation, biomeCategory, false);
                    }
                }
            });
        }
    }

    /**
     * Gets biome category at a specific position
     */
    private String getBiomeCategoryAtPos(Level level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        return getBiomeCategory(biomeHolder);
    }

    /**
     * Notifies nearby players about intercepted village
     */
    private void notifyNearbyPlayers(Level level, BlockPos pos, ResourceLocation structureType, String biomeCategory, boolean wasCanceled) {
        // Find players within 200 blocks
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(
            ServerPlayer.class,
            new net.minecraft.world.phys.AABB(pos).inflate(200),
            player -> true
        );

        if (!nearbyPlayers.isEmpty()) {
            Component message = Component.literal("[MVS] ")
                .withStyle(wasCanceled ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                .append(Component.literal(wasCanceled ? "CANCELED" : "FOUND")
                    .withStyle(wasCanceled ? ChatFormatting.RED : ChatFormatting.GOLD))
                .append(Component.literal(" village: ")
                    .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(structureType.toString())
                    .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" at ")
                    .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()))
                    .withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" [" + biomeCategory + "]")
                    .withStyle(ChatFormatting.GREEN));

            for (ServerPlayer player : nearbyPlayers) {
                player.sendSystemMessage(message);
            }

            MultiVillageSelector.LOGGER.info("Notified {} player(s) about village {}", nearbyPlayers.size(), wasCanceled ? "cancellation" : "detection");
        }
    }

    /**
     * Gets the biome category for replacement lookup
     * Uses same logic as ChunkGeneratorMixin for consistency
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

        // 2. Check biome tags
        if (biomeHolder.is(net.minecraft.tags.BiomeTags.IS_OCEAN)) return "ocean";

        // 3. Enhanced name matching
        String path = biomeName.getPath().toLowerCase();

        // Specific biomes first
        if (path.contains("jungle") || path.contains("tropical") || path.contains("mayan")) return "jungle";
        if (path.contains("swamp") || path.contains("marsh") || path.contains("bog") ||
            path.contains("bayou") || path.contains("wetland") || path.contains("mangrove")) return "swamp";
        if (path.contains("mushroom") || path.contains("mystic_grove")) return "mushroom";
        if (path.contains("dark_forest") || path.contains("dead_forest") || path.contains("ominous")) return "dark_forest";
        if (path.contains("beach") || path.contains("shore")) return "beach";

        // Common biomes
        if (path.contains("desert") || path.contains("badlands") || path.contains("mesa")) return "desert";
        if (path.contains("savanna") || path.contains("shrubland") || path.contains("prairie")) return "savanna";
        if (path.contains("snow") || path.contains("ice") || path.contains("frozen") || path.contains("tundra")) return "snowy";
        if (path.contains("taiga") || path.contains("pine") || path.contains("spruce") || path.contains("conifer")) return "taiga";
        if (path.contains("ocean") || path.contains("sea") || path.contains("river")) return "ocean";
        if (path.contains("plains") || path.contains("meadow") || path.contains("grassland") || path.contains("field")) return "plains";

        // 4. Temperature fallback
        if (biome.coldEnoughToSnow(BlockPos.ZERO)) {
            return "snowy";
        }

        float temp = biome.getBaseTemperature();
        if (temp > 1.5f) return "desert";
        if (temp > 1.0f) return "savanna";
        if (temp < 0.3f) return "taiga";

        // 5. Default
        return "plains";
    }

    /**
     * Checks if a structure should be replaced
     */
    private boolean shouldReplaceStructure(ResourceLocation structureLocation) {
        String fullName = structureLocation.toString();
        for (String pattern : MVSConfig.replaceOfPatterns) {
            if (MVSConfig.matchesPattern(fullName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a replacement structure for the given biome
     */
    public Optional<ResourceLocation> getReplacementStructure(Holder<Biome> biome, Random random) {
        String biomeCategory = getBiomeCategory(biome);
        MVSConfig.WeightedStructure replacement =
            MVSConfig.getRandomReplacement(biomeCategory, random);

        if (replacement != null) {
            if (MVSConfig.debugLogging) {
                MultiVillageSelector.LOGGER.info("Replacing village in biome category {} with {}",
                        biomeCategory, replacement.structure);
            }
            return Optional.of(replacement.structure);
        }

        return Optional.empty();
    }
}
