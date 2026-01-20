package com.rhett.multivillageselector.yacl;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.ConfigState;
import com.rhett.multivillageselector.config.MVSConfig;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * YACL-based configuration screen for Multi Village Selector.
 * This class is only loaded when YACL is present.
 *
 * Provides a navigation-based GUI with:
 * - Welcome screen on first launch
 * - Main navigation to different config sections
 * - Individual views for Structure, Biome, Placement, Debug, and Info
 */
public class MVSConfigScreen {

    /**
     * Creates the YACL config screen.
     * Shows welcome screen if this is first launch, otherwise shows main navigation.
     *
     * @param parent The parent screen to return to when done
     * @return The configured YACL screen
     */
    public static Screen createScreen(Screen parent) {
        MVSCommon.LOGGER.info("MVS: Creating YACL config screen");

        // Get current config state
        ConfigState currentState = MVSConfig.getCurrentState();

        // Check if this is first launch
        if (currentState.showLaunchMessage) {
            return createWelcomeScreen(parent, currentState);
        } else {
            return createMainNavigationScreen(parent, currentState);
        }
    }

    /**
     * Creates the welcome screen shown on first launch.
     * Recommends running /mvs generate and offers Generate or Skip buttons.
     */
    private static Screen createWelcomeScreen(Screen parent, ConfigState state) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Welcome to Multi Village Selector!"))

