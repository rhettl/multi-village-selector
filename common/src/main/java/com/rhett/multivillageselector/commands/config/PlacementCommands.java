package com.rhett.multivillageselector.commands.config;

import com.mojang.brigadier.context.CommandContext;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.ExclusionZone;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.util.ConfigFileEditor;
import com.rhett.multivillageselector.util.PlacementResolver;
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
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Handles /mvs config fill-placements command.
 * Reads placement values from registry and modifies config file in place.
 */
public class PlacementCommands {

    private static final Path CONFIG_PATH = Paths.get("config/multivillageselector.json5");

    /**
     * Execute /mvs config fill-placements
     * Reads placement values from registry and updates config file in place.
     */
    public static int executeFillPlacements(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            // Check config file exists
            if (!Files.exists(CONFIG_PATH)) {
                source.sendFailure(Component.literal("Config file not found: " + CONFIG_PATH)
                    .withStyle(ChatFormatting.RED));
                source.sendSuccess(() -> Component.literal("Tip: Run /mvs generate to create a config first")
                    .withStyle(ChatFormatting.GRAY), false);
                return 0;
            }

            Registry<StructureSet> registry = source.getServer().registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET);

            List<String> interceptedSets = MVSConfig.interceptStructureSets;

            if (interceptedSets.isEmpty()) {
                source.sendFailure(Component.literal("No structure sets configured in intercept_structure_sets")
                    .withStyle(ChatFormatting.RED));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Reading placement values from registry...")
                .withStyle(ChatFormatting.YELLOW), false);

            // Build placement section content using ConfigFileEditor
            StringBuilder placementContent = new StringBuilder();
            placementContent.append(ConfigFileEditor.buildPlacementSectionStart());

            int found = 0;
            int notFound = 0;

