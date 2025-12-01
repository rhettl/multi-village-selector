package com.rhett.multivillageselector.commands.structure;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.util.BiomeRuleResolver;
import com.rhett.multivillageselector.util.BiomeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all /mvs structure subcommands
 * Commands for inspecting structure biome rules (MVS config or vanilla registry)
 */
public class StructureCommands {

    /**
     * Handle /mvs structure pool [full]
     * Lists all structures in the structure_pool (including empty entries)
     * @param showFull if true, shows all structures without truncation
     */
    public static int executePool(CommandContext<CommandSourceStack> context, boolean showFull) {
        CommandSourceStack source = context.getSource();

        try {
            var registryAccess = source.getServer().registryAccess();
            var pool = com.rhett.multivillageselector.config.MVSConfig.structurePool;

            if (pool.isEmpty()) {
                source.sendFailure(Component.literal("Structure pool is empty!"));
                return 0;
            }

            // Header
            String headerSuffix = showFull ? " (full)" : "";
            source.sendSuccess(() -> Component.literal("=== MVS Structure Pool" + headerSuffix + " ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Count empty vs non-empty
            long emptyCount = pool.stream().filter(s -> s.isEmpty).count();
            long structureCount = pool.size() - emptyCount;

            source.sendSuccess(() -> Component.literal("Structures: " + structureCount)
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" | Empty entries: " + emptyCount)
                    .withStyle(ChatFormatting.YELLOW)), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // List structures
            int maxToShow = showFull ? Integer.MAX_VALUE : 15;
            int count = 0;
            for (var configured : pool) {
                if (configured.isEmpty) {
                    continue; // Skip empty for now
                }

                if (count >= maxToShow) break;

                String structureId = configured.structure.toString();
                int tagCount = configured.biomes.size();

                final String finalStructureId = structureId;
                final int finalTagCount = tagCount;

                // Clickable structure ID
                Component structureComponent = Component.literal("  " + finalStructureId)
                    .withStyle(net.minecraft.network.chat.Style.EMPTY
                        .withColor(ChatFormatting.WHITE)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                            "/mvs structure biomes " + finalStructureId))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to view biome rules"))))
                    .append(Component.literal(" (" + finalTagCount + " biome tags)")
                        .withStyle(ChatFormatting.GRAY));

                source.sendSuccess(() -> structureComponent, false);
                count++;
            }

            // Show "... and X more" link if truncated
            if (!showFull && structureCount > maxToShow) {
                final int remaining = (int)structureCount - maxToShow;

                Component expandLink = Component.literal("  ... and " + remaining + " more")
                    .withStyle(net.minecraft.network.chat.Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                            "/mvs structure pool full"))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to show all " + structureCount + " structures"))));

                source.sendSuccess(() -> expandLink, false);
            }

            // List empty entries (only in full mode or if few)
            if (emptyCount > 0 && (showFull || emptyCount <= 5)) {
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("Empty Entries:")
                    .withStyle(ChatFormatting.YELLOW), false);

                int emptyMaxToShow = showFull ? Integer.MAX_VALUE : 5;
                int emptyShown = 0;
                for (var configured : pool) {
                    if (!configured.isEmpty) {
                        continue;
                    }

                    if (emptyShown >= emptyMaxToShow) break;

                    int tagCount = configured.biomes.size();
                    int weight = configured.biomes.values().stream().findFirst().orElse(0);

                    final String line = "  (empty) - weight: " + weight + ", tags: " + tagCount;
                    source.sendSuccess(() -> Component.literal(line)
                        .withStyle(ChatFormatting.GRAY), false);

                    emptyShown++;
                }

                if (!showFull && emptyCount > emptyMaxToShow) {
                    final int remaining = (int)emptyCount - emptyMaxToShow;
                    source.sendSuccess(() -> Component.literal("  ... and " + remaining + " more empty entries")
                        .withStyle(ChatFormatting.DARK_GRAY), false);
                }
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in structure pool command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs structure list [filter]
     * Dumps all structures in the game to a file, grouped by namespace and structure_set.
     * @param filter Optional pattern filter (supports wildcards like "bca:*" or "*village*", no # tags)
     */
    public static int executeList(CommandContext<CommandSourceStack> context, String filter) {
        CommandSourceStack source = context.getSource();

        try {
            // Validate filter - no biome tags allowed
            if (filter != null && filter.startsWith("#")) {
                source.sendFailure(Component.literal("Tag patterns (#) are not supported. Use structure patterns like: bca:*, *village*"));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Scanning all structures...")
                .withStyle(ChatFormatting.YELLOW), false);

            var server = source.getServer();
            var registryAccess = server.registryAccess();

            // Scan registry for ALL structures
            var structureSetRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
            var structureRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

            // Build biome tag map for all structures
            java.util.Map<ResourceLocation, String> structureToBiomeTag = new java.util.HashMap<>();
            for (var entry : structureRegistry.entrySet()) {
                ResourceLocation structureId = entry.getKey().location();
                net.minecraft.world.level.levelgen.structure.Structure structure = entry.getValue();

                try {
                    var biomeHolderSet = structure.biomes();
                    var tagKey = biomeHolderSet.unwrapKey();
                    if (tagKey.isPresent()) {
                        structureToBiomeTag.put(structureId, "#" + tagKey.get().location().toString());
                    } else {
                        structureToBiomeTag.put(structureId, "(direct biomes)");
                    }
                } catch (Exception e) {
                    structureToBiomeTag.put(structureId, "(error)");
                }
            }

            // Group structures by namespace -> structure_set
            // Map: namespace -> (structure_set -> list of structures)
            java.util.Map<String, java.util.Map<String, java.util.List<StructureEntry>>> namespaceMap = new java.util.TreeMap<>();

            // Track structure_set membership
            java.util.Map<ResourceLocation, String> structureToSet = new java.util.HashMap<>();

            // Scan structure_sets
            for (var entry : structureSetRegistry.entrySet()) {
                ResourceLocation setId = entry.getKey().location();
                String setIdStr = setId.toString();
                var structureSet = entry.getValue();

                for (var selectionEntry : structureSet.structures()) {
                    var structureKey = selectionEntry.structure().unwrapKey();
                    if (structureKey.isPresent()) {
                        ResourceLocation structureId = structureKey.get().location();
                        structureToSet.put(structureId, setIdStr);
                    }
                }
            }

            // Build complete structure list
            int totalCount = 0;
            int filteredCount = 0;

            for (var entry : structureRegistry.entrySet()) {
                ResourceLocation structureId = entry.getKey().location();
                String structureIdStr = structureId.toString();
                String namespace = structureId.getNamespace();
                String biomeTag = structureToBiomeTag.getOrDefault(structureId, "(unknown)");
                String setId = structureToSet.getOrDefault(structureId, "(none)");

                totalCount++;

                // Apply filter if provided
                if (filter != null && !filter.isEmpty()) {
                    if (!com.rhett.multivillageselector.util.PatternMatcher.matches(structureIdStr, filter)) {
                        continue;
                    }
                }

                filteredCount++;

                // Get weight from structure_set
                Integer weight = null;
                if (structureToSet.containsKey(structureId)) {
                    String setIdStr = structureToSet.get(structureId);
                    ResourceLocation setLoc = ResourceLocation.parse(setIdStr);
                    var setHolder = structureSetRegistry.getHolder(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.STRUCTURE_SET, setLoc));
                    if (setHolder.isPresent()) {
                        for (var sel : setHolder.get().value().structures()) {
                            if (sel.structure().unwrapKey().map(k -> k.location().equals(structureId)).orElse(false)) {
                                weight = sel.weight();
                                break;
                            }
                        }
                    }
                }

                // Add to namespace map
                namespaceMap
                    .computeIfAbsent(namespace, k -> new java.util.TreeMap<>())
                    .computeIfAbsent(setId, k -> new java.util.ArrayList<>())
                    .add(new StructureEntry(structureIdStr, biomeTag, weight));
            }

            // Build output
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add("=".repeat(80));
            lines.add("MVS Structure List - All Structures in Game");
            lines.add("Generated: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            if (filter != null && !filter.isEmpty()) {
                lines.add("Filter: " + filter);
                lines.add("Matched: " + filteredCount + " / " + totalCount + " structures");
            } else {
                lines.add("Total: " + totalCount + " structures");
            }
            lines.add("=".repeat(80));
            lines.add("");

            // Output grouped by namespace
            for (var namespaceEntry : namespaceMap.entrySet()) {
                String namespace = namespaceEntry.getKey();
                var setMap = namespaceEntry.getValue();

                int namespaceCount = setMap.values().stream().mapToInt(java.util.List::size).sum();
                lines.add("=== " + namespace + " (" + namespaceCount + " structures) ===");
                lines.add("");

                for (var setEntry : setMap.entrySet()) {
                    String setId = setEntry.getKey();
                    var structures = setEntry.getValue();

                    // Calculate normalized weights for this structure_set (target avg: 25)
                    int totalWeight = 0;
                    int weightedCount = 0;
                    for (var s : structures) {
                        if (s.weight != null) {
                            totalWeight += s.weight;
                            weightedCount++;
                        }
                    }
                    double multiplier = (weightedCount > 0 && totalWeight > 0)
                        ? 25.0 / (totalWeight / (double) weightedCount)
                        : 1.0;

                    lines.add("  [" + setId + "]");

                    // Sort structures alphabetically
                    structures.sort((a, b) -> a.id.compareTo(b.id));

                    for (var struct : structures) {
                        String weightStr;
                        if (struct.weight != null) {
                            int normalized = (int) Math.round(struct.weight * multiplier);
                            weightStr = String.format("weight: %d (normalized: %d)", struct.weight, normalized);
                        } else {
                            weightStr = "weight: (none)";
                        }
                        lines.add("    " + struct.id);
                        lines.add("      Biomes: " + struct.biomeTag);
                        lines.add("      " + weightStr);
                    }
                    lines.add("");
                }
            }

            // Write to file
            java.nio.file.Path gameDir = server.getServerDirectory();
            java.nio.file.Path outputDir = gameDir.resolve("local/mvs");
            java.nio.file.Files.createDirectories(outputDir);

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            java.nio.file.Path outputFile = outputDir.resolve("structure-list-" + timestamp + ".txt");
            java.nio.file.Files.write(outputFile, lines, java.nio.charset.StandardCharsets.UTF_8);

            // Success message with clickable link
            final int finalFilteredCount = filteredCount;
            final int finalTotalCount = totalCount;
            final String filterMsg = filter != null ? " (filter: " + filter + ")" : "";

            source.sendSuccess(() -> Component.literal("Wrote " + finalFilteredCount + " structures to file" + filterMsg)
                .withStyle(ChatFormatting.GREEN), false);

            // Clickable file path
            String filePath = outputFile.toAbsolutePath().toString();
            Component fileLink = Component.literal("  " + filePath)
                .withStyle(net.minecraft.network.chat.Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.OPEN_FILE,
                        filePath))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to open file"))));

            source.sendSuccess(() -> fileLink, false);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in structure list command", e);
            return 0;
        }
    }

