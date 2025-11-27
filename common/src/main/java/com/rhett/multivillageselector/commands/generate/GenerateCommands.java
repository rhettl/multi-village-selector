package com.rhett.multivillageselector.commands.generate;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * Handles /mvs generate command
 * TODO: Extract full implementation from MVSCommands
 */
public class GenerateCommands {

    public static int execute(CommandContext<CommandSourceStack> context) {
        // TODO: Extract ~627 lines of generate logic from MVSCommands
        // For now, delegate back to MVSCommands to keep build working
        return com.rhett.multivillageselector.commands.MVSCommands.executeGenerateCommand(context);
    }
}
