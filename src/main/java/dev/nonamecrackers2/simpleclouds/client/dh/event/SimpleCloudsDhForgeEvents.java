package dev.nonamecrackers2.simpleclouds.client.dh.event;

import com.seibel.distanthorizons.api.DhApi;

import dev.nonamecrackers2.simpleclouds.api.client.event.ModifyCloudRenderDistanceEvent;
import dev.nonamecrackers2.simpleclouds.client.dh.SimpleCloudsDhCompatHandler;
import dev.nonamecrackers2.simpleclouds.client.dh.pipeline.DhSupportPipeline;
import dev.nonamecrackers2.simpleclouds.client.event.impl.DetermineCloudRenderPipelineEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class SimpleCloudsDhForgeEvents {
	private static boolean shouldUseDhPipeline() {
		return DhApi.Delayed.configs != null && SimpleCloudsDhCompatHandler.shouldUseDhRendering();
	}

	@SubscribeEvent
	public static void modifyRenderDistance(ModifyCloudRenderDistanceEvent event) {
		if (!shouldUseDhPipeline())
			return;

		float renderDistance = event.getRenderDistance();
		event.setRenderDistance(Math.min(renderDistance,
				(float) DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16.0F));
	}

	@SubscribeEvent
	public static void determineRenderPipeline(DetermineCloudRenderPipelineEvent event) {
		if (shouldUseDhPipeline())
			event.overridePipeline(DhSupportPipeline.INSTANCE);
	}
}