    /**
     * Helper class for structure entries in the list output
     */
    private static class StructureEntry {
        final String id;
        final String biomeTag;
        final Integer weight;

        StructureEntry(String id, String biomeTag, Integer weight) {
            this.id = id;
            this.biomeTag = biomeTag;
            this.weight = weight;
        }
    }

    /**
     * Handle /mvs structure biomes <structure-name> [full]
     * Shows all allowed biomes for a structure (from MVS config or vanilla registry)
     * @param showFull if true, shows all biomes without truncation
     */
    public static int executeBiomes(CommandContext<CommandSourceStack> context, boolean showFull) {
        CommandSourceStack source = context.getSource();
        String structureId = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "structure").toString();

        try {
            var registryAccess = source.getServer().registryAccess();
            BiomeRules rules = BiomeRuleResolver.getEffectiveRules(structureId, registryAccess);

            if (rules.isEmpty()) {
                source.sendFailure(Component.literal("Structure not found: " + structureId));
                return 0;
            }

            // Header
            String headerSuffix = showFull ? " (full)" : "";
            source.sendSuccess(() -> Component.literal("=== Biome Rules for " + structureId + headerSuffix + " ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Source
            String sourceLabel = switch (rules.source) {
                case MVS_CONFIG -> "MVS Config (structure_pool)";
                case VANILLA_REGISTRY -> "Vanilla Registry";
                default -> "Unknown";
            };
            source.sendSuccess(() -> Component.literal("Source: " + sourceLabel)
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Show tags
            if (!rules.tags.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Biome Tags:")
                    .withStyle(ChatFormatting.YELLOW), false);

                int maxBiomesPerTag = showFull ? Integer.MAX_VALUE : 15;

                for (String tag : rules.tags) {
                    int weight = rules.weights.getOrDefault(tag, 0);
                    String weightStr = weight > 0 ? " (weight: " + weight + ")" : "";

                    final String finalTag = tag;
                    final String finalWeightStr = weightStr;
                    source.sendSuccess(() -> Component.literal("  " + finalTag + finalWeightStr)
                        .withStyle(ChatFormatting.WHITE), false);

                    // Expand tag to show actual biomes
                    List<String> biomes = expandTag(tag, registryAccess);
                    if (!biomes.isEmpty()) {
                        int showCount = Math.min(maxBiomesPerTag, biomes.size());
                        for (String biome : biomes.subList(0, showCount)) {
                            final String finalBiome = biome;
                            source.sendSuccess(() -> Component.literal("    - " + finalBiome)
                                .withStyle(ChatFormatting.GRAY), false);
                        }
                        if (!showFull && biomes.size() > maxBiomesPerTag) {
                            final int remaining = biomes.size() - maxBiomesPerTag;

                            Component expandLink = Component.literal("    ... and " + remaining + " more")
                                .withStyle(net.minecraft.network.chat.Style.EMPTY
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        "/mvs structure biomes \"" + structureId + "\" full"
                                    ))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Click to show all biomes")
                                    ))
                                );

                            source.sendSuccess(() -> expandLink, false);
                        }
                    }
                }
            }

            // Show direct biomes
            if (!rules.directBiomes.isEmpty()) {
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("Direct Biomes:")
                    .withStyle(ChatFormatting.YELLOW), false);

                for (String biome : rules.directBiomes) {
                    final String finalBiome = biome;
                    source.sendSuccess(() -> Component.literal("  - " + finalBiome)
                        .withStyle(ChatFormatting.WHITE), false);
                }
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in structure biomes command", e);
            return 0;
        }
    }

    /**
     * Handle /mvs structure test <structure-name> <biome>
     * Tests if a structure can spawn in a specific biome
     */
    public static int executeTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String structureId = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "structure").toString();
        String biomeId = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "biome").toString();

        try {
            var registryAccess = source.getServer().registryAccess();
            BiomeRules rules = BiomeRuleResolver.getEffectiveRules(structureId, registryAccess);

            if (rules.isEmpty()) {
                source.sendFailure(Component.literal("Structure not found: " + structureId));
                return 0;
            }

            // Get biome holder
            Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            ResourceLocation biomeLocation = ResourceLocation.parse(biomeId);
            var biomeHolderOpt = biomeRegistry.getHolder(
                net.minecraft.resources.ResourceKey.create(Registries.BIOME, biomeLocation)
            );

            if (biomeHolderOpt.isEmpty()) {
                source.sendFailure(Component.literal("Biome not found: " + biomeId));
                return 0;
            }

            Holder<Biome> biomeHolder = biomeHolderOpt.get();

            // Test match
            boolean matches = rules.matches(biomeHolder);

            // Header
            source.sendSuccess(() -> Component.literal("=== Structure Biome Test ===")
                .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(""), false);

            // Result
            if (matches) {
                source.sendSuccess(() -> Component.literal("✓ " + structureId + " CAN spawn in " + biomeId)
                    .withStyle(ChatFormatting.GREEN), false);

                // Show which tag matched
                String matchingTag = rules.getMatchingTag(biomeHolder);
                if (matchingTag != null) {
                    int weight = rules.weights.getOrDefault(matchingTag, 0);
                    String weightStr = weight > 0 ? " (weight: " + weight + ")" : "";

                    final String finalTag = matchingTag;
                    final String finalWeightStr = weightStr;
                    source.sendSuccess(() -> Component.literal("  Matched via: " + finalTag + finalWeightStr)
                        .withStyle(ChatFormatting.AQUA), false);
                }
            } else {
                source.sendSuccess(() -> Component.literal("✗ " + structureId + " CANNOT spawn in " + biomeId)
                    .withStyle(ChatFormatting.RED), false);

                // Show configured tags for debugging
                if (!rules.tags.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("  Configured tags:")
                        .withStyle(ChatFormatting.GRAY), false);
                    for (String tag : rules.tags.subList(0, Math.min(3, rules.tags.size()))) {
                        final String finalTag = tag;
                        source.sendSuccess(() -> Component.literal("    " + finalTag)
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                    }
                    if (rules.tags.size() > 3) {
                        final int remaining = rules.tags.size() - 3;
                        source.sendSuccess(() -> Component.literal("    ... and " + remaining + " more")
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                    }
                }
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            MVSCommon.LOGGER.error("Error in structure test command", e);
            return 0;
        }
    }

    /**
     * Expand a biome tag or pattern to get all matching biomes.
     * - For #tags: returns biomes with that tag
     * - For patterns (containing *): returns biome IDs matching the pattern
     * - For literal biome IDs: returns the ID if it exists
     */
    private static List<String> expandTag(String tagOrPattern, net.minecraft.core.RegistryAccess registryAccess) {
        List<String> biomes = new ArrayList<>();
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        // Case 1: Tag (starts with #)
        if (tagOrPattern.startsWith("#")) {
            // Check if it's a pattern or literal tag
            String tagPart = tagOrPattern.substring(1);
            if (tagPart.contains("*")) {
                // Pattern tag like #*:*ocean* - would match multiple tags
                // For display, show that it's a pattern
                return biomes; // Can't easily expand tag patterns
            }

            try {
                ResourceLocation tagId = ResourceLocation.parse(tagPart);
                TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, tagId);

                for (Holder<Biome> biomeHolder : biomeRegistry.holders().toList()) {
                    if (biomeHolder.is(tagKey)) {
                        biomeHolder.unwrapKey().ifPresent(key ->
                            biomes.add(key.location().toString())
                        );
                    }
                }
            } catch (Exception e) {
                // Invalid tag - return empty list
            }
            return biomes;
        }

        // Case 2: Biome ID pattern (contains * but no #)
        if (tagOrPattern.contains("*")) {
            for (var entry : biomeRegistry.entrySet()) {
                String biomeId = entry.getKey().location().toString();
                if (com.rhett.multivillageselector.util.PatternMatcher.matches(biomeId, tagOrPattern)) {
                    biomes.add(biomeId);
                }
            }
            return biomes;
        }

        // Case 3: Literal biome ID
        try {
            ResourceLocation biomeId = ResourceLocation.parse(tagOrPattern);
            if (biomeRegistry.containsKey(biomeId)) {
                biomes.add(tagOrPattern);
            }
        } catch (Exception e) {
            // Invalid biome ID
        }

        return biomes;
    }
}