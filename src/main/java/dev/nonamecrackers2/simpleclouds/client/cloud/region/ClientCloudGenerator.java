package dev.nonamecrackers2.simpleclouds.client.cloud.region;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.google.common.collect.Lists;

import dev.nonamecrackers2.simpleclouds.client.world.ClientCloudManager;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudGetter;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.RemovedCloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.SpawnRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

public class ClientCloudGenerator extends CloudGenerator {
	public ClientCloudGenerator(CloudGetter cloudGetter, Supplier<CloudSpawningConfig> config) {
		super(cloudGetter, config);
	}

	@Override
	public void applyCloudRegionDelta(Collection<CloudRegion> addedClouds,
			Collection<RemovedCloudRegion> removedClouds) {
		for (RemovedCloudRegion removed : removedClouds) {
			if (!removed.reason().removesCloudLocally())
				continue;
			int removedId = removed.syncId();
			this.removeCloudsCount(existing -> existing.getSyncId() == removedId);
		}

		for (CloudRegion region : addedClouds) {
			int syncId = region.getSyncId();
			this.removeCloudsCount(existing -> existing.getSyncId() == syncId);
			this.addCloud(region, CloudGenerator.Order.USE_WEIGHT);
		}
	}

	@Override
	protected float getOffscreenLifetimeAcceleration(Level level, CloudRegion region, boolean isVisible) {
		return ClientCloudManager.isAvailableServerSide() ? 1.0F
				: super.getOffscreenLifetimeAcceleration(level, region, isVisible);
	}

	@Override
	protected boolean shouldResolveLayerConflicts(Level level) {
		return !ClientCloudManager.isAvailableServerSide();
	}

	@Override
	protected boolean shouldGenerateCloud(CloudSpawningConfig config, RandomSource random, Level level) {
		return !ClientCloudManager.isAvailableServerSide() && super.shouldGenerateCloud(config, random, level);
	}

	@Override
	protected List<SpawnRegion> determineValidSpawnRegions(RandomSource random, Level level) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null)
			return Lists.newArrayList(new SpawnRegion(player.getBlockX(), player.getBlockZ(),
					SimpleCloudsConstants.SPAWN_RADIUS));
		else
			return Lists.newArrayList();
	}
}
