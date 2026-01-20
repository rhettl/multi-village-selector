package com.rhett.multivillageselector.yacl;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.ConfigState;
import com.rhett.multivillageselector.config.MVSConfig;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * YACL-based configuration screen for Multi Village Selector.
 * Uses a tabbed category layout that mirrors the JSON5 config structure.
 *
 * Categories:
 * - General (core settings, debug options)
 * - Structures (structure_pool, blacklist, intercepted_sets)
 * - Biomes (biome_frequency map)
 * - Placement (placement rules per structure set)
 * - About (info, credits, docs)
 */
public class MVSConfigScreen {

    /**
     * Creates the YACL config screen.
     * Shows welcome screen if this is first launch, otherwise shows main config.
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
            return createMainConfigScreen(parent, currentState);
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
                                createInfoDialog(parent, state,
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
                                createMainConfigScreen(parent, MVSConfig.getCurrentState())
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
     * Creates the main config screen with tabbed categories.
     * Categories: General | Structures | Biomes | Placement | About
     */
    private static Screen createMainConfigScreen(Screen parent, ConfigState state) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Multi Village Selector Configuration"))

            // ========================================
            // Category: General
            // ========================================
            .category(buildGeneralCategory(state))

            // ========================================
            // Category: Structures
            // ========================================
            .category(buildStructuresCategory(state))

            // ========================================
            // Category: Biomes
            // ========================================
            .category(buildBiomesCategory(state))

            // ========================================
            // Category: Placement
            // ========================================
            .category(buildPlacementCategory(state))

            // ========================================
            // Category: About
            // ========================================
            .category(buildAboutCategory(state))

            .save(() -> {
                // Individual options handle their own saves via MVSConfigSaver
                MVSCommon.LOGGER.info("MVS: Config save completed");
            })

