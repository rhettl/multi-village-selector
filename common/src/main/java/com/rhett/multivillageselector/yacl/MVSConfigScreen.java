package com.rhett.multivillageselector.yacl;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.ConfigState;
import com.rhett.multivillageselector.config.MVSConfig;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

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
        ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
            .name(Component.literal("Structures"))
            .tooltip(Component.literal("Configure structure pools and selection"));

        // Group: Intercepted Structure Sets
        categoryBuilder.group(OptionGroup.createBuilder()
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

            .build());

        // Group: Structure Pool - Show each entry
        if (state.structurePoolRaw.isEmpty()) {
            // Empty pool - show message
            categoryBuilder.group(OptionGroup.createBuilder()
                .name(Component.literal("Structure Pool"))
                .description(OptionDescription.of(Component.literal(
                    "No structures configured.\n" +
                    "Run '/mvs generate' to auto-populate.")))

                .option(ButtonOption.createBuilder()
                    .name(Component.literal("📝 Edit in JSON5"))
                    .description(OptionDescription.of(Component.literal(
                        "The structure pool is empty.\n\n" +
                        "To add structures:\n" +
                        "1. Run '/mvs generate' in-game, or\n" +
                        "2. Edit config/multivillageselector.json5 manually\n\n" +
                        "Dynamic list editor coming in a future update!")))
                    .action((screen, button) -> {})
                    .build())

                .build());
        } else {
            // Show structure pool entries (limit to first 10 for performance)
            int entriesToShow = Math.min(10, state.structurePoolRaw.size());

            for (int i = 0; i < entriesToShow; i++) {
                final int index = i;
                MVSConfig.RawConfigEntry entry = state.structurePoolRaw.get(i);

                String entryName = entry.isEmpty
                    ? String.format("[%d] Empty Entry", i + 1)
                    : String.format("[%d] %s", i + 1, entry.structure);

                String entryDesc = entry.isEmpty
                    ? "Weighted empty entry (allows 'no spawn' outcome)"
                    : formatStructurePoolEntry(entry);

                categoryBuilder.group(OptionGroup.createBuilder()
                    .name(Component.literal(entryName))
                    .collapsed(true)  // Collapsed by default to avoid clutter
                    .description(OptionDescription.of(Component.literal(entryDesc)))

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Structure"))
                        .description(OptionDescription.of(Component.literal(
                            entry.isEmpty
                                ? "This is an empty entry (no structure)"
                                : "Structure: " + entry.structure + "\n\n" +
                                  "Supports wildcards:\n" +
                                  "• 'ctov:*' - All CTOV structures\n" +
                                  "• 'minecraft:village_*' - All vanilla villages")))
                        .binding(
                            "",
                            () -> entry.isEmpty ? "[Empty]" : entry.structure,
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Biomes"))
                        .description(OptionDescription.of(Component.literal(
                            "Biome patterns and their spawn weights:\n\n" +
                            formatBiomeMapDetailed(entry.biomes) + "\n\n" +
                            "Edit JSON5 config to modify biome mappings.")))
                        .binding(
                            "",
                            () -> String.format("%d biome rules", entry.biomes.size()),
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .build());
            }

            // If there are more than 10 entries, show a message
            if (state.structurePoolRaw.size() > 10) {
                categoryBuilder.group(OptionGroup.createBuilder()
                    .name(Component.literal("..."))
                    .description(OptionDescription.of(Component.literal(
                        String.format("+ %d more entries\n\n" +
                            "Showing first 10 of %d total entries.\n" +
                            "Edit JSON5 config to view/modify all entries.",
                            state.structurePoolRaw.size() - 10,
                            state.structurePoolRaw.size()))))

                    .option(ButtonOption.createBuilder()
                        .name(Component.literal("📝 View All in JSON5"))
                        .description(OptionDescription.of(Component.literal(
                            "Too many entries to show in GUI.\n" +
                            "Edit config/multivillageselector.json5 to see all entries.")))
                        .action((screen, button) -> {})
                        .build())

                    .build());
            }
        }

        // Group: Blacklist
        categoryBuilder.group(OptionGroup.createBuilder()
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

            .build());

        return categoryBuilder.build();
    }

    /**
     * Helper: Format a structure pool entry for display
     */
    private static String formatStructurePoolEntry(MVSConfig.RawConfigEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("Structure: ").append(entry.structure).append("\n\n");
        sb.append("Biomes (").append(entry.biomes.size()).append(" rules):\n");

        int count = 0;
        for (Map.Entry<String, Integer> biomeEntry : entry.biomes.entrySet()) {
            if (count++ < 5) {
                sb.append("• ").append(biomeEntry.getKey())
                  .append(": weight ").append(biomeEntry.getValue()).append("\n");
            }
        }

        if (entry.biomes.size() > 5) {
            sb.append("... and ").append(entry.biomes.size() - 5).append(" more");
        }

        return sb.toString();
    }

    /**
     * Helper: Format biome map in detail
     */
    private static String formatBiomeMapDetailed(Map<String, Integer> biomes) {
        if (biomes.isEmpty()) {
            return "No biome rules configured";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : biomes.entrySet()) {
            sb.append("• ").append(entry.getKey())
              .append(" → weight: ").append(entry.getValue()).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Biomes Category: biome_frequency map
     */
    private static ConfigCategory buildBiomesCategory(ConfigState state) {
        ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
            .name(Component.literal("Biomes"))
            .tooltip(Component.literal("Control spawn frequency per biome"));

        if (state.biomeFrequency.isEmpty()) {
            // No frequency rules - show default behavior
            categoryBuilder.group(OptionGroup.createBuilder()
                .name(Component.literal("Biome Frequency"))
                .description(OptionDescription.of(Component.literal(
                    "No custom frequency rules configured.\n\n" +
                    "All biomes use 100% spawn rate (normal frequency).\n\n" +
                    "You can add frequency multipliers to reduce spawn rates\n" +
                    "in specific biomes. Values: 0.0 (never) to 1.0 (normal).")))

                .option(ButtonOption.createBuilder()
                    .name(Component.literal("📝 Add Rules in JSON5"))
                    .description(OptionDescription.of(Component.literal(
                        "To add biome frequency rules:\n\n" +
                        "Edit config/multivillageselector.json5\n" +
                        "Add to biome_frequency section:\n\n" +
                        "\"#minecraft:is_ocean\": 0.3  → 30% in oceans\n" +
                        "\"minecraft:desert\": 0.8      → 80% in desert\n\n" +
                        "Map editor coming in a future update!")))
                    .action((screen, button) -> {})
                    .build())

                .build());
        } else {
            // Show each biome frequency rule
            OptionGroup.Builder groupBuilder = OptionGroup.createBuilder()
                .name(Component.literal("Biome Frequency Rules"))
                .description(OptionDescription.of(Component.literal(
                    String.format("%d biome rules configured\n\n" +
                        "Values: 0.0 (never spawn) to 1.0 (normal frequency)\n" +
                        "Biomes not listed use 100%% spawn rate.",
                        state.biomeFrequency.size()))));

            for (Map.Entry<String, Double> entry : state.biomeFrequency.entrySet()) {
                final String biomePattern = entry.getKey();
                final double initialFrequency = entry.getValue();

                groupBuilder.option(Option.<Double>createBuilder()
                    .name(Component.literal(biomePattern))
                    .description(OptionDescription.of(Component.literal(
                        String.format("Spawn frequency multiplier\n\n" +
                            "%s\n\n" +
                            "Values:\n" +
                            "• 0.0 = Never spawn\n" +
                            "• 0.5 = 50%% spawn rate\n" +
                            "• 1.0 = Normal (100%%) spawn rate\n\n" +
                            "Drag the slider to adjust the spawn frequency.",
                            getBiomePatternExplanation(biomePattern)))))
                    .binding(
                        initialFrequency,  // default
                        () -> MVSConfig.biomeFrequency.getOrDefault(biomePattern, initialFrequency),  // getter
                        newValue -> MVSConfigSaver.saveBiomeFrequency(biomePattern, newValue)  // setter
                    )
                    .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                        .range(0.0, 1.0)
                        .step(0.05)
                        .formatValue(val -> Component.literal(String.format("%.0f%%", val * 100))))
                    .build());
            }

            categoryBuilder.group(groupBuilder.build());
        }

        return categoryBuilder.build();
    }

    /**
     * Helper: Explain what a biome pattern matches
     */
    private static String getBiomePatternExplanation(String pattern) {
        if (pattern.startsWith("#")) {
            return "Tag pattern - matches all biomes in tag: " + pattern;
        } else if (pattern.contains("*")) {
            return "Wildcard pattern - matches multiple biomes: " + pattern;
        } else {
            return "Direct biome ID: " + pattern;
        }
    }

    /**
     * Placement Category: placement map (per-structure-set rules)
     */
    private static ConfigCategory buildPlacementCategory(ConfigState state) {
        ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
            .name(Component.literal("Placement"))
            .tooltip(Component.literal("Configure structure spacing and distribution"));

        if (state.placement.isEmpty()) {
            // No custom placement rules - using vanilla defaults
            categoryBuilder.group(OptionGroup.createBuilder()
                .name(Component.literal("Placement Rules"))
                .description(OptionDescription.of(Component.literal(
                    "No custom placement rules configured.\n\n" +
                    "Using vanilla defaults for all structure sets.\n\n" +
                    "You can configure:\n" +
                    "• spacing - Grid cell size in chunks\n" +
                    "• separation - Min distance between structures\n" +
                    "• spreadType - Distribution pattern\n" +
                    "• strategy - Placement strategy\n" +
                    "• exclusion_zone - Avoid other structure sets")))

                .option(ButtonOption.createBuilder()
                    .name(Component.literal("📝 Add Rules in JSON5"))
                    .description(OptionDescription.of(Component.literal(
                        "To configure placement rules:\n\n" +
                        "Edit config/multivillageselector.json5\n" +
                        "Add to placement section.\n\n" +
                        "Object editor coming in a future update!")))
                    .action((screen, button) -> {})
                    .build())

                .build());
        } else {
            // Show each structure set's placement rules
            for (Map.Entry<String, com.rhett.multivillageselector.config.PlacementRule> entry : state.placement.entrySet()) {
                final String structureSet = entry.getKey();
                final com.rhett.multivillageselector.config.PlacementRule rule = entry.getValue();

                categoryBuilder.group(OptionGroup.createBuilder()
                    .name(Component.literal(structureSet))
                    .collapsed(true)
                    .description(OptionDescription.of(Component.literal(
                        formatPlacementRuleDescription(rule))))

                    // Spacing option
                    .option(Option.<Integer>createBuilder()
                        .name(Component.literal("Spacing"))
                        .description(OptionDescription.of(Component.literal(
                            "Grid cell size in chunks.\n\n" +
                            "Determines how far apart structure attempts are made.\n" +
                            "Vanilla villages use 34 chunks.\n\n" +
                            "Range: 1-256 chunks")))
                        .binding(
                            rule.spacing != null ? rule.spacing : 34,  // default to vanilla
                            () -> rule.spacing != null ? rule.spacing : 34,
                            newValue -> {
                                MVSConfigSaver.savePlacementRuleField(structureSet, "spacing", String.valueOf(newValue));
                                rule.spacing = newValue;
                            }
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                            .range(1, 256)
                            .step(1)
                            .formatValue(val -> Component.literal(val + " chunks")))
                        .build())

                    // Separation option
                    .option(Option.<Integer>createBuilder()
                        .name(Component.literal("Separation"))
                        .description(OptionDescription.of(Component.literal(
                            "Minimum distance between structures in chunks.\n\n" +
                            "Must be less than spacing.\n" +
                            "Vanilla villages use 8 chunks.\n\n" +
                            "Range: 1-256 chunks")))
                        .binding(
                            rule.separation != null ? rule.separation : 8,  // default to vanilla
                            () -> rule.separation != null ? rule.separation : 8,
                            newValue -> {
                                MVSConfigSaver.savePlacementRuleField(structureSet, "separation", String.valueOf(newValue));
                                rule.separation = newValue;
                            }
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                            .range(1, 256)
                            .step(1)
                            .formatValue(val -> Component.literal(val + " chunks")))
                        .build())

                    // Spread Type option
                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Spread Type"))
                        .description(OptionDescription.of(Component.literal(
                            "Distribution pattern within grid cell.\n\n" +
                            "Options:\n" +
                            "• linear - Uniform random\n" +
                            "• triangular - Center-biased\n" +
                            "• gaussian - Strongly center-biased\n" +
                            "• edge_biased - Prefer cell edges\n" +
                            "• corner_biased - Prefer cell corners\n" +
                            "• fixed_center - Always at exact center\n\n" +
                            (rule.spreadType != null
                                ? "Current: " + rule.spreadType
                                : "Using vanilla default (linear)"))))
                        .binding(
                            "",
                            () -> rule.spreadType != null ? rule.spreadType : "Default (linear)",
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    // Strategy option
                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Strategy"))
                        .description(OptionDescription.of(Component.literal(
                            "Placement strategy type.\n\n" +
                            "Options:\n" +
                            "• random_spread - Vanilla random placement\n" +
                            "• concentric_rings - Stronghold-like rings\n\n" +
                            (rule.strategy != null
                                ? "Current: " + rule.strategy
                                : "Using vanilla default (random_spread)"))))
                        .binding(
                            "",
                            () -> rule.strategy != null ? rule.strategy : "Default (random_spread)",
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    // Exclusion Zone option (if present)
                    .option(Option.<String>createBuilder()
                        .name(Component.literal("Exclusion Zone"))
                        .description(OptionDescription.of(Component.literal(
                            rule.exclusionZone != null
                                ? "Avoid spawning near:\n" +
                                  "• Structure set: " + rule.exclusionZone.otherSet + "\n" +
                                  "• Distance: " + rule.exclusionZone.chunkCount + " chunks"
                                : "No exclusion zone configured.\n\n" +
                                  "Exclusion zones prevent this structure from spawning\n" +
                                  "too close to another structure set.")))
                        .binding(
                            "",
                            () -> rule.exclusionZone != null
                                ? "Avoid " + rule.exclusionZone.otherSet
                                : "None",
                            newValue -> {}
                        )
                        .controller(opt -> StringControllerBuilder.create(opt))
                        .build())

                    .build());
            }
        }

        return categoryBuilder.build();
    }

    /**
     * Helper: Format placement rule description
     */
    private static String formatPlacementRuleDescription(com.rhett.multivillageselector.config.PlacementRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("Custom placement configuration:\n\n");

        if (rule.spacing != null) {
            sb.append("Spacing: ").append(rule.spacing).append(" chunks\n");
        }
        if (rule.separation != null) {
            sb.append("Separation: ").append(rule.separation).append(" chunks\n");
        }
        if (rule.spreadType != null) {
            sb.append("Spread: ").append(rule.spreadType).append("\n");
        }
        if (rule.strategy != null) {
            sb.append("Strategy: ").append(rule.strategy).append("\n");
        }
        if (rule.exclusionZone != null) {
            sb.append("Exclusion: Avoid ").append(rule.exclusionZone.otherSet)
              .append(" (").append(rule.exclusionZone.chunkCount).append(" chunks)");
        }

        return sb.toString();
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
