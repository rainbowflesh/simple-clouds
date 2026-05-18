package dev.nonamecrackers2.simpleclouds.common.cloud;

import java.nio.ByteBuffer;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.weather.WeatherType;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.common.noise.NoiseSettings;
import net.minecraft.util.Mth;

public interface CloudInfo {
	public static final int BYTES_PER_TYPE = 24;
	public static final float STORMINESS_MAX = 1.0F;
	public static final float STORM_START_MAX = CloudMeshGenerator.LOCAL_SIZE * CloudMeshGenerator.WORK_SIZE
			* CloudMeshGenerator.VERTICAL_CHUNK_SPAN;
	public static final float STORM_FADE_DISTANCE_MAX = 1600.0F;
	public static final float TRANSPARENCY_FADE_MAX = 32.0F;

	NoiseSettings noiseConfig();

	WeatherType weatherType();

	float storminess();

	float stormStart();

	float stormFadeDistance();

	float transparencyFade();

	default boolean atmospheric() {
		return false;
	}

	default boolean overrideAtmosphericClouds() {
		return false;
	}

	default List<Integer> cloudLayers() {
		return List.of(1);
	}

	default float getLayerSpeedMultiplier() {
		List<Integer> layers = this.cloudLayers();
		if (layers.isEmpty())
			return 1.0F;
		return CloudType.getLayerSpeedMultiplier(layers.getFirst());
	}

	default int getStormAnchorLayer() {
		List<Integer> layers = this.cloudLayers();
		if (layers.isEmpty())
			return 1;
		return layers.getLast();
	}

	default float getStormStartRelativeToCloudBase() {
		return (float) this.noiseConfig().getStartHeight() + this.stormStart();
	}

	default JsonObject toJson() throws JsonSyntaxException {
		JsonObject object = new JsonObject();
		JsonObject visual = new JsonObject();
		visual.add("noise_settings",
				NoiseSettings.CODEC.encodeStart(JsonOps.INSTANCE, this.noiseConfig()).resultOrPartial(error -> {
					throw new JsonSyntaxException(error);
				}).orElseThrow());
		visual.addProperty("transparency_fade", Mth.clamp(this.transparencyFade(), 0.0F, TRANSPARENCY_FADE_MAX));
		if (this.overrideAtmosphericClouds())
			visual.addProperty("override_atmospheric_clouds", true);
		object.add("visual", visual);

		JsonObject weather = new JsonObject();
		weather.addProperty("type", this.weatherType().getSerializedName());
		weather.addProperty("storminess", Mth.clamp(this.storminess(), 0.0F, STORMINESS_MAX));
		weather.addProperty("storm_start", Mth.clamp(this.stormStart(), 0.0F, STORM_START_MAX));
		weather.addProperty("storm_fade_distance", Mth.clamp(this.stormFadeDistance(), 0.0F, STORM_FADE_DISTANCE_MAX));
		object.add("weather", weather);
		return object;
	}

	default int packToBuffer(ByteBuffer b, int layerIndex) {
		int layerCount = this.noiseConfig().layerCount();
		b.putInt(layerIndex);
		b.putInt(layerIndex + layerCount);
		b.putFloat(this.storminess());
		b.putFloat(this.getStormStartRelativeToCloudBase());
		b.putFloat(this.stormFadeDistance());
		b.putFloat(this.transparencyFade());
		return layerIndex + layerCount;
	}
}
