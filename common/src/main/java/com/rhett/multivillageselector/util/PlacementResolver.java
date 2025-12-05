package com.rhett.multivillageselector.util;

import com.rhett.multivillageselector.config.ExclusionZone;
import com.rhett.multivillageselector.config.MVSConfig;
import com.rhett.multivillageselector.config.PlacementRule;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves effective placement values by merging config with registry defaults.
 * Handles inheritance: config values override registry, null = inherit from registry.
 */
public class PlacementResolver {

    /**
     * Resolved placement values with source tracking.
     */
    public static class ResolvedPlacement {
        public final int spacing;
        public final int separation;
        public final int salt;
        public final LocateHelper.SpreadType spreadType;
        public final String strategy;
        public final Vec3i locateOffset; // Offset added to chunk origin for /locate results
        public final ExclusionZone exclusionZone; // Structures to avoid, or null if none

        // Source tracking for debugging
        public final String spacingSource;
        public final String separationSource;
        public final String saltSource;
        public final String spreadTypeSource;
        public final String exclusionZoneSource;

        public ResolvedPlacement(int spacing, int separation, int salt,
                                  LocateHelper.SpreadType spreadType, String strategy,
                                  Vec3i locateOffset, ExclusionZone exclusionZone,
                                  String spacingSource, String separationSource,
                                  String saltSource, String spreadTypeSource,
                                  String exclusionZoneSource) {
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
            this.spreadType = spreadType;
            this.strategy = strategy;
            this.locateOffset = locateOffset != null ? locateOffset : Vec3i.ZERO;
            this.exclusionZone = exclusionZone;
            this.spacingSource = spacingSource;
            this.separationSource = separationSource;
            this.saltSource = saltSource;
            this.spreadTypeSource = spreadTypeSource;
            this.exclusionZoneSource = exclusionZoneSource;
        }

