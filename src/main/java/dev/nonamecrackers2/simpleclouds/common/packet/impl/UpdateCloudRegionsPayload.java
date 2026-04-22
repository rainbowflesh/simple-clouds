package dev.nonamecrackers2.simpleclouds.common.packet.impl;

import java.util.List;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UpdateCloudRegionsPayload(List<CloudRegion> addedCloudRegions, List<Integer> removedCloudRegionIds)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateCloudRegionsPayload> TYPE = new CustomPacketPayload.Type<>(
            SimpleCloudsMod.id("update_cloud_regions"));
    public static final StreamCodec<FriendlyByteBuf, UpdateCloudRegionsPayload> CODEC = StreamCodec
            .ofMember(UpdateCloudRegionsPayload::encode, UpdateCloudRegionsPayload::new);

    public UpdateCloudRegionsPayload(FriendlyByteBuf buffer) {
        this(
                buffer.readList(CloudRegion::new),
                buffer.readList(FriendlyByteBuf::readVarInt));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(this.addedCloudRegions, (buf, cloud) -> cloud.toPacket(buf));
        buffer.writeCollection(this.removedCloudRegionIds, FriendlyByteBuf::writeVarInt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}