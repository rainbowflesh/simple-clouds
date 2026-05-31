package dev.nonamecrackers2.simpleclouds.common.cloud;

import java.nio.ByteBuffer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.weather.WeatherType;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.common.noise.NoiseSettings;
import net.minecraft.util.Mth;

public interface CloudInfo {
	public static final int BYTES_PER_TYPE = 36;
	public static final float STORMINESS_MAX = 1.0F;
	public static final float STORM_START_MAX = CloudMeshGenerator.LOCAL_SIZE * CloudMeshGenerator.WORK_SIZE
			* CloudMeshGenerator.VERTICAL_CHUNK_SPAN;
	public static final float STORM_FADE_DISTANCE_MAX = 1600.0F;

	NoiseSettings noiseConfig();

	WeatherType weatherType();

	float storminess();

	float stormStart();

	float stormFadeDistance();

	default boolean atmospheric() {
		return false;
	}

	default boolean overrideAtmosphericClouds() {
		return false;
	}

	default boolean suppressesAtmosphericClouds() {
		return this.overrideAtmosphericClouds();
	}

	default CloudColorMode colorMode() {
		return CloudColorMode.DEFAULT;
	}

	default float tintRed() {
		return 1.0F;
	}

	default float tintGreen() {
		return 1.0F;
	}

	default float tintBlue() {
		return 1.0F;
	}

	default float getLayerSpeedMultiplier() {
		return 1.0F;
	}

	default int getStormAnchorLayer() {
		return 1;
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
		visual.addProperty("override_atmospheric_clouds", this.overrideAtmosphericClouds());
		if (this.colorMode() != CloudColorMode.DEFAULT)
			visual.addProperty("color_mode", this.colorMode().getSerializedName());
		if (this.colorMode() == CloudColorMode.FIXED) {
			JsonArray color = new JsonArray();
			color.add(Mth.clamp(this.tintRed(), 0.0F, 1.0F));
			color.add(Mth.clamp(this.tintGreen(), 0.0F, 1.0F));
			color.add(Mth.clamp(this.tintBlue(), 0.0F, 1.0F));
			visual.add("color", color);
		}
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
		b.putFloat(this.colorMode().getShaderValue());
		b.putFloat(this.tintRed());
		b.putFloat(this.tintGreen());
		b.putFloat(this.tintBlue());
		return layerIndex + layerCount;
	}
}