        /**
         * Convert to PlacementStrategy for use with LocateHelper.
         */
        public LocateHelper.PlacementStrategy toStrategy() {
            if ("concentric_rings".equals(strategy)) {
                // TODO: Implement concentric rings when needed
                throw new UnsupportedOperationException("Concentric rings strategy not yet implemented");
            }
            // Default to RandomSpreadPlacement with locateOffset
            return new LocateHelper.RandomSpreadPlacement(spacing, separation, salt, spreadType, locateOffset);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ResolvedPlacement{spacing=%d (%s), separation=%d (%s), salt=%d (%s), spreadType=%s (%s), locateOffset=%s",
                spacing, spacingSource, separation, separationSource, salt, saltSource, spreadType, spreadTypeSource, locateOffset));
            if (exclusionZone != null) {
                sb.append(String.format(", exclusionZone=%s (%s)", exclusionZone, exclusionZoneSource));
            }
            sb.append("}");
            return sb.toString();
        }
    }

    // Default vanilla village values (fallback if registry unavailable)
    public static final int DEFAULT_SPACING = 34;
    public static final int DEFAULT_SEPARATION = 8;
    public static final int DEFAULT_SALT = 10387312;
    public static final LocateHelper.SpreadType DEFAULT_SPREAD_TYPE = LocateHelper.SpreadType.LINEAR;

    /**
     * Result of resolution attempt - includes warnings for debugging.
     */
    public static class ResolutionResult {
        public final ResolvedPlacement placement;
        public final List<String> warnings;

        public ResolutionResult(ResolvedPlacement placement, List<String> warnings) {
            this.placement = placement;
            this.warnings = warnings;
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Resolve effective placement for a structure set.
     * Priority: MVSConfig.placement > Registry > Defaults
     *
     * @param structureSetId The structure set ID (e.g., "minecraft:villages")
     * @param structureSetRegistry Registry to read default values from (can be null)
     * @return Resolved placement values
     */
    public static ResolvedPlacement resolve(String structureSetId, Registry<StructureSet> structureSetRegistry) {
        return resolveWithWarnings(structureSetId, structureSetRegistry).placement;
    }

    /**
     * Resolve effective placement with warning collection.
     * Use this when you need to report resolution issues to users.
     *
     * @param structureSetId The structure set ID (e.g., "minecraft:villages")
     * @param structureSetRegistry Registry to read default values from (can be null)
     * @return ResolutionResult with placement and any warnings
     */
    public static ResolutionResult resolveWithWarnings(String structureSetId, Registry<StructureSet> structureSetRegistry) {
        List<String> warnings = new ArrayList<>();

        // Get config rule (may be null or have null fields)
        PlacementRule configRule = MVSConfig.placement.get(structureSetId);

        // Get registry values (may be null if not found)
        RegistryPlacement registryValues = null;
        if (structureSetRegistry != null) {
            registryValues = getFromRegistry(structureSetId, structureSetRegistry);
            if (registryValues == null) {
                warnings.add(String.format(
                    "Structure set '%s' not found in registry - using defaults. Check for typos.",
                    structureSetId
                ));
            }
        } else {
            warnings.add(String.format(
                "Registry not available for '%s' - using defaults. This may indicate resolution at wrong time.",
                structureSetId
            ));
        }

        // Resolve each field with priority: config > registry > default
        int spacing;
        String spacingSource;
        if (configRule != null && configRule.spacing != null) {
            spacing = configRule.spacing;
            spacingSource = "config";
        } else if (registryValues != null) {
            spacing = registryValues.spacing;
            spacingSource = "registry";
        } else {
            spacing = DEFAULT_SPACING;
            spacingSource = "default";
        }

        int separation;
        String separationSource;
        if (configRule != null && configRule.separation != null) {
            separation = configRule.separation;
            separationSource = "config";
        } else if (registryValues != null) {
            separation = registryValues.separation;
            separationSource = "registry";
        } else {
            separation = DEFAULT_SEPARATION;
            separationSource = "default";
        }

        int salt;
        String saltSource;
        if (configRule != null && configRule.salt != null) {
            salt = configRule.salt;
            saltSource = "config";
        } else if (registryValues != null) {
            salt = registryValues.salt;
            saltSource = "registry";
        } else {
            salt = DEFAULT_SALT;
            saltSource = "default";
        }

        LocateHelper.SpreadType spreadType;
        String spreadTypeSource;
        if (configRule != null && configRule.spreadType != null) {
            spreadType = configRule.resolveSpreadType(DEFAULT_SPREAD_TYPE);
            spreadTypeSource = "config";
        } else if (registryValues != null) {
            spreadType = registryValues.triangular ? LocateHelper.SpreadType.TRIANGULAR : LocateHelper.SpreadType.LINEAR;
            spreadTypeSource = "registry";
        } else {
            spreadType = DEFAULT_SPREAD_TYPE;
            spreadTypeSource = "default";
        }

        String strategy = (configRule != null && configRule.strategy != null)
            ? configRule.strategy
            : "random_spread";

        // locateOffset always comes from registry (not configurable)
        Vec3i locateOffset = (registryValues != null) ? registryValues.locateOffset : Vec3i.ZERO;

        // Resolve exclusion zone with priority: config > registry > null
        ExclusionZone exclusionZone;
        String exclusionZoneSource;
        if (configRule != null && configRule.exclusionZone != null) {
            exclusionZone = configRule.exclusionZone;
            exclusionZoneSource = "config";
        } else if (registryValues != null && registryValues.exclusionZone != null) {
            exclusionZone = registryValues.exclusionZone;
            exclusionZoneSource = "registry";
        } else {
            exclusionZone = null;
            exclusionZoneSource = "none";
        }

        ResolvedPlacement placement = new ResolvedPlacement(spacing, separation, salt, spreadType, strategy,
            locateOffset, exclusionZone, spacingSource, separationSource, saltSource, spreadTypeSource, exclusionZoneSource);

        return new ResolutionResult(placement, warnings);
    }

    /**
     * Resolve without registry access (uses config + defaults only).
     * WARNING: This will always use defaults for non-config values. Use with caution.
     */
    public static ResolvedPlacement resolve(String structureSetId) {
        return resolve(structureSetId, null);
    }

    /**
     * Check if a structure set has placement config defined.
     */
    public static boolean hasPlacementConfig(String structureSetId) {
        return MVSConfig.placement.containsKey(structureSetId);
    }

    /**
     * Internal class to hold values extracted from registry.
     */
    private static class RegistryPlacement {
        final int spacing;
        final int separation;
        final int salt;
        final boolean triangular;
        final Vec3i locateOffset;
        final ExclusionZone exclusionZone;

        RegistryPlacement(int spacing, int separation, int salt, boolean triangular,
                         Vec3i locateOffset, ExclusionZone exclusionZone) {
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
            this.triangular = triangular;
            this.locateOffset = locateOffset;
            this.exclusionZone = exclusionZone;
        }
    }

    /**
     * Extract placement values from registry using mixin accessors.
     */
    private static RegistryPlacement getFromRegistry(String structureSetId, Registry<StructureSet> registry) {
        try {
            ResourceLocation location = ResourceLocation.parse(structureSetId);
            StructureSet structureSet = registry.get(location);

            if (structureSet == null) {
                return null;
            }

            StructurePlacement placement = structureSet.placement();

            if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
                int spacing = randomSpread.spacing();
                int separation = randomSpread.separation();
                boolean triangular = randomSpread.spreadType() == RandomSpreadType.TRIANGULAR;

                // Salt, locateOffset, and exclusionZone are protected in parent class, use mixin accessor
                int salt = getSaltViaMixin(placement);
                Vec3i locateOffset = getLocateOffsetViaMixin(placement);
                ExclusionZone exclusionZone = getExclusionZoneViaMixin(placement);

                return new RegistryPlacement(spacing, separation, salt, triangular, locateOffset, exclusionZone);
            }

            // Non-RandomSpread placements not supported yet
            return null;

        } catch (Exception e) {
            // Registry lookup failed, return null
            return null;
        }
    }

    /**
     * Get salt value via mixin accessor (it's protected in StructurePlacement).
     * Uses mixin for cross-platform Fabric/NeoForge compatibility.
     */
    private static int getSaltViaMixin(StructurePlacement placement) {
        return ((com.rhett.multivillageselector.mixin.StructurePlacementAccessor) placement).invokeSalt();
    }

    /**
     * Get locateOffset value via mixin accessor (it's protected in StructurePlacement).
     * This offset is added to chunk origin for /locate results.
     * Default is Vec3i.ZERO, but mods can customize (e.g., Trek structures).
     */
    private static Vec3i getLocateOffsetViaMixin(StructurePlacement placement) {
        return ((com.rhett.multivillageselector.mixin.StructurePlacementAccessor) placement).invokeLocateOffset();
    }

    /**
     * Get exclusion zone via mixin accessor (it's protected in StructurePlacement).
     * Converts vanilla's ExclusionZone to our config ExclusionZone format.
     */
    private static ExclusionZone getExclusionZoneViaMixin(StructurePlacement placement) {
        Optional<StructurePlacement.ExclusionZone> vanillaZone =
            ((com.rhett.multivillageselector.mixin.StructurePlacementAccessor) placement).invokeExclusionZone();

        if (vanillaZone.isEmpty()) {
            return null;
        }

        StructurePlacement.ExclusionZone zone = vanillaZone.get();
        // Extract other_set ID from the Holder
        String otherSetId = zone.otherSet().unwrapKey()
            .map(k -> k.location().toString())
            .orElse(null);

        if (otherSetId == null) {
            return null;
        }

        return new ExclusionZone(otherSetId, zone.chunkCount());
    }
}
