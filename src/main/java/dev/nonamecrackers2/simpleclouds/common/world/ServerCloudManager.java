package dev.nonamecrackers2.simpleclouds.common.world;

import java.util.List;
import java.util.Queue;

import javax.annotation.Nullable;

import com.google.common.collect.Queues;

import dev.nonamecrackers2.simpleclouds.api.SimpleCloudsAPI;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudTypeDataManager;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningDataManager;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.ServerCloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.SpawnLightningPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

public class ServerCloudManager extends CloudManager<ServerLevel> {
	private Queue<SyncType> toSync = Queues.newArrayDeque();
	private float managedThunderLevel = 0.0F;

	public ServerCloudManager(ServerLevel level) {
		super(level, CloudTypeDataManager.getServerInstance(), CloudSpawningDataManager.getInstance()::getConfig,
				ServerCloudGenerator::new);
	}

	@Override
	public ServerCloudGenerator getCloudGenerator() {
		return (ServerCloudGenerator) super.getCloudGenerator();
	}

	@Override
	public CloudMode getCloudMode() {
		return SimpleCloudsConfig.SERVER.cloudMode.get();
	}

	@Override
	public String getSingleModeCloudTypeRawId() {
		return SimpleCloudsConfig.SERVER.singleModeCloudType.get();
	}

	@Override
	public void init(long seed) {
		super.init(seed);
		if (SimpleCloudsConfig.SERVER_SPEC.isLoaded())
			this.setCloudSpeed(SimpleCloudsConfig.SERVER.cloudSpeed.get().floatValue());
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.useVanillaWeather && !SimpleCloudsAPI.getApi().getHooks().isExternalWeatherControlEnabled()) {
			this.clearVanillaWeatherState();
		}

		if (this.isCloudGeneratorActive() && ((ServerCloudGenerator) this.getCloudGenerator()).checkAndResetSync())
			this.queueSync(SyncType.CLOUD_FORMATIONS);
	}

	@Override
	protected void resetVanillaWeather() {
		this.clearVanillaWeatherState();
	}

	private void clearVanillaWeatherState() {
		boolean wasRaining = this.level.isRaining();
		float rainLevel = this.level.getRainLevel(1.0F);
		float prevThunderLevel = this.managedThunderLevel;

		this.level.setWeatherParameters(0, 0, false, false);
		this.level.setRainLevel(0.0F);
		this.level.setThunderLevel(0.0F);

		// Compute cloud-based thunder level across all players and sync to the level
		// field so mods reading level.getThunderLevel() receive the correct value
		float newThunderLevel = 0.0F;
		for (ServerPlayer player : this.level.players()) {
			if (player.isSpectator())
				continue;
			newThunderLevel = Math.max(newThunderLevel,
					this.getThunderLevel((float) player.getX(), (float) player.getY(), (float) player.getZ()));
		}
		this.managedThunderLevel = newThunderLevel;
		if (newThunderLevel > 0.0F)
			this.level.setThunderLevel(newThunderLevel);

		PlayerList list = this.level.getServer().getPlayerList();
		if (wasRaining)
			list.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F),
					this.level.dimension());
		if (rainLevel > 0.0F)
			list.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.0F),
					this.level.dimension());
		// Only zero out thunder for clients when transitioning from active to inactive,
		// not every tick — prevents packet spam while thundering clouds are present
		if (prevThunderLevel > 0.0F && newThunderLevel <= 0.0F)
			list.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0F),
					this.level.dimension());
	}

	public void tickChunkPrecipitation(ChunkAccess chunk) {
		if (this.level.random.nextInt(16) != 0)
			return;

		int blockX = chunk.getPos().getMinBlockX();
		int blockZ = chunk.getPos().getMinBlockZ();
		BlockPos checkPos = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
				this.level.getBlockRandomPos(blockX, 0, blockZ, 15));
		var precipitation = this.getPrecipitationAt(checkPos);
		if (!precipitation.getLeft())
			return;

		BlockPos belowPos = checkPos.below();
		Biome.Precipitation precipitationType = precipitation.getRight();
		if (precipitationType != Biome.Precipitation.NONE) {
			BlockState blockState = this.level.getBlockState(belowPos);
			blockState.getBlock().handlePrecipitation(blockState, this.level, belowPos, precipitationType);
		}

		int snowAccumulationHeight = this.level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
		if (snowAccumulationHeight <= 0 || precipitationType != Biome.Precipitation.SNOW)
			return;

		BlockState blockStateAtCheckPos = this.level.getBlockState(checkPos);
		if (blockStateAtCheckPos.is(Blocks.SNOW)) {
			int layers = blockStateAtCheckPos.getValue(SnowLayerBlock.LAYERS);
			if (layers < Math.min(snowAccumulationHeight, 8)) {
				BlockState updatedBlockState = blockStateAtCheckPos.setValue(SnowLayerBlock.LAYERS, layers + 1);
				Block.pushEntitiesUp(blockStateAtCheckPos, updatedBlockState, this.level, checkPos);
				this.level.setBlockAndUpdate(checkPos, updatedBlockState);
			}
		} else {
			this.level.setBlockAndUpdate(checkPos, Blocks.SNOW.defaultBlockState());
		}
	}

	@Override
	protected float attemptToSpawnLightning() {
		List<SpawnRegion> regions = regionsFromEntities(this.level.players(),
				SimpleCloudsConstants.LIGHTNING_SPAWN_DIAMETER / 2);
		final float[] strikeIntensity = new float[] { 0.0F };

		SpawnRegion.randomPointForEachRegion(regions, this.random, SimpleCloudsConstants.LIGHTNING_SPAWN_ATTEMPTS,
				(r, p) -> {
					var info = this.getThunderCloudTypeAtWorldPos((float) p.x + 0.5F, (float) p.y + 0.5F);
					CloudType type = info.getLeft();
					if (!isValidLightning(type, info.getRight(), this.random))
						return false;
					this.spawnLightning(type, info.getRight(), p.x, p.y, false);
					strikeIntensity[0] = CloudManager.getLightningStrikeIntensity(type, info.getRight());
					return true;
				});
		return strikeIntensity[0];
	}

	@Override
	protected void spawnLightning(CloudType type, float fade, int x, int z, boolean soundOnly) {
		int y = (int) this.getStormStartHeight(type);
		BlockPos pos = new BlockPos(x, y, z);
		BlockPos targetPos = this.getLightningTargetPos(type, x, z);
		float spreadnessFactor = this.random.nextFloat();
		float length = spreadnessFactor * 300.0F + 200.0F;
		float minPitch = 20.0F + spreadnessFactor * 40.0F;
		float maxPitch = 80.0F + spreadnessFactor * 10.0F;
		PacketDistributor.sendToPlayersInDimension(this.level, new SpawnLightningPayload(pos, targetPos, soundOnly,
				this.random.nextInt(), 4, 2, length, 20.0F, minPitch, maxPitch));
	}

	public void queueSync(SyncType syncType) {
		if (!this.toSync.contains(syncType))
			this.toSync.add(syncType);
	}

	public @Nullable SyncType fetchNextSyncOperation() {
		return this.toSync.poll();
	}

	public static List<SpawnRegion> regionsFromEntities(List<? extends Entity> entities, int radius) {
		return entities.stream().map(e -> {
			return new SpawnRegion(e.getBlockX(), e.getBlockZ(), radius);
		}).toList();
	}
}
