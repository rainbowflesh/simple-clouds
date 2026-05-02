package dev.nonamecrackers2.simpleclouds.common.cloud.spawning;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudTypeSource;
import dev.nonamecrackers2.simpleclouds.common.data.SourceCloudTypeImporter;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.util.valueproviders.IntProvider;

public class CloudSpawningDataManager extends SimpleJsonResourceReloadListener {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final IntProvider FALLBACK_SPAWN_INTERVAL = BiasedToBottomInt.of(2400, 12000);
	private static final int FALLBACK_MAX_FORMATIONS = 5;
	private static final int FALLBACK_MAX_INITIAL_FORMATIONS = 3;
	private static CloudSpawningDataManager instance;
	private CloudTypeSource source;
	private CloudSpawningConfig config;

	public CloudSpawningDataManager(CloudTypeSource source) {
		super(GSON, "cloud_spawning");
		this.source = source;
		this.config = CloudSpawningConfig.EMPTY;
	}

	public CloudSpawningConfig getConfig() {
		return this.config;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller filler) {
		JsonElement root = resources.get(SimpleCloudsMod.id("config"));
		Map<ResourceLocation, CloudSpawningConfig.Info> entries = new HashMap<>();

		for (var entry : resources.entrySet()) {
			if (entry.getValue() != root) {
				try {
					CloudSpawningConfig.Info info = CloudSpawningConfig.readInfo(this.source,
							GsonHelper.convertToJsonObject(entry.getValue(), "root"));
					entries.put(info.cloudType(), info);
				} catch (JsonSyntaxException | IllegalArgumentException | NullPointerException e) {
					LOGGER.error("Failed to parse spawn info for file '" + entry.getKey() + "'", e);
					this.config = CloudSpawningConfig.EMPTY;
					return;
				}
			}
		}

		mergeBuiltInEntries(entries);
		JsonObject rootObject = root == null ? createBuiltInRootConfig() : GsonHelper.convertToJsonObject(root, "root");

		try {
			this.config = CloudSpawningConfig.fromJson(this.source, rootObject, ImmutableMap.copyOf(entries));
		} catch (JsonSyntaxException | IllegalArgumentException | NullPointerException e) {
			LOGGER.error("Failed to parse cloud spawn config", e);
			this.config = CloudSpawningConfig.EMPTY;
		}
	}

	private void mergeBuiltInEntries(Map<ResourceLocation, CloudSpawningConfig.Info> entries) {
		try {
			for (var definition : SourceCloudTypeImporter.loadCloudTypes()) {
				JsonObject root = definition.json();
				if (!root.has("spawning") || entries.containsKey(definition.id()))
					continue;

				JsonObject object = GsonHelper.getAsJsonObject(root, "spawning").deepCopy();
				object.addProperty("type", definition.id().toString());
				entries.put(definition.id(), CloudSpawningConfig.readInfo(this.source, object));
			}
		} catch (IllegalStateException e) {
			LOGGER.debug("Skipping bundled cloud spawning fallback", e);
		}
	}

	private static JsonObject createBuiltInRootConfig() {
		JsonObject root = new JsonObject();
		root.add("spawn_interval", IntProvider.NON_NEGATIVE_CODEC.encodeStart(JsonOps.INSTANCE, FALLBACK_SPAWN_INTERVAL)
				.resultOrPartial(e -> {
					throw new IllegalStateException(e);
				}).get());
		root.addProperty("max_formations", FALLBACK_MAX_FORMATIONS);
		root.addProperty("max_initial_formations", FALLBACK_MAX_INITIAL_FORMATIONS);
		return root;
	}

	public static void optionalInitialize(CloudTypeSource cloudTypeSource) {
		if (instance == null)
			instance = new CloudSpawningDataManager(cloudTypeSource);
	}

	public static CloudSpawningDataManager getInstance() {
		return Objects.requireNonNull(instance, "Not initialized");
	}
}
