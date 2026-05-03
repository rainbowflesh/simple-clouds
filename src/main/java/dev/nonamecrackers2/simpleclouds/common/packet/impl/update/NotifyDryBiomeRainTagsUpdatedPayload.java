package dev.nonamecrackers2.simpleclouds.common.packet.impl.update;

import java.util.List;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NotifyDryBiomeRainTagsUpdatedPayload(List<String> dryBiomeRainTags, List<String> dryBiomeRainBiomes,
                List<String> normalRainBiomeTags, List<String> normalRainBiomeBiomes)
                implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NotifyDryBiomeRainTagsUpdatedPayload> TYPE = new CustomPacketPayload.Type<>(
                        SimpleCloudsMod.id("notify_dry_biome_rain_tags_updated"));

        public static final StreamCodec<ByteBuf, NotifyDryBiomeRainTagsUpdatedPayload> CODEC = StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        NotifyDryBiomeRainTagsUpdatedPayload::dryBiomeRainTags,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        NotifyDryBiomeRainTagsUpdatedPayload::dryBiomeRainBiomes,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        NotifyDryBiomeRainTagsUpdatedPayload::normalRainBiomeTags,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        NotifyDryBiomeRainTagsUpdatedPayload::normalRainBiomeBiomes,
                        NotifyDryBiomeRainTagsUpdatedPayload::new);

        @Override
        public CustomPacketPayload.Type<NotifyDryBiomeRainTagsUpdatedPayload> type() {
                return TYPE;
        }
}