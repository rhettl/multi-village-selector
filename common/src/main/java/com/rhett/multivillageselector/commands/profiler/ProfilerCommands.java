package com.rhett.multivillageselector.commands.profiler;

import com.mojang.brigadier.context.CommandContext;
import com.rhett.multivillageselector.profiler.ChunkGenerationProfiler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Commands for performance profiling (behind debug_cmd flag).
 *
 * /mvs debug profiler start - Start profiling (resets counters)
 * /mvs debug profiler stop  - Stop profiling and log results
 * /mvs debug profiler stats - Show current statistics
 */
public class ProfilerCommands {

    /**
     * /mvs debug profiler start - Start profiling
     */
    public static int executeStart(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (ChunkGenerationProfiler.isRunning()) {
            source.sendSuccess(() -> Component.literal("Profiler already running!")
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal(ChunkGenerationProfiler.getStatsString())
                .withStyle(ChatFormatting.WHITE), false);
            return 0;
        }

        ChunkGenerationProfiler.start();

        source.sendSuccess(() -> Component.literal("Profiler started!")
            .withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal("Fly around to generate chunks. Use '/mvs debug profiler stop' when done.")
            .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    /**
     * /mvs debug profiler stop - Stop profiling
     */
    public static int executeStop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!ChunkGenerationProfiler.isRunning()) {
            source.sendSuccess(() -> Component.literal("Profiler is not running.")
                .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        ChunkGenerationProfiler.stop();

        source.sendSuccess(() -> Component.literal("Profiler stopped! Results written to logs/latest.log")
            .withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    /**
     * /mvs debug profiler stats - Show current statistics in chat
     */
    public static int executeStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        boolean isRunning = ChunkGenerationProfiler.isRunning();

        source.sendSuccess(() -> Component.literal("=== MVS Profiler ===")
            .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal("Status: " + (isRunning ? "RUNNING" : "STOPPED"))
            .withStyle(isRunning ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal(ChunkGenerationProfiler.getStatsString())
            .withStyle(ChatFormatting.WHITE), false);

        if (!isRunning) {
            source.sendSuccess(() -> Component.literal("Use '/mvs debug profiler start' to begin profiling")
                .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }
}
