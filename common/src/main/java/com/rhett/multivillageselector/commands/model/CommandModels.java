package com.rhett.multivillageselector.commands.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data models for MVS commands.
 * Extracted from MVSCommands for reusability and testability.
 */
public class CommandModels {

    /**
     * JAR scan intermediate data.
     */
    public static class JarScanData {
        public Map<String, StructureInfo> structures = new HashMap<>();
        public Map<String, StructureSetInfo> structureSetInfo = new HashMap<>();
    }

    /**
     * Result of unified structure scanning.
     */
    public static class ScanResult {
        public Map<String, StructureInfo> allStructures = new HashMap<>();
        public Map<String, StructureSetInfo> structureSetInfo = new HashMap<>();

        // Categorized structures
        public List<StructureInfo> coreStructures = new ArrayList<>();        // *:villages sets
        public List<StructureInfo> likelyStructures = new ArrayList<>();      // Likely other villages
        public List<StructureInfo> questionableStructures = new ArrayList<>(); // Uncertain structures
    }

    /**
     * Structure set metadata (empty weights, spacing, etc.)
     */
    public static class StructureSetInfo {
        public String setId;
        public Integer emptyWeight;
        public Integer spacing;      // Spacing in chunks
        public Integer separation;   // Minimum separation in chunks

        public StructureSetInfo(String setId) {
            this.setId = setId;
        }
    }

    /**
     * Unified structure information from all sources.
     */
    public static class StructureInfo {
        public String id;                    // e.g. "bca:village/default_small"
        public String modId;                 // e.g. "bca"

        // Registry data
        public Integer registryWeight;       // Weight from active registry
        public String registrySet;           // Structure set from registry (e.g. "minecraft:villages")
        public String registryBiome;         // Biome tag from registry

        // JAR data
        public Integer jarWeight;            // Weight from structure_set or lithostitched modifier
        public String jarSet;                // Structure set from JAR
        public String jarBiome;              // Biome tag from structure file in JAR
        public String jarSource;             // "structure_set" or "lithostitched"

        public StructureInfo(String id) {
            this.id = id;
            this.modId = id.contains(":") ? id.split(":")[0] : "unknown";
        }

        // Get final weight (highest wins)
        public Integer getFinalWeight() {
            if (registryWeight == null && jarWeight == null) return null;
            if (registryWeight == null) return jarWeight;
            if (jarWeight == null) return registryWeight;
            return Math.max(registryWeight, jarWeight);
        }

        // Get final biome (registry preferred, fallback to JAR)
        public String getFinalBiome() {
            if (registryBiome != null && !registryBiome.equals("unknown")) return registryBiome;
            return jarBiome;
        }

        // Get final structure set (prefer JAR over registry - JAR has authoritative structure_set data)
        public String getFinalSet() {
            // Prefer JAR if it has a real structure_set (not uncategorized)
            if (jarSet != null && !jarSet.equals("NONE (uncategorized)")) return jarSet;
            // Fallback to registry
            if (registrySet != null) return registrySet;
            // Last resort: uncategorized
            return "NONE (uncategorized)";
        }
    }

    /**
     * Helper class for JAR scanning intermediate data.
     */
    public static class StructureJarInfo {
        public String name;                  // Structure ID
        public String modId;                 // Mod that defines it
        public Integer weight;               // Weight from structure_set
        public String structureSet;          // Structure set ID
        public String biomeTag;              // Biome tag from structure file
        public boolean foundInStructureSet;  // Found in structure_set file
        public boolean foundInStructureFile; // Found in individual structure file

        public StructureJarInfo(String name, String modId) {
            this.name = name;
            this.modId = modId;
        }
    }
}
