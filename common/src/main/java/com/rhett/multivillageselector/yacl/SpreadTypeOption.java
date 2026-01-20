package com.rhett.multivillageselector.yacl;

import com.rhett.multivillageselector.util.LocateHelper;

/**
 * Enum wrapper for spread type values.
 * Used by YACL enum dropdown controller.
 */
public enum SpreadTypeOption {
    LINEAR("linear", "Linear", "Uniform random distribution (vanilla default)", LocateHelper.SpreadType.LINEAR),
    TRIANGULAR("triangular", "Triangular", "Bell curve toward cell center", LocateHelper.SpreadType.TRIANGULAR),
    GAUSSIAN("gaussian", "Gaussian", "Strongly center-biased distribution", LocateHelper.SpreadType.GAUSSIAN),
    EDGE_BIASED("edge_biased", "Edge Biased", "Prefer cell edges", LocateHelper.SpreadType.EDGE_BIASED),
    CORNER_BIASED("corner_biased", "Corner Biased", "Prefer cell corners", LocateHelper.SpreadType.CORNER_BIASED),
    FIXED_CENTER("fixed_center", "Fixed Center", "Always at exact cell center (deterministic grid)", LocateHelper.SpreadType.FIXED_CENTER);

    private final String configValue;
    private final String displayName;
    private final String description;
    private final LocateHelper.SpreadType actualEnum;

    SpreadTypeOption(String configValue, String displayName, String description, LocateHelper.SpreadType actualEnum) {
        this.configValue = configValue;
        this.displayName = displayName;
        this.description = description;
        this.actualEnum = actualEnum;
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

    public LocateHelper.SpreadType getActualEnum() {
        return actualEnum;
    }

    public static SpreadTypeOption fromConfigValue(String value) {
        if (value == null) return LINEAR;
        for (SpreadTypeOption option : values()) {
            if (option.configValue.equalsIgnoreCase(value)) {
                return option;
            }
        }
        return LINEAR;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
