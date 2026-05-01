package dev.nonamecrackers2.simpleclouds.common.packet.impl.update;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NotifyAllowRainInDryBiomesUpdatedPayload(boolean allowRainInDryBiomes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NotifyAllowRainInDryBiomesUpdatedPayload> TYPE = new CustomPacketPayload.Type<>(
            SimpleCloudsMod.id("notify_allow_rain_in_dry_biomes_updated"));

    public static final StreamCodec<ByteBuf, NotifyAllowRainInDryBiomesUpdatedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NotifyAllowRainInDryBiomesUpdatedPayload::allowRainInDryBiomes,
            NotifyAllowRainInDryBiomesUpdatedPayload::new);

    @Override
    public CustomPacketPayload.Type<NotifyAllowRainInDryBiomesUpdatedPayload> type() {
        return TYPE;
    }
}