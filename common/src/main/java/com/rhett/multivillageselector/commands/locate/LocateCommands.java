package com.rhett.multivillageselector.commands.locate;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.locate.PredictionHelper;
import com.rhett.multivillageselector.locate.PredictionHelper.ChunkPrediction;
import com.rhett.multivillageselector.util.LocateHelper;
import com.rhett.multivillageselector.util.PlacementResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /mvs locate command - finds where structures will spawn using MVS selection logic.
 * Unlike vanilla /locate, this simulates MVS weighted selection to find where a specific
 * structure WILL spawn, accounting for competition from other structures in the pool.
 */
public class LocateCommands {

    private static final int MAX_RESULTS = 10;
    private static final int MIN_SEARCH_RADIUS = 200; // chunks - floor for search radius
    private static final int CELLS_TO_SEARCH = 5;     // search at least 5 cells in each direction

    /**
     * Suggestion provider for structure IDs from MVSConfig.structurePool.
     * Only suggests non-empty, non-pattern structures.
     */
    public static final SuggestionProvider<CommandSourceStack> STRUCTURE_SUGGESTIONS = (context, builder) -> {
        List<String> structureIds = MVSConfig.structurePool.stream()
            .filter(s -> !s.isEmpty && s.structure != null)
            .map(s -> s.structure.toString())
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        return SharedSuggestionProvider.suggest(structureIds, builder);
    };

    /**
     * Execute /mvs locate <structure>
     * Finds the nearest location where the specified structure will spawn.
     */
    public static int execute(CommandContext<CommandSourceStack> context, String structureIdStr) {
        return executeLocate(context, structureIdStr, false);
    }

    /**
     * Execute /mvs locate <structure> more
     * Finds up to 10 nearest locations where the specified structure will spawn.
     */
    public static int executeMore(CommandContext<CommandSourceStack> context, String structureIdStr) {
        return executeLocate(context, structureIdStr, true);
    }

