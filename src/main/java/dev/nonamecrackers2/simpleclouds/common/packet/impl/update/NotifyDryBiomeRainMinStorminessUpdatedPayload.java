package dev.nonamecrackers2.simpleclouds.common.packet.impl.update;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NotifyDryBiomeRainMinStorminessUpdatedPayload(double dryBiomeRainMinStorminess)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NotifyDryBiomeRainMinStorminessUpdatedPayload> TYPE = new CustomPacketPayload.Type<>(
            SimpleCloudsMod.id("notify_dry_biome_rain_min_storminess_updated"));

    public static final StreamCodec<ByteBuf, NotifyDryBiomeRainMinStorminessUpdatedPayload> CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.DOUBLE,
                    NotifyDryBiomeRainMinStorminessUpdatedPayload::dryBiomeRainMinStorminess,
                    NotifyDryBiomeRainMinStorminessUpdatedPayload::new);

    @Override
    public CustomPacketPayload.Type<NotifyDryBiomeRainMinStorminessUpdatedPayload> type() {
        return TYPE;
    }
}