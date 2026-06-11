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
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.config.ModConfig;
import dev.nonamecrackers2.simpleclouds.client.gui.Popup;
import dev.nonamecrackers2.simpleclouds.common.config.listener.ConfigListener;

public class SimpleCloudsClientConfigListeners {
	private static ConfigListener listener;

	public static void registerListener() {
		listener = ConfigListener.builder(ModConfig.Type.CLIENT, SimpleCloudsMod.MODID)
				.addListener(SimpleCloudsConfig.CLIENT.cloudMode, (o, n) -> onCloudModeUpdated(n))
				.addListener(SimpleCloudsConfig.CLIENT.shadedClouds, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.transparency, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.edgeTransparencyFade, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.transparencyRenderDistancePercentage,
						(o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.atmosphericClouds, (o, n) -> reloadResources())
				.addListener(SimpleCloudsConfig.CLIENT.levelOfDetail, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.distantShadows, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.shadowDistance, (o, n) -> requestReload(false))
				.addListener(SimpleCloudsConfig.CLIENT.singleModeCloudType, (o, n) -> onSingleModeCloudTypeUpdated(n))
				.addListener(SimpleCloudsConfig.CLIENT.customRainSounds, (o, n) -> reloadResources())
				.buildAndRegister();
		// ModConfigEvent.Loading fires during registerConfig() in the mod constructor,
		// before clientInit registers this listener. Seed the caches now so the first
		// save via pollNow() detects changes rather than just initialising.
		if (SimpleCloudsConfig.CLIENT_SPEC.isLoaded())
			listener.poll();
	}

	public static void pollNow() {
		if (listener != null)
			listener.poll();
	}

	/**
	 * Pushes the client's locally-configured values onto the integrated
	 * singleplayer server's config so a singleplayer world reflects the player's
	 * own client-side preferences without requiring them to edit the server config
	 * separately. No-ops when connected to a remote server.
	 */
	public static void syncSingleplayerConfig() {
		if (!canSyncToSingleplayerServer())
			return;
		syncSingleplayerCloudMode(SimpleCloudsConfig.CLIENT.cloudMode.get());
		syncSingleplayerSingleModeCloudType(SimpleCloudsConfig.CLIENT.singleModeCloudType.get());
	}

	public static void onCloudModeUpdated(CloudMode mode) {
		syncSingleplayerCloudMode(mode);
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
		SimpleCloudsRenderer.getInstance().requestReload();
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

			if (syncSingleplayerSingleModeCloudType(type))
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
			SimpleCloudsRenderer.getInstance().requestReload();
		});
	}

	public static void reloadResources() {
		Minecraft.getInstance().execute(() -> {
			Minecraft.getInstance().reloadResourcePacks();
		});
	}

	public static void onServerConfigEditResult(boolean success, String message) {
		Minecraft.getInstance().execute(() -> {
			if (success)
				SimpleCloudsRenderer.getInstance().requestReload();
			Popup.createInfoPopup(null, 300,
					Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
		});
	}

	private static boolean syncSingleplayerCloudMode(CloudMode mode) {
		return executeForSingleplayerServer(server -> SimpleCloudsConfig.SERVER.cloudMode.set(mode));
	}

	private static boolean syncSingleplayerSingleModeCloudType(String type) {
		return executeForSingleplayerServer(server -> SimpleCloudsConfig.SERVER.singleModeCloudType.set(type));
	}

	private static boolean executeForSingleplayerServer(java.util.function.Consumer<MinecraftServer> action) {
		Minecraft mc = Minecraft.getInstance();
		if (!canSyncToSingleplayerServer())
			return false;
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null)
			return false;
		server.execute(() -> action.accept(server));
		return true;
	}

	private static boolean canSyncToSingleplayerServer() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getSingleplayerServer() != null && !ClientCloudManager.isRemoteServerAvailable();
	}
}
