package com.rhett.multivillageselector.commands.structure;

import com.mojang.brigadier.context.CommandContext;
import com.rhett.multivillageselector.MVSCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handles /mvs nearby command
 */
public class NearbyCommands {

    /**
     * Structure info for sorting and display
     */
    private static class StructureInfo {
        final String id;
        final int x;
        final int y;
        final int z;
        final int distance;

        StructureInfo(String id, int x, int y, int z, int distance) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
        }
    }

    public static int execute(CommandContext<CommandSourceStack> context) {
        return executeWithRadius(context, 500); // Default 500 blocks (like StructureEssentials)
    }

    public static int executeWithRadius(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();

        try {
            var entity = source.getEntity();
            if (entity == null) {
                source.sendFailure(Component.literal("This command must be run by a player"));
                return 0;
            }

            var level = source.getLevel();
            var pos = entity.blockPosition();
            var structureManager = level.structureManager();

            source.sendSuccess(() -> Component.literal("=== Nearby Structures (within " + radius + " blocks) ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            var registryAccess = source.getServer().registryAccess();
            var structureRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

            List<StructureInfo> nearbyStructures = new ArrayList<>();

            // Collect all structures within radius
            for (var entry : structureRegistry.entrySet()) {
                var structureKey = entry.getKey();
                var structure = entry.getValue();
                var structureId = structureKey.location().toString();

                var structureStart = structureManager.getStructureAt(pos, structure);
                if (structureStart.isValid()) {
                    // Get bounding box to get actual Y coordinate
                    var boundingBox = structureStart.getBoundingBox();
                    if (boundingBox != null) {
                        // Use center X/Z and top Y (+1 to spawn on top, not inside)
                        int centerX = (boundingBox.minX() + boundingBox.maxX()) / 2;
                        int centerY = boundingBox.maxY() + 1; // One block above structure top
                        int centerZ = (boundingBox.minZ() + boundingBox.maxZ()) / 2;

                        double distance = Math.sqrt(pos.distSqr(new net.minecraft.core.BlockPos(centerX, centerY, centerZ)));

                        if (distance <= radius) {
                            nearbyStructures.add(new StructureInfo(
                                structureId,
                                centerX,
                                centerY,
                                centerZ,
                                (int)distance
                            ));
                        }
                    }
                }
            }

            // Sort by distance (closest first)
            nearbyStructures.sort(Comparator.comparingInt(s -> s.distance));

            if (nearbyStructures.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No structures found within " + radius + " blocks")
                    .withStyle(ChatFormatting.GRAY), false);
            } else {
                source.sendSuccess(() -> Component.literal("Found " + nearbyStructures.size() + " structure(s):")
                    .withStyle(ChatFormatting.AQUA), false);
                source.sendSuccess(() -> Component.literal(""), false);

                // Show all structures (no limit)
                for (StructureInfo info : nearbyStructures) {
                    // Create clickable coordinates component
                    String teleportCommand = String.format("/tp @s %d %d %d", info.x, info.y, info.z);
                    String coordsText = String.format("[%d, %d, %d]", info.x, info.y, info.z);

                    Component coordsComponent = Component.literal(coordsText)
                        .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleportCommand))
                            .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to teleport to " + coordsText)
                            ))
                        );

                    // Create clickable structure ID (copy to clipboard)
                    Component structureIdComponent = Component.literal(info.id)
                        .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, info.id))
                            .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to copy: " + info.id)
                            ))
                        );

                    // Build full message: "  123 blocks: structure_name [x, y, z]"
                    Component message = Component.literal("  " + info.distance + " blocks: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(structureIdComponent)
                        .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                        .append(coordsComponent);

                    source.sendSuccess(() -> message, false);
                }
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in nearby command", e);
            return 0;
        }
    }
}
