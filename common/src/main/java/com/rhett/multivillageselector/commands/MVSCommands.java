package com.rhett.multivillageselector.commands;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.commands.biome.BiomeCommands;
import com.rhett.multivillageselector.commands.debug.DebugCommands;
import com.rhett.multivillageselector.commands.generate.GenerateCommands;
import com.rhett.multivillageselector.commands.model.CommandModels.*;
import com.rhett.multivillageselector.commands.config.ReloadCommands;
import com.rhett.multivillageselector.commands.profiler.ProfilerCommands;
import com.rhett.multivillageselector.commands.structure.NearbyCommands;
import com.rhett.multivillageselector.commands.structure.StructureCommands;
import com.rhett.multivillageselector.commands.structure.StructureSetCommands;
import com.rhett.multivillageselector.commands.test.TestCommands;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.util.StructureScanner;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Handles all /mvs commands for debugging and inspecting MVS configuration
 */
public class MVSCommands {

    private static final Gson GSON = new Gson();
    private static final String MINECRAFT_VILLAGES_SET = "minecraft:villages";
    private static final int TARGET_AVERAGE_WEIGHT = 25;
    private static final int VANILLA_SPACING = 34;

    /**
     * Register all MVS commands
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var mvsCommand = Commands.literal("mvs")
            .requires(source -> {
                // Allow in single-player or if OP level 2+
                return !source.getServer().isDedicatedServer() || source.hasPermission(2);
            })
            .executes(MVSCommands::executeHelpCommand) // No args: show help
            .then(Commands.literal("help")
                .executes(MVSCommands::executeHelpCommand)
            )
            .then(Commands.literal("info")
                .executes(MVSCommands::executeInfoCommand)
            )
            .then(Commands.literal("generate")
                .executes(GenerateCommands::execute)
            )
            .then(Commands.literal("config")
                .then(Commands.literal("reload")
                    .executes(ReloadCommands::execute)
                )
            )
            .then(Commands.literal("biome")
                .executes(BiomeCommands::executeCurrent)
                .then(Commands.literal("tags")
                    .executes(BiomeCommands::executeTagsCurrent)
                    .then(Commands.argument("biome", StringArgumentType.greedyString())
                        .executes(BiomeCommands::executeTags)
                    )
                )
                .then(Commands.literal("by-tag")
                    .then(Commands.argument("tag", StringArgumentType.greedyString())
                        .executes(BiomeCommands::executeByTag)
                    )
                )
                .then(Commands.literal("similar")
                    .executes(BiomeCommands::executeSimilarCurrent)
                    .then(Commands.argument("biome", StringArgumentType.greedyString())
                        .executes(BiomeCommands::executeSimilar)
                    )
                )
            )
            .then(Commands.literal("test")
                .then(Commands.literal("biome")
                    .executes(TestCommands::executeBiomeCurrent)
                    .then(Commands.argument("biome", StringArgumentType.greedyString())
                        .executes(TestCommands::executeBiome)
                    )
                )
                .then(Commands.literal("structure")
                    .then(Commands.argument("structure", StringArgumentType.greedyString())
                        .executes(TestCommands::executeStructure)
                    )
                )
            )
            .then(Commands.literal("structure")
                .then(Commands.literal("list")
                    .executes(ctx -> StructureCommands.executeList(ctx, false))
                    .then(Commands.literal("full")
                        .executes(ctx -> StructureCommands.executeList(ctx, true))
                    )
                )
                .then(Commands.literal("biomes")
                    .then(Commands.argument("structure", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .executes(ctx -> StructureCommands.executeBiomes(ctx, false))
                        .then(Commands.literal("full")
                            .executes(ctx -> StructureCommands.executeBiomes(ctx, true))
                        )
                    )
                )
                .then(Commands.literal("test")
                    .then(Commands.argument("structure", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .then(Commands.argument("biome", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                            .executes(StructureCommands::executeTest)
                        )
                    )
                )
                .then(Commands.literal("nearby")
                    .executes(NearbyCommands::execute)
                    .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 500))
                        .executes(ctx -> NearbyCommands.executeWithRadius(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))
                    )
                )
                .then(Commands.literal("set")
                    .then(Commands.argument("structure_set", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .executes(ctx -> StructureSetCommands.executeSet(ctx, false))
                        .then(Commands.literal("full")
                            .executes(ctx -> StructureSetCommands.executeSet(ctx, true))
                        )
                    )
                )
            );

        // Conditionally add debug commands if debug_cmd is enabled
        if (MVSConfig.debugCmd) {
            mvsCommand = mvsCommand.then(Commands.literal("debug")
                .executes(DebugCommands::executeHelp)
                .then(Commands.literal("mod-scan")
                    .executes(ctx -> DebugCommands.executeModScan(ctx, false))
                    .then(Commands.literal("all")
                        .executes(ctx -> DebugCommands.executeModScan(ctx, true))
                    )
                )
                .then(Commands.literal("profiler")
                    .executes(ProfilerCommands::executeStats)
                    .then(Commands.literal("start")
                        .executes(ProfilerCommands::executeStart)
                    )
                    .then(Commands.literal("stop")
                        .executes(ProfilerCommands::executeStop)
                    )
                    .then(Commands.literal("stats")
                        .executes(ProfilerCommands::executeStats)
                    )
                )
            );
        }

        dispatcher.register(mvsCommand);
    }

    /**
     * Handle /mvs help command (or /mvs with no arguments)
     * Shows an overview of all available commands
     */
    private static int executeHelpCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("=== Multi Village Selector v0.3.0 ===")
            .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // /mvs info
        source.sendSuccess(() -> Component.literal("/mvs info")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Show MVS status and intercepted structure sets")
                .withStyle(ChatFormatting.GRAY)), false);

        // /mvs generate
        source.sendSuccess(() -> Component.literal("/mvs generate")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Generate config from installed mods")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal(""), false);

        // /mvs biome
        source.sendSuccess(() -> Component.literal("/mvs biome")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Show current biome info and tags")
                .withStyle(ChatFormatting.GRAY)), false);

        // /mvs biome tags <biome>
        source.sendSuccess(() -> Component.literal("/mvs biome tags [biome]")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - List biome tags")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal(""), false);

        // /mvs structure list
        source.sendSuccess(() -> Component.literal("/mvs structure list [full]")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - List structures in pool")
                .withStyle(ChatFormatting.GRAY)), false);

        // /mvs structure biomes
        source.sendSuccess(() -> Component.literal("/mvs structure biomes <id>")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Show biome rules for structure")
                .withStyle(ChatFormatting.GRAY)), false);

        // /mvs structure test
        source.sendSuccess(() -> Component.literal("/mvs structure test <structure> <biome>")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Test spawn eligibility")
                .withStyle(ChatFormatting.GRAY)), false);

        // /mvs structure set
        source.sendSuccess(() -> Component.literal("/mvs structure set <set_id>")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Inspect structure set")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal(""), false);

        // Debug commands note
        if (MVSConfig.debugCmd) {
            source.sendSuccess(() -> Component.literal("/mvs debug")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - Debug commands (mod-scan, profiler)")
                    .withStyle(ChatFormatting.GRAY)), false);
            source.sendSuccess(() -> Component.literal(""), false);
        }

        source.sendSuccess(() -> Component.literal("Tip: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/mvs info")
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" for quick status check")
                .withStyle(ChatFormatting.GRAY)), false);

        return 1;
    }

    /**
     * Handle /mvs info command
     * Shows MVS status: blocked sets, intercepted sets with spacing/salt, link to structure list
     */
    private static int executeInfoCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            var registryAccess = source.getServer().registryAccess();
            var structureSetRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);

            source.sendSuccess(() -> Component.literal("=== Multi Village Selector v0.3.0 ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Status
            source.sendSuccess(() -> Component.literal("Status: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MVSConfig.enabled ? "ENABLED" : "DISABLED")
                    .withStyle(MVSConfig.enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);

            source.sendSuccess(() -> Component.literal("Pool Size: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(MVSConfig.structurePool.size()))
                    .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" structures")
                    .withStyle(ChatFormatting.GRAY)), false);

            source.sendSuccess(() -> Component.literal(""), false);

            // Blocked structure sets
            source.sendSuccess(() -> Component.literal("Blocked Structure Sets: ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(String.valueOf(MVSConfig.blockStructureSets.size()))
                    .withStyle(ChatFormatting.WHITE)), false);

            if (!MVSConfig.blockStructureSets.isEmpty()) {
                for (String setId : MVSConfig.blockStructureSets) {
                    final String finalSetId = setId;
                    source.sendSuccess(() -> Component.literal("  ⛔ " + finalSetId)
                        .withStyle(ChatFormatting.DARK_RED), false);
                }
            }

            source.sendSuccess(() -> Component.literal(""), false);

            // Intercepted structure sets with spacing/salt
            source.sendSuccess(() -> Component.literal("Intercepted Structure Sets: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.valueOf(MVSConfig.interceptStructureSets.size()))
                    .withStyle(ChatFormatting.WHITE)), false);

            for (String setId : MVSConfig.interceptStructureSets) {
                final String finalSetId = setId;

                // Get spacing and salt info
                try {
                    ResourceLocation setLocation = ResourceLocation.parse(setId);
                    var structureSet = structureSetRegistry.get(setLocation);

                    if (structureSet != null) {
                        var placement = structureSet.placement();
                        if (placement instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement randomSpread) {
                            int spacing = randomSpread.spacing();
                            int separation = randomSpread.separation();

                            // Get salt via reflection
                            int salt = 0;
                            try {
                                var saltField = net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement.class.getDeclaredField("salt");
                                saltField.setAccessible(true);
                                salt = (int) saltField.get(randomSpread);
                            } catch (Exception ignored) {}

                            final int finalSalt = salt;
                            final int finalSpacing = spacing;
                            final int finalSeparation = separation;

                            // Clickable to run /mvs structure set <id>
                            Component setComponent = Component.literal("  ⚡ " + finalSetId)
                                .withStyle(net.minecraft.network.chat.Style.EMPTY
                                    .withColor(ChatFormatting.GOLD)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        "/mvs structure set " + finalSetId))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Click for details"))));

                            source.sendSuccess(() -> setComponent, false);
                            source.sendSuccess(() -> Component.literal("      spacing: " + finalSpacing + ", separation: " + finalSeparation + ", salt: " + finalSalt)
                                .withStyle(ChatFormatting.GRAY), false);
                        } else {
                            source.sendSuccess(() -> Component.literal("  ⚡ " + finalSetId)
                                .withStyle(ChatFormatting.GOLD), false);
                        }
                    } else {
                        source.sendSuccess(() -> Component.literal("  ⚠ " + finalSetId + " (not found)")
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                    }
                } catch (Exception e) {
                    source.sendSuccess(() -> Component.literal("  ⚡ " + finalSetId)
                        .withStyle(ChatFormatting.GOLD), false);
                }
            }

            source.sendSuccess(() -> Component.literal(""), false);

            // Link to structure list
            Component structureListLink = Component.literal("→ View Structure Pool")
                .withStyle(net.minecraft.network.chat.Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/mvs structure list"))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Run /mvs structure list"))));

            source.sendSuccess(() -> structureListLink, false);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in /mvs info command", e);
            return 0;
        }
    }

    // ========== v0.3.0 COMMENTED OUT - TODO: Rewrite biome/pools commands for tag-based system ==========

    /**
     * Handle /mvs generate command
     * Generates a smart config file based on installed mods and structures
     */
    public static int executeGenerateCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            source.sendSuccess(() -> Component.literal("=== Generating Smart Config ===")
                .withStyle(ChatFormatting.GOLD), false);

            // ===========================================
            // UNIFIED DATA COLLECTION (using unified scan)
            // ===========================================
            ScanResult scan = StructureScanner.scanAllStructures(source.getServer());

            // ===========================================
            // GENERATE CONFIG
            // ===========================================
            java.util.List<String> lines = generateV3ConfigLines(source.getServer(), scan);

            // Write to file (create directory if needed)
            java.nio.file.Path outputDir = java.nio.file.Paths.get("local/mvs");
            java.nio.file.Files.createDirectories(outputDir);
            final java.nio.file.Path outputFile = outputDir.resolve("multivillageselector.json5");
            java.nio.file.Files.write(outputFile, lines, java.nio.charset.StandardCharsets.UTF_8);

            // Send success
            final int modsDetected = scan.allStructures.values().stream()
                .map(s -> s.modId)
                .collect(java.util.stream.Collectors.toSet())
                .size();
            final int villagesDetected = scan.allStructures.size();
            final int structureSetsDetected = scan.structureSetInfo.size();

            source.sendSuccess(() -> Component.literal("Mods detected: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(modsDetected)).withStyle(ChatFormatting.GREEN)), false);

            source.sendSuccess(() -> Component.literal("Villages found: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(villagesDetected)).withStyle(ChatFormatting.GREEN)), false);

            source.sendSuccess(() -> Component.literal("Structure sets: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(structureSetsDetected)).withStyle(ChatFormatting.YELLOW)), false);

            source.sendSuccess(() -> Component.literal("File: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("local/mvs/multivillageselector.json5")
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.OPEN_FILE,
                        outputFile.toAbsolutePath().toString())))), false);

            source.sendSuccess(() -> Component.literal(""), false);

            // Next steps instructions
            source.sendSuccess(() -> Component.literal("💡 What to do next:")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal("  1. Click the file link above to review the config")
                .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("  2. Adjust village weights if desired (higher = more common)")
                .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("  3. Copy file to: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("config/multivillageselector.json5").withStyle(ChatFormatting.AQUA)), false);
            source.sendSuccess(() -> Component.literal("  4. Restart Minecraft")
                .withStyle(ChatFormatting.GRAY), false);

            source.sendSuccess(() -> Component.literal(""), false);

            // Documentation link
            source.sendSuccess(() -> Component.literal("📖 Need help? See: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Configuration Guide")
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                            "https://github.com/RhettL/multi-village-selector/blob/master/docs/Configuration.md"))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to open in browser"))))), false);

            MVSCommon.LOGGER.info("Generated config with {} mods, {} villages", modsDetected, villagesDetected);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error generating config: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error generating config", e);
            return 0;
        }
    }

    /**
     * Generate v0.3.0 config with structure_pool and normalized weights
     */
    private static java.util.List<String> generateV3ConfigLines(
            net.minecraft.server.MinecraftServer server,
            ScanResult scan) {

        java.util.List<String> lines = new java.util.ArrayList<>();

        // Header
        lines.add("// ===========================================");
        lines.add("//  Multi Village Selector - Generated Config (v0.3.0)");
        lines.add("//  Generated: " + java.time.LocalDateTime.now());
        lines.add("// ===========================================");
        lines.add("//");
        lines.add("// This config uses NORMALIZED WEIGHTS based on mod-intended spawn rates.");
        lines.add("// Weights have been adjusted to:");
        lines.add("//   1. Equalize representation across mods");
        lines.add("//   2. Preserve internal mod ratios (small:medium:large)");
        lines.add("//   3. Account for structure spacing (rarer structures = higher weights)");
        lines.add("//");
        lines.add("// ═══════════════════════════════════════════════════════════════");
        lines.add("//  📋 NEXT STEPS:");
        lines.add("//  1. Review weights below (higher = spawns more often)");
        lines.add("//  2. Use biome_frequency{} to control spawn density per biome");
        lines.add("//  3. Copy to: config/multivillageselector.json5");
        lines.add("//  4. Restart Minecraft");
        lines.add("// ═══════════════════════════════════════════════════════════════");
        lines.add("//");
        lines.add("// 📖 For complete documentation, see:");
        lines.add("//    https://github.com/RhettL/multi-village-selector/blob/master/docs/Configuration.md");
        lines.add("//");
        lines.add("// ═══════════════════════════════════════════════════════════════");
        lines.add("");
        lines.add("{");
        lines.add("  enabled: true,");
        lines.add("");

        // Group ALL filtered structures by structure_set (core + likely + questionable)
        // This ensures consistent per-mod normalization across all categories
        Map<String, List<StructureInfo>> structuresBySet = new TreeMap<>();
        List<StructureInfo> allFilteredStructures = new ArrayList<>();
        allFilteredStructures.addAll(scan.coreStructures);
        allFilteredStructures.addAll(scan.likelyStructures);
        allFilteredStructures.addAll(scan.questionableStructures);

        for (StructureInfo info : allFilteredStructures) {
            String setId = info.getFinalSet();
            if (setId != null && !setId.equals("NONE (uncategorized)")) {
                structuresBySet.computeIfAbsent(setId, k -> new ArrayList<>()).add(info);
            }
        }

        // Calculate normalized weights PER MOD within each structure_set
        // This gives each mod equal representation while preserving internal ratios
        Map<String, Integer> normalizedWeightsByStructure = new HashMap<>();

        for (Map.Entry<String, List<StructureInfo>> entry : structuresBySet.entrySet()) {
            String setId = entry.getKey();
            List<StructureInfo> setStructures = entry.getValue();
            StructureSetInfo setInfo = scan.structureSetInfo.get(setId);

            // Group structures by mod within this structure_set
            Map<String, List<StructureInfo>> structuresByMod = new TreeMap<>();
            for (StructureInfo info : setStructures) {
                structuresByMod.computeIfAbsent(info.modId, k -> new ArrayList<>()).add(info);
            }

            // Normalize each mod separately
            for (Map.Entry<String, List<StructureInfo>> modEntry : structuresByMod.entrySet()) {
                String modId = modEntry.getKey();
                List<StructureInfo> modStructures = modEntry.getValue();

                // Calculate normalized weights for this mod
                Map<Integer, Integer> normalizedWeights = StructureScanner.calculateNormalizedWeights(modStructures, setInfo);

                MVSCommon.LOGGER.debug("Normalizing mod {} in set {}: {} structures, {} weight mappings",
                    modId, setId, modStructures.size(), normalizedWeights.size());

                // Store per-structure normalized weights
                for (StructureInfo info : modStructures) {
                    Integer originalWeight = info.getFinalWeight();
                    if (originalWeight != null && normalizedWeights.containsKey(originalWeight)) {
                        Integer normalized = normalizedWeights.get(originalWeight);
                        normalizedWeightsByStructure.put(info.id, normalized);
                        MVSCommon.LOGGER.debug("  {} {} -> {}", info.id, originalWeight, normalized);
                    } else {
                        // Fallback: structures with missing/invalid weights get default normalized weight
                        int fallbackWeight = 25; // TARGET_AVERAGE_WEIGHT
                        normalizedWeightsByStructure.put(info.id, fallbackWeight);
                        MVSCommon.LOGGER.debug("  {} assigned fallback weight {} (originalWeight={})",
                            info.id, fallbackWeight, originalWeight);
                    }
                }
            }
        }

        // Pre-group likely and questionable structures by structure_set (needed for block_structure_sets)
        Map<String, List<StructureInfo>> likelyBySet = new TreeMap<>();
        for (StructureInfo info : scan.likelyStructures) {
            String setId = info.getFinalSet();
            if (setId != null && !setId.equals("NONE (uncategorized)")) {
                likelyBySet.computeIfAbsent(setId, k -> new ArrayList<>()).add(info);
            }
        }

        Map<String, List<StructureInfo>> questionableBySet = new TreeMap<>();
        for (StructureInfo info : scan.questionableStructures) {
            String setId = info.getFinalSet();
            if (setId != null && !setId.equals("NONE (uncategorized)")) {
                questionableBySet.computeIfAbsent(setId, k -> new ArrayList<>()).add(info);
            }
        }

        // block_structure_sets section - block other *:villages sets to prevent duplicate grids
        // Their structures are still in the pool, spawned via minecraft:villages grid
        lines.add("  // Structure sets to block (prevents double-spawning, MVS controls these instead)");
        lines.add("  block_structure_sets: [");

        // Core: other *:villages sets
        boolean hasCore = false;
        for (String setId : structuresBySet.keySet()) {
            if (!setId.equals(MINECRAFT_VILLAGES_SET) && setId.endsWith(":villages")) {
                lines.add("    \"" + setId + "\",");
                hasCore = true;
            }
        }

        // Likely: custom structure_sets that appear to be villages
        if (!likelyBySet.isEmpty()) {
            if (hasCore) lines.add("");
            lines.add("    // LIKELY village sets (review - remove if incorrect)");
            for (String setId : likelyBySet.keySet()) {
                lines.add("    \"" + setId + "\",");
            }
        }

        // Questionable: uncertain structure_sets (commented out)
        if (!questionableBySet.isEmpty()) {
            lines.add("");
            lines.add("    // QUESTIONABLE (uncomment to use - verify these are actually villages)");
            for (String setId : questionableBySet.keySet()) {
                lines.add("    // \"" + setId + "\",");
            }
        }

        lines.add("  ],");
        lines.add("");

        // intercept_structure_sets section - only minecraft:villages
        lines.add("  // Structure sets to intercept (MVS controls structure selection)");
        lines.add("  intercept_structure_sets: [");
        lines.add("    \"" + MINECRAFT_VILLAGES_SET + "\",");
        lines.add("  ],");
        lines.add("");

        // structure_pool section
        lines.add("  // Structure pool - MVS selects from this list");
        lines.add("  // biomes controls spawn weights per biome (same pattern system as biome_frequency)");
        lines.add("  //");
        lines.add("  // Supports: Direct IDs (\"minecraft:plains\": 10), Tags (\"#minecraft:is_plains\": 10), Patterns (\"minecraft:*\": 5)");
        lines.add("  // Specificity: More specific patterns win, literal IDs beat tags, tie-breaker = higher weight");
        lines.add("  structure_pool: [");
        lines.add("");

        // Output structures grouped by mod (ONLY filtered village structures)
        Map<String, List<StructureInfo>> structuresByMod = new TreeMap<>();
        for (StructureInfo info : scan.coreStructures) {
            structuresByMod.computeIfAbsent(info.modId, k -> new ArrayList<>()).add(info);
        }

        for (Map.Entry<String, List<StructureInfo>> modEntry : structuresByMod.entrySet()) {
            String modId = modEntry.getKey();
            List<StructureInfo> modStructures = modEntry.getValue();

            lines.add("    // === " + modId.toUpperCase() + " ===");
            lines.add("");

            // Group by structure_set within mod
            Map<String, List<StructureInfo>> modStructuresBySet = new TreeMap<>();
            for (StructureInfo info : modStructures) {
                String setId = info.getFinalSet();
                modStructuresBySet.computeIfAbsent(setId, k -> new ArrayList<>()).add(info);
            }

            for (Map.Entry<String, List<StructureInfo>> setEntry : modStructuresBySet.entrySet()) {
                String setId = setEntry.getKey();
                List<StructureInfo> setStructures = setEntry.getValue();
                setStructures.sort(Comparator.comparing(s -> s.id));

                lines.add("    // " + setId);

                // Output each structure
                for (StructureInfo info : setStructures) {
                    Integer normalizedWeight = normalizedWeightsByStructure.get(info.id);
                    if (normalizedWeight == null) continue;

                    String biomeTag = info.getFinalBiome();

                    // Expand "direct_biomes" into actual biome IDs
                    if ("direct_biomes".equals(biomeTag)) {
                        Map<String, Integer> expandedBiomes = expandDirectBiomes(info.id, normalizedWeight, server);
                        if (expandedBiomes.isEmpty()) {
                            biomeTag = "#minecraft:is_overworld"; // Fallback if expansion fails
                            lines.add(String.format("    { structure: \"%s\",", info.id));
                            lines.add("      biomes: {");
                            lines.add(String.format("        \"%s\": %d,", biomeTag, normalizedWeight));
                            lines.add("      } },");
                        } else {
                            lines.add(String.format("    { structure: \"%s\",", info.id));
                            lines.add("      biomes: {");
                            for (Map.Entry<String, Integer> biomeEntry : expandedBiomes.entrySet()) {
                                lines.add(String.format("        \"%s\": %d,", biomeEntry.getKey(), biomeEntry.getValue()));
                            }
                            lines.add("      } },");
                        }
                    } else {
                        if (biomeTag == null || biomeTag.equals("unknown")) {
                            biomeTag = "#minecraft:is_overworld"; // Default fallback
                        }
                        lines.add(String.format("    { structure: \"%s\",", info.id));
                        lines.add("      biomes: {");
                        lines.add(String.format("        \"%s\": %d,", biomeTag, normalizedWeight));
                        lines.add("      } },");
                    }
                }

                lines.add("");
            }
        }

        // === ADD LIKELY + QUESTIONABLE STRUCTURES ===
        // These are already grouped in likelyBySet/questionableBySet (populated earlier for block_structure_sets)

        // Add LIKELY structures (uncommented, structure_set already blocked above)
        if (!likelyBySet.isEmpty()) {
            lines.add("    // === LIKELY VILLAGE MODS (review and remove if incorrect) ===");
            lines.add("    // These appear to be villages. Their structure_sets are blocked above.");
            lines.add("");

            for (Map.Entry<String, List<StructureInfo>> setEntry : likelyBySet.entrySet()) {
                String setId = setEntry.getKey();
                List<StructureInfo> setStructures = setEntry.getValue();

                lines.add("    // " + setId + " (" + setStructures.size() + " structure(s))");

                // Use pre-calculated normalized weights (same as core structures)
                for (StructureInfo info : setStructures) {
                    Integer normalizedWeight = normalizedWeightsByStructure.get(info.id);
                    if (normalizedWeight == null) continue; // Skip if not normalized

                    String biomeTag = info.getFinalBiome();

                    // Expand "direct_biomes" into actual biome IDs
                    if ("direct_biomes".equals(biomeTag)) {
                        Map<String, Integer> expandedBiomes = expandDirectBiomes(info.id, normalizedWeight, server);
                        if (expandedBiomes.isEmpty()) {
                            biomeTag = "#minecraft:is_overworld"; // Fallback if expansion fails
                            lines.add(String.format("    { structure: \"%s\",", info.id));
                            lines.add("      biomes: {");
                            lines.add(String.format("        \"%s\": %d,", biomeTag, normalizedWeight));
                            lines.add("      } },");
                        } else {
                            lines.add(String.format("    { structure: \"%s\",", info.id));
                            lines.add("      biomes: {");
                            for (Map.Entry<String, Integer> biomeEntry : expandedBiomes.entrySet()) {
                                lines.add(String.format("        \"%s\": %d,", biomeEntry.getKey(), biomeEntry.getValue()));
                            }
                            lines.add("      } },");
                        }
                    } else {
                        if (biomeTag == null || biomeTag.equals("unknown")) {
                            biomeTag = "#minecraft:is_overworld"; // Default fallback
                        }
                        lines.add(String.format("    { structure: \"%s\",", info.id));
                        lines.add("      biomes: {");
                        lines.add(String.format("        \"%s\": %d,", biomeTag, normalizedWeight));
                        lines.add("      } },");
                    }
                }

                lines.add("");
            }
        }

        // Add QUESTIONABLE structures (commented out, structure_set also commented in block list)
        if (!questionableBySet.isEmpty()) {
            lines.add("    // === UNCERTAIN STRUCTURES (verify before enabling) ===");
            lines.add("    // These may or may not be villages. To enable:");
            lines.add("    // 1. Uncomment the structure entries below");
            lines.add("    // 2. Uncomment the structure_set in block_structure_sets above");
            lines.add("");

            for (Map.Entry<String, List<StructureInfo>> setEntry : questionableBySet.entrySet()) {
                String setId = setEntry.getKey();
                List<StructureInfo> setStructures = setEntry.getValue();

                lines.add("    // " + setId + " (" + setStructures.size() + " structure(s))");

                // Use pre-calculated normalized weights (same as core structures)
                for (StructureInfo info : setStructures) {
                    Integer normalizedWeight = normalizedWeightsByStructure.get(info.id);
                    if (normalizedWeight == null) continue; // Skip if not normalized

                    String biomeTag = info.getFinalBiome();

                    // Expand "direct_biomes" into actual biome IDs
                    if ("direct_biomes".equals(biomeTag)) {
                        Map<String, Integer> expandedBiomes = expandDirectBiomes(info.id, normalizedWeight, server);
                        if (expandedBiomes.isEmpty()) {
                            biomeTag = "#minecraft:is_overworld"; // Fallback if expansion fails
                            lines.add(String.format("    // { structure: \"%s\",", info.id));
                            lines.add("    //   biomes: {");
                            lines.add(String.format("    //     \"%s\": %d,", biomeTag, normalizedWeight));
                            lines.add("    //   } },");
                        } else {
                            lines.add(String.format("    // { structure: \"%s\",", info.id));
                            lines.add("    //   biomes: {");
                            for (Map.Entry<String, Integer> biomeEntry : expandedBiomes.entrySet()) {
                                lines.add(String.format("    //     \"%s\": %d,", biomeEntry.getKey(), biomeEntry.getValue()));
                            }
                            lines.add("    //   } },");
                        }
                    } else {
                        if (biomeTag == null || biomeTag.equals("unknown")) {
                            biomeTag = "#minecraft:is_overworld"; // Default fallback
                        }
                        lines.add(String.format("    // { structure: \"%s\",", info.id));
                        lines.add("    //   biomes: {");
                        lines.add(String.format("    //     \"%s\": %d,", biomeTag, normalizedWeight));
                        lines.add("    //   } },");
                    }
                }

                lines.add("");
            }
        }

        lines.add("  ],");
        lines.add("");
        lines.add("  // Blacklist specific structures from non-intercepted sources");
        lines.add("  blacklisted_structures: [");
        lines.add("    // Example: \"bca:village/witch_hut\",");
        lines.add("  ],");
        lines.add("");
        lines.add("  // ## Biome-specific spawn frequency (0.0-1.0, applied before structure selection)");
        lines.add("  // Controls % chance of structure spawn attempt per biome");
        lines.add("  //");
        lines.add("  // Supports three formats:");
        lines.add("  // 1. Direct biome ID: \"minecraft:plains\": 1.0 (most specific, easiest for beginners)");
        lines.add("  // 2. Biome tags: \"#minecraft:is_ocean\": 0.2 (affects all matching biomes)");
        lines.add("  // 3. Patterns: \"minecraft:*\": 0.7, \"#terralith:*\": 0.85 (wildcards supported)");
        lines.add("  //");
        lines.add("  // Specificity scoring (more specific wins):");
        lines.add("  // - Base: 20, No #: +1, Literals before colon: +2, Literals after colon: +2, Each *: -5");
        lines.add("  // - Examples: \"minecraft:plains\" = 25, \"#minecraft:is_plains\" = 24, \"#*craft*:*village*\" = 4, \"*:*\" = -10");
        lines.add("  // - Tie-breaker: Higher frequency value wins");
        lines.add("  biome_frequency: {");
        lines.add("    // \"*:*\": 1.0,                      // Default: 100% spawn rate (implied if not set)");
        lines.add("    // \"minecraft:warm_ocean\": 0.5,     // Warm ocean specifically: 50% spawn rate");
        lines.add("    // \"#minecraft:is_ocean\": 0.2,      // All ocean biomes: 20% spawn rate");
        lines.add("    // \"#terralith:*\": 0.85,            // All Terralith biomes: 85% spawn rate");
        lines.add("  },");
        lines.add("");
        lines.add("  // ## Debugging functionality");
        lines.add("  // Auto-enabled in dev environments (MVS_DEV=true environment variable)");

        // Detect dev environment via MVS_DEV environment variable
        boolean isDevEnvironment = "true".equalsIgnoreCase(System.getenv("MVS_DEV"));
        String debugCmdValue = isDevEnvironment ? "true" : "false";
        String debugLoggingValue = isDevEnvironment ? "true" : "false";

        if (isDevEnvironment) {
            MVSCommon.LOGGER.info("MVS: Dev environment detected (MVS_DEV=true), enabling debug flags in generated config");
        }

        lines.add("  debug_cmd: " + debugCmdValue + ",");
        lines.add("  debug_logging: " + debugLoggingValue + ",");
        lines.add("}");

        return lines;
    }

    /**
     * Expand "direct_biomes" placeholder into actual biome IDs from structure definition
     *
     * @param structureId The structure to expand biomes for
     * @param weight The weight to apply to each biome
     * @param server Server instance for registry access
     * @return Map of biome ID -> weight for all biomes this structure can spawn in
     */
    private static Map<String, Integer> expandDirectBiomes(
            String structureId,
            int weight,
            net.minecraft.server.MinecraftServer server) {

        Map<String, Integer> result = new LinkedHashMap<>();

        try {
            var registryAccess = server.registryAccess();
            var structureRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            var biomeRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);

            ResourceLocation structureLoc = ResourceLocation.parse(structureId);
            var structure = structureRegistry.get(structureLoc);

            if (structure == null) {
                MVSCommon.LOGGER.debug("Structure not found in registry: {}", structureId);
                return result;
            }

            var biomeHolderSet = structure.biomes();

            // Iterate through all biomes and check if structure can spawn there
            for (var biomeEntry : biomeRegistry.entrySet()) {
                var biomeHolder = biomeRegistry.wrapAsHolder(biomeEntry.getValue());
                if (biomeHolderSet.contains(biomeHolder)) {
                    String biomeId = biomeEntry.getKey().location().toString();
                    result.put(biomeId, weight);
                }
            }

            MVSCommon.LOGGER.debug("Expanded direct_biomes for {}: {} biomes", structureId, result.size());

        } catch (Exception e) {
            MVSCommon.LOGGER.debug("Failed to expand direct_biomes for {}: {}", structureId, e.getMessage());
        }

        return result;
    }

    /**
     * Handle /mvs debug command (show debug command help)
     */
    public static int executeDebugHelpCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("=== MVS Debug Commands (Advanced) ===")
            .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        source.sendSuccess(() -> Component.literal("/mvs debug mod-scan")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Scan mods for village structures")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("/mvs debug mod-scan all")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Scan ALL structures (unfiltered)")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("  Shows registry state AND datapack/JAR definitions")
            .withStyle(ChatFormatting.DARK_GRAY), false);

        source.sendSuccess(() -> Component.literal("  Reveals what mods define even if overridden by others")
            .withStyle(ChatFormatting.DARK_GRAY), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        source.sendSuccess(() -> Component.literal("⚠️  These commands are for advanced debugging only")
            .withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

    /**
     * Handle /mvs debug mod-scan command
     * Scans all mods/datapacks for village structures and outputs comprehensive report
     * @param showAll if true, shows ALL structures (unfiltered); if false, shows only village-related
     */
    public static int executeModScanCommand(CommandContext<CommandSourceStack> context, boolean showAll) {
        CommandSourceStack source = context.getSource();

        String scanType = showAll ? "ALL structures (unfiltered)" : "village structures";
        source.sendSuccess(() -> Component.literal("Scanning registry and mods for " + scanType + "...")
            .withStyle(ChatFormatting.YELLOW), false);

        try {
            Path gameDir = source.getServer().getServerDirectory();
            Path outputDir = gameDir.resolve("local/mvs");
            java.nio.file.Files.createDirectories(outputDir);

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            String filePrefix = showAll ? "mod-scan-all-" : "mod-scan-";
            Path outputFile = outputDir.resolve(filePrefix + timestamp + ".txt");

            // ===========================================
            // UNIFIED DATA COLLECTION (ONE PASS)
            // ===========================================
            ScanResult scan = StructureScanner.scanAllStructures(source.getServer());

            // ===========================================
            // OUTPUT GENERATION
            // ===========================================
            List<String> lines = new ArrayList<>();
            lines.add("===========================================");
            lines.add(showAll ? "  MVS Complete Structure Dump (UNFILTERED)" : "  MVS Unified Structure Analysis");
            lines.add("===========================================");
            lines.add("");
            lines.add("Generated: " + timestamp);
            lines.add("");
            if (showAll) {
                lines.add("Scope: ALL structures from ALL structure_sets (no filtering)");
                lines.add("");
                lines.add("This dump shows every structure found in:");
                lines.add("  1. Registry (all structure_sets)");
                lines.add("  2. JAR files (all structure_sets)");
                lines.add("  3. Datapacks (all structure_sets)");
            } else {
                lines.add("Scope: Only structures in 'villages' structure_sets + uncategorized");
                lines.add("");
                lines.add("Collected data from:");
                lines.add("  1. Registry (minecraft:villages structure_set + uncategorized structures)");
                lines.add("  2. JAR files (*:villages structure_sets + uncategorized structures)");
            }
            lines.add("");
            lines.add("Format:");
            lines.add("  Structure ID");
            lines.add("    Registry: weight X, set Y, biomes Z");
            lines.add("    JAR: weight X, set Y, biomes Z, source (structure_set/lithostitched)");
            lines.add("    Final: weight X (highest), biomes Z (registry preferred)");
            lines.add("");
            lines.add("===========================================");
            lines.add("");

            // Output structures sorted by mod
            // When showAll: use ALL structures; otherwise: use filtered core structures
            Map<String, List<StructureInfo>> structuresByMod = new TreeMap<>();
            Collection<StructureInfo> structuresToShow = showAll ? scan.allStructures.values() : scan.coreStructures;
            for (StructureInfo info : structuresToShow) {
                structuresByMod.computeIfAbsent(info.modId, k -> new ArrayList<>()).add(info);
            }

            for (Map.Entry<String, List<StructureInfo>> modEntry : structuresByMod.entrySet()) {
                String modId = modEntry.getKey();
                List<StructureInfo> modStructures = modEntry.getValue();

                lines.add("=== " + modId.toUpperCase() + " (" + modStructures.size() + " structures) ===");
                lines.add("");

                // Group structures by structure_set
                Map<String, List<StructureInfo>> structuresBySet = new TreeMap<>();
                for (StructureInfo info : modStructures) {
                    String setId = info.getFinalSet();
                    if (setId != null) {
                        structuresBySet.computeIfAbsent(setId, k -> new ArrayList<>()).add(info);
                    }
                }

                // Output each structure_set with its structures and weight analysis
                for (Map.Entry<String, List<StructureInfo>> setEntry : structuresBySet.entrySet()) {
                    String setId = setEntry.getKey();
                    List<StructureInfo> setStructures = setEntry.getValue();

                    // Sort structures alphabetically within set
                    setStructures.sort(Comparator.comparing(s -> s.id));

                    // Get structure_set info
                    StructureSetInfo setInfo = scan.structureSetInfo.get(setId);
                    Integer emptyWeight = setInfo != null && setInfo.emptyWeight != null ? setInfo.emptyWeight : 0;

                    // Header with structure_set and empty weight
                    lines.add("Structure Set: " + setId + " (Empty Weight: " + emptyWeight + ")");

                    // List structures with clean format: weight - structure_id
                    for (StructureInfo info : setStructures) {
                        Integer weight = info.getFinalWeight();
                        if (weight != null) {
                            lines.add(String.format("  %3d - %s", weight, info.id));
                        } else {
                            lines.add("    ? - " + info.id + " (NO WEIGHT)");
                        }
                    }

                    lines.add("");

                    // Add weight analysis for this structure_set
                    StructureScanner.addWeightAnalysisForSet(lines, setStructures, setInfo);
                }

                lines.add("");
            }

            lines.add("===========================================");
            lines.add("SUMMARY");
            lines.add("===========================================");
            int totalStructures = showAll ? scan.allStructures.size() : scan.coreStructures.size();
            lines.add("Total structures: " + totalStructures);
            lines.add("Total mods: " + structuresByMod.size());

            int withRegistryData = (int) structuresToShow.stream()
                .filter(s -> s.registryWeight != null || s.registrySet != null)
                .count();
            int withJarData = (int) structuresToShow.stream()
                .filter(s -> s.jarWeight != null || s.jarSet != null)
                .count();

            lines.add("Structures with registry data: " + withRegistryData);
            lines.add("Structures with JAR data: " + withJarData);
            lines.add("===========================================");
            lines.add("");
            lines.add("");

            // ===========================================
            // FYI: OTHER VILLAGE-RELATED STRUCTURES (only in filtered mode)
            // ===========================================
            if (!showAll) {
                lines.add("===========================================");
                lines.add("  FYI: Other Village-Related Structures");
                lines.add("===========================================");
                lines.add("");
                lines.add("The following structures have 'village' in their name but use");
                lines.add("CUSTOM structure_sets (not *:villages). MVS cannot automatically");
                lines.add("handle these - you may need to configure them manually.");
                lines.add("");

                // Combine likely + questionable structures
                List<StructureInfo> allOtherStructures = new ArrayList<>();
                allOtherStructures.addAll(scan.likelyStructures);
                allOtherStructures.addAll(scan.questionableStructures);

                if (allOtherStructures.isEmpty()) {
                    lines.add("No other village-related structures found.");
                } else {
                    // Group by mod
                    Map<String, List<StructureInfo>> otherStructuresByMod = new TreeMap<>();
                    for (StructureInfo info : allOtherStructures) {
                        otherStructuresByMod.computeIfAbsent(info.modId, k -> new ArrayList<>()).add(info);
                    }

                    for (Map.Entry<String, List<StructureInfo>> modEntry : otherStructuresByMod.entrySet()) {
                        String modId = modEntry.getKey();
                        List<StructureInfo> modStructures = modEntry.getValue();

                        lines.add("=== " + modId.toUpperCase() + " (" + modStructures.size() + " structures) ===");
                        lines.add("");

                        // Group by structure_set
                        Map<String, List<StructureInfo>> structuresBySet = new TreeMap<>();
                        for (StructureInfo info : modStructures) {
                            String setId = info.getFinalSet();
                            if (setId != null) {
                                structuresBySet.computeIfAbsent(setId, k -> new ArrayList<>()).add(info);
                            }
                        }

                        // Output each structure_set
                        for (Map.Entry<String, List<StructureInfo>> setEntry : structuresBySet.entrySet()) {
                            String setId = setEntry.getKey();
                            List<StructureInfo> setStructures = setEntry.getValue();

                            // Sort alphabetically
                            setStructures.sort(Comparator.comparing(s -> s.id));

                            // Get structure_set info
                            StructureSetInfo setInfo = scan.structureSetInfo.get(setId);
                            Integer emptyWeight = setInfo != null && setInfo.emptyWeight != null ? setInfo.emptyWeight : 0;

                            lines.add("Structure Set: " + setId + " (Empty Weight: " + emptyWeight + ")");

                            // List structures with clean format
                            for (StructureInfo info : setStructures) {
                                Integer weight = info.getFinalWeight();
                                if (weight != null) {
                                    lines.add(String.format("  %3d - %s", weight, info.id));
                                } else {
                                    lines.add("    ? - " + info.id + " (NO WEIGHT)");
                                }
                            }

                            lines.add("");

                            // Add weight analysis
                            StructureScanner.addWeightAnalysisForSet(lines, setStructures, setInfo);
                        }

                        lines.add("");
                    }

                    lines.add("NOTE: These structures use their own structure_sets and will NOT be");
                    lines.add("automatically included by MVS. To use them, you may need to:");
                    lines.add("  1. Add their structure_set to MVS config (if they can be intercepted)");
                    lines.add("  2. Configure the mod directly to disable their native spawning");
                    lines.add("  3. Use prevent_spawn patterns if they conflict with MVS villages");
                }

                lines.add("===========================================");
            }

            // Write to file
            java.nio.file.Files.write(outputFile, lines, java.nio.charset.StandardCharsets.UTF_8);

            String relativePath = gameDir.relativize(outputFile).toString();
            source.sendSuccess(() -> Component.literal("✅ Mod scan written to: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(relativePath)
                    .withStyle(ChatFormatting.AQUA)), false);

        } catch (Exception e) {
            source.sendFailure(Component.literal("❌ Error scanning mods: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            MVSCommon.LOGGER.error("Error in /mvs debug mod-scan command", e);
            return 0;
        }

        return 1;
    }

}
