package dev.nonamecrackers2.simpleclouds.common.cloud;

public enum CloudColorMode {
    DEFAULT("default", 0.0F),
    FIXED("fixed", 1.0F),
    RAINBOW("rainbow", 2.0F);

    private final String serializedName;
    private final float shaderValue;

    CloudColorMode(String serializedName, float shaderValue) {
        this.serializedName = serializedName;
        this.shaderValue = shaderValue;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public float getShaderValue() {
        return this.shaderValue;
    }

    public static CloudColorMode byName(String name) {
        if ("rainbow_cloud".equals(name) || "roygbiv".equals(name))
            return RAINBOW;
        for (CloudColorMode mode : values()) {
            if (mode.serializedName.equals(name))
                return mode;
        }
        throw new IllegalArgumentException("Unknown cloud color mode '" + name + "'");
    }
}