package com.rhett.multivillageselector.commands.structure;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /mvs structure set <structure_set> command
 * Displays detailed information about a structure set
 */
public class StructureSetCommands {

    // Minecraft's villages salt for comparison
    private static final int VANILLA_VILLAGES_SALT = 10387312;

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
            var structureSetRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE_SET);
            var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

            // Get the structure set
            ResourceLocation setLocation = ResourceLocation.parse(structureSetId);
            var structureSet = structureSetRegistry.get(setLocation);

            if (structureSet == null) {
                source.sendFailure(Component.literal("Structure set not found: " + structureSetId));
                return 0;
            }

            // Header
            String headerSuffix = showFull ? " (full)" : "";
            source.sendSuccess(() -> Component.literal("=== Structure Set: " + structureSetId + headerSuffix + " ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Get placement info
            StructurePlacement placement = structureSet.placement();

            // --- PLACEMENT TYPE ---
            source.sendSuccess(() -> Component.literal("Placement Type: " + placement.type().toString())
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // --- SPACING, SEPARATION, SALT (if RandomSpread) ---
            if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
                int spacing = randomSpread.spacing();
                int separation = randomSpread.separation();

                // Get salt via mixin accessor (cross-platform)
                int salt = ((com.rhett.multivillageselector.mixin.StructurePlacementAccessor) randomSpread).invokeSalt();

                source.sendSuccess(() -> Component.literal("Spacing: " + spacing + " chunks")
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal("  Structure attempts occur every " + spacing + " chunks")
                    .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(""), false);

                source.sendSuccess(() -> Component.literal("Separation: " + separation + " chunks")
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal("  Minimum distance between structures in this set")
                    .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(""), false);

                // Salt with comparison
                final int finalSalt = salt;
                boolean matchesVanillaSalt = (salt == VANILLA_VILLAGES_SALT);
                ChatFormatting saltColor = matchesVanillaSalt ? ChatFormatting.YELLOW : ChatFormatting.WHITE;

                source.sendSuccess(() -> Component.literal("Salt: " + finalSalt)
                    .withStyle(saltColor), false);

                if (matchesVanillaSalt) {
                    source.sendSuccess(() -> Component.literal("  ⚠ WARNING: Same salt as minecraft:villages!")
                        .withStyle(ChatFormatting.YELLOW), false);
                    source.sendSuccess(() -> Component.literal("  This can cause overlapping structures")
                        .withStyle(ChatFormatting.GRAY), false);
                } else {
                    source.sendSuccess(() -> Component.literal("  Unique salt (no conflict with vanilla villages)")
                        .withStyle(ChatFormatting.GRAY), false);
                }
                source.sendSuccess(() -> Component.literal(""), false);

                // Spawn frequency calculation
                // Minecraft uses a square grid: spacing × spacing chunks per attempt
                double averageChunksPerStructure = spacing * spacing;
                int averageBlocksPerStructure = (int)(averageChunksPerStructure * 16 * 16);

                source.sendSuccess(() -> Component.literal("Spawn Frequency:")
                    .withStyle(ChatFormatting.AQUA), false);
                source.sendSuccess(() -> Component.literal("  ~1 structure per " +
                    String.format("%.0f", averageChunksPerStructure) + " chunks")
                    .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal("  ~1 structure per " +
                    String.format("%,d", averageBlocksPerStructure) + " blocks²")
                    .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(""), false);
            }

            // --- MVS STATUS ---
            boolean isBlocked = MVSConfig.blockStructureSets.contains(structureSetId);
            boolean isIntercepted = MVSConfig.interceptStructureSets.contains(structureSetId);

            source.sendSuccess(() -> Component.literal("MVS Status:")
                .withStyle(ChatFormatting.AQUA), false);

            if (isBlocked) {
                source.sendSuccess(() -> Component.literal("  ⛔ BLOCKED - Structure set will not spawn")
                    .withStyle(ChatFormatting.RED), false);
            } else if (isIntercepted) {
                source.sendSuccess(() -> Component.literal("  ⚡ INTERCEPTED - MVS filter-first strategy")
                    .withStyle(ChatFormatting.YELLOW), false);
            } else {
                source.sendSuccess(() -> Component.literal("  ○ Not controlled by MVS (vanilla behavior)")
                    .withStyle(ChatFormatting.GRAY), false);
            }
            source.sendSuccess(() -> Component.literal(""), false);

            // --- STRUCTURES IN SET ---
            var structures = structureSet.structures();
            int structureCount = structures.size();

            source.sendSuccess(() -> Component.literal("Structures in Set: " + structureCount)
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Collect structure info
            List<StructureEntry> entries = new ArrayList<>();
            for (var entry : structures) {
                var structureHolder = entry.structure();
                var structure = structureHolder.value();
                int weight = entry.weight();

                // Get structure ID
                ResourceLocation structureId = structureRegistry.getKey(structure);
                if (structureId != null) {
                    entries.add(new StructureEntry(structureId.toString(), weight));
                }
            }

            // Calculate total weight
            int totalWeight = entries.stream().mapToInt(e -> e.weight).sum();

            // Show structures (limit to 5 unless full)
            int maxToShow = showFull ? Integer.MAX_VALUE : 15;
            int shown = 0;
            for (StructureEntry entry : entries) {
                if (shown >= maxToShow) break;

                double percentage = totalWeight > 0 ? (entry.weight * 100.0 / totalWeight) : 0;
                final String line = String.format("  %s (weight: %d, %.1f%%)",
                    entry.id, entry.weight, percentage);

                source.sendSuccess(() -> Component.literal(line)
                    .withStyle(ChatFormatting.WHITE), false);
                shown++;
            }

            // Show "... and X more" link if needed (only in truncated mode)
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

    /**
     * Helper class for structure entries
     */
    private static class StructureEntry {
        final String id;
        final int weight;

        StructureEntry(String id, int weight) {
            this.id = id;
            this.weight = weight;
        }
    }
}