            .category(ConfigCategory.createBuilder()
                .name(Component.literal("First Time Setup"))

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Welcome!"))
                    .description(OptionDescription.of(Component.literal(
                        "Thank you for installing Multi Village Selector!\n\n" +
                        "This mod allows you to control which village structures spawn in your world.\n\n" +
                        "We recommend running the config generator to automatically detect\n" +
                        "all installed village mods and create an optimized configuration.")))

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("🔧 Generate Config"))
                        .description(OptionDescription.of(Component.literal(
                            "Automatically scan for village structures and generate a config.\n\n" +
                            "This will:\n" +
                            "• Detect vanilla and modded village structures\n" +
                            "• Create biome-appropriate spawn rules\n" +
                            "• Set recommended weights and placement rules\n\n" +
                            "You can run '/mvs generate' in-game to do this later.")))
                        .action((screen, button) -> {
                            // TODO: Trigger config generation
                            MVSCommon.LOGGER.info("MVS: Generate button clicked");
                            Minecraft.getInstance().setScreen(
                                createInfoScreen(parent, state,
                                    "Config generation via GUI is not yet implemented.\n\n" +
                                    "Please run the command '/mvs generate' in-game instead.\n\n" +
                                    "After generating, you can return to this config screen to review settings.")
                            );
                        })
                        .build())

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("⏭ Skip Setup"))
                        .description(OptionDescription.of(Component.literal(
                            "Skip the welcome screen and go directly to the config menu.\n\n" +
                            "You can generate config later using '/mvs generate' command.")))
                        .action((screen, button) -> {
                            // Disable welcome message and reload screen
                            MVSConfigSaver.saveShowWelcomeMessage(false);
                            MVSCommon.LOGGER.info("MVS: Welcome screen skipped");
                            Minecraft.getInstance().setScreen(
                                createMainNavigationScreen(parent, MVSConfig.getCurrentState())
                            );
                        })
                        .build())

                    .build())

                .build())

            .save(() -> {})  // No save needed for welcome screen
            .build()
            .generateScreen(parent);
    }

    /**
     * Creates the main navigation screen.
     * Shows category buttons to navigate to different config sections.
     */
    private static Screen createMainNavigationScreen(Screen parent, ConfigState state) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Multi Village Selector"))

            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Configuration"))

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Navigate to:"))
                    .description(OptionDescription.of(Component.literal(
                        "Select a category to configure")))

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("🏘 Structure Configuration"))
                        .description(OptionDescription.of(Component.literal(
                            "Configure which village structures can spawn.\n" +
                            "Set up structure pools, blacklists, and biome mappings.")))
                        .action((screen, button) -> {
                            Minecraft.getInstance().setScreen(
                                createStructureScreen(parent, state)
                            );
                        })
                        .build())

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("🌍 Biome Configuration"))
                        .description(OptionDescription.of(Component.literal(
                            "Configure spawn frequency per biome.\n" +
                            "Control how often villages appear in different biomes.")))
                        .action((screen, button) -> {
                            Minecraft.getInstance().setScreen(
                                createBiomeScreen(parent, state)
                            );
                        })
                        .build())

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("📍 Placement Configuration"))
                        .description(OptionDescription.of(Component.literal(
                            "Configure structure placement rules.\n" +
                            "Set spacing, separation, spread types, and exclusion zones.")))
                        .action((screen, button) -> {
                            Minecraft.getInstance().setScreen(
                                createPlacementScreen(parent, state)
                            );
                        })
                        .build())

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("🐛 Debug & Advanced"))
                        .description(OptionDescription.of(Component.literal(
                            "Enable debug logging, debug commands, and advanced options.")))
                        .action((screen, button) -> {
                            Minecraft.getInstance().setScreen(
                                createDebugScreen(parent, state)
                            );
                        })
                        .build())

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("ℹ️ Info & About"))
                        .description(OptionDescription.of(Component.literal(
                            "Information about Multi Village Selector.\n" +
                            "View version, credits, and documentation links.")))
                        .action((screen, button) -> {
                            Minecraft.getInstance().setScreen(
                                createAboutScreen(parent, state)
                            );
                        })
                        .build())

                    .build())

                .build())

            .save(() -> {})  // Save handled by individual screens
            .build()
            .generateScreen(parent);
    }

    /**
     * Creates the Structure Configuration screen.
     */
    private static Screen createStructureScreen(Screen parent, ConfigState state) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Structure Configuration"))

            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Structure Sets"))
                .tooltip(Component.literal("Control which structure sets MVS manages"))

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Intercepted Structure Sets"))
                    .description(OptionDescription.of(Component.literal(
                        "Structure sets that MVS takes control of.\n" +
                        "Usually: minecraft:villages")))

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Intercepted Sets"))
                        .description(OptionDescription.of(Component.literal(
                            "Currently intercepting: " + String.join(", ", state.interceptStructureSets) + "\n\n" +
                            "Edit the JSON5 config file to modify this list.")))
                        .binding(
                            "",
                            () -> String.join(", ", state.interceptStructureSets),
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .build())

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Structure Pool"))
                    .description(OptionDescription.of(Component.literal(
                        "Structures that can be selected for spawning")))

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Pool Status"))
                        .description(OptionDescription.of(Component.literal(
                            "The structure pool defines which villages can spawn and where.\n\n" +
                            "This is a complex configuration with patterns, weights, and biome mappings.\n" +
                            "Please edit the JSON5 file manually for detailed structure pool changes.\n\n" +
                            "GUI-based structure pool editor coming in a future update!")))
                        .binding(
                            "",
                            () -> String.format("%d entries configured", state.structurePoolRaw.size()),
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .build())

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Blacklist"))
                    .description(OptionDescription.of(Component.literal(
                        "Structures that will never spawn")))

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Blacklisted Structures"))
                        .description(OptionDescription.of(Component.literal(
                            "These structures are completely disabled.\n\n" +
                            "Edit the JSON5 config file to modify the blacklist.")))
                        .binding(
                            "",
                            () -> state.blacklistedStructures.isEmpty()
                                ? "None"
                                : String.join(", ", state.blacklistedStructures),
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .build())

                .build())

            .save(() -> {})
            .build()
            .generateScreen(parent);
    }

    /**
     * Creates the Biome Configuration screen.
     */
    private static Screen createBiomeScreen(Screen parent, ConfigState state) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Biome Configuration"))

            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Biome Frequency"))
                .tooltip(Component.literal("Control spawn rates per biome"))

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Frequency Multipliers"))
                    .description(OptionDescription.of(Component.literal(
                        "Adjust how often structures spawn in different biomes.\n" +
                        "Values: 0.0 (never) to 1.0 (normal frequency)\n\n" +
                        "Edit JSON5 config to modify biome frequencies.")))

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Configured Biomes"))
                        .description(OptionDescription.of(Component.literal(
                            "Biomes with custom spawn frequencies.\n\n" +
                            "Example:\n" +
                            "\"#minecraft:is_ocean\": 0.3 → 30% spawn rate in oceans\n" +
                            "\"minecraft:desert\": 0.8 → 80% spawn rate in desert")))
                        .binding(
                            "",
                            () -> state.biomeFrequency.isEmpty()
                                ? "None configured (all biomes use 100%)"
                                : String.format("%d biome rules configured", state.biomeFrequency.size()),
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .build())

                .build())

            .save(() -> {})
            .build()
            .generateScreen(parent);
    }

    /**
     * Creates the Placement Configuration screen.
     */
    private static Screen createPlacementScreen(Screen parent, ConfigState state) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Placement Configuration"))

            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Placement Rules"))
                .tooltip(Component.literal("Configure structure spacing and distribution"))

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Per-Structure-Set Rules"))
                    .description(OptionDescription.of(Component.literal(
                        "Advanced placement control for each structure set.\n\n" +
                        "Configure:\n" +
                        "• Spacing - Grid cell size in chunks\n" +
                        "• Separation - Minimum distance between structures\n" +
                        "• Spread Type - Distribution pattern (linear, gaussian, etc.)\n" +
                        "• Exclusion Zones - Avoid spawning near other structure sets")))

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Configured Rules"))
                        .description(OptionDescription.of(Component.literal(
                            "Per-structure-set placement configuration.\n\n" +
                            "Edit JSON5 config to modify placement rules.")))
                        .binding(
                            "",
                            () -> state.placement.isEmpty()
                                ? "Using vanilla defaults"
                                : String.format("%d structure sets configured", state.placement.size()),
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .build())

                .build())

            .save(() -> {})
            .build()
            .generateScreen(parent);
    }

    /**
     * Creates the Debug & Advanced screen.
     */
    private static Screen createDebugScreen(Screen parent, ConfigState state) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Debug & Advanced"))

            .category(ConfigCategory.createBuilder()
                .name(Component.literal("General"))

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Core Settings"))

                    .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Enable Mod"))
                        .description(OptionDescription.of(Component.literal(
                            "Master switch to enable/disable the entire mod.\n" +
                            "When disabled, vanilla village spawning behavior is restored.")))
                        .binding(
                            true,
                            () -> state.enabled,
                            newValue -> MVSConfigSaver.saveEnabled(newValue)
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                    .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Relaxed Biome Validation"))
                        .description(OptionDescription.of(Component.literal(
                            "Bypass vanilla's placement-point biome check.\n" +
                            "Useful for 3D biome mods like Terralith where structures may shift across biome layers.\n" +
                            "MVS uses chunk center biome for selection instead of exact placement point.")))
                        .binding(
                            false,
                            () -> state.relaxedBiomeValidation,
                            newValue -> MVSConfigSaver.saveRelaxedBiomeValidation(newValue)
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                    .build())

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Debug Options"))

                    .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Debug Logging"))
                        .description(OptionDescription.of(Component.literal(
                            "Enable verbose logging for structure selection.\n" +
                            "Shows detailed information about biome matching, weight calculations, and structure picks.\n\n" +
                            "⚠️ Warning: May spam logs during world generation!")))
                        .binding(
                            false,
                            () -> state.debugLogging,
                            newValue -> MVSConfigSaver.saveDebugLogging(newValue)
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                    .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Debug Commands"))
                        .description(OptionDescription.of(Component.literal(
                            "Enable advanced debug commands like /mvs debug.\n" +
                            "For advanced users and developers only.")))
                        .binding(
                            false,
                            () -> state.debugCmd,
                            newValue -> MVSConfigSaver.saveDebugCmd(newValue)
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                    .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Show Launch Message"))
                        .description(OptionDescription.of(Component.literal(
                            "Display a welcome message in chat when joining a world.\n" +
                            "Confirms that MVS is active and loaded.")))
                        .binding(
                            false,
                            () -> state.showLaunchMessage,
                            newValue -> MVSConfigSaver.saveShowLaunchMessage(newValue)
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                    .build())

                .build())

            .save(() -> {})  // Individual options handle their own saves
            .build()
            .generateScreen(parent);
    }

    /**
     * Creates the About/Info screen.
     */
    private static Screen createAboutScreen(Screen parent, ConfigState state) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("About Multi Village Selector"))

            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Information"))

                .group(OptionGroup.createBuilder()
                    .name(Component.literal("Multi Village Selector"))
                    .description(OptionDescription.of(Component.literal(
                        "A sophisticated village structure management mod for Minecraft.\n\n" +
                        "Brings village variety to your world by intelligently replacing\n" +
                        "vanilla village spawns with villages from multiple mods.")))

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Features"))
                        .description(OptionDescription.of(Component.literal(
                            "• Biome-aware structure selection\n" +
                            "• Weighted spawn pools with wildcard patterns\n" +
                            "• Per-biome spawn frequency control\n" +
                            "• Advanced placement rules (spacing, spread types)\n" +
                            "• Exclusion zones to avoid structure overlap\n" +
                            "• Compatible with CTOV, Towns & Towers, BCA, and more!")))
                        .binding("", () -> "See description", v -> {})
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Documentation"))
                        .description(OptionDescription.of(Component.literal(
                            "Visit the GitHub repository for full documentation:\n" +
                            "https://github.com/RhettL/multi-village-selector\n\n" +
                            "Configuration guide:\n" +
                            "https://github.com/RhettL/multi-village-selector/blob/master/docs/Configuration.md")))
                        .binding("", () -> "github.com/RhettL/multi-village-selector", v -> {})
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Credits"))
                        .description(OptionDescription.of(Component.literal(
                            "Concept: RhettL\n" +
                            "Implementation: Claude Code (Claude Sonnet 4.5) under RhettL's guidance\n\n" +
                            "Special thanks to the Minecraft modding community!")))
                        .binding("", () -> "See description", v -> {})
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .build())

                .build())

            .save(() -> {})
            .build()
            .generateScreen(parent);
    }

    /**
     * Creates a simple info screen with a message and OK button.
     */
    private static Screen createInfoScreen(Screen parent, ConfigState state, String message) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Information"))

            .category(ConfigCategory.createBuilder()
                .name(Component.literal("Message"))

                .group(OptionGroup.createBuilder()
                    .name(Component.literal(""))
                    .description(OptionDescription.of(Component.literal(message)))

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("OK"))
                        .action((screen, button) -> {
                            Minecraft.getInstance().setScreen(parent);
                        })
                        .build())

                    .build())

                .build())

            .save(() -> {})
            .build()
            .generateScreen(parent);
    }
}
