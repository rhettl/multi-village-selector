package com.rhett.multivillageselector.commands.biome;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rhett.multivillageselector.MultiVillageSelector;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            MultiVillageSelector.LOGGER.error("Error in biome command", e);
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
            MultiVillageSelector.LOGGER.error("Error in biome tags command", e);
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
            MultiVillageSelector.LOGGER.error("Error in biome tags command", e);
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
        var tags = biomesRegistry.getTags()
            .filter(pair -> pair.getSecond().contains(biomeHolder))
            .map(pair -> "#" + pair.getFirst().location().toString())
            .sorted()
            .toList();

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

            var registryAccess = source.getServer().registryAccess();
            var biomeRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> tagKey =
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, tagId);

            // Get all biomes with this tag
            List<String> matchingBiomes = new ArrayList<>();
            for (var entry : biomeRegistry.entrySet()) {
                var biomeHolder = biomeRegistry.wrapAsHolder(entry.getValue());
                if (biomeHolder.is(tagKey)) {
                    matchingBiomes.add(entry.getKey().location().toString());
                }
            }

            matchingBiomes.sort(String::compareTo);

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
            MultiVillageSelector.LOGGER.error("Error in biome by-tag command", e);
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
            MultiVillageSelector.LOGGER.error("Error in biome similar command", e);
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
            MultiVillageSelector.LOGGER.error("Error in biome similar command", e);
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

        // Get tags for target biome
        var registryAccess = source.getServer().registryAccess();
        var biomeRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);

        Set<net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome>> targetTags = new HashSet<>();
        biomeRegistry.getTags()
            .filter(pair -> pair.getSecond().contains(targetBiomeHolder))
            .forEach(pair -> targetTags.add(pair.getFirst()));

        if (targetTags.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Target biome has no tags, cannot compare")
                .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        // Calculate similarity for all biomes
        List<BiomeSimilarity> similarities = new ArrayList<>();
        for (var entry : biomeRegistry.entrySet()) {
            String compareId = entry.getKey().location().toString();
            if (compareId.equals(targetBiomeId)) continue; // Skip self

            var compareHolder = biomeRegistry.wrapAsHolder(entry.getValue());

            // Count shared tags
            int sharedCount = 0;
            int compareTagCount = 0;
            for (var tagPair : biomeRegistry.getTags().toList()) {
                if (tagPair.getSecond().contains(compareHolder)) {
                    compareTagCount++;
                    if (targetTags.contains(tagPair.getFirst())) {
                        sharedCount++;
                    }
                }
            }

            if (sharedCount > 0) {
                // Similarity = shared / total unique tags
                int totalUniqueTags = targetTags.size() + compareTagCount - sharedCount;
                double similarity = (double) sharedCount / totalUniqueTags;
                similarities.add(new BiomeSimilarity(compareId, similarity, sharedCount));
            }
        }

        // Sort by similarity (descending)
        similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // Show top 10
        int limit = Math.min(10, similarities.size());
        if (similarities.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No similar biomes found")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("Top " + limit + " most similar biomes:")
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal(""), false);
            for (int i = 0; i < limit; i++) {
                BiomeSimilarity sim = similarities.get(i);
                int percentage = (int)(sim.similarity * 100);
                final String line = String.format("  %2d%% - %s (%d shared tags)",
                    percentage, sim.biomeId, sim.sharedTags);
                source.sendSuccess(() -> Component.literal(line)
                    .withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }

    /**
     * Helper class for biome similarity comparison
     */
    private static class BiomeSimilarity {
        final String biomeId;
        final double similarity;
        final int sharedTags;

        BiomeSimilarity(String biomeId, double similarity, int sharedTags) {
            this.biomeId = biomeId;
            this.similarity = similarity;
            this.sharedTags = sharedTags;
        }
    }
}
