package com.rhett.multivillageselector;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("multivillageselector")
public class MultiVillageSelector {
    public static final String MOD_ID = "multivillageselector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MultiVillageSelector(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);

        // Load configuration
        MVSConfig.load();

        LOGGER.info("Multi Village Selector (MVS) mod initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register event handler
        NeoForge.EVENT_BUS.register(new VillageReplacementHandler());

        // Register server starting handler for structure discovery and commands
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

        LOGGER.info("Multi Village Selector (MVS) mod setup complete");
    }

    private void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        Registry<Structure> structureRegistry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);

        // Discover structures based on config patterns
        MVSConfig.discoverStructures(structureRegistry);

        // Register commands
        LOGGER.info("Registering MVS commands...");
        try {
            MVSCommands.register(server.getCommands().getDispatcher());
            LOGGER.info("MVS commands registered successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to register MVS commands", e);
        }
    }
}
