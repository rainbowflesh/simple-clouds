package dev.nonamecrackers2.simpleclouds.client.config;

import java.util.List;

import com.google.common.base.Joiner;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.client.cloud.ClientSideCloudTypeManager;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.SingleRegionCloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.world.ClientCloudManager;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.ModConfig;
import nonamecrackers2.crackerslib.client.gui.Popup;
import nonamecrackers2.crackerslib.common.config.listener.ConfigListener;

public class SimpleCloudsClientConfigListeners {
	public static void registerListener() {
		ConfigListener.builder(ModConfig.Type.CLIENT, SimpleCloudsMod.MODID)
				.addListener(SimpleCloudsConfig.CLIENT.cloudMode, (o, n) -> onCloudModeUpdated(n))
				.addListener(SimpleCloudsConfig.CLIENT.shadedClouds, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.transparency, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.levelOfDetail, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.distantShadows, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.shadowDistance, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.concurrentComputeDispatches, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.singleModeCloudType, (o, n) -> onSingleModeCloudTypeUpdated(n))
				.addListener(SimpleCloudsConfig.CLIENT.customRainSounds, (o, n) -> reloadResources())
				.buildAndRegister();
	}

	public static void onCloudModeUpdated(CloudMode mode) {
		requestReload(true);
	}

	/**
	 * Updates the instance of the server config on the client with the value from
	 * the server.
	 * After called, this method will then request a reload from the cloud renderer,
	 * which
	 * will reinitialize the mesh generator so the change in the config value is
	 * applied.
	 */
	public static void onCloudModeUpdatedFromServer(CloudMode mode) {
		SimpleCloudsConfig.SERVER.cloudMode.set(mode);
		Popup.createInfoPopup(null, 300, Component.translatable("gui.simpleclouds.reload_confirmation.server.info"),
				() -> {
					SimpleCloudsRenderer.getInstance().requestReload();
				});
	}

	/**
	 * Updates the instance of the server config on the client with the value from
	 * the server.
	 * After called, this method will then update the single mode cloud type for the
	 * single mode cloud mesh
	 * generator.
	 */
	public static void onSingleModeCloudTypeUpdatedFromServer(String type) {
		SimpleCloudsConfig.SERVER.singleModeCloudType.set(type);
		if (SimpleCloudsRenderer.getInstance().getMeshGenerator() instanceof SingleRegionCloudMeshGenerator generator) {
			ClientSideCloudTypeManager.getInstance().getCloudTypeFromRawId(type).ifPresentOrElse(t -> {
				generator.setCloudType(t);
			}, () -> {
				generator.setCloudType(SimpleCloudsConstants.EMPTY);
			});
		}
	}

	public static void onAllowRainInDryBiomesUpdatedFromServer(boolean allowRainInDryBiomes) {
		SimpleCloudsConfig.SERVER.allowRainInDryBiomes.set(allowRainInDryBiomes);
	}

	public static void onDryBiomeRainMinStorminessUpdatedFromServer(double dryBiomeRainMinStorminess) {
		SimpleCloudsConfig.SERVER.dryBiomeRainMinStorminess.set(dryBiomeRainMinStorminess);
	}

	public static void onDryBiomeRainTagsUpdatedFromServer(List<String> dryBiomeRainTags,
			List<String> dryBiomeRainBiomes, List<String> normalRainBiomeTags, List<String> normalRainBiomeBiomes) {
		SimpleCloudsConfig.SERVER.dryBiomeRainTags.set(dryBiomeRainTags);
		SimpleCloudsConfig.SERVER.normalRainBiomeTags.set(normalRainBiomeTags);
		CloudManager.updateRainBiomeOverrides(dryBiomeRainTags, dryBiomeRainBiomes, normalRainBiomeTags,
				normalRainBiomeBiomes);
	}

	public static void onSingleModeCloudTypeUpdated(String type) {
		Minecraft.getInstance().execute(() -> {
			if (ClientCloudManager.isRemoteServerAvailable())
				return;

			ResourceLocation loc = ResourceLocation.tryParse(type);
			var types = ClientSideCloudTypeManager.getInstance().getCloudTypes();
			if (loc != null && types.containsKey(loc)
					&& ClientSideCloudTypeManager.isValidClientSideSingleModeCloudType(types.get(loc))) {
				if (SimpleCloudsRenderer.getInstance()
						.getMeshGenerator() instanceof SingleRegionCloudMeshGenerator generator)
					generator.setCloudType(types.get(loc));
			} else {
				Component valid = Component.literal(Joiner.on(", ").join(types.values().stream().filter(t -> {
					return ClientSideCloudTypeManager.isValidClientSideSingleModeCloudType(t);
				}).map(t -> t.id().toString()).iterator())).withStyle(ChatFormatting.YELLOW);
				Popup.createInfoPopup(null, 300,
						Component.translatable("gui.simpleclouds.unknown_or_invalid_client_side_cloud_type.info",
								loc == null ? type : loc.toString(), valid));
			}
		});
	}

	public static void requestReload(boolean skipIfServerAvailable) {
		Minecraft.getInstance().execute(() -> {
			if (skipIfServerAvailable && ClientCloudManager.isRemoteServerAvailable())
				return;
			Popup.createYesNoPopup(null, () -> {
				SimpleCloudsRenderer.getInstance().requestReload();
			}, 300, Component.translatable("gui.simpleclouds.requires_reload.info"));
			Popup.clearQueue();
		});
	}

	public static void reloadResources() {
		Minecraft.getInstance().execute(() -> {
			Popup.createYesNoPopup(null, () -> {
				Minecraft.getInstance().reloadResourcePacks();
			}, 300, Component.translatable("gui.simpleclouds.requires_reload_resource_packs.info"));
		});
	}
}
