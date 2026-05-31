package dev.nonamecrackers2.simpleclouds.common.packet.impl;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SendCloudManagerPayload(long seed, float speed, float scrollAngle, int cloudHeight)
		implements CustomPacketPayload, CloudManagerInfoPayload {
	public static final CustomPacketPayload.Type<SendCloudManagerPayload> TYPE = new CustomPacketPayload.Type<>(
			SimpleCloudsMod.id("send_cloud_manager"));

	public static final StreamCodec<FriendlyByteBuf, SendCloudManagerPayload> CODEC = CloudManagerInfoPayload
			.codec(SendCloudManagerPayload::new);

	public SendCloudManagerPayload(CloudManager<?> manager) {
		this(
				manager.getSeed(),
				manager.getCloudSpeed(),
				manager.getScrollAngle(),
				manager.getCloudHeight());
	}

	public SendCloudManagerPayload(FriendlyByteBuf buffer) {
		this(
				buffer.readLong(),
				buffer.readFloat(),
				buffer.readFloat(),
				buffer.readVarInt());
	}

	@Override
	public void encode(FriendlyByteBuf buffer) {
		buffer.writeLong(this.seed);
		CloudManagerInfoPayload.super.encode(buffer);
	}

	@Override
	public CustomPacketPayload.Type<SendCloudManagerPayload> type() {
		return TYPE;
	}
}
