package dev.nonamecrackers2.simpleclouds.common.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfigListeners;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.SendCloudManagerPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.SendCloudRegionsPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.UpdateCloudRegionsPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.UpdateCloudManagerPayload;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import dev.nonamecrackers2.simpleclouds.common.world.SyncType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class CloudManagerEvents {
	private static final Map<UUID, Set<Integer>> SYNCHED_CLOUDS_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Long> LAST_SYNCED_PLAYER_POSITIONS = new HashMap<>();

	@SubscribeEvent
	public static void onWorldTick(LevelTickEvent.Pre event) {
		Level level = event.getLevel();
		CloudManager<?> manager = CloudManager.get(level);
		manager.tick();
		if (!level.isClientSide() && manager instanceof ServerCloudManager serverManager) {
			ServerLevel serverLevel = (ServerLevel) level;
			SyncType syncType = serverManager.fetchNextSyncOperation();
			if (syncType != null) {
				switch (syncType) {
					case BASE_PROPERTIES: {
						PacketDistributor.sendToPlayersInDimension(serverLevel,
								new SendCloudManagerPayload(serverManager));
						break;
					}
					case MOVEMENT: {
						PacketDistributor.sendToPlayersInDimension(serverLevel,
								new UpdateCloudManagerPayload(serverManager));
						break;
					}
					case CLOUD_FORMATIONS: {
						for (ServerPlayer player : serverLevel.players())
							sendCloudRegionDeltaToPlayer(player);
						break;
					}
					default:
						throw new IllegalArgumentException("Unexpected value: " + syncType);
				}
			} else if (manager.getTickCount() % CloudManager.UPDATE_INTERVAL == 0) {
				PacketDistributor.sendToPlayersInDimension(serverLevel,
						new UpdateCloudManagerPayload(serverManager));
			}

			syncCloudRegionsForMovingPlayers(serverLevel);
		}
	}

	@SubscribeEvent
	public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
		CloudManager.get(event.getEntity().level()).onPlayerJoin(event.getEntity());
		if (event.getEntity() instanceof ServerPlayer player)
			update(player);
	}

	@SubscribeEvent
	public static void onPlayerSwapDimensions(PlayerEvent.PlayerChangedDimensionEvent event) {
		CloudManager.get(event.getEntity().level()).onPlayerJoin(event.getEntity());
		if (event.getEntity() instanceof ServerPlayer player)
			update(player);
	}

	@SubscribeEvent
	public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
		CloudManager.get(event.getEntity().level()).onPlayerJoin(event.getEntity());
		if (event.getEntity() instanceof ServerPlayer player)
			update(player);
	}

	@SubscribeEvent
	public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
		SYNCHED_CLOUDS_BY_PLAYER.remove(event.getEntity().getUUID());
		LAST_SYNCED_PLAYER_POSITIONS.remove(event.getEntity().getUUID());
	}

	private static void update(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new SendCloudManagerPayload(CloudManager.get(player.level())));
		SimpleCloudsConfigListeners.syncDryBiomeRainSettings(player);
		sendFullCloudRegionsToPlayer(player);
		LAST_SYNCED_PLAYER_POSITIONS.put(player.getUUID(), getPlayerRegionKey(player));
	}

	private static void syncCloudRegionsForMovingPlayers(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			long currentRegionKey = getPlayerRegionKey(player);
			Long previousRegionKey = LAST_SYNCED_PLAYER_POSITIONS.put(player.getUUID(), currentRegionKey);
			if (previousRegionKey == null || previousRegionKey.longValue() == currentRegionKey)
				continue;

			sendCloudRegionDeltaToPlayer(player);
		}
	}

	private static void sendFullCloudRegionsToPlayer(ServerPlayer player) {
		List<CloudRegion> formationsForPlayer = getCloudsForPlayer(player);
		SYNCHED_CLOUDS_BY_PLAYER.put(player.getUUID(), collectCloudIds(formationsForPlayer));
		PacketDistributor.sendToPlayer(player, new SendCloudRegionsPayload(formationsForPlayer));
	}

	private static void sendCloudRegionDeltaToPlayer(ServerPlayer player) {
		List<CloudRegion> formationsForPlayer = getCloudsForPlayer(player);
		Set<Integer> currentCloudIds = collectCloudIds(formationsForPlayer);
		Set<Integer> previousCloudIds = SYNCHED_CLOUDS_BY_PLAYER.get(player.getUUID());
		if (previousCloudIds == null) {
			sendFullCloudRegionsToPlayer(player);
			return;
		}

		List<CloudRegion> addedClouds = formationsForPlayer.stream()
				.filter(region -> !previousCloudIds.contains(region.getSyncId())).toList();
		List<Integer> removedCloudIds = previousCloudIds.stream().filter(id -> !currentCloudIds.contains(id)).toList();
		if (!addedClouds.isEmpty() || !removedCloudIds.isEmpty())
			PacketDistributor.sendToPlayer(player, new UpdateCloudRegionsPayload(addedClouds, removedCloudIds));

		SYNCHED_CLOUDS_BY_PLAYER.put(player.getUUID(), currentCloudIds);
	}

	private static List<CloudRegion> getCloudsForPlayer(ServerPlayer player) {
		CloudManager<ServerLevel> manager = CloudManager.get(player.serverLevel());
		SpawnRegion region = new SpawnRegion(player.getBlockX(), player.getBlockZ(),
				SimpleCloudsConstants.SPAWN_RADIUS);
		return manager.getCloudGenerator().getCloudsInRegion(region);
	}

	private static Set<Integer> collectCloudIds(List<CloudRegion> clouds) {
		Set<Integer> cloudIds = new HashSet<>();
		for (CloudRegion region : clouds)
			cloudIds.add(region.getSyncId());
		return cloudIds;
	}

	private static long getPlayerRegionKey(ServerPlayer player) {
		return ChunkPos.asLong(player.getBlockX(), player.getBlockZ());
	}
}
