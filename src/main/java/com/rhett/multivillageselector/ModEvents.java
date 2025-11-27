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
 */
public class ModEvents {

    public static void register() {
        // Server about to start - structure set injection
        // Equivalent to ServerAboutToStartEvent
        LifecycleEvent.SERVER_BEFORE_START.register(server -> {
            StructureSetInjector.onServerAboutToStart(server);
        });

        // Server starting - structure discovery + command registration
        // Equivalent to ServerStartingEvent
        LifecycleEvent.SERVER_STARTING.register(server -> {
            // Get registry access for structure and biome lookups
            net.minecraft.core.RegistryAccess registryAccess = server.registryAccess();
            Registry<Structure> structureRegistry =
                registryAccess.registryOrThrow(Registries.STRUCTURE);

            // Discover structures based on config patterns
            MVSConfig.discoverStructures(registryAccess, structureRegistry);

            // Register commands
            MultiVillageSelector.LOGGER.info("Registering MVS commands...");
            try {
                MVSCommands.register(server.getCommands().getDispatcher());
                MultiVillageSelector.LOGGER.info("MVS commands registered successfully");
            } catch (Exception e) {
                MultiVillageSelector.LOGGER.error("Failed to register MVS commands", e);
            }
        });

        MultiVillageSelector.LOGGER.info("MVS: Architectury events registered");
    }
}
