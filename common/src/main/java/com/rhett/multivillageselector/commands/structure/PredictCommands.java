package com.rhett.multivillageselector.commands.structure;

import com.mojang.brigadier.context.CommandContext;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.locate.PredictionHelper;
import com.rhett.multivillageselector.locate.PredictionHelper.ChunkPrediction;
import com.rhett.multivillageselector.util.LocateHelper;
import com.rhett.multivillageselector.util.PlacementResolver;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Debug commands for chunk prediction analysis.
 * Used to validate /mvs locate position calculations.
 */
public class PredictCommands {

    private static final int PREDICTIONS_PER_PAGE = 10;
    private static final int MAX_PREDICTIONS = 100;

    // Cache predictions per player to support pagination without recalculating
    private static final Map<UUID, CachedPredictions> playerCache = new HashMap<>();

    /**
     * Cached predictions with metadata for invalidation.
     */
    private static class CachedPredictions {
        final List<ChunkPrediction> predictions;
        final BlockPos startPos;
        final long timestamp;

        CachedPredictions(List<ChunkPrediction> predictions, BlockPos startPos) {
            this.predictions = predictions;
            this.startPos = startPos;
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Check if cache is still valid (same location, within 5 minutes).
         */
        boolean isValid(BlockPos currentPos) {
            // Invalidate if player moved more than 100 blocks
            double distance = Math.sqrt(
                Math.pow(currentPos.getX() - startPos.getX(), 2) +
                Math.pow(currentPos.getZ() - startPos.getZ(), 2)
            );
            if (distance > 100) return false;

            // Invalidate after 5 minutes
            if (System.currentTimeMillis() - timestamp > 5 * 60 * 1000) return false;

            return true;
        }
    }

    /**
     * Execute /mvs debug predict [page]
     * Shows paginated predictions with teleport links.
     */
    public static int executePaginated(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();

        try {
            // Get or generate predictions
            List<ChunkPrediction> predictions = getOrGeneratePredictions(source);
            if (predictions == null) {
                return 0; // Error already sent
            }

            // Calculate pagination
            int totalPages = (int) Math.ceil((double) predictions.size() / PREDICTIONS_PER_PAGE);
            int currentPage = page;
            if (currentPage < 1) currentPage = 1;
            if (currentPage > totalPages) currentPage = totalPages;
            final int finalPage = currentPage;

            int startIdx = (finalPage - 1) * PREDICTIONS_PER_PAGE;
            int endIdx = Math.min(startIdx + PREDICTIONS_PER_PAGE, predictions.size());

            // Header
            final int finalTotalPages = totalPages;
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("=== Chunk Predictions (Page " + finalPage + "/" + finalTotalPages + ") ===")
                .withStyle(ChatFormatting.GOLD), false);

            // Show placement info
            String structureSetId = getStructureSetId();
            var structureSetRegistry = source.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
            PlacementResolver.ResolvedPlacement resolved = PlacementResolver.resolve(structureSetId, structureSetRegistry);

            source.sendSuccess(() -> Component.literal(String.format(
                "Placement: spacing=%d, separation=%d, salt=%d",
                resolved.spacing, resolved.separation, resolved.salt))
                .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Show predictions for this page
            for (int i = startIdx; i < endIdx; i++) {
                ChunkPrediction pred = predictions.get(i);
                sendPredictionLine(source, pred, i + 1);
            }

            // Navigation footer
            source.sendSuccess(() -> Component.literal(""), false);
            sendNavigationFooter(source, finalPage, finalTotalPages);

            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            MVSCommon.LOGGER.error("Error in predict command", e);
            return 0;
        }
    }