            for (String setId : interceptedSets) {
                try {
                    ResourceLocation location = ResourceLocation.parse(setId);
                    StructureSet structureSet = registry.get(location);

                    if (structureSet == null) {
                        notFound++;
                        final String finalSetId = setId;
                        source.sendSuccess(() -> Component.literal("  ⚠ " + finalSetId + " - not found")
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                        placementContent.append("    // \"").append(setId).append("\": { }, // NOT FOUND\n");
                        continue;
                    }

                    StructurePlacement placement = structureSet.placement();

                    if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
                        found++;
                        int spacing = randomSpread.spacing();
                        int separation = randomSpread.separation();
                        String spreadType = randomSpread.spreadType() == RandomSpreadType.TRIANGULAR
                            ? "triangular" : "linear";
                        int salt = getSaltViaMixin(placement);
                        var exclusionZone = getExclusionZoneViaMixin(placement);

                        final String finalSetId = setId;
                        final int finalSpacing = spacing;
                        final int finalSeparation = separation;
                        final int finalSalt = salt;
                        final String finalSpreadType = spreadType;

                        String exclusionInfo = exclusionZone != null
                            ? ", exclusion_zone=" + exclusionZone.otherSet + ":" + exclusionZone.chunkCount
                            : "";

                        source.sendSuccess(() -> Component.literal("  ✓ " + finalSetId + ": ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal("spacing=" + finalSpacing +
                                ", separation=" + finalSeparation +
                                ", salt=" + finalSalt +
                                ", spread_type=" + finalSpreadType + exclusionInfo)
                                .withStyle(ChatFormatting.GRAY)), false);

                        // Use ConfigFileEditor to build the entry
                        ConfigFileEditor.ExclusionZoneInfo zoneInfo = exclusionZone != null
                            ? new ConfigFileEditor.ExclusionZoneInfo(exclusionZone.otherSet, exclusionZone.chunkCount)
                            : null;
                        placementContent.append(ConfigFileEditor.buildPlacementEntry(
                            setId, spacing, separation, salt, spreadType, zoneInfo));
                    } else {
                        notFound++;
                        String placementType = placement.getClass().getSimpleName();
                        final String finalSetId = setId;
                        source.sendSuccess(() -> Component.literal("  ⚠ " + finalSetId + " - unsupported: " + placementType)
                            .withStyle(ChatFormatting.YELLOW), false);
                        placementContent.append("    // \"").append(setId).append("\": { }, // Unsupported: ").append(placementType).append("\n");
                    }
                } catch (Exception e) {
                    notFound++;
                    final String finalSetId = setId;
                    source.sendSuccess(() -> Component.literal("  ✗ " + finalSetId + " - error")
                        .withStyle(ChatFormatting.RED), false);
                    placementContent.append("    // \"").append(setId).append("\": { }, // Error\n");
                }
            }

            placementContent.append(ConfigFileEditor.buildPlacementSectionEnd());

            if (found == 0) {
                source.sendFailure(Component.literal("No valid placements found to write")
                    .withStyle(ChatFormatting.RED));
                return 0;
            }

            // Read existing config
            String configContent = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);

            // Update config with placement section using ConfigFileEditor
            String updatedContent = ConfigFileEditor.insertOrReplacePlacement(configContent, placementContent.toString());

            // Write back
            Files.writeString(CONFIG_PATH, updatedContent, StandardCharsets.UTF_8);

            source.sendSuccess(() -> Component.literal(""), false);

            final int finalFound = found;
            final int finalNotFound = notFound;
            source.sendSuccess(() -> Component.literal("✅ Updated config with " + finalFound + " placement(s)")
                .withStyle(ChatFormatting.GREEN), false);

            if (finalNotFound > 0) {
                source.sendSuccess(() -> Component.literal("   (" + finalNotFound + " not found/unsupported)")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            }

            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("📄 ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("config/multivillageselector.json5")
                    .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(
                            ClickEvent.Action.COPY_TO_CLIPBOARD,
                            CONFIG_PATH.toAbsolutePath().toString()))
                        .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to copy path"))))), false);

            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("💡 Run /mvs config reload to apply changes")
                .withStyle(ChatFormatting.GRAY), false);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            MVSCommon.LOGGER.error("Error in fill-placements command", e);
            return 0;
        }
    }

    /**
     * Execute /mvs debug placement [structure_set]
     * Shows resolved placement values with source tracking.
     */
    public static int executeDebugPlacement(CommandContext<CommandSourceStack> context, String structureSetId) {
        CommandSourceStack source = context.getSource();

        try {
            Registry<StructureSet> registry = source.getServer().registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET);

            // If no specific set provided, show all intercepted sets
            List<String> setsToShow = (structureSetId != null)
                ? List.of(structureSetId)
                : MVSConfig.interceptStructureSets;

            if (setsToShow.isEmpty()) {
                source.sendFailure(Component.literal("No structure sets to inspect")
                    .withStyle(ChatFormatting.RED));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("=== Resolved Placement Values ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal("Priority: Config > Registry > Defaults")
                .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal(""), false);

            for (String setId : setsToShow) {
                final String finalSetId = setId;

                PlacementResolver.ResolutionResult result =
                    PlacementResolver.resolveWithWarnings(setId, registry);

                PlacementResolver.ResolvedPlacement resolved = result.placement;

                // Show warnings if any
                if (result.hasWarnings()) {
                    for (String warning : result.warnings) {
                        final String finalWarning = warning;
                        source.sendSuccess(() -> Component.literal("  ⚠ " + finalWarning)
                            .withStyle(ChatFormatting.YELLOW), false);
                    }
                }

                // Header
                source.sendSuccess(() -> Component.literal("📦 " + finalSetId)
                    .withStyle(ChatFormatting.AQUA), false);

                // Show each resolved value with source
                source.sendSuccess(() -> Component.literal("   spacing: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(String.valueOf(resolved.spacing))
                        .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (" + resolved.spacingSource + ")")
                        .withStyle(getSourceColor(resolved.spacingSource))), false);

                source.sendSuccess(() -> Component.literal("   separation: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(String.valueOf(resolved.separation))
                        .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (" + resolved.separationSource + ")")
                        .withStyle(getSourceColor(resolved.separationSource))), false);

                source.sendSuccess(() -> Component.literal("   salt: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(String.valueOf(resolved.salt))
                        .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (" + resolved.saltSource + ")")
                        .withStyle(getSourceColor(resolved.saltSource))), false);

                source.sendSuccess(() -> Component.literal("   spread_type: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(resolved.spreadType.name().toLowerCase())
                        .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (" + resolved.spreadTypeSource + ")")
                        .withStyle(getSourceColor(resolved.spreadTypeSource))), false);

                source.sendSuccess(() -> Component.literal(""), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            MVSCommon.LOGGER.error("Error in debug placement command", e);
            return 0;
        }
    }

    /**
     * Get salt via mixin accessor (protected in StructurePlacement).
     * Uses mixin for cross-platform Fabric/NeoForge compatibility.
     */
    private static int getSaltViaMixin(StructurePlacement placement) {
        return ((com.rhett.multivillageselector.mixin.StructurePlacementAccessor) placement).invokeSalt();
    }

    /**
     * Get exclusion zone via mixin accessor.
     * Converts vanilla's ExclusionZone to our config ExclusionZone format.
     */
    private static ExclusionZone getExclusionZoneViaMixin(StructurePlacement placement) {
        var vanillaZone = ((com.rhett.multivillageselector.mixin.StructurePlacementAccessor) placement)
            .invokeExclusionZone();

        if (vanillaZone.isEmpty()) {
            return null;
        }

        StructurePlacement.ExclusionZone zone = vanillaZone.get();
        String otherSetId = zone.otherSet().unwrapKey()
            .map(k -> k.location().toString())
            .orElse(null);

        if (otherSetId == null) {
            return null;
        }

        return new ExclusionZone(otherSetId, zone.chunkCount());
    }

    /**
     * Get color for source indicator.
     */
    private static ChatFormatting getSourceColor(String source) {
        return switch (source) {
            case "config" -> ChatFormatting.LIGHT_PURPLE;
            case "registry" -> ChatFormatting.AQUA;
            case "default" -> ChatFormatting.DARK_GRAY;
            default -> ChatFormatting.GRAY;
        };
    }
}
