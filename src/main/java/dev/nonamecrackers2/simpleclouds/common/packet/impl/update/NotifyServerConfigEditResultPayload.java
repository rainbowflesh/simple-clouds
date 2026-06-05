package dev.nonamecrackers2.simpleclouds.common.packet.impl.update;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NotifyServerConfigEditResultPayload(boolean success, String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NotifyServerConfigEditResultPayload> TYPE = new CustomPacketPayload.Type<>(
            SimpleCloudsMod.id("notify_server_config_edit_result"));

    public static final StreamCodec<ByteBuf, NotifyServerConfigEditResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NotifyServerConfigEditResultPayload::success,
            ByteBufCodecs.STRING_UTF8, NotifyServerConfigEditResultPayload::message,
            NotifyServerConfigEditResultPayload::new);

    @Override
    public CustomPacketPayload.Type<NotifyServerConfigEditResultPayload> type() {
        return TYPE;
    }
}