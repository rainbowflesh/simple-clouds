package dev.nonamecrackers2.simpleclouds.common.packet.impl.update;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ApplyServerConfigEditsPayload(Map<String, String> changedValues) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ApplyServerConfigEditsPayload> TYPE = new CustomPacketPayload.Type<>(
            SimpleCloudsMod.id("apply_server_config_edits"));

    public static final StreamCodec<FriendlyByteBuf, ApplyServerConfigEditsPayload> CODEC = StreamCodec
            .ofMember(ApplyServerConfigEditsPayload::encode, ApplyServerConfigEditsPayload::new);

    public ApplyServerConfigEditsPayload(FriendlyByteBuf buffer) {
        this(readChangedValues(buffer));
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.changedValues.size());
        for (var entry : this.changedValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static Map<String, String> readChangedValues(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        Map<String, String> changedValues = new LinkedHashMap<>();
        for (int i = 0; i < count; i++)
            changedValues.put(buffer.readUtf(), buffer.readUtf());
        return changedValues;
    }

    @Override
    public CustomPacketPayload.Type<ApplyServerConfigEditsPayload> type() {
        return TYPE;
    }
}