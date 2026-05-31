package dev.nonamecrackers2.simpleclouds.common.cloud;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.ScAPICloudType;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.weather.WeatherType;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.common.noise.NoiseSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public record CloudType(ResourceLocation id, WeatherType weatherType, float storminess, float stormStart,
		float stormFadeDistance, NoiseSettings noiseConfig,
		boolean overrideAtmosphericClouds, CloudColorMode colorMode, float tintRed, float tintGreen, float tintBlue)
		implements CloudInfo, ScAPICloudType {

	@Override
	@Deprecated(forRemoval = false)
	public float transparencyFade() {
		return 0.0F;
	}

	private static float getOptionalRangedParam(JsonObject object, String name, float defaultValue, float min,
			float max) throws JsonSyntaxException {
		float value = GsonHelper.getAsFloat(object, name, defaultValue);
		if (value < min || value > max)
			throw new JsonSyntaxException("'" + name + "' is out of bounds. MIN: " + min + ", MAX: " + max);
		return value;
	}

	private static float getOptionalRangedParam(JsonObject primary, JsonObject fallback, String name,
			float defaultValue, float min, float max) throws JsonSyntaxException {
		if (primary != null && primary.has(name))
			return getOptionalRangedParam(primary, name, defaultValue, min, max);
		return getOptionalRangedParam(fallback, name, defaultValue, min, max);
	}

	private static JsonObject getOptionalSection(JsonObject object, String name) {
		if (!object.has(name))
			return null;

		JsonElement element = object.get(name);
		if (!element.isJsonObject())
			throw new JsonSyntaxException("'" + name + "' must be an object");
		return element.getAsJsonObject();
	}

	private static JsonElement getFirstPresent(JsonObject primary, JsonObject fallback, String... names) {
		if (primary != null) {
			for (String name : names) {
				if (primary.has(name))
					return primary.get(name);
			}
		}

		for (String name : names) {
			if (fallback.has(name))
				return fallback.get(name);
		}

		return null;
	}

	private static boolean parseOverrideAtmosphericClouds(JsonObject object) {
		return GsonHelper.getAsBoolean(object, "override_atmospheric_clouds", false);
	}

	private static boolean parseOverrideAtmosphericClouds(JsonObject primary, JsonObject fallback) {
		if (primary != null && primary.has("override_atmospheric_clouds"))
			return parseOverrideAtmosphericClouds(primary);
		return parseOverrideAtmosphericClouds(fallback);
	}

	private static CloudColorMode parseColorMode(JsonObject object) {
		return CloudColorMode.byName(
				GsonHelper.getAsString(object, "color_mode", CloudColorMode.DEFAULT.getSerializedName()));
	}

	private static CloudColorMode parseColorMode(JsonObject primary, JsonObject fallback) {
		if (primary != null && primary.has("color_mode"))
			return parseColorMode(primary);
		if (primary != null && primary.has("color"))
			return CloudColorMode.FIXED;
		if (fallback.has("color_mode"))
			return parseColorMode(fallback);
		if (fallback.has("color"))
			return CloudColorMode.FIXED;
		return CloudColorMode.DEFAULT;
	}

	private static float[] parseTintColor(JsonObject object) {
		JsonArray array = GsonHelper.getAsJsonArray(object, "color");
		if (array.size() != 3)
			throw new JsonSyntaxException("'color' must contain exactly 3 float values");
		float[] values = new float[3];
		for (int i = 0; i < 3; i++) {
			float value = array.get(i).getAsFloat();
			if (value < 0.0F || value > 1.0F)
				throw new JsonSyntaxException("'color' values must be between 0.0 and 1.0");
			values[i] = value;
		}
		return values;
	}

	private static float[] parseTintColor(JsonObject primary, JsonObject fallback) {
		if (primary != null && primary.has("color"))
			return parseTintColor(primary);
		if (fallback.has("color"))
			return parseTintColor(fallback);
		return new float[] { 1.0F, 1.0F, 1.0F };
	}

	private static WeatherType parseWeatherType(JsonObject primary, JsonObject fallback) {
		JsonElement element = getFirstPresent(primary, fallback, "type", "weather_type");
		if (element == null)
			return WeatherType.NONE;

		String rawWeatherTypeId = element.getAsString();
		for (WeatherType type : WeatherType.values()) {
			if (type.getSerializedName().equals(rawWeatherTypeId))
				return type;
		}

		throw new JsonSyntaxException("Unknown weather type '" + rawWeatherTypeId + "'");
	}

	public static CloudType readFromJson(ResourceLocation id, JsonObject object) throws JsonSyntaxException {
		JsonObject visual = getOptionalSection(object, "visual");
		JsonObject weather = getOptionalSection(object, "weather");
		JsonElement element = getFirstPresent(visual, object, "noise_settings", "noise_layers");
		if (element == null)
			throw new JsonSyntaxException("Please include one of 'noise_settings' or 'noise_layers'");
		NoiseSettings settings = NoiseSettings.CODEC.parse(JsonOps.INSTANCE, element).resultOrPartial(error -> {
			throw new JsonSyntaxException(error);
		}).orElseThrow();
		boolean overrideAtmosphericClouds = parseOverrideAtmosphericClouds(visual, object);
		CloudColorMode colorMode = parseColorMode(visual, object);
		float[] tint = parseTintColor(visual, object);

		if (settings.layerCount() > CloudMeshGenerator.MAX_NOISE_LAYERS)
			throw new JsonSyntaxException("Too many noise layers. Maximum amount of layers allowed is "
					+ CloudMeshGenerator.MAX_NOISE_LAYERS);

		WeatherType weatherType = parseWeatherType(weather, object);

		float storminess = getOptionalRangedParam(weather, object, "storminess", 0.0F, 0.0F,
				CloudInfo.STORMINESS_MAX);
		float stormStart = getOptionalRangedParam(weather, object, "storm_start", 16.0F, 0.0F,
				CloudInfo.STORM_START_MAX);
		float stormFadeDistance = getOptionalRangedParam(weather, object, "storm_fade_distance", 32.0F, 0.0F,
				CloudInfo.STORM_FADE_DISTANCE_MAX);
		return new CloudType(id, weatherType, storminess, stormStart, stormFadeDistance, settings,
				overrideAtmosphericClouds, colorMode, tint[0], tint[1], tint[2]);
	}
}
