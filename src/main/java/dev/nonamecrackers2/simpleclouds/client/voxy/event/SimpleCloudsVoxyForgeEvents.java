package dev.nonamecrackers2.simpleclouds.client.voxy.event;

import dev.nonamecrackers2.simpleclouds.client.event.impl.DetermineCloudRenderPipelineEvent;
import dev.nonamecrackers2.simpleclouds.client.voxy.pipeline.VoxySupportPipeline;
import net.neoforged.bus.api.SubscribeEvent;

public class SimpleCloudsVoxyForgeEvents {
    @SubscribeEvent
    public static void determineRenderPipeline(DetermineCloudRenderPipelineEvent event) {
        event.overridePipeline(VoxySupportPipeline.INSTANCE);
    }
}