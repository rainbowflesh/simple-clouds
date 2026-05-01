package dev.nonamecrackers2.simpleclouds.client.packet.handler;

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

public interface SimpleCloudsClientPacketHandler {
	void handleUpdateCloudManagerPayload(UpdateCloudManagerPayload packet, IPayloadContext context);

	void handleSendCloudManagerPayload(SendCloudManagerPayload packet, IPayloadContext context);

	void handleSendCloudRegionsPacket(SendCloudRegionsPayload packet, IPayloadContext context);

	void handleUpdateCloudRegionsPayload(UpdateCloudRegionsPayload packet, IPayloadContext context);

	void handleSendCloudTypesPayload(SendCloudTypesPayload packet, IPayloadContext context);

	void handleSpawnLightningPayload(SpawnLightningPayload packet, IPayloadContext context);

	void handleNotifyAllowRainInDryBiomesUpdatedPayload(NotifyAllowRainInDryBiomesUpdatedPayload packet,
			IPayloadContext context);

	void handleNotifyDryBiomeRainMinStorminessUpdatedPayload(NotifyDryBiomeRainMinStorminessUpdatedPayload packet,
			IPayloadContext context);

	void handleNotifyDryBiomeRainTagsUpdatedPayload(NotifyDryBiomeRainTagsUpdatedPayload packet,
			IPayloadContext context);

	void handleNotifyCloudModeUpdatedPayload(NotifyCloudModeUpdatedPayload packet, IPayloadContext context);

	void handleNotifySingleModeCloudTypeUpdatedPayload(NotifySingleModeCloudTypeUpdatedPayload packet,
			IPayloadContext context);
}
