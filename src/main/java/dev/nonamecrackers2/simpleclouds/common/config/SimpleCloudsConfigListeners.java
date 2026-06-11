package dev.nonamecrackers2.simpleclouds.common.config;

import java.util.List;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyAllowRainInDryBiomesUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyCloudModeUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyDryBiomeRainMinStorminessUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyDryBiomeRainTagsUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifySingleModeCloudTypeUpdatedPayload;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SyncType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import dev.nonamecrackers2.simpleclouds.common.config.listener.ConfigListener;

public class SimpleCloudsConfigListeners {
	private static ConfigListener listener;

	public static void registerListener() {
		listener = ConfigListener.builder(ModConfig.Type.SERVER, SimpleCloudsMod.MODID)
				.addListener(SimpleCloudsConfig.SERVER.cloudMode, (o, n) -> onCloudModeChanged(n))
				.addListener(SimpleCloudsConfig.SERVER.cloudSpeed, (o, n) -> onCloudSpeedChanged(n.floatValue()))
				.addListener(SimpleCloudsConfig.SERVER.cloudHeight, (o, n) -> onCloudHeightChanged(n))
				.addListener(SimpleCloudsConfig.SERVER.allowRainInDryBiomes, (o, n) -> onAllowRainInDryBiomesChanged(n))
				.addListener(SimpleCloudsConfig.SERVER.dryBiomeRainMinStorminess,
						(o, n) -> onDryBiomeRainMinStorminessChanged(n.doubleValue()))
				.addListener(SimpleCloudsConfig.SERVER.dryBiomeRainTags, (o, n) -> onRainBiomeTagsChanged())
				.addListener(SimpleCloudsConfig.SERVER.normalRainBiomeTags, (o, n) -> onRainBiomeTagsChanged())
				.addListener(SimpleCloudsConfig.SERVER.singleModeCloudType, (o, n) -> onSingleModeCloudTypeChanged(n))
				.buildAndRegister();
	}

	public static void pollNow() {
		if (listener != null)
			listener.poll();
	}

	public static void onCloudModeChanged(CloudMode newMode) {
		executeOnServerThread(() -> {
			MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
			if (server == null)
				return;

			for (ServerLevel level : server.getAllLevels()) {
				ServerCloudManager manager = (ServerCloudManager) CloudManager.get(level);
				if (newMode != CloudMode.SINGLE && manager.getClouds().isEmpty()) {
					for (ServerPlayer player : level.players())
						manager.onPlayerJoin(player);
					manager.queueSync(SyncType.CLOUD_FORMATIONS);
				}
			}

			PacketDistributor.sendToAllPlayers(new NotifyCloudModeUpdatedPayload(newMode));
		});
	}

	public static void onSingleModeCloudTypeChanged(String newType) {
		executeOnServerThread(
				() -> PacketDistributor.sendToAllPlayers(new NotifySingleModeCloudTypeUpdatedPayload(newType)));
	}

	public static void onAllowRainInDryBiomesChanged(boolean allowRainInDryBiomes) {
		executeOnServerThread(() -> PacketDistributor.sendToAllPlayers(
				new NotifyAllowRainInDryBiomesUpdatedPayload(allowRainInDryBiomes)));
	}

	public static void onDryBiomeRainMinStorminessChanged(double dryBiomeRainMinStorminess) {
		executeOnServerThread(() -> PacketDistributor.sendToAllPlayers(
				new NotifyDryBiomeRainMinStorminessUpdatedPayload(dryBiomeRainMinStorminess)));
	}

	public static void onRainBiomeTagsChanged() {
		List<String> dryBiomeRainTags = List.copyOf(SimpleCloudsConfig.SERVER.dryBiomeRainTags.get());
		List<String> normalRainBiomeTags = List.copyOf(SimpleCloudsConfig.SERVER.normalRainBiomeTags.get());
		CloudManager.updateRainBiomeTags(dryBiomeRainTags, normalRainBiomeTags);
		executeOnServerThread(
				() -> PacketDistributor.sendToAllPlayers(createDryBiomeRainTagsUpdatedPayload(dryBiomeRainTags,
						normalRainBiomeTags)));
	}

	public static void syncDryBiomeRainSettings(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player,
				new NotifyAllowRainInDryBiomesUpdatedPayload(SimpleCloudsConfig.SERVER.allowRainInDryBiomes.get()));
		PacketDistributor.sendToPlayer(player, new NotifyDryBiomeRainMinStorminessUpdatedPayload(
				SimpleCloudsConfig.SERVER.dryBiomeRainMinStorminess.get()));
		PacketDistributor.sendToPlayer(player,
				createDryBiomeRainTagsUpdatedPayload(SimpleCloudsConfig.SERVER.dryBiomeRainTags.get(),
						SimpleCloudsConfig.SERVER.normalRainBiomeTags.get()));
	}

	public static void onCloudSpeedChanged(float newSpeed) {
		executeOnServerThread(() -> {
			MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
			if (server == null)
				return;
			for (ServerLevel level : server.getAllLevels()) {
				ServerCloudManager manager = (ServerCloudManager) CloudManager.get(level);
				manager.setCloudSpeed(newSpeed);
				manager.queueSync(SyncType.MOVEMENT);
			}
		});
	}

	public static void onCloudHeightChanged(int newHeight) {
		executeOnServerThread(() -> {
			MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
			if (server == null)
				return;
			for (ServerLevel level : server.getAllLevels()) {
				ServerCloudManager manager = (ServerCloudManager) CloudManager.get(level);
				manager.setCloudHeight(newHeight);
				manager.queueSync(SyncType.MOVEMENT);
			}
		});
	}

	private static void executeOnServerThread(Runnable runnable) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server != null)
			server.execute(runnable);
	}

	private static NotifyDryBiomeRainTagsUpdatedPayload createDryBiomeRainTagsUpdatedPayload(
			List<? extends String> dryBiomeRainTags, List<? extends String> normalRainBiomeTags) {
		List<String> dryTagIds = List.copyOf(dryBiomeRainTags);
		List<String> normalTagIds = List.copyOf(normalRainBiomeTags);
		return new NotifyDryBiomeRainTagsUpdatedPayload(dryTagIds,
				CloudManager.resolveDryBiomeRainBiomeIds(CloudManager.mergeBiomeTagIds(dryTagIds, normalTagIds)),
				normalTagIds,
				CloudManager.resolveDryBiomeRainBiomeIds(normalTagIds));
	}
}
