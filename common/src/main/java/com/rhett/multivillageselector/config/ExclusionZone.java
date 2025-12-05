package com.rhett.multivillageselector.config;

/**
 * Represents an exclusion zone for structure placement.
 * Structures won't spawn within chunk_count chunks of the other_set.
 *
 * Example: Pillager outposts have exclusion_zone referencing villages,
 * meaning outposts won't spawn within 10 chunks of a village.
 *
 * This matches vanilla's StructurePlacement.ExclusionZone exactly.
 */
public class ExclusionZone {

    /** The structure set to check distance from (e.g., "minecraft:villages") */
    public final String otherSet;

    /** Number of chunks to keep clear (e.g., 10 means no spawn within 10 chunks) */
    public final int chunkCount;

    public ExclusionZone(String otherSet, int chunkCount) {
        this.otherSet = otherSet;
        this.chunkCount = chunkCount;
    }

    /**
     * Validate the exclusion zone configuration.
     * @return null if valid, error message if invalid
     */
    public String validate() {
        if (otherSet == null || otherSet.isBlank()) {
            return "exclusion_zone.other_set is required";
        }
        if (chunkCount < 1 || chunkCount > 16) {
            return "exclusion_zone.chunk_count must be between 1 and 16";
        }
        return null; // Valid
    }

    @Override
    public String toString() {
        return String.format("ExclusionZone{otherSet='%s', chunkCount=%d}", otherSet, chunkCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExclusionZone that = (ExclusionZone) o;
        return chunkCount == that.chunkCount && otherSet.equals(that.otherSet);
    }

    @Override
    public int hashCode() {
        return 31 * otherSet.hashCode() + chunkCount;
    }
}