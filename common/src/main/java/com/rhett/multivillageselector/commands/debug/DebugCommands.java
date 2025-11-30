package com.rhett.multivillageselector.commands.debug;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * Handles /mvs debug commands
 * TODO: Extract full implementation from MVSCommands (~600+ lines)
 */
public class DebugCommands {

    public static int executeHelp(CommandContext<CommandSourceStack> context) {
        return com.rhett.multivillageselector.commands.MVSCommands.executeDebugHelpCommand(context);
    }

    public static int executeModScan(CommandContext<CommandSourceStack> context, boolean showAll) {
        return com.rhett.multivillageselector.commands.MVSCommands.executeModScanCommand(context, showAll);
    }
}
