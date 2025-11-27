package com.rhett.multivillageselector.commands.config;

import com.mojang.brigadier.context.CommandContext;
import com.rhett.multivillageselector.MultiVillageSelector;
import com.rhett.multivillageselector.config.MVSConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Handles /mvs reload command
 */
public class ReloadCommands {

    public static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            source.sendSuccess(() -> Component.literal("Reloading MVS config...")
                .withStyle(ChatFormatting.YELLOW), false);

            MVSConfig.load();

            source.sendSuccess(() -> Component.literal("✅ Config reloaded successfully!")
                .withStyle(ChatFormatting.GREEN), false);

            source.sendSuccess(() -> Component.literal("Enabled: " + MVSConfig.enabled)
                .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Structure pool: " + MVSConfig.structurePool.size() + " entries")
                .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Biome frequency rules: " + MVSConfig.biomeFrequency.size() + " entries")
                .withStyle(ChatFormatting.GRAY), false);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("❌ Failed to reload config: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            MultiVillageSelector.LOGGER.error("Error reloading config", e);
            return 0;
        }
    }
}
