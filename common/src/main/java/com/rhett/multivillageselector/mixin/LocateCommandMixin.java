package com.rhett.multivillageselector.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.locate.PredictionHelper;
import com.rhett.multivillageselector.locate.PredictionHelper.ChunkPrediction;
import com.rhett.multivillageselector.util.LocateHelper;
import com.rhett.multivillageselector.util.PlacementResolver;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * Mixin to intercept vanilla /locate structure command for MVS-controlled structures.
 * Uses MVS's prediction logic instead of vanilla's biome-only check.
 */
@Mixin(LocateCommand.class)
public abstract class LocateCommandMixin {

    /**
     * Intercept the locateStructure method to use MVS logic for controlled structures.
     * Note: Method signature uses ResourceOrTagKeyArgument.Result which is converted to HolderSet internally.
     */
    @Inject(
        method = "locateStructure",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onLocateStructure(
            CommandSourceStack source,
            ResourceOrTagKeyArgument.Result<Structure> structureArg,
            CallbackInfoReturnable<Integer> cir
    ) throws CommandSyntaxException {
        if (!MVSConfig.enabled) {
            return; // Let vanilla handle it
        }

        // Convert ResourceOrTagKeyArgument.Result to HolderSet for processing
        Registry<Structure> registry = source.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<HolderSet<Structure>> holderSetOpt = getHolders(structureArg, registry);
        if (holderSetOpt.isEmpty()) {
            return; // Let vanilla handle (will throw error)
        }
        HolderSet<Structure> structures = holderSetOpt.get();

        // Check if any of the requested structures are MVS-controlled
        Optional<String> mvsStructureId = findMVSControlledStructure(structures);
        if (mvsStructureId.isEmpty()) {
            return; // Not MVS-controlled, let vanilla handle it
        }

        String structureId = mvsStructureId.get();

        if (MVSConfig.debugLogging) {
            MVSCommon.LOGGER.info("[MVS] Intercepting /locate for MVS-controlled structure: {}", structureId);
        }

        // Use MVS locate logic
        try {
            int result = executeMVSLocate(source, structureId);
            cir.setReturnValue(result);
        } catch (Exception e) {
            MVSCommon.LOGGER.error("[MVS] Error in /locate interception", e);
            // Let vanilla handle it as fallback
        }
    }

    /**
     * Convert ResourceOrTagKeyArgument.Result to HolderSet.
     * Mirrors vanilla's getHolders logic.
     */
    @SuppressWarnings("unchecked")
    private static Optional<HolderSet<Structure>> getHolders(
            ResourceOrTagKeyArgument.Result<Structure> result,
            Registry<Structure> registry) {
        // Use Either.map with explicit function types to help type inference
        return result.unwrap().map(
            (ResourceKey<Structure> key) -> registry.getHolder(key).map(holder -> (HolderSet<Structure>) HolderSet.direct(holder)),
            (net.minecraft.tags.TagKey<Structure> tag) -> registry.getTag(tag).map(holders -> (HolderSet<Structure>) holders)
        );
    }

    /**
     * Check if any structure in the HolderSet is MVS-controlled.
     * Returns the first MVS-controlled structure ID found.
     */
    private static Optional<String> findMVSControlledStructure(HolderSet<Structure> structures) {
        for (Holder<Structure> holder : structures) {
            Optional<ResourceLocation> keyOpt = holder.unwrapKey().map(k -> k.location());
            if (keyOpt.isEmpty()) continue;

            String structureId = keyOpt.get().toString();

            // Check if this structure is in MVS pool
            boolean inPool = MVSConfig.structurePool.stream()
                .anyMatch(s -> s.structure != null && s.structure.toString().equals(structureId));

            if (inPool) {
                return Optional.of(structureId);
            }
        }

        return Optional.empty();
    }

    /**
     * Execute MVS-aware locate for a structure.
     */
    private static int executeMVSLocate(CommandSourceStack source, String structureId) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        BlockPos startPos = BlockPos.containing(source.getPosition());
        long seed = level.getSeed();

        // Get structure set for placement
        String structureSetId = getStructureSetForStructure(structureId);

        // Create surface-aware biome sampler
        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        var randomState = chunkSource.randomState();
        Climate.Sampler climateSampler = randomState.sampler();
        LocateHelper.BiomeSampler biomeSampler = LocateHelper.createSurfaceAwareBiomeSampler(
            biomeSource, climateSampler, generator, level, randomState);

        // Get placement strategy
        var structureSetRegistry = level.registryAccess()
            .registryOrThrow(Registries.STRUCTURE_SET);
        LocateHelper.PlacementStrategy strategy = LocateHelper.getConfiguredPlacement(structureSetId, structureSetRegistry);

        // Calculate search radius
        int spacing = 34;
        if (strategy instanceof LocateHelper.RandomSpreadPlacement rsp) {
            spacing = rsp.spacing;
        }
        int searchRadius = Math.max(200, spacing * 5);

        // Find the structure
        List<ChunkPrediction> results = PredictionHelper.findChunksForStructureWithConfig(
            structureId, structureSetId, startPos, seed, biomeSampler,
            structureSetRegistry, 1, searchRadius);

        if (results.isEmpty()) {
            // Use vanilla's exception for consistency
            throw new DynamicCommandExceptionType(
                name -> Component.translatable("commands.locate.structure.not_found", name)
            ).create(structureId);
        }

        ChunkPrediction result = results.get(0);

        // Send result in vanilla format
        return sendLocateResult(source, structureId, startPos, result);
    }

    /**
     * Send locate result in vanilla-compatible format.
     */
    private static int sendLocateResult(
            CommandSourceStack source,
            String structureId,
            BlockPos startPos,
            ChunkPrediction result) {

        BlockPos pos = result.worldPos;
        int distance = result.distanceFromStart;

        // Create clickable coordinates (vanilla style)
        Component coordsComponent = ComponentUtils.wrapInSquareBrackets(
            Component.translatable("chat.coordinates", pos.getX(), "~", pos.getZ())
        ).withStyle(style -> style
            .withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                "/tp @s " + pos.getX() + " ~ " + pos.getZ()))
            .withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Component.translatable("chat.coordinates.tooltip")))
        );

        // Send vanilla-style success message
        // "The nearest <structure> is at <coords> (<distance> blocks away)"
        source.sendSuccess(() -> Component.translatable(
            "commands.locate.structure.success",
            structureId.substring(structureId.indexOf(':') + 1), // Remove namespace for cleaner display
            coordsComponent,
            distance
        ), false);

        // Add MVS indicator for transparency
        if (MVSConfig.debugLogging) {
            source.sendSuccess(() -> Component.literal("  (MVS prediction - biome: " + result.biomeId + ")")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        }

        return 1;
    }

    /**
     * Get the structure set ID for a given structure.
     */
    private static String getStructureSetForStructure(String structureId) {
        if (!MVSConfig.interceptStructureSets.isEmpty()) {
            return MVSConfig.interceptStructureSets.get(0);
        }
        return "minecraft:villages";
    }
}
