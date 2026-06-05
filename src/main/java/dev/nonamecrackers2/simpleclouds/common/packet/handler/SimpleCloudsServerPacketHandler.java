package dev.nonamecrackers2.simpleclouds.common.packet.handler;

import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.ApplyServerConfigEditsPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public interface SimpleCloudsServerPacketHandler {
    void handleApplyServerConfigEditsPayload(ApplyServerConfigEditsPayload packet, IPayloadContext context);
}