package com.rhett.multivillageselector.test;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.*;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reusable mock utilities for Minecraft types in unit tests.
 * Provides factory methods for common mocking patterns.
 */
public class TestMocks {

    // ========================================
    // Biome Mocking
    // ========================================

    /**
     * Create a mock Holder<Biome> with the given ID and tags.
     *
     * @param biomeId Biome ID like "minecraft:plains"
     * @param tags    Tags this biome belongs to (with or without # prefix)
     * @return Mocked Holder<Biome>
     */
    @SuppressWarnings("unchecked")
    public static Holder<Biome> mockBiome(String biomeId, String... tags) {
        Holder<Biome> holder = mock(Holder.class);

        // Mock biome key (for direct ID lookup)
        ResourceLocation location = ResourceLocation.parse(biomeId);
        ResourceKey<Biome> key = ResourceKey.create(
            net.minecraft.core.registries.Registries.BIOME,
            location
        );
        when(holder.unwrapKey()).thenReturn(Optional.of(key));

        // Mock tags stream
        Stream<TagKey<Biome>> tagStream = Arrays.stream(tags)
            .map(tagId -> {
                String cleanTag = tagId.startsWith("#") ? tagId.substring(1) : tagId;
                ResourceLocation tagLocation = ResourceLocation.parse(cleanTag);
                return TagKey.create(net.minecraft.core.registries.Registries.BIOME, tagLocation);
            });
        when(holder.tags()).thenReturn(tagStream);

        // Mock is() method for tag checking
        when(holder.is(any(TagKey.class))).thenAnswer(invocation -> {
            TagKey<Biome> queryTag = invocation.getArgument(0);
            for (String tag : tags) {
                String cleanTag = tag.startsWith("#") ? tag.substring(1) : tag;
                if (queryTag.location().toString().equals(cleanTag)) {
                    return true;
                }
            }
            return false;
        });

        return holder;
    }

    // ========================================
    // Registry Mocking
    // ========================================

    /**
     * Builder for creating mock Registry<Biome>.
     */
    public static class BiomeRegistryBuilder {
        private final Map<String, BiomeEntry> biomes = new LinkedHashMap<>();
        private final Map<String, Set<String>> tagToBiomes = new HashMap<>();

        /**
         * Add a biome to the registry.
         *
         * @param biomeId Biome ID like "minecraft:plains"
         * @param tags    Tags this biome belongs to
         */
        public BiomeRegistryBuilder withBiome(String biomeId, String... tags) {
            biomes.put(biomeId, new BiomeEntry(biomeId, tags));

            // Track tag -> biomes mapping
            for (String tag : tags) {
                String cleanTag = tag.startsWith("#") ? tag.substring(1) : tag;
                tagToBiomes.computeIfAbsent(cleanTag, k -> new LinkedHashSet<>()).add(biomeId);
            }

            return this;
        }

        /**
         * Build the mock registry.
         * Note: We avoid mocking Biome directly as it triggers Minecraft bootstrap.
         * Instead, we use marker objects and track IDs.
         */
        @SuppressWarnings("unchecked")
        public Registry<Biome> build() {
            Registry<Biome> registry = mock(Registry.class);

            // Create holder map for consistent returns
            Map<String, Holder<Biome>> holderMap = new HashMap<>();
            for (Map.Entry<String, BiomeEntry> entry : biomes.entrySet()) {
                holderMap.put(entry.getKey(), mockBiome(entry.getKey(), entry.getValue().tags));
            }

            // Mock entrySet() - returns all biomes (using null for Biome value)
            // This works because callers typically only need the key
            Set<Map.Entry<ResourceKey<Biome>, Biome>> entrySet = new LinkedHashSet<>();
            for (String biomeId : biomes.keySet()) {
                ResourceLocation location = ResourceLocation.parse(biomeId);
                ResourceKey<Biome> key = ResourceKey.create(
                    net.minecraft.core.registries.Registries.BIOME, location);
                // Use null for Biome - callers should use getKey() only
                entrySet.add(new AbstractMap.SimpleEntry<>(key, null));
            }
            when(registry.entrySet()).thenReturn(entrySet);

            // Mock containsKey()
            when(registry.containsKey(any(ResourceLocation.class))).thenAnswer(invocation -> {
                ResourceLocation loc = invocation.getArgument(0);
                return biomes.containsKey(loc.toString());
            });

            // Mock get() - returns null (can't mock Biome)
            when(registry.get(any(ResourceLocation.class))).thenReturn(null);

            // Mock wrapAsHolder() - return the pre-created holder
            when(registry.wrapAsHolder(any())).thenAnswer(invocation -> {
                // Return first holder as fallback
                return holderMap.values().iterator().next();
            });

            // Mock getTags() - returns stream of tag pairs
            List<TagEntry> tagEntries = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : tagToBiomes.entrySet()) {
                String tagId = entry.getKey();
                Set<String> biomeIds = entry.getValue();

                TagKey<Biome> tagKey = TagKey.create(
                    net.minecraft.core.registries.Registries.BIOME,
                    ResourceLocation.parse(tagId)
                );

                // Create holders for biomes with this tag
                List<Holder<Biome>> holders = new ArrayList<>();
                for (String biomeId : biomeIds) {
                    Holder<Biome> holder = holderMap.get(biomeId);
                    if (holder != null) {
                        holders.add(holder);
                    }
                }

                tagEntries.add(new TagEntry(tagKey, holders));
            }