    /**
     * Execute /mvs debug predict file
     * Writes all predictions to a file.
     */
    public static int executeFile(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            // Always generate fresh predictions for file output
            List<ChunkPrediction> predictions = generatePredictions(source);
            if (predictions == null) {
                return 0;
            }

            // Get placement info for header
            String structureSetId = getStructureSetId();
            var structureSetRegistry = source.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
            PlacementResolver.ResolvedPlacement resolved = PlacementResolver.resolve(structureSetId, structureSetRegistry);

            // Build file content
            List<String> lines = new ArrayList<>();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            lines.add("===========================================");
            lines.add("  MVS Chunk Predictions");
            lines.add("===========================================");
            lines.add("");
            lines.add("Generated: " + timestamp);
            lines.add("Start Position: " + formatBlockPos(BlockPos.containing(source.getPosition())));
            lines.add("World Seed: " + source.getLevel().getSeed());
            lines.add("");
            lines.add("Placement Settings:");
            lines.add(String.format("  Structure Set: %s", structureSetId));
            lines.add(String.format("  Spacing: %d (%s)", resolved.spacing, resolved.spacingSource));
            lines.add(String.format("  Separation: %d (%s)", resolved.separation, resolved.separationSource));
            lines.add(String.format("  Salt: %d (%s)", resolved.salt, resolved.saltSource));
            lines.add(String.format("  Spread Type: %s (%s)", resolved.spreadType, resolved.spreadTypeSource));
            lines.add(String.format("  Locate Offset: %s", resolved.locateOffset));
            lines.add("");
            lines.add("===========================================");
            lines.add("  Predictions (" + predictions.size() + " chunks)");
            lines.add("===========================================");
            lines.add("");

            // Add each prediction
            for (int i = 0; i < predictions.size(); i++) {
                ChunkPrediction pred = predictions.get(i);
                lines.add(String.format("#%d", i + 1));
                lines.add(pred.toFileString());
                lines.add("");
            }

            lines.add("===========================================");
            lines.add("  Summary by Structure");
            lines.add("===========================================");
            lines.add("");

            // Count structures
            Map<String, Integer> structureCounts = new LinkedHashMap<>();
            for (ChunkPrediction pred : predictions) {
                String key = pred.structureId != null ? pred.structureId : "(none)";
                structureCounts.merge(key, 1, Integer::sum);
            }

            for (Map.Entry<String, Integer> entry : structureCounts.entrySet()) {
                lines.add(String.format("  %s: %d", entry.getKey(), entry.getValue()));
            }

            // Write to file
            Path gameDir = source.getServer().getServerDirectory();
            Path outputDir = gameDir.resolve("local/mvs");
            Files.createDirectories(outputDir);

            String fileTimestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            Path outputFile = outputDir.resolve("predictions-" + fileTimestamp + ".txt");
            Files.write(outputFile, lines, StandardCharsets.UTF_8);

            // Send success message
            String relativePath = gameDir.relativize(outputFile).toString();
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("Predictions written to: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(relativePath)
                    .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,
                            outputFile.toAbsolutePath().toString()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to copy path"))))), false);

            source.sendSuccess(() -> Component.literal(predictions.size() + " predictions saved")
                .withStyle(ChatFormatting.GRAY), false);

            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("Error writing file: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            MVSCommon.LOGGER.error("Error in predict file command", e);
            return 0;
        }
    }

    /**
     * Execute /mvs debug predict new
     * Forces regeneration of predictions (clears cache).
     */
    public static int executeNew(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Clear cache for this player
        if (source.getEntity() != null) {
            playerCache.remove(source.getEntity().getUUID());
        }

        source.sendSuccess(() -> Component.literal("Prediction cache cleared. Running new prediction...")
            .withStyle(ChatFormatting.YELLOW), false);

        return executePaginated(context, 1);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Get cached predictions or generate new ones.
     */
    private static List<ChunkPrediction> getOrGeneratePredictions(CommandSourceStack source) {
        UUID playerId = source.getEntity() != null ? source.getEntity().getUUID() : null;
        BlockPos currentPos = BlockPos.containing(source.getPosition());

        // Check cache
        if (playerId != null) {
            CachedPredictions cached = playerCache.get(playerId);
            if (cached != null && cached.isValid(currentPos)) {
                return cached.predictions;
            }
        }

        // Generate new predictions
        List<ChunkPrediction> predictions = generatePredictions(source);

        // Cache for pagination
        if (playerId != null && predictions != null) {
            playerCache.put(playerId, new CachedPredictions(predictions, currentPos));
        }

        return predictions;
    }

    /**
     * Generate fresh predictions from current position.
     */
    private static List<ChunkPrediction> generatePredictions(CommandSourceStack source) {
        try {
            BlockPos startPos = BlockPos.containing(source.getPosition());
            ServerLevel level = source.getLevel();
            long seed = level.getSeed();

            // Get structure set for placement
            String structureSetId = getStructureSetId();

            // Create surface-aware biome sampler (uses noise-based height estimation)
            // This correctly handles 3D biome mods like Terralith
            var chunkSource = level.getChunkSource();
            var generator = chunkSource.getGenerator();
            BiomeSource biomeSource = generator.getBiomeSource();
            var randomState = chunkSource.randomState();
            Climate.Sampler climateSampler = randomState.sampler();
            LocateHelper.BiomeSampler biomeSampler = LocateHelper.createSurfaceAwareBiomeSampler(
                biomeSource, climateSampler, generator, level, randomState);

            // Get registry
            var structureSetRegistry = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);

            // Generate predictions
            return PredictionHelper.predictChunksWithConfig(
                structureSetId, startPos, seed, biomeSampler, structureSetRegistry, MAX_PREDICTIONS);

        } catch (Exception e) {
            source.sendFailure(Component.literal("Error generating predictions: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            MVSCommon.LOGGER.error("Error generating predictions", e);
            return null;
        }
    }

    /**
     * Send a single prediction line with teleport link.
     */
    private static void sendPredictionLine(CommandSourceStack source, ChunkPrediction pred, int index) {
        String tpCommand = pred.getTeleportCommand();

        // Structure name (or indicator if none)
        String structureName = pred.structureId != null
            ? pred.structureId.replaceFirst("^[^:]+:", "") // Remove namespace
            : "(no spawn)";

        // Build the line: #1 [160, 0, 320] village_plains (plains) 500m
        Component line = Component.literal(String.format("#%d ", index))
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("[")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal(String.format("%d, %d, %d",
                    pred.worldPos.getX(), pred.worldPos.getY(), pred.worldPos.getZ()))
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to teleport")))))
            .append(Component.literal("] ")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal(structureName)
                .withStyle(pred.structureId != null ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY))
            .append(Component.literal(" (" + pred.biomeId.replaceFirst("^[^:]+:", "") + ")")
                .withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(" " + pred.distanceFromStart + "m")
                .withStyle(ChatFormatting.GRAY));

        source.sendSuccess(() -> line, false);
    }

    /**
     * Send navigation footer with prev/next links.
     */
    private static void sendNavigationFooter(CommandSourceStack source, int currentPage, int totalPages) {
        net.minecraft.network.chat.MutableComponent footer = Component.literal("");

        // Previous page link
        if (currentPage > 1) {
            footer.append(Component.literal("[< Prev]")
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/mvs structure predict " + (currentPage - 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Page " + (currentPage - 1))))));
        } else {
            footer.append(Component.literal("[< Prev]")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        footer.append(Component.literal("  ").withStyle(ChatFormatting.GRAY));

        // Next page link
        if (currentPage < totalPages) {
            footer.append(Component.literal("[Next >]")
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/mvs structure predict " + (currentPage + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Page " + (currentPage + 1))))));
        } else {
            footer.append(Component.literal("[Next >]")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        footer.append(Component.literal("  ").withStyle(ChatFormatting.GRAY));

        // File export link
        footer.append(Component.literal("[Export to File]")
            .withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/mvs structure predict file"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Save all predictions to file")))));

        final Component finalFooter = footer;
        source.sendSuccess(() -> finalFooter, false);
    }

    /**
     * Get the structure set ID to use for predictions.
     */
    private static String getStructureSetId() {
        if (!MVSConfig.interceptStructureSets.isEmpty()) {
            return MVSConfig.interceptStructureSets.get(0);
        }
        return "minecraft:villages";
    }

    /**
     * Format a BlockPos for display.
     */
    private static String formatBlockPos(BlockPos pos) {
        return String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
    }
}
