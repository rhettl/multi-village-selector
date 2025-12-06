package com.rhett.multivillageselector;

import com.rhett.multivillageselector.commands.MVSCommands;
import com.rhett.multivillageselector.config.MVSConfig;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;

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

        MVSCommon.LOGGER.info("MVS: Architectury events registered");
    }
}
