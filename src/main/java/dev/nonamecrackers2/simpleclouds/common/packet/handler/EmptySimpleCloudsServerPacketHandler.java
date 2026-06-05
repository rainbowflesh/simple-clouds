package dev.nonamecrackers2.simpleclouds.common.packet.handler;

import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.ApplyServerConfigEditsPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class EmptySimpleCloudsServerPacketHandler implements SimpleCloudsServerPacketHandler {
    public static final EmptySimpleCloudsServerPacketHandler INSTANCE = new EmptySimpleCloudsServerPacketHandler();

    private EmptySimpleCloudsServerPacketHandler() {
    }

    @Override
    public void handleApplyServerConfigEditsPayload(ApplyServerConfigEditsPayload packet, IPayloadContext context) {
    }
}