            .build()
            .generateScreen(parent);
    }

    /**
     * General Category: Core settings and debug options
     */
    private static ConfigCategory buildGeneralCategory(ConfigState state) {
        return ConfigCategory.createBuilder()
            .name(Component.literal("General"))
            .tooltip(Component.literal("Core settings and debug options"))

            // Group: Core Settings
            .group(OptionGroup.createBuilder()
                .name(Component.literal("Core Settings"))
                .description(OptionDescription.of(Component.literal(
                    "Essential settings that control the mod's behavior")))

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
                        "Bypass vanilla's placement-point biome check.\n\n" +
                        "Useful for 3D biome mods like Terralith where structures may\n" +
                        "shift across biome layers during terrain adaptation.\n\n" +
                        "When enabled, MVS uses chunk center biome for selection\n" +
                        "instead of exact placement point.")))
                    .binding(
                        false,
                        () -> state.relaxedBiomeValidation,
                        newValue -> MVSConfigSaver.saveRelaxedBiomeValidation(newValue)
                    )
                    .controller(TickBoxControllerBuilder::create)
                    .build())

                .build())

            // Group: Debug Options
            .group(OptionGroup.createBuilder()
                .name(Component.literal("Debug Options"))
                .description(OptionDescription.of(Component.literal(
                    "Advanced options for debugging and troubleshooting")))

                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Debug Logging"))
                    .description(OptionDescription.of(Component.literal(
                        "Enable verbose logging for structure selection.\n\n" +
                        "Shows detailed information about:\n" +
                        "• Biome pattern matching\n" +
                        "• Weight calculations\n" +
                        "• Structure picks\n\n" +
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

            .build();
    }

    /**
     * Structures Category: structure_pool, blacklist, intercepted_structure_sets
     */
    private static ConfigCategory buildStructuresCategory(ConfigState state) {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Structures"))
            .tooltip(Component.literal("Configure structure pools and selection"))

            // Group: Intercepted Structure Sets
            .group(OptionGroup.createBuilder()
                .name(Component.literal("Intercepted Structure Sets"))
                .description(OptionDescription.of(Component.literal(
                    "Structure sets that MVS takes control of.\n" +
                    "Usually: minecraft:villages")))

                .option(Option.<String>createBuilder()
                    .name(Component.literal("Intercepted Sets"))
                    .description(OptionDescription.of(Component.literal(
                        "Currently intercepting:\n" +
                        String.join("\n", state.interceptStructureSets) + "\n\n" +
                        "Edit the JSON5 config file to modify this list.")))
                    .binding(
                        "",
                        () -> String.format("%d sets", state.interceptStructureSets.size()),
                        newValue -> {}
                    )
                    .controller(opt -> StringControllerBuilder.create(opt))
                    .build())

                .build())

            // Group: Structure Pool
            .group(OptionGroup.createBuilder()
                .name(Component.literal("Structure Pool"))
                .description(OptionDescription.of(Component.literal(
                    "Array of structures that can be selected for spawning.\n" +
                    "Each entry has: structure (id or pattern) + biomes (map of biome→weight)")))

                .option(Option.<String>createBuilder()
                    .name(Component.literal("Pool Entries"))
                    .description(OptionDescription.of(Component.literal(
                        "Structure pool defines which villages can spawn and where.\n\n" +
                        "Each entry contains:\n" +
                        "• structure: Structure ID or wildcard (e.g., 'ctov:*')\n" +
                        "• biomes: Map of biome patterns to spawn weights\n\n" +
                        "Supports wildcards (*) and tags (#minecraft:is_plains)\n\n" +
                        "Dynamic list editor coming soon!\n" +
                        "For now, edit the JSON5 file manually.")))
                    .binding(
                        "",
                        () -> String.format("%d entries configured", state.structurePoolRaw.size()),
                        newValue -> {}
                    )
                    .controller(opt -> StringControllerBuilder.create(opt))
                    .build())

                .build())

            // Group: Blacklist
            .group(OptionGroup.createBuilder()
                .name(Component.literal("Blacklist"))
                .description(OptionDescription.of(Component.literal(
                    "Array of structure IDs that will never spawn")))

                .option(Option.<String>createBuilder()
                    .name(Component.literal("Blacklisted Structures"))
                    .description(OptionDescription.of(Component.literal(
                        "These structures are completely disabled:\n\n" +
                        (state.blacklistedStructures.isEmpty()
                            ? "None"
                            : String.join("\n", state.blacklistedStructures)) + "\n\n" +
                        "Edit the JSON5 config file to modify the blacklist.")))
                    .binding(
                        "",
                        () -> state.blacklistedStructures.isEmpty()
                            ? "None"
                            : String.format("%d structures", state.blacklistedStructures.size()),
                        newValue -> {}
                    )
                    .controller(opt -> StringControllerBuilder.create(opt))
                    .build())

                .build())

            .build();
    }

    /**
     * Biomes Category: biome_frequency map
     */
    private static ConfigCategory buildBiomesCategory(ConfigState state) {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Biomes"))
            .tooltip(Component.literal("Control spawn frequency per biome"))

            // Group: Biome Frequency Map
            .group(OptionGroup.createBuilder()
                .name(Component.literal("Biome Frequency"))
                .description(OptionDescription.of(Component.literal(
                    "Map of biome patterns to frequency multipliers.\n" +
                    "Values: 0.0 (never spawn) to 1.0 (normal frequency)\n\n" +
                    "Examples:\n" +
                    "\"#minecraft:is_ocean\": 0.3 → 30% spawn rate in oceans\n" +
                    "\"minecraft:desert\": 0.8 → 80% spawn rate in desert")))

                .option(Option.<String>createBuilder()
                    .name(Component.literal("Configured Biomes"))
                    .description(OptionDescription.of(Component.literal(
                        "Biomes with custom spawn frequencies:\n\n" +
                        (state.biomeFrequency.isEmpty()
                            ? "None configured (all biomes use 100%)"
                            : formatBiomeFrequencyMap(state)) + "\n\n" +
                        "Map editor coming soon!\n" +
                        "Edit JSON5 config to modify biome frequencies.")))
                    .binding(
                        "",
                        () -> state.biomeFrequency.isEmpty()
                            ? "None (100% everywhere)"
                            : String.format("%d rules", state.biomeFrequency.size()),
                        newValue -> {}
                    )
                    .controller(opt -> StringControllerBuilder.create(opt))
                    .build())

                .build())

            .build();
    }

    /**
     * Placement Category: placement map (per-structure-set rules)
     */
    private static ConfigCategory buildPlacementCategory(ConfigState state) {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Placement"))
            .tooltip(Component.literal("Configure structure spacing and distribution"))

            // Group: Per-Structure-Set Placement Rules
            .group(OptionGroup.createBuilder()
                .name(Component.literal("Placement Rules"))
                .description(OptionDescription.of(Component.literal(
                    "Map of structure set IDs to placement configuration.\n\n" +
                    "Each entry can configure:\n" +
                    "• spacing - Grid cell size in chunks\n" +
                    "• separation - Minimum distance between structures\n" +
                    "• spreadType - Distribution pattern (enum)\n" +
                    "• strategy - Placement strategy (enum)\n" +
                    "• exclusion_zone - Avoid other structure sets")))

                .option(Option.<String>createBuilder()
                    .name(Component.literal("Configured Structure Sets"))
                    .description(OptionDescription.of(Component.literal(
                        "Per-structure-set placement configuration:\n\n" +
                        (state.placement.isEmpty()
                            ? "None (using vanilla defaults)"
                            : formatPlacementMap(state)) + "\n\n" +
                        "Nested object editor coming soon!\n" +
                        "Edit JSON5 config to modify placement rules.")))
                    .binding(
                        "",
                        () -> state.placement.isEmpty()
                            ? "Using vanilla defaults"
                            : String.format("%d sets configured", state.placement.size()),
                        newValue -> {}
                    )
                    .controller(opt -> StringControllerBuilder.create(opt))
                    .build())

                .build())

            .build();
    }

    /**
     * About Category: Info, credits, documentation
     */
    private static ConfigCategory buildAboutCategory(ConfigState state) {
        return ConfigCategory.createBuilder()
            .name(Component.literal("About"))
            .tooltip(Component.literal("Information about Multi Village Selector"))

            // Group: Information
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

            .build();
    }

    /**
     * Helper: Format biome_frequency map for display
     */
    private static String formatBiomeFrequencyMap(ConfigState state) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (var entry : state.biomeFrequency.entrySet()) {
            if (count++ < 5) {
                sb.append(String.format("• %s: %.1f%%\n", entry.getKey(), entry.getValue() * 100));
            }
        }
        if (state.biomeFrequency.size() > 5) {
            sb.append(String.format("... and %d more", state.biomeFrequency.size() - 5));
        }
        return sb.toString();
    }

    /**
     * Helper: Format placement map for display
     */
    private static String formatPlacementMap(ConfigState state) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (var entry : state.placement.entrySet()) {
            if (count++ < 3) {
                sb.append(String.format("• %s\n", entry.getKey()));
                var rule = entry.getValue();
                if (rule.spacing != null) sb.append(String.format("  spacing: %d\n", rule.spacing));
                if (rule.separation != null) sb.append(String.format("  separation: %d\n", rule.separation));
                if (rule.spreadType != null) sb.append(String.format("  spreadType: %s\n", rule.spreadType));
            }
        }
        if (state.placement.size() > 3) {
            sb.append(String.format("... and %d more", state.placement.size() - 3));
        }
        return sb.toString();
    }

    /**
     * Creates a simple info dialog with a message and OK button.
     */
    private static Screen createInfoDialog(Screen parent, ConfigState state, String message) {
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
