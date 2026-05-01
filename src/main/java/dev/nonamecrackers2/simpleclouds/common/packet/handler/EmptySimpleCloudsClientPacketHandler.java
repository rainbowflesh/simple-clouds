package dev.nonamecrackers2.simpleclouds.common.packet.handler;

import dev.nonamecrackers2.simpleclouds.client.packet.handler.SimpleCloudsClientPacketHandler;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.SendCloudManagerPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.SendCloudRegionsPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.SendCloudTypesPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.SpawnLightningPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.UpdateCloudRegionsPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.UpdateCloudManagerPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyAllowRainInDryBiomesUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyCloudModeUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyDryBiomeRainMinStorminessUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyDryBiomeRainTagsUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifySingleModeCloudTypeUpdatedPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class EmptySimpleCloudsClientPacketHandler implements SimpleCloudsClientPacketHandler {
	public static final EmptySimpleCloudsClientPacketHandler INSTANCE = new EmptySimpleCloudsClientPacketHandler();

	private EmptySimpleCloudsClientPacketHandler() {
	}

	@Override
	public void handleUpdateCloudManagerPayload(UpdateCloudManagerPayload packet, IPayloadContext context) {
	}

	@Override
	public void handleSendCloudManagerPayload(SendCloudManagerPayload packet, IPayloadContext context) {
	}

	@Override
	public void handleSendCloudRegionsPacket(SendCloudRegionsPayload packet, IPayloadContext context) {
	}

	@Override
	public void handleUpdateCloudRegionsPayload(UpdateCloudRegionsPayload packet, IPayloadContext context) {
	}

	@Override
	public void handleSendCloudTypesPayload(SendCloudTypesPayload packet, IPayloadContext context) {
	}

	@Override
	public void handleSpawnLightningPayload(SpawnLightningPayload packet, IPayloadContext context) {
	}

	@Override
	public void handleNotifyAllowRainInDryBiomesUpdatedPayload(NotifyAllowRainInDryBiomesUpdatedPayload packet,
			IPayloadContext context) {
	}

	@Override
	public void handleNotifyDryBiomeRainMinStorminessUpdatedPayload(
			NotifyDryBiomeRainMinStorminessUpdatedPayload packet, IPayloadContext context) {
	}

	@Override
	public void handleNotifyDryBiomeRainTagsUpdatedPayload(NotifyDryBiomeRainTagsUpdatedPayload packet,
			IPayloadContext context) {
	}

	@Override
	public void handleNotifyCloudModeUpdatedPayload(NotifyCloudModeUpdatedPayload packet, IPayloadContext context) {
	}

	@Override
	public void handleNotifySingleModeCloudTypeUpdatedPayload(NotifySingleModeCloudTypeUpdatedPayload packet,
			IPayloadContext context) {
	}
}
