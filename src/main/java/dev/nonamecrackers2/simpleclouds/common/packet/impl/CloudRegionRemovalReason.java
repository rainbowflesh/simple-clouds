package dev.nonamecrackers2.simpleclouds.common.packet.impl;

public enum CloudRegionRemovalReason {
    OUT_OF_SYNC_RANGE,
    DELETE;

    public boolean removesCloudLocally() {
        return this == DELETE;
    }
}