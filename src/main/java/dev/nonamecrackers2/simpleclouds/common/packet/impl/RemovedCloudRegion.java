package dev.nonamecrackers2.simpleclouds.common.packet.impl;

import net.minecraft.network.FriendlyByteBuf;

public record RemovedCloudRegion(int syncId, CloudRegionRemovalReason reason) {
    public RemovedCloudRegion(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readEnum(CloudRegionRemovalReason.class));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.syncId);
        buffer.writeEnum(this.reason);
    }
}