            when(registry.getTags()).thenAnswer(invocation -> {
                return tagEntries.stream().map(te ->
                    com.mojang.datafixers.util.Pair.of(te.tagKey, new HolderSetLike(te.holders)));
            });

            return registry;
        }

        private static class BiomeEntry {
            final String id;
            final String[] tags;

            BiomeEntry(String id, String[] tags) {
                this.id = id;
                this.tags = tags;
            }
        }

        private static class TagEntry {
            final TagKey<Biome> tagKey;
            final List<Holder<Biome>> holders;

            TagEntry(TagKey<Biome> tagKey, List<Holder<Biome>> holders) {
                this.tagKey = tagKey;
                this.holders = holders;
            }
        }
    }

    /**
     * Simple holder set that supports contains() check.
     * Used to mock HolderSet behavior in registry.getTags().
     */
    public static class HolderSetLike implements Iterable<Holder<Biome>> {
        private final List<Holder<Biome>> holders;

        public HolderSetLike(List<Holder<Biome>> holders) {
            this.holders = holders;
        }

        public boolean contains(Holder<Biome> holder) {
            // Check if any holder has the same biome ID
            var queryKey = holder.unwrapKey();
            if (queryKey.isEmpty()) return false;

            for (Holder<Biome> h : holders) {
                var hKey = h.unwrapKey();
                if (hKey.isPresent() && hKey.get().equals(queryKey.get())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Iterator<Holder<Biome>> iterator() {
            return holders.iterator();
        }
    }

    // ========================================
    // RegistryAccess Mocking
    // ========================================

    /**
     * Create a mock RegistryAccess with the given biome registry.
     */
    @SuppressWarnings("unchecked")
    public static RegistryAccess mockRegistryAccess(Registry<Biome> biomeRegistry) {
        RegistryAccess registryAccess = mock(RegistryAccess.class);

        when(registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.BIOME))
            .thenReturn(biomeRegistry);

        return registryAccess;
    }

    /**
     * Create a simple RegistryAccess with common biomes pre-configured.
     */
    public static RegistryAccess mockSimpleRegistryAccess() {
        Registry<Biome> biomeRegistry = new BiomeRegistryBuilder()
            .withBiome("minecraft:plains", "minecraft:is_overworld", "minecraft:has_structure/village_plains")
            .withBiome("minecraft:desert", "minecraft:is_overworld", "minecraft:has_structure/village_desert")
            .withBiome("minecraft:savanna", "minecraft:is_overworld", "minecraft:has_structure/village_savanna")
            .withBiome("minecraft:taiga", "minecraft:is_overworld", "minecraft:has_structure/village_taiga")
            .withBiome("minecraft:snowy_plains", "minecraft:is_overworld", "minecraft:has_structure/village_snowy")
            .withBiome("minecraft:ocean", "minecraft:is_overworld", "minecraft:is_ocean")
            .withBiome("minecraft:deep_ocean", "minecraft:is_overworld", "minecraft:is_ocean", "minecraft:is_deep_ocean")
            .build();

        return mockRegistryAccess(biomeRegistry);
    }

    // ========================================
    // Factory Methods
    // ========================================

    /**
     * Start building a biome registry.
     */
    public static BiomeRegistryBuilder biomeRegistry() {
        return new BiomeRegistryBuilder();
    }
}
