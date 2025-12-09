package com.rhett.multivillageselector;

import com.rhett.multivillageselector.commands.MVSCommands;
import com.rhett.multivillageselector.config.MVSConfig;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import com.rhett.multivillageselector.util.MVSStructureAccessor;

/**
 * v0.3.0: Cross-platform event registration using Architectury API
 * Replaces NeoForge.EVENT_BUS with Architectury's LifecycleEvent
 *
 * v0.4.0: Structure discovery moved to SERVER_BEFORE_START to ensure
 * the structure pool is ready before spawn chunks are generated.
 * Event order: SERVER_BEFORE_START → loadLevel() (spawn chunks) → SERVER_STARTING
 */
public class ModEvents {

    public static void register() {
        // Server about to start - structure set injection + structure discovery
        // Equivalent to ServerAboutToStartEvent
        // CRITICAL: This runs BEFORE loadLevel() which generates spawn chunks.
        // Structure discovery must happen here so MVS can select structures
        // during spawn chunk generation.
        LifecycleEvent.SERVER_BEFORE_START.register(server -> {
            StructureSetInjector.onServerAboutToStart(server);

            // Discover structures based on config patterns
            // Must happen BEFORE spawn chunks are generated (during loadLevel)
            net.minecraft.core.RegistryAccess registryAccess = server.registryAccess();
            Registry<Structure> structureRegistry =
                registryAccess.registryOrThrow(Registries.STRUCTURE);
            MVSConfig.discoverStructures(registryAccess, structureRegistry);

            // v0.4.0: Inject structure IDs into Structure instances for StructureMixin
            // This allows Structure.biomes() intercept to know which structure it's handling
            initializeStructureIds(structureRegistry, registryAccess);
        });

        // Server starting - command registration only
        // Equivalent to ServerStartingEvent
        // This runs AFTER loadLevel() - spawn chunks already generated
        LifecycleEvent.SERVER_STARTING.register(server -> {
            // Register commands (requires CommandDispatcher which is ready now)
            MVSCommon.LOGGER.info("Registering MVS commands...");
            try {
                MVSCommands.register(server.getCommands().getDispatcher());
                MVSCommon.LOGGER.info("MVS commands registered successfully");
            } catch (Exception e) {
                MVSCommon.LOGGER.error("Failed to register MVS commands", e);
            }
        });

        // Player join - show launch message if configured
        // Only shown to operators who can run /mvs commands
        PlayerEvent.PLAYER_JOIN.register(ModEvents::onPlayerJoin);

        MVSCommon.LOGGER.info("MVS: Architectury events registered");
    }

    /**
     * v0.4.0: Initialize structure IDs on all Structure instances.
     * This allows StructureMixin.biomes() intercept to identify the structure without
     * external Map lookups - the ID is stored directly on the object.
     */
    private static void initializeStructureIds(
            Registry<Structure> structureRegistry,
            net.minecraft.core.RegistryAccess registryAccess) {

        int count = 0;
        for (var entry : structureRegistry.entrySet()) {
            Structure structure = entry.getValue();
            String structureId = entry.getKey().location().toString();

            // Cast to our duck interface to access injected methods
            ((MVSStructureAccessor) structure).mvs$setStructureId(structureId, registryAccess);
            count++;
        }

        if (MVSConfig.debugLogging) {
            MVSCommon.LOGGER.info("[MVS] Initialized structure IDs on {} structures", count);
        }
    }

    /**
     * Send launch message to operators when they join, if configured.
     * Guides new users to run /mvs generate or /mvs help.
     */
    private static void onPlayerJoin(ServerPlayer player) {
        if (!MVSConfig.showLaunchMessage) {
            return;
        }

        // Only show to players who can run /mvs commands (op level 2+)
        if (!player.hasPermissions(2)) {
            return;
        }

        // Build the message with clickable commands and separators
        Component separator = Component.literal("──────────────────────────────────")
            .withStyle(ChatFormatting.DARK_GRAY);

        Component message = Component.literal("[MVS] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Multi Village Selector active but not configured.")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal("\n      Run ")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal("/mvs generate")
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mvs generate"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to run /mvs generate")))))
            .append(Component.literal(" to create a config, or ")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal("/mvs help")
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mvs help"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to run /mvs help")))))
            .append(Component.literal(" for commands.")
                .withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(separator);
        player.sendSystemMessage(message);
        player.sendSystemMessage(separator);
    }
}
