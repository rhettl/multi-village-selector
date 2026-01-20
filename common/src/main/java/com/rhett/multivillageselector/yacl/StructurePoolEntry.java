package com.rhett.multivillageselector.yacl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable wrapper for a structure pool entry.
 * Used by YACL GUI for editing.
 */
public class StructurePoolEntry {
    private String structure;
    private Map<String, Integer> biomes;
    private boolean empty;

    public StructurePoolEntry() {
        this.structure = "";
        this.biomes = new LinkedHashMap<>();
        this.empty = false;
    }

    public StructurePoolEntry(String structure, Map<String, Integer> biomes, boolean empty) {
        this.structure = structure != null ? structure : "";
        this.biomes = biomes != null ? new LinkedHashMap<>(biomes) : new LinkedHashMap<>();
        this.empty = empty;
    }

    public String getStructure() {
        return structure;
    }

    public void setStructure(String structure) {
        this.structure = structure;
    }

    public Map<String, Integer> getBiomes() {
        return biomes;
    }

    public void setBiomes(Map<String, Integer> biomes) {
        this.biomes = biomes != null ? new LinkedHashMap<>(biomes) : new LinkedHashMap<>();
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    /**
     * Formats biomes map for display
     */
    public String getBiomesDisplay() {
        if (biomes.isEmpty()) {
            return "No biomes configured";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Integer> entry : biomes.entrySet()) {
            if (count++ < 3) {
                if (count > 1) sb.append(", ");
                sb.append(String.format("%s: %d", entry.getKey(), entry.getValue()));
            }
        }
        if (biomes.size() > 3) {
            sb.append(String.format(" +%d more", biomes.size() - 3));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        if (empty) {
            return "[Empty Entry]";
        }
        return structure.isEmpty() ? "[No structure]" : structure;
    }

    /**
     * Creates a copy of this entry
     */
    public StructurePoolEntry copy() {
        return new StructurePoolEntry(this.structure, this.biomes, this.empty);
    }
}
