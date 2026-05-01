package dev.nonamecrackers2.simpleclouds.common.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudTypeSource;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.random.Weight;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.util.valueproviders.UniformInt;

public class SimpleCloudsCloudSpawningConfigProvider extends CloudSpawningConfigProvider {
	private static final IntProvider SPAWN_INTERVAL = BiasedToBottomInt.of(2400, 12000);
	private static final int MAX_FORMATIONS = 5;
	private static final int MAX_INITIAL_FORMATIONS = 3;

	public SimpleCloudsCloudSpawningConfigProvider(PackOutput output) {
		super(output);
	}

	@Override
	protected void addEntries() {
		Map<ResourceLocation, CloudType> cloudTypes = SourceCloudTypeImporter.loadCloudTypes().stream()
				.collect(Collectors.toMap(SourceCloudTypeImporter.SourceCloudTypeDefinition::id,
						SourceCloudTypeImporter.SourceCloudTypeDefinition::type));
		CloudTypeSource validator = new CloudTypeSource() {
			@Override
			public CloudType getCloudTypeForId(ResourceLocation id) {
				return cloudTypes.get(id);
			}

			@Override
			public CloudType[] getIndexedCloudTypes() {
				return cloudTypes.values().toArray(CloudType[]::new);
			}
		};

		for (var definition : SourceCloudTypeImporter.loadCloudTypes()) {
			JsonObject root = definition.json();
			if (!root.has("spawning"))
				continue;

			JsonObject object = GsonHelper.getAsJsonObject(root, "spawning").deepCopy();
			object.addProperty("type", definition.id().toString());
			this.addEntry(CloudSpawningConfig.readInfo(validator, object));
		}
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		JsonObject root = new JsonObject();
		root.add("spawn_interval",
				IntProvider.NON_NEGATIVE_CODEC.encodeStart(JsonOps.INSTANCE, SPAWN_INTERVAL).resultOrPartial(e -> {
					throw new IllegalArgumentException(e);
				}).get());
		root.addProperty("max_formations", MAX_FORMATIONS);
		root.addProperty("max_initial_formations", MAX_INITIAL_FORMATIONS);

		List<CompletableFuture<?>> futures = Lists.newArrayList();

		this.jsonForPaths(SimpleCloudsMod.id("config"), p -> {
			futures.add(DataProvider.saveStable(output, root, p));
		});

		futures.add(super.run(output));
		return CompletableFuture.allOf(futures.toArray(i -> new CompletableFuture[i]));
	}
}
