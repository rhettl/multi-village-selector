package com.rhett.multivillageselector.config;

import com.rhett.multivillageselector.util.LocateHelper;

/**
 * Immutable placement configuration for a single structure_set.
 * All fields are nullable - null means "inherit from registry".
 *
 * Validation:
 * - spacing must be > 0 if specified
 * - separation must be >= 0 if specified
 * - separation must be < spacing if both specified
 * - salt must be a valid integer if specified
 * - spreadType must be a valid SpreadType name if specified
 * - strategy must be a valid strategy name if specified
 * - exclusionZone must have valid other_set and chunk_count if specified
 */
public class PlacementRule {

    /** Grid cell size in chunks. Null = inherit from registry. */
    public final Integer spacing;

    /** Separation between structures in chunks. Null = inherit from registry. */
    public final Integer separation;

    /** Random seed modifier. Null = inherit from registry. */
    public final Integer salt;

    /** Distribution type within cell. Null = inherit from registry. */
    public final String spreadType;

    /** Placement strategy type. Null = default to "random_spread". */
    public final String strategy;

    /** Exclusion zone - avoid spawning near another structure set. Null = inherit from registry. */
    public final ExclusionZone exclusionZone;

    public PlacementRule(Integer spacing, Integer separation, Integer salt,
                         String spreadType, String strategy, ExclusionZone exclusionZone) {
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.spreadType = spreadType;
        this.strategy = strategy;
        this.exclusionZone = exclusionZone;
    }

    /** Legacy constructor without exclusionZone for backwards compatibility */
    public PlacementRule(Integer spacing, Integer separation, Integer salt,
                         String spreadType, String strategy) {
        this(spacing, separation, salt, spreadType, strategy, null);
    }

    /**
     * Empty rule - inherit everything from registry.
     */
    public static PlacementRule empty() {
        return new PlacementRule(null, null, null, null, null);
    }

    /**
     * Check if all fields are null (full inheritance).
     */
    public boolean isFullyInherited() {
        return spacing == null && separation == null && salt == null
            && spreadType == null && strategy == null && exclusionZone == null;
    }

    /**
     * Resolve spread type to enum, with fallback.
     * @param fallback Default if spreadType is null or invalid
     * @return Resolved SpreadType
     */
    public LocateHelper.SpreadType resolveSpreadType(LocateHelper.SpreadType fallback) {
        if (spreadType == null) {
            return fallback;
        }
        try {
            return LocateHelper.SpreadType.valueOf(spreadType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Check if strategy is "random_spread" (default).
     */
    public boolean isRandomSpread() {
        return strategy == null || "random_spread".equalsIgnoreCase(strategy);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PlacementRule{");
        if (spacing != null) sb.append("spacing=").append(spacing).append(", ");
        if (separation != null) sb.append("separation=").append(separation).append(", ");
        if (salt != null) sb.append("salt=").append(salt).append(", ");
        if (spreadType != null) sb.append("spreadType=").append(spreadType).append(", ");
        if (strategy != null) sb.append("strategy=").append(strategy).append(", ");
        if (sb.charAt(sb.length() - 2) == ',') {
            sb.setLength(sb.length() - 2); // Remove trailing ", "
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builder for easier construction.
     */
    public static class Builder {
        private Integer spacing;
        private Integer separation;
        private Integer salt;
        private String spreadType;
        private String strategy;
        private ExclusionZone exclusionZone;

        public Builder spacing(Integer spacing) {
            this.spacing = spacing;
            return this;
        }

        public Builder separation(Integer separation) {
            this.separation = separation;
            return this;
        }

        public Builder salt(Integer salt) {
            this.salt = salt;
            return this;
        }

        public Builder spreadType(String spreadType) {
            this.spreadType = spreadType;
            return this;
        }

        public Builder strategy(String strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder exclusionZone(ExclusionZone exclusionZone) {
            this.exclusionZone = exclusionZone;
            return this;
        }

        public PlacementRule build() {
            return new PlacementRule(spacing, separation, salt, spreadType, strategy, exclusionZone);
        }
    }
}