    /**
     * Core locate implementation.
     */
    private static int executeLocate(CommandContext<CommandSourceStack> context, String structureIdStr, boolean findAll) {
        CommandSourceStack source = context.getSource();

        try {
            // Validate structure is in pool
            ResourceLocation structureId = ResourceLocation.parse(structureIdStr);
            boolean inPool = MVSConfig.structurePool.stream()
                .anyMatch(s -> s.structure != null && s.structure.equals(structureId));

            if (!inPool) {
                source.sendFailure(Component.literal("Structure not in MVS pool: " + structureIdStr)
                    .withStyle(ChatFormatting.RED));
                source.sendSuccess(() -> Component.literal("Tip: Use ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("/mvs structure pool")
                        .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mvs structure pool"))))
                    .append(Component.literal(" to see configured structures")
                        .withStyle(ChatFormatting.GRAY)), false);
                return 0;
            }

            // Get player position
            BlockPos startPos = BlockPos.containing(source.getPosition());
            ServerLevel level = source.getLevel();
            long seed = level.getSeed();

            // Get structure set for placement (default to minecraft:villages)
            String structureSetId = getStructureSetForStructure(structureIdStr);

            // Create surface-aware biome sampler (uses noise-based height estimation)
            // This correctly handles 3D biome mods like Terralith
            var chunkSource = level.getChunkSource();
            var generator = chunkSource.getGenerator();
            BiomeSource biomeSource = generator.getBiomeSource();
            var randomState = chunkSource.randomState();
            Climate.Sampler climateSampler = randomState.sampler();
            LocateHelper.BiomeSampler biomeSampler = LocateHelper.createSurfaceAwareBiomeSampler(
                biomeSource, climateSampler, generator, level, randomState);

            // Get placement strategy from config
            var structureSetRegistry = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
            LocateHelper.PlacementStrategy strategy = LocateHelper.getConfiguredPlacement(structureSetId, structureSetRegistry);

            // Calculate dynamic search radius based on spacing
            // Ensures ~5 cells in each direction regardless of placement config
            int spacing = 34; // default
            if (strategy instanceof LocateHelper.RandomSpreadPlacement rsp) {
                spacing = rsp.spacing;
            }
            int searchRadius = Math.max(MIN_SEARCH_RADIUS, spacing * CELLS_TO_SEARCH);

            // Debug logging to help diagnose placement mismatches
            if (MVSConfig.debugLogging && strategy instanceof LocateHelper.RandomSpreadPlacement rsp) {
                PlacementResolver.ResolvedPlacement resolved = PlacementResolver.resolve(structureSetId, structureSetRegistry);
                MVSCommon.LOGGER.info("[MVS] Locate using placement: spacing={} ({}), separation={} ({}), salt={} ({}), spreadType={} ({}), locateOffset={}, searchRadius={}",
                    resolved.spacing, resolved.spacingSource,
                    resolved.separation, resolved.separationSource,
                    resolved.salt, resolved.saltSource,
                    resolved.spreadType, resolved.spreadTypeSource,
                    resolved.locateOffset,
                    searchRadius);
            }

            // Header
            String headerSuffix = findAll ? " (searching for multiple)" : "";
            source.sendSuccess(() -> Component.literal("Searching for " + structureIdStr + headerSuffix + "...")
                .withStyle(ChatFormatting.YELLOW), false);

            // Use PredictionHelper for unified locate logic
            int maxResults = findAll ? MAX_RESULTS : 1;
            List<ChunkPrediction> results = PredictionHelper.findChunksForStructureWithConfig(
                structureIdStr, structureSetId, startPos, seed, biomeSampler,
                structureSetRegistry, maxResults, searchRadius);

            final int finalSearchRadius = searchRadius;
            if (results.isEmpty()) {
                source.sendFailure(Component.literal("No spawn points found within " + finalSearchRadius + " chunks")
                    .withStyle(ChatFormatting.RED));
                // Suggest teleporting beyond searched area and trying again
                // Moving 1 radius away gives ~75% new territory (overlapping circles)
                double kmToMove = (finalSearchRadius * 16.0) / 1000.0;
                source.sendSuccess(() -> Component.literal(
                    String.format("Tip: Teleport %.1fkm+ away and try again (rare structure)", kmToMove))
                    .withStyle(ChatFormatting.GRAY), false);
                return 0;
            }

            if (findAll) {
                // Header for multiple results
                final int count = results.size();
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("=== Found " + count + " location(s) ===")
                    .withStyle(ChatFormatting.GOLD), false);
                source.sendSuccess(() -> Component.literal(""), false);

                // Show each result
                int index = 1;
                for (ChunkPrediction result : results) {
                    sendPredictionWithTeleport(source, result, index++, startPos);
                }
            } else {
                // Single result
                ChunkPrediction result = results.get(0);

                // Header
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("=== Found " + structureIdStr + " ===")
                    .withStyle(ChatFormatting.GOLD), false);
                source.sendSuccess(() -> Component.literal(""), false);

                sendPredictionWithTeleport(source, result, 0, startPos);

                // Tip for finding more
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("Tip: Use ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("/mvs locate " + structureIdStr + " more")
                        .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/mvs locate " + structureIdStr + " more"))))
                    .append(Component.literal(" to find more")
                        .withStyle(ChatFormatting.GRAY)), false);
            }

            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            MVSCommon.LOGGER.error("Error in locate command", e);
            return 0;
        }
    }

    /**
     * Send a prediction result to the player with teleport link.
     */
    private static void sendPredictionWithTeleport(
            CommandSourceStack source,
            ChunkPrediction result,
            int index,
            BlockPos startPos) {

        // Position with teleport (Y = ~ to keep player's current height)
        String tpCommand = result.getTeleportCommand();

        // Build position component with teleport
        String prefix = index > 0 ? index + ". " : "";
        Component posComponent = Component.literal(prefix + "[")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal(result.worldPos.getX() + ", ~, " + result.worldPos.getZ())
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to teleport")))))
            .append(Component.literal("]")
                .withStyle(ChatFormatting.WHITE));

        source.sendSuccess(() -> posComponent, false);

        // Distance and biome info
        final int distance = result.distanceFromStart;
        source.sendSuccess(() -> Component.literal("   Distance: " + distance + " blocks")
            .withStyle(ChatFormatting.GRAY), false);

        final String biomeId = result.biomeId;
        source.sendSuccess(() -> Component.literal("   Biome: " + biomeId)
            .withStyle(ChatFormatting.GRAY), false);

        // Chunk info
        source.sendSuccess(() -> Component.literal("   Chunk: [" + result.chunkX + ", " + result.chunkZ + "]")
            .withStyle(ChatFormatting.DARK_GRAY), false);
    }

    /**
     * Get the structure set ID for a given structure.
     * Checks MVSConfig.interceptStructureSets or defaults to minecraft:villages.
     */
    private static String getStructureSetForStructure(String structureId) {
        // For now, default to first intercepted set or minecraft:villages
        if (!MVSConfig.interceptStructureSets.isEmpty()) {
            return MVSConfig.interceptStructureSets.get(0);
        }
        return "minecraft:villages";
    }
}
