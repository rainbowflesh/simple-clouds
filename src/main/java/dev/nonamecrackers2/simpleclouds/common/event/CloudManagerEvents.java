package dev.nonamecrackers2.simpleclouds.common.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfigListeners;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.CloudRegionRemovalReason;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.RemovedCloudRegion;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class CloudManagerEvents {
	private static final float CLOUD_SYNC_RADIUS_MULTIPLIER = 1.35F;
	private static final int FULL_SYNC_BATCH_SIZE = 96;
	private static final Map<UUID, Set<Integer>> SYNCHED_CLOUDS_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Long> LAST_SYNCED_PLAYER_POSITIONS = new HashMap<>();
	private static final Map<UUID, PendingCloudRegionSync> PENDING_FULL_SYNCS = new HashMap<>();

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
						List<RemovedCloudRegion> hardRemovedClouds = serverManager.getCloudGenerator()
								.drainPendingRemovedClouds();
						for (ServerPlayer player : serverLevel.players())
							sendCloudRegionDeltaToPlayer(player, hardRemovedClouds);
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
			flushPendingFullCloudSyncs(serverLevel);
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
		PENDING_FULL_SYNCS.remove(event.getEntity().getUUID());
	}

	private static void update(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new SendCloudManagerPayload(CloudManager.get(player.level())));
		SimpleCloudsConfigListeners.syncDryBiomeRainSettings(player);
		queueFullCloudRegionsToPlayer(player);
		SYNCHED_CLOUDS_BY_PLAYER.put(player.getUUID(), collectCloudIds(getCloudsForPlayer(player)));
		LAST_SYNCED_PLAYER_POSITIONS.put(player.getUUID(), getPlayerRegionKey(player));
	}

	private static void syncCloudRegionsForMovingPlayers(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			long currentRegionKey = getPlayerRegionKey(player);
			Long previousRegionKey = LAST_SYNCED_PLAYER_POSITIONS.put(player.getUUID(), currentRegionKey);
			if (previousRegionKey == null || previousRegionKey.longValue() == currentRegionKey)
				continue;

			sendCloudRegionDeltaToPlayer(player, List.of());
		}
	}

	private static void queueFullCloudRegionsToPlayer(ServerPlayer player) {
		CloudManager<ServerLevel> manager = CloudManager.get(player.serverLevel());
		List<CloudRegion> allClouds = manager.getCloudGenerator().getClouds();
		Set<Integer> nearbyCloudIds = collectCloudIds(getCloudsForPlayer(player));
		List<CloudRegion> prioritizedClouds = new ArrayList<>(allClouds.size());
		for (CloudRegion region : allClouds) {
			if (nearbyCloudIds.contains(region.getSyncId()))
				prioritizedClouds.add(region);
		}
		for (CloudRegion region : allClouds) {
			if (!nearbyCloudIds.contains(region.getSyncId()))
				prioritizedClouds.add(region);
		}
		PENDING_FULL_SYNCS.put(player.getUUID(), new PendingCloudRegionSync(prioritizedClouds));
	}

	private static void sendCloudRegionDeltaToPlayer(ServerPlayer player, List<RemovedCloudRegion> hardRemovedClouds) {
		List<CloudRegion> formationsForPlayer = getCloudsForPlayer(player);
		Set<Integer> previousCloudIds = SYNCHED_CLOUDS_BY_PLAYER.get(player.getUUID());
		if (previousCloudIds == null) {
			queueFullCloudRegionsToPlayer(player);
			SYNCHED_CLOUDS_BY_PLAYER.put(player.getUUID(), collectCloudIds(formationsForPlayer));
			return;
		}

		Set<Integer> currentCloudIds = new HashSet<>(formationsForPlayer.size());
		List<CloudRegion> addedClouds = new ArrayList<>();
		for (CloudRegion region : formationsForPlayer) {
			int syncId = region.getSyncId();
			currentCloudIds.add(syncId);
			if (!previousCloudIds.contains(syncId))
				addedClouds.add(region);
		}

		Set<Integer> hardRemovedIds = new LinkedHashSet<>();
		for (RemovedCloudRegion removedCloud : hardRemovedClouds)
			hardRemovedIds.add(removedCloud.syncId());

		List<RemovedCloudRegion> removedClouds = new ArrayList<>(hardRemovedClouds);
		for (int syncId : previousCloudIds) {
			if (!currentCloudIds.contains(syncId))
				if (!hardRemovedIds.contains(syncId))
					removedClouds.add(new RemovedCloudRegion(syncId, CloudRegionRemovalReason.OUT_OF_SYNC_RANGE));
		}

		if (!addedClouds.isEmpty() || !removedClouds.isEmpty())
			PacketDistributor.sendToPlayer(player, new UpdateCloudRegionsPayload(addedClouds, removedClouds));

		SYNCHED_CLOUDS_BY_PLAYER.put(player.getUUID(), currentCloudIds);
	}

	private static void flushPendingFullCloudSyncs(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			PendingCloudRegionSync sync = PENDING_FULL_SYNCS.get(player.getUUID());
			if (sync == null)
				continue;

			if (!sync.hasMore()) {
				PacketDistributor.sendToPlayer(player,
						new SendCloudRegionsPayload(List.of(), sync.markAndCheckFirstBatch()));
				PENDING_FULL_SYNCS.remove(player.getUUID());
				continue;
			}

			PacketDistributor.sendToPlayer(player,
					new SendCloudRegionsPayload(sync.nextBatch(FULL_SYNC_BATCH_SIZE), sync.markAndCheckFirstBatch()));
			if (!sync.hasMore())
				PENDING_FULL_SYNCS.remove(player.getUUID());
		}
	}

	private static List<CloudRegion> getCloudsForPlayer(ServerPlayer player) {
		CloudManager<ServerLevel> manager = CloudManager.get(player.serverLevel());
		int syncRadius = Math.max(SimpleCloudsConstants.SPAWN_RADIUS,
				Mth.floor((float) SimpleCloudsConstants.SPAWN_RADIUS * CLOUD_SYNC_RADIUS_MULTIPLIER));
		SpawnRegion region = new SpawnRegion(player.getBlockX(), player.getBlockZ(),
				syncRadius);
		return manager.getCloudGenerator().getCloudsInRegion(region);
	}

	private static Set<Integer> collectCloudIds(List<CloudRegion> clouds) {
		Set<Integer> cloudIds = new HashSet<>(clouds.size());
		for (CloudRegion region : clouds)
			cloudIds.add(region.getSyncId());
		return cloudIds;
	}

	private static long getPlayerRegionKey(ServerPlayer player) {
		ChunkPos pos = player.chunkPosition();
		return ChunkPos.asLong(pos.x, pos.z);
	}

	private static final class PendingCloudRegionSync {
		private final List<CloudRegion> clouds;
		private int index;
		private boolean firstBatch = true;

		private PendingCloudRegionSync(List<CloudRegion> clouds) {
			this.clouds = clouds;
		}

		private boolean hasMore() {
			return this.index < this.clouds.size();
		}

		private List<CloudRegion> nextBatch(int batchSize) {
			int end = Math.min(this.index + batchSize, this.clouds.size());
			List<CloudRegion> batch = new ArrayList<>(this.clouds.subList(this.index, end));
			this.index = end;
			return batch;
		}

		private boolean markAndCheckFirstBatch() {
			boolean first = this.firstBatch;
			this.firstBatch = false;
			return first;
		}
	}
}
