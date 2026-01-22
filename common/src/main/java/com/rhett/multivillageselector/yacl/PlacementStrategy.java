package com.rhett.multivillageselector.yacl;

/**
 * Enum wrapper for placement strategy values.
 * Used by YACL enum dropdown controller.
 */
public enum PlacementStrategy {
    RANDOM_SPREAD("random_spread", "Random Spread", "Vanilla random placement (default)"),
    CONCENTRIC_RINGS("concentric_rings", "Concentric Rings", "Stronghold-like ring placement");

    private final String configValue;
    private final String displayName;
    private final String description;

    PlacementStrategy(String configValue, String displayName, String description) {
        this.configValue = configValue;
        this.displayName = displayName;
        this.description = description;
    }

    public String getConfigValue() {
        return configValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static PlacementStrategy fromConfigValue(String value) {
        if (value == null) return RANDOM_SPREAD;
        for (PlacementStrategy strategy : values()) {
            if (strategy.configValue.equalsIgnoreCase(value)) {
                return strategy;
            }
        }
        return RANDOM_SPREAD;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
