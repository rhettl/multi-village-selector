package com.rhett.multivillageselector.yacl;

import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.ConfigLoader;
import com.rhett.multivillageselector.config.ConfigParser;
import com.rhett.multivillageselector.config.ConfigState;
import com.rhett.multivillageselector.config.MVSConfig;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * YACL-based configuration screen for Multi Village Selector.
 * This class is only loaded when YACL is present.
 *
 * The GUI provides a user-friendly way to edit configuration,
 * but the underlying JSON5 file remains the source of truth.
 */
public class MVSConfigScreen {

    /**
     * Creates the YACL config screen.
     *
     * @param parent The parent screen to return to when done
     * @return The configured YACL screen
     */
    public static Screen createScreen(Screen parent) {
        MVSCommon.LOGGER.info("MVS: Creating YACL config screen");

        // Get current config state
        ConfigState currentState = MVSConfig.getCurrentState();

        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Multi Village Selector Configuration"))

            // Category: General Settings
            .category(buildGeneralCategory(currentState))

            // Category: Feature Flags
            .category(buildFeatureFlagsCategory(currentState))

            // Category: Structure Configuration
            .category(buildStructureCategory(currentState))

            // Save handler - writes back to JSON5 file
            .save(() -> {
                MVSCommon.LOGGER.info("MVS: Config GUI save triggered");
                // Note: For now, this is a placeholder
                // In a full implementation, we'd serialize the current values back to JSON5
                MVSCommon.LOGGER.warn("MVS: Config GUI saving not yet implemented - please edit JSON5 manually for now");
            })

            .build()
            .generateScreen(parent);
    }

    /**
     * Builds the General Settings category.
     */
    private static ConfigCategory buildGeneralCategory(ConfigState state) {
        return ConfigCategory.createBuilder()
            .name(Component.literal("General Settings"))
            .tooltip(Component.literal("Basic mod configuration"))

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
                        true,  // default
                        () -> state.enabled,  // getter
                        newValue -> { }  // setter (placeholder for now)
                    )
                    .controller(BooleanControllerBuilder::create)
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
                        newValue -> { }
                    )
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .build())

            .build();
    }

    /**
     * Builds the Feature Flags category.
     */
    private static ConfigCategory buildFeatureFlagsCategory(ConfigState state) {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Debug & Features"))
            .tooltip(Component.literal("Advanced features and debugging options"))

            .group(OptionGroup.createBuilder()
                .name(Component.literal("Debug Options"))
                .description(OptionDescription.of(Component.literal(
                    "Options for debugging and troubleshooting")))

                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Debug Logging"))
                    .description(OptionDescription.of(Component.literal(
                        "Enable verbose logging for structure selection.\n" +
                        "Shows detailed information about biome matching, weight calculations, and structure picks.\n" +
                        "Warning: May spam logs during world generation!")))
                    .binding(
                        false,
                        () -> state.debugLogging,
                        newValue -> { }
                    )
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Debug Commands"))
                    .description(OptionDescription.of(Component.literal(
                        "Enable advanced debug commands like /mvs debug.\n" +
                        "For advanced users and developers only.")))
                    .binding(
                        false,
                        () -> state.debugCmd,
                        newValue -> { }
                    )
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Show Launch Message"))
                    .description(OptionDescription.of(Component.literal(
                        "Display a message in chat when joining a world.\n" +
                        "Confirms that MVS is active and loaded.")))
                    .binding(
                        false,
                        () -> state.showLaunchMessage,
                        newValue -> { }
                    )
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .build())

            .build();
    }

    /**
     * Builds the Structure Configuration category.
     * This is a placeholder for now - full structure pool editing will be complex.
     */
    private static ConfigCategory buildStructureCategory(ConfigState state) {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Structure Configuration"))
            .tooltip(Component.literal("Configure structure pools and placement"))

            .group(OptionGroup.createBuilder()
                .name(Component.literal("Information"))
                .description(OptionDescription.of(Component.literal(
                    "Advanced structure configuration")))

                .option(Option.<String>createBuilder()
                    .name(Component.literal("Structure Pool"))
                    .description(OptionDescription.of(Component.literal(
                        "The structure pool defines which villages can spawn and where.\n\n" +
                        "This is a complex configuration with patterns, weights, and biome mappings.\n" +
                        "For now, please edit the JSON5 file manually for structure pool changes.\n\n" +
                        "GUI-based structure pool editor coming in a future update!")))
                    .binding(
                        "Edit JSON5 manually",
                        () -> String.format("%d structure pool entries configured", state.structurePoolRaw.size()),
                        newValue -> { }
                    )
                    .controller(opt -> StringControllerBuilder.create(opt))
                    .build())

                .build())

            .build();
    }
}
