package com.rhett.multivillageselector.commands.test;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.util.BiomeRules;
import com.rhett.multivillageselector.util.StructureSetAnalyzer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all /mvs test subcommands
 */
public class TestCommands {

    /**
     * Handle /mvs test structure <structure>
     * Shows if structure is MVS-controlled and if it will be blocked
     */
    public static int executeStructure(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String structureId = StringArgumentType.getString(context, "structure");

        try {
            var registryAccess = source.getServer().registryAccess();

            // Use StructureSetAnalyzer for status check
            StructureSetAnalyzer.StructureStatus status =
                StructureSetAnalyzer.getStructureStatus(structureId, registryAccess);

            if (!status.exists) {
                source.sendFailure(Component.literal("Structure not found in registry: " + structureId));
                return 0;
            }

            // Header
            source.sendSuccess(() -> Component.literal("=== MVS Structure Status: " + structureId + " ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // MVS Controlled
            if (status.isMVSControlled) {
                source.sendSuccess(() -> Component.literal("✓ MVS Controlled: YES")
                    .withStyle(ChatFormatting.GREEN), false);
                source.sendSuccess(() -> Component.literal("  This structure is in the MVS structure_pool")
                    .withStyle(ChatFormatting.GRAY), false);
            } else {
                source.sendSuccess(() -> Component.literal("✗ MVS Controlled: NO")
                    .withStyle(ChatFormatting.YELLOW), false);
                source.sendSuccess(() -> Component.literal("  This structure is NOT in the MVS structure_pool")
                    .withStyle(ChatFormatting.GRAY), false);
            }

            source.sendSuccess(() -> Component.literal(""), false);

            // Blacklisted
            if (status.isBlacklisted) {
                source.sendSuccess(() -> Component.literal("⛔ Blacklisted: YES")
                    .withStyle(ChatFormatting.RED), false);
                source.sendSuccess(() -> Component.literal("  This structure will NOT spawn (blacklisted)")
                    .withStyle(ChatFormatting.GRAY), false);

                final String pattern = status.matchedBlacklistPattern;
                source.sendSuccess(() -> Component.literal("  Matched pattern: " + pattern)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            } else {
                source.sendSuccess(() -> Component.literal("✓ Blacklisted: NO")
                    .withStyle(ChatFormatting.GREEN), false);
                source.sendSuccess(() -> Component.literal("  This structure is not blacklisted")
                    .withStyle(ChatFormatting.GRAY), false);
            }

            source.sendSuccess(() -> Component.literal(""), false);

            // Structure set status
            if (status.structureSetId != null) {
                final String setId = status.structureSetId;
                source.sendSuccess(() -> Component.literal("Structure Set: " + setId)
                    .withStyle(ChatFormatting.AQUA), false);

                if (status.isSetBlocked) {
                    source.sendSuccess(() -> Component.literal("  ⛔ Structure set is BLOCKED")
                        .withStyle(ChatFormatting.RED), false);
                    source.sendSuccess(() -> Component.literal("     Entire structure_set will not spawn")
                        .withStyle(ChatFormatting.GRAY), false);
                } else {
                    source.sendSuccess(() -> Component.literal("  ✓ Structure set is not blocked")
                        .withStyle(ChatFormatting.GREEN), false);
                }

                if (status.isSetIntercepted) {
                    source.sendSuccess(() -> Component.literal("  ⚡ Structure set is INTERCEPTED by MVS")
                        .withStyle(ChatFormatting.YELLOW), false);
                    source.sendSuccess(() -> Component.literal("     MVS controls spawning for this structure_set")
                        .withStyle(ChatFormatting.GRAY), false);
                } else {
                    source.sendSuccess(() -> Component.literal("  ○ Structure set is not intercepted")
                        .withStyle(ChatFormatting.GRAY), false);
                }
            } else {
                source.sendSuccess(() -> Component.literal("Structure Set: (not found)")
                    .withStyle(ChatFormatting.GRAY), false);
            }

            source.sendSuccess(() -> Component.literal(""), false);

            // Final verdict
            if (status.willSpawn) {
                source.sendSuccess(() -> Component.literal("Verdict: Structure CAN spawn")
                    .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.literal("Verdict: Structure will NOT spawn")
                    .withStyle(ChatFormatting.RED), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in test structure command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs test biome (current biome)
     */
    public static int executeBiomeCurrent(CommandContext<CommandSourceStack> context) {
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

            return testBiome(source, biomeHolder);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in test biome command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs test biome <biome>
     */
    public static int executeBiome(CommandContext<CommandSourceStack> context) {
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

            return testBiome(source, biomeHolderOpt.get());
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in test biome command", e);
            return 0;
        }
    }

    /**
     * Common logic for testing biome pool
     */
    private static int testBiome(CommandSourceStack source, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
        var biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) {
            source.sendFailure(Component.literal("Could not resolve biome"));
            return 0;
        }

        String biomeId = biomeKey.get().location().toString();

        source.sendSuccess(() -> Component.literal("=== Testing MVS Selection: " + biomeId + " ===")
            .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // Show filtered pool (MVS filter-first strategy)
        showMVSPool(source, biomeHolder);

        source.sendSuccess(() -> Component.literal(""), false);

        // MVS Strategy: Filter-first (only strategy)
        com.rhett.multivillageselector.strategy.StructurePicker picker =
            new com.rhett.multivillageselector.strategy.StructurePicker(MVSConfig.structurePool);
        MVSConfig.ConfiguredStructure selected = picker.select(new java.util.Random(), biomeHolder);

        // Display result (capture final reference for lambdas)
        final MVSConfig.ConfiguredStructure finalSelected = selected;

        if (finalSelected == null) {
            source.sendSuccess(() -> Component.literal("Result: No structures match this biome")
                .withStyle(ChatFormatting.RED), false);
        } else if (finalSelected.isEmpty) {
            source.sendSuccess(() -> Component.literal("Result: Empty (no village)")
                .withStyle(ChatFormatting.YELLOW), false);
            int weight = finalSelected.biomes.values().stream().findFirst().orElse(0);
            final int finalWeight = weight;
            source.sendSuccess(() -> Component.literal("  Weight: " + finalWeight)
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("Result: " + finalSelected.structure.toString())
                .withStyle(ChatFormatting.GREEN), false);
            int weight = finalSelected.biomes.values().stream().max(Integer::compare).orElse(0);
            final int finalWeight = weight;
            source.sendSuccess(() -> Component.literal("  Weight: " + finalWeight)
                .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    /**
     * Show MVS pool (FILTERED structures matching this biome)
     */
    private static void showMVSPool(CommandSourceStack source, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
        source.sendSuccess(() -> Component.literal("Pool: Filtered by biome tags")
            .withStyle(ChatFormatting.YELLOW), false);

        List<MVSConfig.ConfiguredStructure> filteredPool = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        int totalWeight = 0;

        for (MVSConfig.ConfiguredStructure configured : MVSConfig.structurePool) {
            // Use BiomeRules for weight calculation (eliminates duplicate logic)
            BiomeRules rules = BiomeRules.fromMVSConfig(configured);
            int weight = rules.getWeightForBiome(biomeHolder);

            if (weight > 0) {
                filteredPool.add(configured);
                totalWeight += weight;
            } else {
                String name = configured.isEmpty ? "(empty)" : configured.structure.toString();
                excluded.add(name);
            }
        }

        if (filteredPool.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  No structures match this biome!")
                .withStyle(ChatFormatting.RED), false);
            return;
        }

        final int finalTotalWeight = totalWeight;
        source.sendSuccess(() -> Component.literal("  Matches: " + filteredPool.size() + " structures, weight: " + finalTotalWeight)
            .withStyle(ChatFormatting.GRAY), false);

        int limit = Math.min(20, filteredPool.size());
        for (int i = 0; i < limit; i++) {
            MVSConfig.ConfiguredStructure structure = filteredPool.get(i);
            // Use BiomeRules for weight calculation (eliminates duplicate logic)
            BiomeRules rules = BiomeRules.fromMVSConfig(structure);
            int weight = rules.getWeightForBiome(biomeHolder);
            String name = structure.isEmpty ? "(empty)" : structure.structure.toString();

            double percentage = (weight * 100.0) / finalTotalWeight;
            final String line = String.format("    %3d (%4.1f%%) - %s", weight, percentage, name);
            source.sendSuccess(() -> Component.literal(line)
                .withStyle(ChatFormatting.GRAY), false);
        }

        if (filteredPool.size() > limit) {
            final int remaining = filteredPool.size() - limit;
            source.sendSuccess(() -> Component.literal("    ... and " + remaining + " more")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        }

        if (!excluded.isEmpty()) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("  Excluded (no matching tags): " + excluded.size())
                .withStyle(ChatFormatting.RED), false);
            int excludedLimit = Math.min(5, excluded.size());
            for (int i = 0; i < excludedLimit; i++) {
                final String name = excluded.get(i);
                source.sendSuccess(() -> Component.literal("    ✗ " + name)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            }
            if (excluded.size() > excludedLimit) {
                final int remaining = excluded.size() - excludedLimit;
                source.sendSuccess(() -> Component.literal("    ... and " + remaining + " more")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
    }

}
