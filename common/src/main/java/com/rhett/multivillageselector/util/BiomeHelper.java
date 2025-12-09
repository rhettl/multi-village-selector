package com.rhett.multivillageselector.util;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper methods for biome operations: tag lookup, similarity calculation, expansion.
 * Extracted from BiomeCommands and StructureCommands for testability.
 */
public class BiomeHelper {

    /**
     * Result of biome similarity calculation.
     */
    public static class BiomeSimilarity {
        public final String biomeId;
        public final double similarity;
        public final int sharedTags;

        public BiomeSimilarity(String biomeId, double similarity, int sharedTags) {
            this.biomeId = biomeId;
            this.similarity = similarity;
            this.sharedTags = sharedTags;
        }
    }

    /**
     * Get all tags for a biome.
     *
     * @param biomeHolder The biome holder
     * @param biomeRegistry The biome registry
     * @return Sorted list of tag strings (prefixed with #)
     */
    public static List<String> getTagsForBiome(Holder<Biome> biomeHolder, Registry<Biome> biomeRegistry) {
        return biomeRegistry.getTags()
            .filter(pair -> pair.getSecond().contains(biomeHolder))
            .map(pair -> "#" + pair.getFirst().location().toString())
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Get all biomes that have a specific tag.
     *
     * @param tagId The tag location (without # prefix)
     * @param biomeRegistry The biome registry
     * @return Sorted list of biome IDs
     */
    public static List<String> getBiomesWithTag(ResourceLocation tagId, Registry<Biome> biomeRegistry) {
        List<String> matchingBiomes = new ArrayList<>();
        TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, tagId);

        for (var entry : biomeRegistry.entrySet()) {
            var biomeHolder = biomeRegistry.wrapAsHolder(entry.getValue());
            if (biomeHolder.is(tagKey)) {
                matchingBiomes.add(entry.getKey().location().toString());
            }
        }

        matchingBiomes.sort(String::compareTo);
        return matchingBiomes;
    }

    /**
     * Find biomes similar to a target biome based on tag overlap.
     *
     * @param targetBiomeHolder The target biome to compare against
     * @param biomeRegistry The biome registry
     * @param maxResults Maximum number of results to return
     * @return List of BiomeSimilarity sorted by similarity (descending)
     */
    public static List<BiomeSimilarity> findSimilarBiomes(Holder<Biome> targetBiomeHolder,
                                                          Registry<Biome> biomeRegistry,
                                                          int maxResults) {
        var biomeKey = targetBiomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) {
            return Collections.emptyList();
        }

        String targetBiomeId = biomeKey.get().location().toString();

        // Get tags for target biome
        Set<TagKey<Biome>> targetTags = new HashSet<>();
        biomeRegistry.getTags()
            .filter(pair -> pair.getSecond().contains(targetBiomeHolder))
            .forEach(pair -> targetTags.add(pair.getFirst()));

        if (targetTags.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate similarity for all biomes
        List<BiomeSimilarity> similarities = new ArrayList<>();
        for (var entry : biomeRegistry.entrySet()) {
            String compareId = entry.getKey().location().toString();
            if (compareId.equals(targetBiomeId)) continue; // Skip self

            var compareHolder = biomeRegistry.wrapAsHolder(entry.getValue());

            // Count shared tags
            int sharedCount = 0;
            int compareTagCount = 0;
            for (var tagPair : biomeRegistry.getTags().toList()) {
                if (tagPair.getSecond().contains(compareHolder)) {
                    compareTagCount++;
                    if (targetTags.contains(tagPair.getFirst())) {
                        sharedCount++;
                    }
                }
            }

            if (sharedCount > 0) {
                // Similarity = shared / total unique tags (Jaccard index)
                int totalUniqueTags = targetTags.size() + compareTagCount - sharedCount;
                double similarity = (double) sharedCount / totalUniqueTags;
                similarities.add(new BiomeSimilarity(compareId, similarity, sharedCount));
            }
        }

        // Sort by similarity (descending) and limit
        similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        return similarities.subList(0, Math.min(maxResults, similarities.size()));
    }

    /**
     * Expand a biome tag or pattern to get all matching biomes.
     *
     * @param tagOrPattern Tag (starting with #), pattern (containing *), or literal biome ID
     * @param registryAccess Registry access for lookups
     * @return List of matching biome IDs
     */
    public static List<String> expandTagOrPattern(String tagOrPattern, RegistryAccess registryAccess) {
        List<String> biomes = new ArrayList<>();
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        // Case 1: Tag (starts with #)
        if (tagOrPattern.startsWith("#")) {
            String tagPart = tagOrPattern.substring(1);
            if (tagPart.contains("*")) {
                // Pattern tag - can't easily expand
                return biomes;
            }

            try {
                ResourceLocation tagId = ResourceLocation.parse(tagPart);
                return getBiomesWithTag(tagId, biomeRegistry);
            } catch (Exception e) {
                return biomes;
            }
        }

        // Case 2: Biome ID pattern (contains * but no #)
        if (tagOrPattern.contains("*")) {
            for (var entry : biomeRegistry.entrySet()) {
                String biomeId = entry.getKey().location().toString();
                if (PatternMatcher.matches(biomeId, tagOrPattern)) {
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
