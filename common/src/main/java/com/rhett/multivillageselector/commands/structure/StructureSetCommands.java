package com.rhett.multivillageselector.commands.structure;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.util.StructureSetAnalyzer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * Handles /mvs structure set <structure_set> command
 * Displays detailed information about a structure set
 */
public class StructureSetCommands {

    /**
     * Handle /mvs structure set <structure_set> [full]
     * Shows comprehensive info about a structure_set
     * @param showFull if true, shows all structures without truncation
     */
    public static int executeSet(CommandContext<CommandSourceStack> context, boolean showFull) {
        CommandSourceStack source = context.getSource();
        String structureSetId = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "structure_set").toString();

        try {
            var registryAccess = source.getServer().registryAccess();

            // Use StructureSetAnalyzer for all the analysis
            StructureSetAnalyzer.StructureSetInfo info = StructureSetAnalyzer.analyze(structureSetId, registryAccess);

            if (info == null) {
                source.sendFailure(Component.literal("Structure set not found: " + structureSetId));
                return 0;
            }

            // Header
            String headerSuffix = showFull ? " (full)" : "";
            source.sendSuccess(() -> Component.literal("=== Structure Set: " + structureSetId + headerSuffix + " ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Placement type
            source.sendSuccess(() -> Component.literal("Placement Type: " + info.placementType)
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Spacing, separation, salt (if available)
            if (info.spacing != null) {
                final int spacing = info.spacing;
                source.sendSuccess(() -> Component.literal("Spacing: " + spacing + " chunks")
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal("  Structure attempts occur every " + spacing + " chunks")
                    .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(""), false);
            }

            if (info.separation != null) {
                final int separation = info.separation;
                source.sendSuccess(() -> Component.literal("Separation: " + separation + " chunks")
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal("  Minimum distance between structures in this set")
                    .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(""), false);
            }

            if (info.salt != null) {
                final int salt = info.salt;
                ChatFormatting saltColor = info.matchesVanillaSalt ? ChatFormatting.YELLOW : ChatFormatting.WHITE;

                source.sendSuccess(() -> Component.literal("Salt: " + salt)
                    .withStyle(saltColor), false);

                if (info.matchesVanillaSalt) {
                    source.sendSuccess(() -> Component.literal("  ⚠ WARNING: Same salt as minecraft:villages!")
                        .withStyle(ChatFormatting.YELLOW), false);
                    source.sendSuccess(() -> Component.literal("  This can cause overlapping structures")
                        .withStyle(ChatFormatting.GRAY), false);
                } else {
                    source.sendSuccess(() -> Component.literal("  Unique salt (no conflict with vanilla villages)")
                        .withStyle(ChatFormatting.GRAY), false);
                }
                source.sendSuccess(() -> Component.literal(""), false);
            }

            // Spawn frequency
            if (info.spacing != null) {
                double avgChunks = info.getAverageChunksPerStructure();
                int avgBlocks = (int)(avgChunks * 16 * 16);

                source.sendSuccess(() -> Component.literal("Spawn Frequency:")
                    .withStyle(ChatFormatting.AQUA), false);
                source.sendSuccess(() -> Component.literal("  ~1 structure per " +
                    String.format("%.0f", avgChunks) + " chunks")
                    .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal("  ~1 structure per " +
                    String.format("%,d", avgBlocks) + " blocks²")
                    .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(""), false);
            }

            // MVS Status
            source.sendSuccess(() -> Component.literal("MVS Status:")
                .withStyle(ChatFormatting.AQUA), false);

            if (info.isBlocked) {
                source.sendSuccess(() -> Component.literal("  ⛔ BLOCKED - Structure set will not spawn")
                    .withStyle(ChatFormatting.RED), false);
            } else if (info.isIntercepted) {
                source.sendSuccess(() -> Component.literal("  ⚡ INTERCEPTED - MVS filter-first strategy")
                    .withStyle(ChatFormatting.YELLOW), false);
            } else {
                source.sendSuccess(() -> Component.literal("  ○ Not controlled by MVS (vanilla behavior)")
                    .withStyle(ChatFormatting.GRAY), false);
            }
            source.sendSuccess(() -> Component.literal(""), false);

            // Structures in set
            int structureCount = info.structures.size();
            source.sendSuccess(() -> Component.literal("Structures in Set: " + structureCount)
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Show structures (limit unless full)
            int maxToShow = showFull ? Integer.MAX_VALUE : 15;
            int shown = 0;
            for (StructureSetAnalyzer.StructureEntry entry : info.structures) {
                if (shown >= maxToShow) break;

                final String line = String.format("  %s (weight: %d, %.1f%%)",
                    entry.id, entry.weight, entry.percentage);

                source.sendSuccess(() -> Component.literal(line)
                    .withStyle(ChatFormatting.WHITE), false);
                shown++;
            }

            // Show "... and X more" link if needed
            if (!showFull && structureCount > maxToShow) {
                final int remaining = structureCount - maxToShow;

                Component expandLink = Component.literal("  ... and " + remaining + " more")
                    .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/mvs structure set \"" + structureSetId + "\" full"
                        ))
                        .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to show all " + structureCount + " structures")
                        ))
                    );

                source.sendSuccess(() -> expandLink, false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in structure set command", e);
            return 0;
        }
    }
}
