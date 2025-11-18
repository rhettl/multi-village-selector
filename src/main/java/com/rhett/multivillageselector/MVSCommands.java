package com.rhett.multivillageselector;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Handles all /mvs commands for debugging and inspecting MVS configuration
 */
public class MVSCommands {

    /**
     * Register all MVS commands
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("mvs")
                .requires(source -> {
                    // Allow in single-player or if OP level 2+
                    return !source.getServer().isDedicatedServer() || source.hasPermission(2);
                })
                .executes(MVSCommands::executeHelpCommand) // No args: show help
                .then(Commands.literal("help")
                    .executes(MVSCommands::executeHelpCommand)
                )
                .then(Commands.literal("biome")
                    .executes(MVSCommands::executeCurrentBiomeCommand) // No args: show current biome
                    .then(Commands.literal("list")
                        .executes(MVSCommands::executeBiomesDumpCommand) // list: dump all biomes
                    )
                    .then(Commands.argument("biome_id", StringArgumentType.greedyString())
                        .executes(MVSCommands::executeBiomeLookupCommand) // <id>: lookup specific biome
                    )
                )
                .then(Commands.literal("pools")
                    .executes(MVSCommands::executePoolsCommand)
                    .then(Commands.argument("category", StringArgumentType.word())
                        .executes(MVSCommands::executePoolsCategoryCommand)
                    )
                )
                .then(Commands.literal("generate")
                    .executes(MVSCommands::executeGenerateCommand) // Generate smart config
                )
        );
    }

    /**
     * Handle /mvs help command (or /mvs with no arguments)
     * Shows an overview of all available commands
     */
    private static int executeHelpCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("=== Multi Village Selector Commands ===")
            .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        // /mvs biome
        source.sendSuccess(() -> Component.literal("/mvs biome")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Show your current biome, location, and category")
                .withStyle(ChatFormatting.GRAY)), false);

        // /mvs biome <id>
        source.sendSuccess(() -> Component.literal("/mvs biome <biome_id>")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Look up category for a specific biome")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("  Example: ")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("/mvs biome minecraft:plains")
                .withStyle(ChatFormatting.DARK_GRAY)), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        // /mvs biome list
        source.sendSuccess(() -> Component.literal("/mvs biome list")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Dump all registered biomes to file")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        // /mvs pools
        source.sendSuccess(() -> Component.literal("/mvs pools")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - List all configured pool categories")
                .withStyle(ChatFormatting.GRAY)), false);

        // /mvs pools <category>
        source.sendSuccess(() -> Component.literal("/mvs pools <category>")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Show structures in a pool with weights")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("  Example: ")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("/mvs pools plains")
                .withStyle(ChatFormatting.DARK_GRAY)), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        // /mvs generate
        source.sendSuccess(() -> Component.literal("/mvs generate")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Generate smart config from installed mods")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        // /mvs help
        source.sendSuccess(() -> Component.literal("/mvs help")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - Show this help message")
                .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line
        source.sendSuccess(() -> Component.literal("All commands require OP permission level 2")
            .withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

    /**
     * Handle /mvs biome command (no arguments)
     * Shows the player's current biome, location, temperature, and category
     */
    private static int executeCurrentBiomeCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            // Get player's position and biome
            net.minecraft.world.entity.Entity entity = source.getEntity();
            if (entity == null) {
                source.sendFailure(Component.literal("This command can only be used by a player"));
                return 0;
            }

            net.minecraft.core.BlockPos pos = entity.blockPosition();
            net.minecraft.server.level.ServerLevel level = source.getLevel();

            // Get biome at player's location
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder =
                level.getBiome(pos);

            net.minecraft.resources.ResourceLocation biomeLocation = biomeHolder.unwrapKey()
                .map(net.minecraft.resources.ResourceKey::location)
                .orElse(null);

            if (biomeLocation == null) {
                source.sendFailure(Component.literal("Unable to determine biome"));
                return 0;
            }

            String biomeId = biomeLocation.toString();
            net.minecraft.world.level.biome.Biome biome = biomeHolder.value();

            // Get temperature and category
            float temperature = biome.getBaseTemperature();

            // Check if there's a config override
            boolean isOverride = MVSConfig.biomeCategoryOverrides.containsKey(biomeId);
            final String category;
            if (isOverride) {
                category = MVSConfig.biomeCategoryOverrides.get(biomeId);
            } else {
                category = getSimpleBiomeCategory(biomeId);
            }

            // Send formatted output
            source.sendSuccess(() -> Component.literal("=== Current Biome ===")
                .withStyle(ChatFormatting.GOLD), false);

            source.sendSuccess(() -> Component.literal("Location: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ()))
                    .withStyle(ChatFormatting.WHITE)), false);

            source.sendSuccess(() -> Component.literal("Biome: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(biomeId).withStyle(ChatFormatting.AQUA)), false);

            source.sendSuccess(() -> Component.literal("Temperature: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.2f", temperature))
                    .withStyle(ChatFormatting.YELLOW)), false);

            source.sendSuccess(() -> Component.literal("Category: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(category).withStyle(ChatFormatting.YELLOW)), false);

            if (isOverride) {
                source.sendSuccess(() -> Component.literal("Source: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Config Override").withStyle(ChatFormatting.LIGHT_PURPLE)), false);
            }

            // Show available structures for this category
            if (MVSConfig.biomeReplacements.containsKey(category)) {
                List<MVSConfig.WeightedStructure> structures = MVSConfig.biomeReplacements.get(category);
                source.sendSuccess(() -> Component.literal("Available Structures: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(structures.size())).withStyle(ChatFormatting.GREEN)), false);
            } else {
                source.sendSuccess(() -> Component.literal("Available Structures: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("None (would use DEFAULT)").withStyle(ChatFormatting.RED)), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MultiVillageSelector.LOGGER.error("Error in /mvs biome command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs biome <mod:biome> command (with argument)
     * Shows which category a specific biome maps to
     */
    private static int executeBiomeLookupCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String biomeId = StringArgumentType.getString(context, "biome_id");

        try {
            // Validate format
            if (!biomeId.contains(":")) {
                source.sendFailure(Component.literal("Invalid biome format. Use 'namespace:biome' (e.g., 'minecraft:plains')"));
                return 0;
            }

            // Check if there's a config override
            String category;
            boolean isOverride = false;

            if (MVSConfig.biomeCategoryOverrides.containsKey(biomeId)) {
                category = MVSConfig.biomeCategoryOverrides.get(biomeId);
                isOverride = true;
            } else {
                // Use the categorization logic from the mixin
                // For now, we'll do simple name matching (can't instantiate Biome here)
                category = getSimpleBiomeCategory(biomeId);
            }

            // Send result
            source.sendSuccess(() -> Component.literal("=== MVS Biome Info ===")
                .withStyle(ChatFormatting.GOLD), false);

            source.sendSuccess(() -> Component.literal("Biome: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(biomeId).withStyle(ChatFormatting.WHITE)), false);

            source.sendSuccess(() -> Component.literal("Category: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(category).withStyle(ChatFormatting.YELLOW)), false);

            if (isOverride) {
                source.sendSuccess(() -> Component.literal("Source: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Config Override").withStyle(ChatFormatting.LIGHT_PURPLE)), false);
            } else {
                source.sendSuccess(() -> Component.literal("Source: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Name Matching / Temperature").withStyle(ChatFormatting.AQUA)), false);
            }

            // Show available structures in this category
            if (MVSConfig.biomeReplacements.containsKey(category)) {
                List<MVSConfig.WeightedStructure> structures = MVSConfig.biomeReplacements.get(category);
                source.sendSuccess(() -> Component.literal("Available Structures: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(structures.size())).withStyle(ChatFormatting.GREEN)), false);
            } else {
                source.sendSuccess(() -> Component.literal("Available Structures: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("None (would use DEFAULT)").withStyle(ChatFormatting.RED)), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Handle /mvs biome list command
     * Lists all registered biomes grouped by category in chat
     */
    private static int executeBiomesDumpCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            source.sendSuccess(() -> Component.literal("=== All Biomes by Category ===")
                .withStyle(ChatFormatting.GOLD), false);

            // Get biome registry
            net.minecraft.server.level.ServerLevel level = source.getLevel();
            net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomeRegistry =
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);

            // Group biomes by category
            java.util.Map<String, java.util.List<String>> biomesByCategory = new java.util.TreeMap<>();
            java.util.List<String> uncategorized = new java.util.ArrayList<>();

            // Iterate through all registered biomes
            for (net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key : biomeRegistry.registryKeySet()) {
                net.minecraft.resources.ResourceLocation biomeLocation = key.location();
                String biomeId = biomeLocation.toString();

                // Get biome
                net.minecraft.world.level.biome.Biome biome = biomeRegistry.get(key);
                if (biome == null) continue;

                // Determine category and whether it was explicitly matched
                boolean isOverride = MVSConfig.biomeCategoryOverrides.containsKey(biomeId);
                String category;
                boolean hasExplicitMatch = false;

                if (isOverride) {
                    category = MVSConfig.biomeCategoryOverrides.get(biomeId);
                    hasExplicitMatch = true;
                } else {
                    // Check if name matches (not just temperature fallback)
                    String path = biomeLocation.getPath().toLowerCase();
                    if (path.contains("jungle") || path.contains("tropical") || path.contains("mayan") ||
                        path.contains("swamp") || path.contains("marsh") || path.contains("bog") ||
                        path.contains("bayou") || path.contains("wetland") || path.contains("mangrove") ||
                        path.contains("mushroom") || path.contains("mystic_grove") ||
                        path.contains("dark_forest") || path.contains("dead_forest") || path.contains("ominous") ||
                        path.contains("beach") || path.contains("shore") ||
                        path.contains("desert") || path.contains("dune") || path.contains("sand") ||
                        path.contains("badlands") || path.contains("mesa") || path.contains("outback") ||
                        path.contains("savanna") || path.contains("shrubland") || path.contains("prairie") ||
                        path.contains("steppe") || path.contains("arid") ||
                        path.contains("snow") || path.contains("ice") || path.contains("frozen") ||
                        path.contains("arctic") || path.contains("tundra") || path.contains("glacier") ||
                        path.contains("grove") ||
                        path.contains("taiga") || path.contains("pine") || path.contains("spruce") ||
                        path.contains("boreal") || path.contains("conifer") ||
                        path.contains("ocean") || path.contains("sea") || path.contains("river") ||
                        path.contains("lake") ||
                        path.contains("plains") || path.contains("meadow") || path.contains("grassland") ||
                        path.contains("field") || path.contains("pasture") || path.contains("forest")) {
                        hasExplicitMatch = true;
                    }

                    category = getSimpleBiomeCategory(biomeId);
                }

                // Add to appropriate list
                if (hasExplicitMatch) {
                    biomesByCategory.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(biomeId);
                } else {
                    uncategorized.add(biomeId);
                }
            }

            source.sendSuccess(() -> Component.literal(""), false); // Blank line

            // Output each category with its biomes
            for (Map.Entry<String, java.util.List<String>> entry : biomesByCategory.entrySet()) {
                final String category = entry.getKey();
                final int count = entry.getValue().size();

                // Category header
                source.sendSuccess(() -> Component.literal(category.toUpperCase())
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" (" + count + ")")
                        .withStyle(ChatFormatting.GRAY)), false);

                // List biomes
                for (String biomeId : entry.getValue()) {
                    source.sendSuccess(() -> Component.literal("  " + biomeId)
                        .withStyle(ChatFormatting.WHITE), false);
                }

                source.sendSuccess(() -> Component.literal(""), false); // Blank line
            }

            // Output uncategorized biomes if any
            if (!uncategorized.isEmpty()) {
                final int uncatCount = uncategorized.size();
                source.sendSuccess(() -> Component.literal("UNCATEGORIZED")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" (" + uncatCount + ") - uses temperature fallback → DEFAULT")
                        .withStyle(ChatFormatting.GRAY)), false);

                for (String biomeId : uncategorized) {
                    source.sendSuccess(() -> Component.literal("  " + biomeId)
                        .withStyle(ChatFormatting.DARK_GRAY), false);
                }

                source.sendSuccess(() -> Component.literal(""), false); // Blank line
            }

            // Summary
            final int totalBiomes = biomesByCategory.values().stream().mapToInt(List::size).sum() + uncategorized.size();
            final int totalCategories = biomesByCategory.size();

            source.sendSuccess(() -> Component.literal("Total: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(totalBiomes) + " biomes")
                    .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(", ")
                    .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(totalCategories) + " categories")
                    .withStyle(ChatFormatting.YELLOW)), false);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error listing biomes: " + e.getMessage()));
            MultiVillageSelector.LOGGER.error("Error listing biomes", e);
            return 0;
        }
    }

    /**
     * Handle /mvs pools command
     * Lists all configured pool categories
     */
    private static int executePoolsCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (MVSConfig.biomeReplacements.isEmpty()) {
            source.sendFailure(Component.literal("No pools configured"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== MVS Pool Categories ===")
            .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal("Total Categories: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(MVSConfig.biomeReplacements.size()))
                .withStyle(ChatFormatting.GREEN)), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        for (Map.Entry<String, List<MVSConfig.WeightedStructure>> entry : MVSConfig.biomeReplacements.entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue().size();

            source.sendSuccess(() -> Component.literal("  • ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(category).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + count + " structures)").withStyle(ChatFormatting.GRAY)), false);
        }

        source.sendSuccess(() -> Component.literal(""), false); // Blank line
        source.sendSuccess(() -> Component.literal("Use ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("/mvs pools <category>").withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" to see details").withStyle(ChatFormatting.GRAY)), false);

        return 1;
    }

    /**
     * Handle /mvs pools <category> command
     * Shows all structures in a specific pool with weights and percentages
     */
    private static int executePoolsCategoryCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String category = StringArgumentType.getString(context, "category");

        if (!MVSConfig.biomeReplacements.containsKey(category)) {
            source.sendFailure(Component.literal("Unknown category: " + category));
            source.sendSystemMessage(Component.literal("Available categories: " +
                String.join(", ", MVSConfig.biomeReplacements.keySet())).withStyle(ChatFormatting.GRAY));
            return 0;
        }

        List<MVSConfig.WeightedStructure> structures = MVSConfig.biomeReplacements.get(category);
        int totalWeight = structures.stream().mapToInt(s -> s.weight).sum();

        source.sendSuccess(() -> Component.literal("=== MVS Pool: ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(category).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" ===").withStyle(ChatFormatting.GOLD)), false);

        source.sendSuccess(() -> Component.literal("Total Weight: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(totalWeight)).withStyle(ChatFormatting.GREEN)), false);

        source.sendSuccess(() -> Component.literal(""), false); // Blank line

        // List each structure with weight and percentage
        for (MVSConfig.WeightedStructure ws : structures) {
            double percentage = (ws.weight / (double) totalWeight) * 100.0;

            if (ws.isEmpty) {
                // Empty entry (weighted no-spawn)
                source.sendSuccess(() -> Component.literal(String.format("  [%d] ", ws.weight))
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(String.format("(%.1f%%) ", percentage))
                        .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("(empty - no spawn)")
                        .withStyle(ChatFormatting.DARK_RED)), false);
            } else {
                // Regular structure entry
                String structurePath = ws.structure.getPath();
                String structureNamespace = ws.structure.getNamespace();

                source.sendSuccess(() -> Component.literal(String.format("  [%d] ", ws.weight))
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(String.format("(%.1f%%) ", percentage))
                        .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(structureNamespace + ":")
                        .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(structurePath)
                        .withStyle(ChatFormatting.WHITE)), false);
            }
        }

        return 1;
    }

    /**
     * Simple biome categorization based on name only (for command usage)
     * This mirrors the logic in ChunkGeneratorMixin but without access to Biome instance
     */
    private static String getSimpleBiomeCategory(String biomeId) {
        String path = biomeId.toLowerCase();

        // Check overrides first
        if (MVSConfig.biomeCategoryOverrides.containsKey(biomeId)) {
            return MVSConfig.biomeCategoryOverrides.get(biomeId);
        }

        // Name matching (same order as mixin)
        if (path.contains("jungle") || path.contains("tropical") || path.contains("mayan")) {
            return "jungle";
        }
        if (path.contains("swamp") || path.contains("marsh") || path.contains("bog") ||
            path.contains("bayou") || path.contains("wetland") || path.contains("mangrove")) {
            return "swamp";
        }
        if (path.contains("mushroom") || path.contains("mystic_grove")) {
            return "mushroom";
        }
        if (path.contains("dark_forest") || path.contains("dead_forest") || path.contains("ominous")) {
            return "dark_forest";
        }
        if (path.contains("beach") || path.contains("shore")) {
            return "beach";
        }
        if (path.contains("desert") || path.contains("dune") || path.contains("sand") ||
            path.contains("badlands") || path.contains("mesa") || path.contains("outback")) {
            return "desert";
        }
        if (path.contains("savanna") || path.contains("shrubland") || path.contains("prairie") ||
            path.contains("steppe") || path.contains("arid")) {
            return "savanna";
        }
        if (path.contains("snow") || path.contains("ice") || path.contains("frozen") ||
            path.contains("arctic") || path.contains("tundra") || path.contains("glacier") ||
            path.contains("grove")) {
            return "snowy";
        }
        if (path.contains("taiga") || path.contains("pine") || path.contains("spruce") ||
            path.contains("boreal") || path.contains("conifer")) {
            return "taiga";
        }
        if (path.contains("ocean") || path.contains("sea") || path.contains("river") ||
            path.contains("lake")) {
            return "ocean";
        }
        if (path.contains("plains") || path.contains("meadow") || path.contains("grassland") ||
            path.contains("field") || path.contains("pasture") || path.contains("forest")) {
            return "plains";
        }

        // Default to plains (can't check temperature without Biome instance)
        return "plains";
    }

    /**
     * Handle /mvs generate command
     * Generates a smart config file based on installed mods and structures
     */
    private static int executeGenerateCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            source.sendSuccess(() -> Component.literal("=== Generating Smart Config ===")
                .withStyle(ChatFormatting.GOLD), false);

            // Get registries
            net.minecraft.server.level.ServerLevel level = source.getLevel();
            net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomeRegistry =
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);

            // Scan for village structures
            java.util.Map<String, java.util.List<String>> villagesByMod = new java.util.TreeMap<>();
            for (net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> key : structureRegistry.registryKeySet()) {
                String structureId = key.location().toString();
                String namespace = key.location().getNamespace();
                String path = key.location().getPath().toLowerCase();

                // Check if it's a village structure
                if (path.contains("village")) {
                    villagesByMod.computeIfAbsent(namespace, k -> new java.util.ArrayList<>()).add(structureId);
                }
            }

            // Detect installed mods
            boolean hasBCA = villagesByMod.containsKey("bca");
            boolean hasCTOV = villagesByMod.containsKey("ctov");
            boolean hasTownsAndTowers = villagesByMod.containsKey("towns_and_towers");
            boolean hasTerralith = villagesByMod.containsKey("terralith");

            // Categorize villages by biome
            java.util.Map<String, java.util.List<String>> villagesByCategory = new java.util.TreeMap<>();
            for (java.util.List<String> villages : villagesByMod.values()) {
                for (String villageId : villages) {
                    // Extract path from full ID (mod:path)
                    String path = villageId.contains(":") ? villageId.split(":", 2)[1].toLowerCase() : villageId.toLowerCase();
                    String category = categorizeVillageByName(path);
                    villagesByCategory.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(villageId);
                }
            }

            // Scan for uncategorized biomes
            java.util.List<String> uncategorizedBiomes = new java.util.ArrayList<>();
            for (net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key : biomeRegistry.registryKeySet()) {
                String biomeId = key.location().toString();
                if (biomeId.startsWith("minecraft:")) continue; // Skip vanilla

                String path = key.location().getPath().toLowerCase();
                if (!hasExplicitBiomeMatch(path)) {
                    uncategorizedBiomes.add(biomeId);
                }
            }

            // Generate config file
            java.util.List<String> lines = generateConfigLines(villagesByMod, villagesByCategory, uncategorizedBiomes,
                hasBCA, hasCTOV, hasTownsAndTowers, hasTerralith);

            // Write to file (create directory if needed)
            java.nio.file.Path outputDir = java.nio.file.Paths.get("local/mvs");
            java.nio.file.Files.createDirectories(outputDir);
            final java.nio.file.Path outputFile = outputDir.resolve("multivillageselector.json5");
            java.nio.file.Files.write(outputFile, lines, java.nio.charset.StandardCharsets.UTF_8);

            // Send success
            final int modsDetected = villagesByMod.size();
            final int villagesDetected = villagesByMod.values().stream().mapToInt(List::size).sum();
            final int uncatCount = uncategorizedBiomes.size();

            source.sendSuccess(() -> Component.literal("Mods detected: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(modsDetected)).withStyle(ChatFormatting.GREEN)), false);

            source.sendSuccess(() -> Component.literal("Villages found: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(villagesDetected)).withStyle(ChatFormatting.GREEN)), false);

            source.sendSuccess(() -> Component.literal("Uncategorized biomes: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(uncatCount)).withStyle(ChatFormatting.YELLOW)), false);

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

            MultiVillageSelector.LOGGER.info("Generated config with {} mods, {} villages", modsDetected, villagesDetected);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error generating config: " + e.getMessage()));
            MultiVillageSelector.LOGGER.error("Error generating config", e);
            return 0;
        }
    }

    private static String categorizeVillageByName(String path) {
        if (path.contains("jungle")) return "jungle";
        if (path.contains("swamp")) return "swamp";
        if (path.contains("mushroom")) return "mushroom";
        if (path.contains("dark")) return "dark_forest";
        if (path.contains("beach")) return "beach";
        if (path.contains("desert") || path.contains("badlands") || path.contains("mesa")) return "desert";
        if (path.contains("savanna")) return "savanna";
        if (path.contains("snow") || path.contains("ice") || path.contains("frozen")) return "snowy";
        if (path.contains("taiga")) return "taiga";
        if (path.contains("ocean")) return "ocean";
        return "plains"; // Default
    }

    private static boolean hasExplicitBiomeMatch(String path) {
        return path.contains("jungle") || path.contains("tropical") || path.contains("swamp") ||
               path.contains("marsh") || path.contains("mushroom") || path.contains("dark") ||
               path.contains("beach") || path.contains("shore") || path.contains("desert") ||
               path.contains("dune") || path.contains("sand") || path.contains("badlands") ||
               path.contains("mesa") || path.contains("savanna") || path.contains("shrubland") ||
               path.contains("snow") || path.contains("ice") || path.contains("frozen") ||
               path.contains("taiga") || path.contains("pine") || path.contains("spruce") ||
               path.contains("ocean") || path.contains("sea") || path.contains("river") ||
               path.contains("plains") || path.contains("meadow") || path.contains("grassland") ||
               path.contains("forest");
    }

    private static java.util.List<String> generateConfigLines(
            java.util.Map<String, java.util.List<String>> villagesByMod,
            java.util.Map<String, java.util.List<String>> villagesByCategory,
            java.util.List<String> uncategorizedBiomes,
            boolean hasBCA, boolean hasCTOV, boolean hasTownsAndTowers, boolean hasTerralith) {

        java.util.List<String> lines = new java.util.ArrayList<>();

        // Header
        lines.add("// ===========================================");
        lines.add("//  Multi Village Selector - Generated Config");
        lines.add("//  Generated: " + java.time.LocalDateTime.now());
        lines.add("// ===========================================");
        lines.add("//");
        lines.add("// ═══════════════════════════════════════════════════════════════");
        lines.add("//  📋 NEXT STEPS:");
        lines.add("//  1. Review this file (adjust weights, check biome overrides)");
        lines.add("//  2. Copy to: config/multivillageselector.json5");
        lines.add("//  3. Restart Minecraft");
        lines.add("//");
        lines.add("//  💡 TIP: The weight numbers are deliberately simple (10, 50)");
        lines.add("//       Adjust them to control spawn chances:");
        lines.add("//       - Higher weight = spawns more often");
        lines.add("//       - Lower weight = spawns less often");
        lines.add("//       - Empty weight = chance of no village");
        lines.add("//");
        lines.add("//  📖 Documentation:");
        lines.add("//     github.com/RhettL/multi-village-selector/blob/master/docs/Configuration.md");
        lines.add("// ═══════════════════════════════════════════════════════════════");
        lines.add("//");
        lines.add("// Detected mods:");
        for (String mod : villagesByMod.keySet()) {
            lines.add("//   - " + mod + " (" + villagesByMod.get(mod).size() + " villages)");
        }
        lines.add("//");
        lines.add("// ===========================================");
        lines.add("");
        lines.add("{");
        lines.add("  enabled: true,");
        lines.add("  debug_logging: false, // Set to true for verbose debugging");
        lines.add("");

        // replace_of
        lines.add("  // Structures to intercept and replace");
        lines.add("  replace_of: [");
        lines.add("    // Vanilla villages");
        lines.add("    \"minecraft:village_plains\",");
        lines.add("    \"minecraft:village_desert\",");
        lines.add("    \"minecraft:village_savanna\",");
        lines.add("    \"minecraft:village_snowy\",");
        lines.add("    \"minecraft:village_taiga\",");
        if (hasBCA) {
            lines.add("    // BCA villages (structure set override)");
            for (String village : villagesByMod.get("bca")) {
                // Skip witch_hut - it's not a village
                if (village.contains("witch_hut")) continue;
                lines.add("    \"" + village + "\",");
            }
        }
        // Remove trailing comma
        String lastLine = lines.get(lines.size() - 1);
        lines.set(lines.size() - 1, lastLine.replace(",", ""));
        lines.add("  ],");
        lines.add("");

        // prevent_spawn
        lines.add("  // ⚠️ RECOMMENDED: Disable village spawning in each mod's config instead!");
        lines.add("  // See docs/ModCompatibility.md for per-mod setup instructions.");
        lines.add("  //");
        lines.add("  // Only use prevent_spawn as a fallback if you can't disable via mod configs.");
        lines.add("  // Examples:");
        lines.add("  //   CTOV: Set generatesmallVillage/medium/large = false in ctov-common.toml");
        lines.add("  //   BCA: Disable villages in cristellib config (or add to replace_of instead)");
        lines.add("  prevent_spawn: [");
        lines.add("    // Uncomment if needed:");
        lines.add("    // \"ctov:*/*\",");
        lines.add("    // \"towns_and_towers:village_*\",");
        lines.add("  ],");
        lines.add("");

        // replace_with
        lines.add("  // Villages to use as replacements, organized by biome category");
        lines.add("  replace_with: {");

        // Define all standard categories in order
        String[] STANDARD_CATEGORIES = {
            "plains", "desert", "savanna", "snowy", "taiga",
            "jungle", "swamp", "beach", "dark_forest", "ocean", "mushroom"
        };

        for (String category : STANDARD_CATEGORIES) {
            java.util.List<String> villages = villagesByCategory.getOrDefault(category, new java.util.ArrayList<>());

            lines.add("");
            lines.add("    // === " + category.toUpperCase() + " ===");
            lines.add("    " + category + ": [");

            if (villages.isEmpty()) {
                // No villages detected for this category - show as empty with comment
                lines.add("      // No villages detected - add modded villages here or leave empty");
            } else {
                // Has villages - add them
                for (String village : villages) {
                    lines.add("      { structure: \"" + village + "\", weight: 10 },");
                }

                // Add empty weight for sparse biomes (50% = visually obvious example of weighting)
                if (category.equals("ocean") || category.equals("mushroom") ||
                    category.equals("swamp")) {
                    lines.add("      { empty: true, weight: 50 } // 50% chance no village (adjust as desired)");
                } else {
                    // Remove trailing comma for other categories
                    lastLine = lines.get(lines.size() - 1);
                    lines.set(lines.size() - 1, lastLine.substring(0, lastLine.length() - 1));
                }
            }

            lines.add("    ],");
        }

        // DEFAULT pool
        lines.add("");
        lines.add("    // === DEFAULT (fallback for unmatched biomes) ===");
        lines.add("    DEFAULT: [");
        lines.add("      { structure: \"minecraft:village_plains\", weight: 10 },");
        lines.add("      { empty: true, weight: 50 } // 50% chance no village (conservative fallback)");
        lines.add("    ]");
        lines.add("  },");
        lines.add("");

        // biome_category_overrides
        lines.add("  // Override biome categorization (uncomment and adjust as needed)");
        lines.add("  biome_category_overrides: {");
        if (!uncategorizedBiomes.isEmpty()) {
            lines.add("    // Uncategorized biomes detected:");
            for (String biomeId : uncategorizedBiomes) {
                lines.add("    // \"" + biomeId + "\": \"plains\", // or desert, snowy, etc.");
            }
        } else {
            lines.add("    // Add custom overrides here if needed");
        }
        lines.add("  }");
        lines.add("}");

        return lines;
    }
}
