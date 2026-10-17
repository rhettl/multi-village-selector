package com.rhett.multivillageselector.commands.generate;

import com.mojang.brigadier.context.CommandContext;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.ConfigLoader;
import com.rhett.multivillageselector.config.GeneratedConfigMover;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles /mvs generate and its two-step apply flow:
 * generate -> do-confirm (warning) -> apply (move). The admin must click through both steps.
 */
public class GenerateCommands {

    public static final Path GENERATED_PATH = Paths.get("local/mvs/multivillageselector.json5");
    public static final Path CONFIG_PATH = Paths.get("config/multivillageselector.json5");

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static int execute(CommandContext<CommandSourceStack> context) {
        // TODO: Extract ~627 lines of generate logic from MVSCommands
        // For now, delegate back to MVSCommands to keep build working
        return com.rhett.multivillageselector.commands.MVSCommands.executeGenerateCommand(context);
    }

    /** Clickable "Apply changes now" line shown at the end of /mvs generate. */
    public static MutableComponent applyPrompt() {
        return Component.literal("[Apply changes now]")
            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mvs generate do-confirm"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Review before applying: nothing is moved yet"))));
    }

    /** /mvs generate do-confirm: warn, then offer the final button. Moves nothing. */
    public static int executeDoConfirm(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!GeneratedConfigMover.hasGenerated(GENERATED_PATH)) {
            return sendMissing(source);
        }

        source.sendSuccess(() -> Component.literal("⚠ Please review the generated list before applying.")
            .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(
                "We strongly recommend examining it first. The generated list can be wrong: "
                    + "it is a best guess from the installed mods, and it is your server.")
            .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Generated file: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(GENERATED_PATH.toString()).withStyle(ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> Component.literal("If " + CONFIG_PATH + " has your own changes, it is backed up first.")
            .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("[Confirm, move now]")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mvs generate apply"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Moves the generated file into config/")))), false);
        return 1;
    }

    /** /mvs generate apply: move the generated file into place, or fail if it isn't there. */
    public static int executeApply(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            String stamp = LocalDateTime.now().format(BACKUP_STAMP);
            GeneratedConfigMover.Result result = GeneratedConfigMover.apply(
                GENERATED_PATH, CONFIG_PATH, stamp, ConfigLoader.bundledDefaultConfig());

            if (result.status() == GeneratedConfigMover.Status.SOURCE_MISSING) {
                return sendMissing(source);
            }

            source.sendSuccess(() -> Component.literal("✅ Moved generated config to " + CONFIG_PATH)
                .withStyle(ChatFormatting.GREEN), false);
            if (result.backup() != null) {
                source.sendSuccess(() -> Component.literal("Previous config saved as " + result.backup())
                    .withStyle(ChatFormatting.GRAY), false);
            }
            source.sendSuccess(() -> Component.literal("Restart Minecraft for the new config to take effect.")
                .withStyle(ChatFormatting.YELLOW), false);
            MVSCommon.LOGGER.info("Applied generated config to {}", CONFIG_PATH);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to move generated config: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error applying generated config", e);
            return 0;
        }
    }

    private static int sendMissing(CommandSourceStack source) {
        source.sendFailure(Component.literal("No generated config found at " + GENERATED_PATH
            + ". Run /mvs generate first."));
        return 0;
    }
}
