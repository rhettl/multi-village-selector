package com.rhett.multivillageselector.config;

import com.rhett.multivillageselector.config.MVSConfig;

import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Immutable configuration state for MVS.
 * Pure data class - no logic, no I/O, no side effects.
 * Completely testable.
 */
public class ConfigState {

    // Feature flags
    public final boolean enabled;
    public final boolean debugLogging;
    public final boolean debugCmd;
    public final boolean showLaunchMessage;

    // Structure sets
    public final List<String> blockStructureSets;
    public final List<String> interceptStructureSets;

    // Structure pool (raw and configured)
    public final List<MVSConfig.RawConfigEntry> structurePoolRaw;
    public final List<MVSConfig.ConfiguredStructure> structurePool;

    // Blacklist
    public final List<String> blacklistedStructures;

    // Biome frequency (controls spawn density per biome/pattern)
    public final Map<String, Double> biomeFrequency;

    // Relaxed biome validation (bypasses vanilla's placement-point biome check for 3D biome mods)
    public final boolean relaxedBiomeValidation;

    // Placement rules (per-structure-set placement configuration)
    public final Map<String, PlacementRule> placement;

    // Validation warnings (non-fatal issues found during parsing)
    public final List<String> validationWarnings;

    /**
     * Full constructor - creates immutable config state.
     * All lists are defensively copied to ensure immutability.
     */
    public ConfigState(
            boolean enabled,
            boolean debugLogging,
            boolean debugCmd,
            boolean showLaunchMessage,
            List<String> blockStructureSets,
            List<String> interceptStructureSets,
            List<MVSConfig.RawConfigEntry> structurePoolRaw,
            List<MVSConfig.ConfiguredStructure> structurePool,
            List<String> blacklistedStructures,
            Map<String, Double> biomeFrequency,
            boolean relaxedBiomeValidation,
            Map<String, PlacementRule> placement,
            List<String> validationWarnings) {

        this.enabled = enabled;
        this.debugLogging = debugLogging;
        this.debugCmd = debugCmd;
        this.showLaunchMessage = showLaunchMessage;

        // Defensive copy + make unmodifiable
        this.blockStructureSets = Collections.unmodifiableList(List.copyOf(blockStructureSets));
        this.interceptStructureSets = Collections.unmodifiableList(List.copyOf(interceptStructureSets));
        this.structurePoolRaw = Collections.unmodifiableList(List.copyOf(structurePoolRaw));
        this.structurePool = Collections.unmodifiableList(List.copyOf(structurePool));
        this.blacklistedStructures = Collections.unmodifiableList(List.copyOf(blacklistedStructures));
        this.biomeFrequency = Collections.unmodifiableMap(Map.copyOf(biomeFrequency));
        this.relaxedBiomeValidation = relaxedBiomeValidation;
        this.placement = Collections.unmodifiableMap(Map.copyOf(placement));
        this.validationWarnings = Collections.unmodifiableList(List.copyOf(validationWarnings));
    }

    /**
     * Builder for easier construction in tests.
     */
    public static class Builder {
        private boolean enabled = true;
        private boolean debugLogging = false;
        private boolean debugCmd = false;
        private boolean showLaunchMessage = false;
        private List<String> blockStructureSets = List.of();
        private List<String> interceptStructureSets = List.of();
        private List<MVSConfig.RawConfigEntry> structurePoolRaw = List.of();
        private List<MVSConfig.ConfiguredStructure> structurePool = List.of();
        private List<String> blacklistedStructures = List.of();
        private Map<String, Double> biomeFrequency = Map.of();
        private boolean relaxedBiomeValidation = false;
        private Map<String, PlacementRule> placement = Map.of();
        private List<String> validationWarnings = List.of();

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder debugLogging(boolean debugLogging) {
            this.debugLogging = debugLogging;
            return this;
        }

        public Builder debugCmd(boolean debugCmd) {
            this.debugCmd = debugCmd;
            return this;
        }

        public Builder showLaunchMessage(boolean showLaunchMessage) {
            this.showLaunchMessage = showLaunchMessage;
            return this;
        }

        public Builder blockStructureSets(List<String> blockStructureSets) {
            this.blockStructureSets = blockStructureSets;
            return this;
        }

        public Builder interceptStructureSets(List<String> interceptStructureSets) {
            this.interceptStructureSets = interceptStructureSets;
            return this;
        }

        public Builder structurePoolRaw(List<MVSConfig.RawConfigEntry> structurePoolRaw) {
            this.structurePoolRaw = structurePoolRaw;
            return this;
        }

        public Builder structurePool(List<MVSConfig.ConfiguredStructure> structurePool) {
            this.structurePool = structurePool;
            return this;
        }

        public Builder blacklistedStructures(List<String> blacklistedStructures) {
            this.blacklistedStructures = blacklistedStructures;
            return this;
        }

        public Builder biomeFrequency(Map<String, Double> biomeFrequency) {
            this.biomeFrequency = biomeFrequency;
            return this;
        }

        public Builder relaxedBiomeValidation(boolean relaxedBiomeValidation) {
            this.relaxedBiomeValidation = relaxedBiomeValidation;
            return this;
        }

        public Builder placement(Map<String, PlacementRule> placement) {
            this.placement = placement;
            return this;
        }

        public Builder validationWarnings(List<String> validationWarnings) {
            this.validationWarnings = validationWarnings;
            return this;
        }

        public ConfigState build() {
            return new ConfigState(
                enabled, debugLogging, debugCmd, showLaunchMessage,
                blockStructureSets, interceptStructureSets,
                structurePoolRaw, structurePool, blacklistedStructures,
                biomeFrequency, relaxedBiomeValidation, placement, validationWarnings
            );
        }
    }
}
