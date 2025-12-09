package com.rhett.multivillageselector.commands.biome;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.util.BiomeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Handles all /mvs biome subcommands
 */
public class BiomeCommands {

    /**
     * Handle /mvs biome (show current biome)
     */
    public static int executeCurrent(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            var entity = source.getEntity();
            if (entity == null) {
                source.sendFailure(Component.literal("This command must be run by a player"));
                return 0;
            }

            var level = entity.level();
            var pos = entity.blockPosition();
            var biomeHolder = level.getBiome(pos);
            var biomeKey = biomeHolder.unwrapKey();

            if (biomeKey.isEmpty()) {
                source.sendFailure(Component.literal("Could not resolve biome"));
                return 0;
            }

            String biomeId = biomeKey.get().location().toString();

            source.sendSuccess(() -> Component.literal("=== Current Biome ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("Biome: " + biomeId)
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal("Location: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .withStyle(ChatFormatting.GRAY), false);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in biome command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs biome tags (show tags for current biome)
     */
    public static int executeTagsCurrent(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            var entity = source.getEntity();
            if (entity == null) {
                source.sendFailure(Component.literal("This command must be run by a player"));
                return 0;
            }

            var level = entity.level();
            var pos = entity.blockPosition();
            var biomeHolder = level.getBiome(pos);

            return showBiomeTags(source, biomeHolder);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in biome tags command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs biome tags <biome> (show tags for specific biome)
     */
    public static int executeTags(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String biomeId = StringArgumentType.getString(context, "biome");

        try {
            var registryAccess = source.getServer().registryAccess();
            var biomeRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            var biomeKey = net.minecraft.resources.ResourceLocation.tryParse(biomeId);

            if (biomeKey == null) {
                source.sendFailure(Component.literal("Invalid biome ID: " + biomeId));
                return 0;
            }

            var biome = biomeRegistry.get(biomeKey);
            if (biome == null) {
                source.sendFailure(Component.literal("Biome not found: " + biomeId));
                return 0;
            }

            var biomeHolderOpt = biomeRegistry.getHolder(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BIOME, biomeKey));

            if (biomeHolderOpt.isEmpty()) {
                source.sendFailure(Component.literal("Could not get biome holder for: " + biomeId));
                return 0;
            }

            return showBiomeTags(source, biomeHolderOpt.get());
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in biome tags command", e);
            return 0;
        }
    }

    /**
     * Common logic for showing biome tags
     */
    private static int showBiomeTags(CommandSourceStack source, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
        var biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) {
            source.sendFailure(Component.literal("Could not resolve biome"));
            return 0;
        }

        String biomeId = biomeKey.get().location().toString();

        source.sendSuccess(() -> Component.literal("=== Biome Tags: " + biomeId + " ===")
            .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(""), false);

        var biomesRegistry = source.getServer().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        List<String> tags = BiomeHelper.getTagsForBiome(biomeHolder, biomesRegistry);

        if (tags.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  (no tags)")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("Found " + tags.size() + " tags:")
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal(""), false);
            for (String tag : tags) {
                source.sendSuccess(() -> Component.literal("  " + tag)
                    .withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }

    /**
     * Handle /mvs biome by-tag <tag> (list biomes with tag)
     */
    public static int executeByTag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String tagString = StringArgumentType.getString(context, "tag");

        try {
            // Ensure tag starts with #
            if (!tagString.startsWith("#")) {
                tagString = "#" + tagString;
            }

            String tagIdString = tagString.substring(1);
            ResourceLocation tagId = ResourceLocation.tryParse(tagIdString);

            if (tagId == null) {
                source.sendFailure(Component.literal("Invalid tag ID: " + tagString));
                return 0;
            }

            var biomeRegistry = source.getServer().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.BIOME);

            List<String> matchingBiomes = BiomeHelper.getBiomesWithTag(tagId, biomeRegistry);

            final String finalTagString = tagString;
            source.sendSuccess(() -> Component.literal("=== Biomes with tag: " + finalTagString + " ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            if (matchingBiomes.isEmpty()) {
                source.sendSuccess(() -> Component.literal("  No biomes found with this tag")
                    .withStyle(ChatFormatting.GRAY), false);
            } else {
                source.sendSuccess(() -> Component.literal("Found " + matchingBiomes.size() + " biomes:")
                    .withStyle(ChatFormatting.YELLOW), false);
                source.sendSuccess(() -> Component.literal(""), false);
                for (String biome : matchingBiomes) {
                    source.sendSuccess(() -> Component.literal("  " + biome)
                        .withStyle(ChatFormatting.GRAY), false);
                }
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in biome by-tag command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs biome similar (show biomes similar to current)
     */
    public static int executeSimilarCurrent(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            var entity = source.getEntity();
            if (entity == null) {
                source.sendFailure(Component.literal("This command must be run by a player"));
                return 0;
            }

            var level = entity.level();
            var pos = entity.blockPosition();
            var biomeHolder = level.getBiome(pos);

            return showSimilarBiomes(source, biomeHolder);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in biome similar command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs biome similar <biome> (show biomes similar to specific biome)
     */
    public static int executeSimilar(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String biomeId = StringArgumentType.getString(context, "biome");

        try {
            var registryAccess = source.getServer().registryAccess();
            var biomeRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            var biomeKey = net.minecraft.resources.ResourceLocation.tryParse(biomeId);

            if (biomeKey == null) {
                source.sendFailure(Component.literal("Invalid biome ID: " + biomeId));
                return 0;
            }

            var biome = biomeRegistry.get(biomeKey);
            if (biome == null) {
                source.sendFailure(Component.literal("Biome not found: " + biomeId));
                return 0;
            }

            var biomeHolderOpt = biomeRegistry.getHolder(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BIOME, biomeKey));

            if (biomeHolderOpt.isEmpty()) {
                source.sendFailure(Component.literal("Could not get biome holder for: " + biomeId));
                return 0;
            }

            return showSimilarBiomes(source, biomeHolderOpt.get());
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in biome similar command", e);
            return 0;
        }
    }

    /**
     * Common logic for finding similar biomes
     * Compares biomes by tag overlap (more shared tags = more similar)
     */
    private static int showSimilarBiomes(CommandSourceStack source, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> targetBiomeHolder) {
        var biomeKey = targetBiomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) {
            source.sendFailure(Component.literal("Could not resolve biome"));
            return 0;
        }

        String targetBiomeId = biomeKey.get().location().toString();

        source.sendSuccess(() -> Component.literal("=== Similar Biomes to: " + targetBiomeId + " ===")
            .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(""), false);

        var biomeRegistry = source.getServer().registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.BIOME);

        // Use BiomeHelper to calculate similarity
        List<BiomeHelper.BiomeSimilarity> similarities = BiomeHelper.findSimilarBiomes(
            targetBiomeHolder, biomeRegistry, 10);

        if (similarities.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No similar biomes found (or target biome has no tags)")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            final int limit = similarities.size();
            source.sendSuccess(() -> Component.literal("Top " + limit + " most similar biomes:")
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal(""), false);
            for (BiomeHelper.BiomeSimilarity sim : similarities) {
                int percentage = (int)(sim.similarity * 100);
                final String line = String.format("  %2d%% - %s (%d shared tags)",
                    percentage, sim.biomeId, sim.sharedTags);
                source.sendSuccess(() -> Component.literal(line)
                    .withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }
}
