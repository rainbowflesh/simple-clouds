package dev.nonamecrackers2.simpleclouds.common.world;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector2f;

import dev.nonamecrackers2.simpleclouds.api.SimpleCloudsAPI;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.weather.WeatherType;
import dev.nonamecrackers2.simpleclouds.api.common.event.ModifyCloudSpeedEvent;
import dev.nonamecrackers2.simpleclouds.api.common.world.ScAPICloudManager;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudTypeSource;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudGetter;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.CloudSpawningConfig;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;

public abstract class CloudManager<T extends Level> implements CloudGetter, ScAPICloudManager {
	public static final int CLOUD_HEIGHT_MAX = 2048;
	public static final int CLOUD_HEIGHT_MIN = 0;
	public static final int UPDATE_INTERVAL = 200;
	public static final float RANDOM_SPREAD = 10000.0F;
	public static final float SCROLL_OFFSET = 100.0F;
	public static final float DEFAULT_CLOUD_SPEED = 0.675F;
	public static final double DEFAULT_DRY_BIOME_RAIN_MIN_STORMINESS = 0.55D;
	private static final List<String> DEFAULT_DRY_BIOME_RAIN_TAG_IDS = List.of("c:is_dry/overworld",
			"minecraft:is_savanna");
	private static final List<String> DEFAULT_NORMAL_RAIN_BIOME_TAG_IDS = List.of("terralith:reference/plains");
	private static volatile List<String> dryBiomeRainTagIds = mergeBiomeTagIds(DEFAULT_DRY_BIOME_RAIN_TAG_IDS,
			DEFAULT_NORMAL_RAIN_BIOME_TAG_IDS);
	private static volatile Set<ResourceLocation> dryBiomeRainBiomeIds = Set.of();
	private static volatile List<TagKey<Biome>> dryBiomeRainTags = createDryBiomeRainTags(
			dryBiomeRainTagIds);
	private static volatile List<String> normalRainBiomeTagIds = DEFAULT_NORMAL_RAIN_BIOME_TAG_IDS;
	private static volatile Set<ResourceLocation> normalRainBiomeIds = Set.of();
	private static volatile List<TagKey<Biome>> normalRainBiomeTags = createDryBiomeRainTags(
			DEFAULT_NORMAL_RAIN_BIOME_TAG_IDS);
	protected final T level;
	protected final CloudTypeSource cloudSource;
	protected final CloudGenerator cloudGenerator;
	private long seed;
	protected @Nullable RandomSource random;
	protected float scrollAngle;
	protected float scrollXO;
	protected float scrollYO;
	protected float scrollZO;
	protected float scrollX;
	protected float scrollY;
	protected float scrollZ;
	protected float speed = DEFAULT_CLOUD_SPEED;
	protected int cloudHeight = 128;
	protected int layerSeparation = CloudType.DEFAULT_LAYER_SEPARATION;
	protected int tickCount;
	protected int nextLightningStrike = 60;
	protected boolean useVanillaWeather;

	@SuppressWarnings("unchecked")
	public static <T extends Level> CloudManager<T> get(T level) {
		return Objects.requireNonNull(((CloudManagerHolder<T>) level).getCloudManager(),
				"Cloud manager is not available, this shouldn't happen!");
	}

	public CloudManager(T level, CloudTypeSource source, Supplier<CloudSpawningConfig> configGetter,
			BiFunction<CloudGetter, Supplier<CloudSpawningConfig>, CloudGenerator> generatorFunc) {
		this.level = level;
		this.cloudSource = source;
		this.cloudGenerator = generatorFunc.apply(this, configGetter);
		this.useVanillaWeather = this.determineUseVanillaWeather();
	}

	@Override
	public CloudGenerator getCloudGenerator() {
		return this.cloudGenerator;
	}

	@Override
	public List<CloudRegion> getClouds() {
		return this.cloudGenerator.getClouds();
	}

	@Override
	public CloudType getCloudTypeForId(ResourceLocation id) {
		return this.cloudSource.getCloudTypeForId(id);
	}

	@Override
	public CloudType[] getIndexedCloudTypes() {
		return this.cloudSource.getIndexedCloudTypes();
	}

	@Override
	public boolean isCloudGeneratorActive() {
		return this.getCloudMode() != CloudMode.SINGLE;
	}

	public void onPlayerJoin(Player player) {
		if (this.isCloudGeneratorActive() && !SimpleCloudsAPI.getApi().getHooks().isExternalWeatherControlEnabled())
			this.cloudGenerator.doInitialGen(player.getBlockX(), player.getBlockZ(), this.level, false);
	}

	@Override
	public Pair<CloudType, Float> getCloudTypeAtPosition(float x, float z) {
		if (this.getCloudMode() != CloudMode.SINGLE) {
			Pair<CloudRegion, Float> result = CloudRegion.calculateAt(this.getClouds(), x, z);
			CloudType type = null;
			if (result.getLeft() != null)
				type = this.getCloudTypeForId(result.getLeft().getCloudTypeId());
			if (type == null)
				type = SimpleCloudsConstants.EMPTY;
			return Pair.of(type, 1.0F - result.getRight());
		} else {
			String rawId = this.getSingleModeCloudTypeRawId();
			ResourceLocation id = ResourceLocation.tryParse(rawId);
			if (id != null) {
				CloudType type = this.getCloudTypeForId(id);
				if (type != null)
					return Pair.of(type, 0.0F);
			}
			return Pair.of(SimpleCloudsConstants.EMPTY, 0.0F);
		}
	}

	public Pair<CloudType, Float> getRainCloudTypeAtWorldPos(float x, float z) {
		return this.getWeatherCloudTypeAtPosition(x / (float) SimpleCloudsConstants.CLOUD_SCALE,
				z / (float) SimpleCloudsConstants.CLOUD_SCALE, WeatherSelection.RAIN);
	}

	public Pair<CloudType, Float> getThunderCloudTypeAtWorldPos(float x, float z) {
		return this.getWeatherCloudTypeAtPosition(x / (float) SimpleCloudsConstants.CLOUD_SCALE,
				z / (float) SimpleCloudsConstants.CLOUD_SCALE, WeatherSelection.THUNDER);
	}

	public Pair<CloudType, Float> getDarkeningCloudTypeAtWorldPos(float x, float z) {
		return this.getWeatherCloudTypeAtPosition(x / (float) SimpleCloudsConstants.CLOUD_SCALE,
				z / (float) SimpleCloudsConstants.CLOUD_SCALE, WeatherSelection.DARKENING);
	}

	public Pair<CloudType, Float> getCloudTypeAtPositionForLayer(float x, float z, int cloudLayer) {
		if (cloudLayer < 1 || cloudLayer > CloudType.MAX_CLOUD_LAYERS)
			return Pair.of(SimpleCloudsConstants.EMPTY, 1.0F);

		if (this.getCloudMode() == CloudMode.SINGLE) {
			Pair<CloudType, Float> info = this.getCloudTypeAtPosition(x, z);
			return info.getLeft().cloudLayers().contains(cloudLayer) ? info
					: Pair.of(SimpleCloudsConstants.EMPTY, 1.0F);
		}

		LayerCloudSelection[] selections = this.resolveLayerCloudSelections(x, z);
		LayerCloudSelection selection = selections[cloudLayer - 1];
		return selection != null ? Pair.of(selection.type(), selection.fade())
				: Pair.of(SimpleCloudsConstants.EMPTY, 1.0F);
	}

	public Pair<CloudType, Float> getCloudTypeAtWorldPosForLayer(float x, float z, int cloudLayer) {
		return this.getCloudTypeAtPositionForLayer(x / (float) SimpleCloudsConstants.CLOUD_SCALE,
				z / (float) SimpleCloudsConstants.CLOUD_SCALE, cloudLayer);
	}

	public WeatherSample sampleWeatherAtWorldPos(float x, float y, float z) {
		WeatherStatus status = this.resolveWeatherStatusAtWorldPos(x, z);
		return new WeatherSample(status.darkeningType(), status.darkeningFade(),
				this.calculateRainLevel(y, status.rainType(), status.rainFade()),
				this.calculateThunderLevel(y, status.thunderType(), status.thunderFade()));
	}

	private Pair<CloudType, Float> getWeatherCloudTypeAtPosition(float x, float z, WeatherSelection selection) {
		WeatherStatus status = this.resolveWeatherStatusAtPosition(x, z);
		return switch (selection) {
			case RAIN -> Pair.of(status.rainType(), status.rainFade());
			case THUNDER -> Pair.of(status.thunderType(), status.thunderFade());
			case DARKENING -> Pair.of(status.darkeningType(), status.darkeningFade());
		};
	}

	private WeatherStatus resolveWeatherStatusAtWorldPos(float x, float z) {
		return this.resolveWeatherStatusAtPosition(x / (float) SimpleCloudsConstants.CLOUD_SCALE,
				z / (float) SimpleCloudsConstants.CLOUD_SCALE);
	}

	private WeatherStatus resolveWeatherStatusAtPosition(float x, float z) {
		if (this.getCloudMode() == CloudMode.SINGLE) {
			Pair<CloudType, Float> info = this.getCloudTypeAtPosition(x, z);
			CloudType type = info.getLeft();
			float fade = info.getRight();
			WeatherType weatherType = type.weatherType();
			return new WeatherStatus(weatherType.includesRain() ? type : SimpleCloudsConstants.EMPTY,
					weatherType.includesRain() ? fade : 1.0F,
					weatherType.includesThunder() ? type : SimpleCloudsConstants.EMPTY,
					weatherType.includesThunder() ? fade : 1.0F,
					weatherType.causesDarkening() ? type : SimpleCloudsConstants.EMPTY,
					weatherType.causesDarkening() ? fade : 1.0F);
		}

		CloudType layer1Type = SimpleCloudsConstants.EMPTY;
		float layer1Fade = 1.0F;
		float layer1Storminess = -1.0F;
		CloudType layer2Type = SimpleCloudsConstants.EMPTY;
		float layer2Fade = 1.0F;
		float layer2Storminess = -1.0F;
		CloudType layer3Type = SimpleCloudsConstants.EMPTY;
		float layer3Fade = 1.0F;
		float layer3Storminess = -1.0F;

		for (CloudRegion region : this.getClouds()) {
			CloudType type = this.getCloudTypeForId(region.getCloudTypeId());
			if (type == null)
				continue;

			float fade = this.getRegionFadeAt(region, x, z);
			if (fade >= 1.0F)
				continue;

			float storminess = type.storminess();
			for (int cloudLayer : type.cloudLayers()) {
				switch (cloudLayer) {
					case 1 -> {
						if (fade < layer1Fade - 1.0E-4F
								|| (Math.abs(fade - layer1Fade) <= 1.0E-4F && storminess > layer1Storminess)) {
							layer1Type = type;
							layer1Fade = fade;
							layer1Storminess = storminess;
						}
					}
					case 2 -> {
						if (fade < layer2Fade - 1.0E-4F
								|| (Math.abs(fade - layer2Fade) <= 1.0E-4F && storminess > layer2Storminess)) {
							layer2Type = type;
							layer2Fade = fade;
							layer2Storminess = storminess;
						}
					}
					case 3 -> {
						if (fade < layer3Fade - 1.0E-4F
								|| (Math.abs(fade - layer3Fade) <= 1.0E-4F && storminess > layer3Storminess)) {
							layer3Type = type;
							layer3Fade = fade;
							layer3Storminess = storminess;
						}
					}
					default -> {
					}
				}
			}
		}

		CloudType bestRainType = SimpleCloudsConstants.EMPTY;
		float bestRainFade = 1.0F;
		float highestRainStorminess = -1.0F;
		if (layer1Type.weatherType().includesRain()) {
			bestRainType = layer1Type;
			bestRainFade = layer1Fade;
			highestRainStorminess = layer1Type.storminess();
		}
		if (layer2Type.weatherType().includesRain() && (layer2Type.storminess() > highestRainStorminess
				|| (layer2Type.storminess() == highestRainStorminess && layer2Fade < bestRainFade))) {
			bestRainType = layer2Type;
			bestRainFade = layer2Fade;
			highestRainStorminess = layer2Type.storminess();
		}
		if (layer3Type.weatherType().includesRain() && (layer3Type.storminess() > highestRainStorminess
				|| (layer3Type.storminess() == highestRainStorminess && layer3Fade < bestRainFade))) {
			bestRainType = layer3Type;
			bestRainFade = layer3Fade;
		}

		CloudType bestThunderType = SimpleCloudsConstants.EMPTY;
		float bestThunderFade = 1.0F;
		float highestThunderStorminess = -1.0F;
		if (layer1Type.weatherType().includesThunder()) {
			bestThunderType = layer1Type;
			bestThunderFade = layer1Fade;
			highestThunderStorminess = layer1Type.storminess();
		}
		if (layer2Type.weatherType().includesThunder() && (layer2Type.storminess() > highestThunderStorminess
				|| (layer2Type.storminess() == highestThunderStorminess && layer2Fade < bestThunderFade))) {
			bestThunderType = layer2Type;
			bestThunderFade = layer2Fade;
			highestThunderStorminess = layer2Type.storminess();
		}
		if (layer3Type.weatherType().includesThunder() && (layer3Type.storminess() > highestThunderStorminess
				|| (layer3Type.storminess() == highestThunderStorminess && layer3Fade < bestThunderFade))) {
			bestThunderType = layer3Type;
			bestThunderFade = layer3Fade;
		}

		CloudType bestDarkeningType = SimpleCloudsConstants.EMPTY;
		float bestDarkeningFade = 1.0F;
		float highestDarkeningStorminess = -1.0F;
		if (layer1Type.weatherType().causesDarkening()) {
			bestDarkeningType = layer1Type;
			bestDarkeningFade = layer1Fade;
			highestDarkeningStorminess = layer1Type.storminess();
		}
		if (layer2Type.weatherType().causesDarkening()
				&& (layer2Type.storminess() > highestDarkeningStorminess
						|| (layer2Type.storminess() == highestDarkeningStorminess
								&& layer2Fade < bestDarkeningFade))) {
			bestDarkeningType = layer2Type;
			bestDarkeningFade = layer2Fade;
			highestDarkeningStorminess = layer2Type.storminess();
		}
		if (layer3Type.weatherType().causesDarkening()
				&& (layer3Type.storminess() > highestDarkeningStorminess
						|| (layer3Type.storminess() == highestDarkeningStorminess
								&& layer3Fade < bestDarkeningFade))) {
			bestDarkeningType = layer3Type;
			bestDarkeningFade = layer3Fade;
		}

		return new WeatherStatus(bestRainType, bestRainFade, bestThunderType, bestThunderFade, bestDarkeningType,
				bestDarkeningFade);
	}

	private LayerCloudSelection[] resolveLayerCloudSelections(float x, float z) {
		LayerCloudSelection[] selections = new LayerCloudSelection[CloudType.MAX_CLOUD_LAYERS];
		for (CloudRegion region : this.getClouds()) {
			CloudType type = this.getCloudTypeForId(region.getCloudTypeId());
			if (type == null)
				continue;

			float fade = this.getRegionFadeAt(region, x, z);
			if (fade >= 1.0F)
				continue;

			for (int cloudLayer : type.cloudLayers()) {
				int index = cloudLayer - 1;
				LayerCloudSelection current = selections[index];
				if (current == null || fade < current.fade() - 1.0E-4F
						|| (Math.abs(fade - current.fade()) <= 1.0E-4F
								&& type.storminess() > current.type().storminess())) {
					selections[index] = new LayerCloudSelection(type, fade);
				}
			}
		}
		return selections;
	}

	private float getRegionFadeAt(CloudRegion region, float x, float z) {
		float dx = x - region.getPosX();
		float dz = z - region.getPosZ();
		float cos = Mth.cos(region.getRotation());
		float sin = Mth.sin(region.getRotation());
		float rotatedX = dx * cos - dz * sin;
		float rotatedZ = dx * sin + dz * cos;
		float scaledX = rotatedX * region.getStretch();
		float distanceSq = scaledX * scaledX + rotatedZ * rotatedZ;
		float edgeFadeDistance = 1.0F / SimpleCloudsConstants.REGION_EDGE_FADE_FACTOR;
		float maxDistance = region.getRadius() + edgeFadeDistance;
		if (distanceSq > maxDistance * maxDistance)
			return 1.0F;

		float distance = Mth.sqrt(distanceSq);
		if (distance < region.getRadius()) {
			float coverage = Math.min((region.getRadius() - distance) * SimpleCloudsConstants.REGION_EDGE_FADE_FACTOR,
					1.0F);
			return 1.0F - coverage;
		}
		return 1.0F;
	}

	private static record LayerCloudSelection(CloudType type, float fade) {
	}

	private @Nullable LocalizedPrecipitation resolveLocalizedPrecipitation(BlockPos pos) {
		if (!this.level.canSeeSky(pos)
				|| this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY())
			return null;

		WeatherStatus status = this.resolveWeatherStatusAtWorldPos((float) pos.getX() + 0.5F,
				(float) pos.getZ() + 0.5F);
		CloudType rainType = status.rainType();
		Biome.Precipitation precipitation = resolveBiomePrecipitation(this.level, this.level.getBiome(pos), pos,
				rainType);
		if (precipitation == Biome.Precipitation.NONE)
			return null;
		if ((float) pos.getY() + 0.5F > this.getPrecipitationCeilingHeight(rainType))
			return null;
		if (!rainType.weatherType().includesRain() || status.rainFade() >= SimpleCloudsConstants.RAIN_THRESHOLD - 0.01F)
			return null;

		return new LocalizedPrecipitation(status, rainType, precipitation);
	}

	public Pair<Boolean, Biome.Precipitation> getPrecipitationAt(BlockPos pos) {
		LocalizedPrecipitation precipitation = this.resolveLocalizedPrecipitation(pos);
		return precipitation != null ? Pair.of(true, precipitation.precipitation())
				: Pair.of(false, Biome.Precipitation.NONE);
	}

	public Biome.Precipitation getBiomePrecipitationAt(BlockPos pos) {
		return resolveBiomePrecipitation(this.level, this.level.getBiome(pos), pos);
	}

	public static List<String> getDefaultDryBiomeRainTagIds() {
		return DEFAULT_DRY_BIOME_RAIN_TAG_IDS;
	}

	public static List<String> getDefaultNormalRainBiomeTagIds() {
		return DEFAULT_NORMAL_RAIN_BIOME_TAG_IDS;
	}

	public static Biome.Precipitation resolveBiomePrecipitation(Level level, Holder<Biome> biome, BlockPos pos) {
		CloudType type = CloudManager.get(level)
				.getRainCloudTypeAtWorldPos((float) pos.getX() + 0.5F, (float) pos.getZ() + 0.5F)
				.getLeft();
		return resolveBiomePrecipitation(level, biome, pos, type);
	}

	public static Biome.Precipitation resolveBiomePrecipitation(Level level, Holder<Biome> biome, BlockPos pos,
			CloudType type) {
		if (shouldTreatAsNormalRainBiome(biome))
			return biome.value().shouldSnow(level, pos) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
		if (shouldOverrideDryBiomePrecipitation(biome, type))
			return biome.value().shouldSnow(level, pos) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
		return biome.value().getPrecipitationAt(pos);
	}

	public static boolean biomeHasConfiguredPrecipitation(Holder<Biome> biome) {
		return biome.value().hasPrecipitation() || shouldTreatAsNormalRainBiome(biome)
				|| shouldOverrideDryBiomePrecipitation(biome);
	}

	public static boolean shouldOverrideDryBiomePrecipitation(Holder<Biome> biome) {
		return shouldAllowRainInDryBiomes() && isDryBiomeForRainOverride(biome);
	}

	public static boolean shouldOverrideDryBiomePrecipitation(Holder<Biome> biome, CloudType type) {
		return shouldOverrideDryBiomePrecipitation(biome) && type.storminess() >= getDryBiomeRainMinStorminess();
	}

	public static boolean shouldTreatAsNormalRainBiome(Holder<Biome> biome) {
		return isBiomeInRainOverrideSet(biome, normalRainBiomeIds, getNormalRainBiomeTags());
	}

	public static float getDryBiomeRainMinStorminess() {
		if (!SimpleCloudsConfig.SERVER_SPEC.isLoaded())
			return (float) DEFAULT_DRY_BIOME_RAIN_MIN_STORMINESS;
		return SimpleCloudsConfig.SERVER.dryBiomeRainMinStorminess.get().floatValue();
	}

	private static boolean isDryBiomeForRainOverride(Holder<Biome> biome) {
		return isBiomeInRainOverrideSet(biome, dryBiomeRainBiomeIds, getDryBiomeRainTags());
	}

	public static void updateDryBiomeRainTags(List<? extends String> tagIds) {
		updateRainBiomeOverrides(tagIds,
				resolveDryBiomeRainBiomeIds(mergeBiomeTagIds(tagIds, getConfiguredNormalRainBiomeTagIds())),
				getConfiguredNormalRainBiomeTagIds(),
				resolveDryBiomeRainBiomeIds(getConfiguredNormalRainBiomeTagIds()));
	}

	public static void updateDryBiomeRainOverrides(List<? extends String> tagIds, List<? extends String> biomeIds) {
		updateRainBiomeOverrides(tagIds, biomeIds, getConfiguredNormalRainBiomeTagIds(),
				resolveDryBiomeRainBiomeIds(getConfiguredNormalRainBiomeTagIds()));
	}

	public static void updateRainBiomeTags(List<? extends String> dryTagIds, List<? extends String> normalTagIds) {
		updateRainBiomeOverrides(dryTagIds,
				resolveDryBiomeRainBiomeIds(mergeBiomeTagIds(dryTagIds, normalTagIds)),
				normalTagIds, resolveDryBiomeRainBiomeIds(normalTagIds));
	}

	public static void updateRainBiomeOverrides(List<? extends String> dryTagIds, List<? extends String> dryBiomeIds,
			List<? extends String> normalTagIds, List<? extends String> normalBiomeIds) {
		List<String> normalizedNormalTagIds = List.copyOf(normalTagIds);
		List<String> normalizedDryTagIds = mergeBiomeTagIds(dryTagIds, normalizedNormalTagIds);
		dryBiomeRainTagIds = normalizedDryTagIds;
		dryBiomeRainBiomeIds = createDryBiomeRainBiomeIds(dryBiomeIds);
		dryBiomeRainTags = createDryBiomeRainTags(normalizedDryTagIds);
		normalRainBiomeTagIds = normalizedNormalTagIds;
		normalRainBiomeIds = createDryBiomeRainBiomeIds(normalBiomeIds);
		normalRainBiomeTags = createDryBiomeRainTags(normalizedNormalTagIds);
	}

	public static List<String> resolveDryBiomeRainBiomeIds(List<? extends String> tagIds) {
		MinecraftServer server = switch (net.neoforged.fml.util.thread.EffectiveSide.get()) {
			case SERVER -> levelServer();
			default -> null;
		};
		if (server == null)
			return List.of();

		List<TagKey<Biome>> tags = createDryBiomeRainTags(tagIds);
		if (tags.isEmpty())
			return List.of();

		return server.registryAccess().lookupOrThrow(Registries.BIOME).listElements()
				.filter(holder -> tags.stream().anyMatch(holder::is))
				.map(holder -> holder.unwrapKey().map(key -> key.location().toString()).orElse(null))
				.filter(Objects::nonNull)
				.distinct()
				.toList();
	}

	private static List<TagKey<Biome>> getDryBiomeRainTags() {
		if (SimpleCloudsConfig.SERVER_SPEC.isLoaded()) {
			List<String> configuredTagIds = getConfiguredDryBiomeRainTagIds();
			List<String> configuredNormalTagIds = getConfiguredNormalRainBiomeTagIds();
			List<String> effectiveTagIds = mergeBiomeTagIds(configuredTagIds, configuredNormalTagIds);
			if (!effectiveTagIds.equals(dryBiomeRainTagIds) || !configuredNormalTagIds.equals(normalRainBiomeTagIds))
				updateRainBiomeTags(configuredTagIds, configuredNormalTagIds);
		}
		return dryBiomeRainTags;
	}

	private static List<TagKey<Biome>> getNormalRainBiomeTags() {
		if (SimpleCloudsConfig.SERVER_SPEC.isLoaded()) {
			List<String> configuredTagIds = getConfiguredDryBiomeRainTagIds();
			List<String> configuredNormalTagIds = getConfiguredNormalRainBiomeTagIds();
			List<String> effectiveTagIds = mergeBiomeTagIds(configuredTagIds, configuredNormalTagIds);
			if (!effectiveTagIds.equals(dryBiomeRainTagIds) || !configuredNormalTagIds.equals(normalRainBiomeTagIds))
				updateRainBiomeTags(configuredTagIds, configuredNormalTagIds);
		}
		return normalRainBiomeTags;
	}

	private static List<String> getConfiguredDryBiomeRainTagIds() {
		if (!SimpleCloudsConfig.SERVER_SPEC.isLoaded())
			return DEFAULT_DRY_BIOME_RAIN_TAG_IDS;
		return List.copyOf(SimpleCloudsConfig.SERVER.dryBiomeRainTags.get());
	}

	private static List<String> getConfiguredNormalRainBiomeTagIds() {
		if (!SimpleCloudsConfig.SERVER_SPEC.isLoaded())
			return DEFAULT_NORMAL_RAIN_BIOME_TAG_IDS;
		return List.copyOf(SimpleCloudsConfig.SERVER.normalRainBiomeTags.get());
	}

	public static List<String> mergeBiomeTagIds(List<? extends String> tagIds, List<? extends String> appendedTagIds) {
		java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(tagIds);
		merged.addAll(appendedTagIds);
		return List.copyOf(merged);
	}

	private static boolean isBiomeInRainOverrideSet(Holder<Biome> biome, Set<ResourceLocation> biomeIds,
			List<TagKey<Biome>> biomeTags) {
		var biomeKey = biome.unwrapKey();
		if (biomeKey.isPresent() && biomeIds.contains(biomeKey.get().location()))
			return true;
		for (TagKey<Biome> tag : biomeTags) {
			if (biome.is(tag))
				return true;
		}
		return false;
	}

	private static List<TagKey<Biome>> createDryBiomeRainTags(List<? extends String> tagIds) {
		return tagIds.stream()
				.map(ResourceLocation::tryParse)
				.filter(Objects::nonNull)
				.map(loc -> TagKey.create(Registries.BIOME, loc))
				.toList();
	}

	private static Set<ResourceLocation> createDryBiomeRainBiomeIds(List<? extends String> biomeIds) {
		return biomeIds.stream()
				.map(ResourceLocation::tryParse)
				.filter(Objects::nonNull)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private static @Nullable MinecraftServer levelServer() {
		return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
	}

	protected static boolean shouldAllowRainInDryBiomes() {
		return SimpleCloudsConfig.SERVER_SPEC.isLoaded() && SimpleCloudsConfig.SERVER.allowRainInDryBiomes.get();
	}

	// For API calls, use Level#isRainingAt
	public boolean isRainingAt(BlockPos pos) {
		LocalizedPrecipitation precipitation = this.resolveLocalizedPrecipitation(pos);
		return precipitation != null && precipitation.precipitation() == Biome.Precipitation.RAIN;
	}

	public boolean isRainingAt(double x, double y, double z) {
		return this.isRainingAt(BlockPos.containing(x, y, z));
	}

	public boolean isSnowingAt(BlockPos pos) {
		LocalizedPrecipitation precipitation = this.resolveLocalizedPrecipitation(pos);
		return precipitation != null && precipitation.precipitation() == Biome.Precipitation.SNOW;
	}

	public boolean isSnowingAt(double x, double y, double z) {
		return this.isSnowingAt(BlockPos.containing(x, y, z));
	}

	public boolean isThunderingAt(BlockPos pos) {
		LocalizedPrecipitation precipitation = this.resolveLocalizedPrecipitation(pos);
		if (precipitation == null)
			return false;

		WeatherStatus status = precipitation.weatherStatus();
		return status.thunderType().weatherType().includesThunder()
				&& status.thunderFade() < SimpleCloudsConstants.RAIN_THRESHOLD - 0.01F;
	}

	public boolean isThunderingAt(double x, double y, double z) {
		return this.isThunderingAt(BlockPos.containing(x, y, z));
	}

	public boolean hasPrecipitationAt(BlockPos pos) {
		return this.resolveLocalizedPrecipitation(pos) != null;
	}

	public boolean hasPrecipitationAt(double x, double y, double z) {
		return this.hasPrecipitationAt(BlockPos.containing(x, y, z));
	}

	public boolean hasPrecipitationForAnyPlayer(Iterable<? extends Player> players) {
		return this.anyPlayerMatchesWeather(players, pos -> this.hasPrecipitationAt(pos));
	}

	public boolean isThunderingForAnyPlayer(Iterable<? extends Player> players) {
		return this.anyPlayerMatchesWeather(players, pos -> this.isThunderingAt(pos));
	}

	private boolean anyPlayerMatchesWeather(Iterable<? extends Player> players, Predicate<BlockPos> weatherCheck) {
		for (Player player : players) {
			if (player.isSpectator())
				continue;
			if (weatherCheck.test(player.blockPosition()))
				return true;
		}
		return false;
	}

	@Override
	public float getRainLevel(float x, float y, float z) {
		var info = this.getRainCloudTypeAtWorldPos(x, z);
		return this.calculateRainLevel(y, info.getLeft(), info.getRight());
	}

	public float getThunderLevel(float x, float y, float z) {
		var info = this.getThunderCloudTypeAtWorldPos(x, z);
		return this.calculateThunderLevel(y, info.getLeft(), info.getRight());
	}

	private float calculateRainLevel(float y, CloudType type, float fade) {
		if (!type.weatherType().includesRain())
			return 0.0F;

		float verticalFade = 1.0F - Mth
				.clamp((y - this.getPrecipitationCeilingHeight(type)) / SimpleCloudsConstants.RAIN_VERTICAL_FADE,
						0.0F, 1.0F);
		return Math.min(1.0F,
				Math.max(0.0F, SimpleCloudsConstants.RAIN_THRESHOLD - fade) / SimpleCloudsConstants.RAIN_FADE)
				* verticalFade;
	}

	private float calculateThunderLevel(float y, CloudType type, float fade) {
		if (!type.weatherType().includesThunder())
			return 0.0F;

		float rainLevel = this.calculateRainLevel(y, type, fade);
		if (rainLevel <= 0.0F)
			return 0.0F;

		return Mth.clamp(type.storminess() * rainLevel, 0.0F, 1.0F);
	}

	public void init(long seed) {
		RandomSource random = this.setSeed(seed);
		this.random = random;
		if (SimpleCloudsConfig.SERVER_SPEC.isLoaded()) {
			this.cloudHeight = SimpleCloudsConfig.SERVER.cloudHeight.get();
			this.layerSeparation = SimpleCloudsConfig.SERVER.cloudLayerSeparation.get();
			this.speed = SimpleCloudsConfig.SERVER.cloudSpeed.get().floatValue();
		} else {
			this.layerSeparation = CloudType.DEFAULT_LAYER_SEPARATION;
			this.speed = DEFAULT_CLOUD_SPEED;
		}
		this.cloudGenerator.initialize(random, this.level);
	}

	@Override
	public int getCloudHeight() {
		return this.cloudHeight;
	}

	@Override
	public void setCloudHeight(int height) {
		this.cloudHeight = height;
	}

	public int getCloudLayerSeparation() {
		return this.layerSeparation;
	}

	public void setCloudLayerSeparation(int separation) {
		this.layerSeparation = Math.max(1, separation);
	}

	public float getStormStartHeight(CloudType type) {
		return (float) this.getCloudHeight()
				+ type.getStormStartRelativeToCloudBase() * SimpleCloudsConstants.CLOUD_SCALE;
	}

	public float getCloudTopHeight(CloudType type) {
		return (float) this.getCloudHeight()
				+ (float) type.noiseConfig().getEndHeight() * (float) SimpleCloudsConstants.CLOUD_SCALE;
	}

	public BlockPos getLightningTargetPos(CloudType type, int x, int z) {
		int startY = Mth.floor(this.getStormStartHeight(type));
		int terrainY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		int targetY = Math.min(terrainY, startY - 1);
		return new BlockPos(x, targetY, z);
	}

	protected float getCloudBaseHeight(CloudType type) {
		return (float) this.getCloudHeight()
				+ (float) type.noiseConfig().getStartHeight() * (float) SimpleCloudsConstants.CLOUD_SCALE;
	}

	protected float getPrecipitationCeilingHeight(CloudType type) {
		return this.getStormStartHeight(type);
	}

	public void tick() {
		MinecraftServer server = this.level.getServer();
		if (server instanceof DedicatedServer && server.getPlayerCount() == 0)
			return;

		this.tickCount++;

		this.scrollXO = this.scrollX;
		this.scrollYO = this.scrollY;
		this.scrollZO = this.scrollZ;
		float speed = this.getCloudSpeed();
		speed = this.modifyCloudSpeed(speed);

		if (this.isCloudGeneratorActive())
			this.cloudGenerator.tick(this.level, speed);

		speed *= 0.0001F;
		this.scrollAngle += speed;
		this.scrollX = (float) Math.cos(this.scrollAngle) * SCROLL_OFFSET;
		this.scrollY = 0.0F;// (float)Math.sin(this.scrollAngle + (float)Math.PI / 4.0F) * SCROLL_OFFSET *
							// 0.5F;
		this.scrollZ = (float) Math.sin(this.scrollAngle) * SCROLL_OFFSET;

		boolean flag = this.determineUseVanillaWeather();
		if (flag != this.useVanillaWeather) {
			this.useVanillaWeather = flag;
			this.resetVanillaWeather();
		}

		if (!this.useVanillaWeather)
			this.tickLightning();
	}

	protected void resetVanillaWeather() {
	}

	protected void tickLightning() {
		if (this.nextLightningStrike <= 0 || --this.nextLightningStrike > 0)
			return;
		float strikeIntensity = this.attemptToSpawnLightning();
		this.nextLightningStrike = this.sampleNextLightningInterval(strikeIntensity);
	}

	protected int sampleNextLightningInterval(float strikeIntensity) {
		int minInterval = SimpleCloudsConfig.SERVER.lightningSpawnIntervalMin.get();
		int maxInterval = Math.max(minInterval, SimpleCloudsConfig.SERVER.lightningSpawnIntervalMax.get());
		int sampled = Mth.randomBetweenInclusive(this.random, minInterval, maxInterval);
		float clampedIntensity = Mth.clamp(strikeIntensity, 0.0F, 1.0F);
		return Math.max(1, Mth.floor(Mth.lerp(clampedIntensity, (float) sampled, (float) minInterval)));
	}

	protected boolean determineUseVanillaWeather() {
		return useVanillaWeather(this.level, this);
	}

	@Override
	public final boolean shouldUseVanillaWeather() {
		return this.useVanillaWeather;
	}

	private static record WeatherStatus(CloudType rainType, float rainFade, CloudType thunderType, float thunderFade,
			CloudType darkeningType, float darkeningFade) {
	}

	public static record WeatherSample(CloudType darkeningType, float darkeningFade, float rainLevel,
			float thunderLevel) {
	}

	private static record LocalizedPrecipitation(WeatherStatus weatherStatus, CloudType rainType,
			Biome.Precipitation precipitation) {
	}

	private static enum WeatherSelection {
		RAIN,
		THUNDER,
		DARKENING
	}

	protected abstract float attemptToSpawnLightning();

	protected abstract void spawnLightning(CloudType type, float fade, int x, int z, boolean soundOnly);

	@Override
	public abstract CloudMode getCloudMode();

	@Override
	public abstract String getSingleModeCloudTypeRawId();

	@Override
	public void spawnLightning(int x, int z, boolean soundOnly) {
		var info = this.getCloudTypeAtWorldPos((float) x + 0.5F, (float) z + 0.5f);
		this.spawnLightning(info.getLeft(), info.getRight(), x, z, soundOnly);
	}

	@Override
	public Vector2f calculateWindDirection() {
		float dirX = Mth.cos(this.scrollAngle);
		float dirZ = Mth.sin(this.scrollAngle);
		return new Vector2f(dirX, dirZ);
	}

	@Override
	public int getTickCount() {
		return this.tickCount;
	}

	@Override
	public long getSeed() {
		return this.seed;
	}

	public RandomSource setSeed(long seed) {
		this.seed = seed;
		return RandomSource.create(seed);
	}

	protected float modifyCloudSpeed(float speed) {
		ModifyCloudSpeedEvent event = new ModifyCloudSpeedEvent(this.level, this, speed);
		NeoForge.EVENT_BUS.post(event);
		return event.getCurrentSpeed();
	}

	@Override
	public float getCloudSpeed() {
		return this.speed;
	}

	@Override
	public void setCloudSpeed(float speed) {
		this.speed = Math.max(0.0F, speed);
	}

	@Override
	public float getScrollAngle() {
		return this.scrollAngle;
	}

	@Override
	public void setScrollAngle(float angle) {
		this.scrollAngle = angle;
	}

	@Override
	public float getScrollX() {
		return this.scrollX;
	}

	@Override
	public float getScrollY() {
		return this.scrollY;
	}

	@Override
	public float getScrollZ() {
		return this.scrollZ;
	}

	@Override
	public float getScrollX(float partialTicks) {
		return Mth.lerp(partialTicks, this.scrollXO, this.scrollX);
	}

	@Override
	public float getScrollY(float partialTicks) {
		return Mth.lerp(partialTicks, this.scrollYO, this.scrollY);
	}

	@Override
	public float getScrollZ(float partialTicks) {
		return Mth.lerp(partialTicks, this.scrollZO, this.scrollZ);
	}

	public static boolean isValidLightning(CloudType type, float fade, RandomSource random) {
		return type.weatherType().includesThunder() && fade < 0.8F;// && (fade > 0.7F || random.nextInt(3) == 0);
	}

	public static float getLightningStrikeIntensity(CloudType type, float fade) {
		if (!type.weatherType().includesThunder())
			return 0.0F;
		float rainFactor = Math.min(1.0F,
				Math.max(0.0F, SimpleCloudsConstants.RAIN_THRESHOLD - fade) / SimpleCloudsConstants.RAIN_FADE);
		return Mth.clamp(type.storminess() * rainFactor, 0.0F, 1.0F);
	}

	public static boolean useVanillaWeather(Level level, CloudTypeSource source) {
		if (!SimpleCloudsConfig.SERVER_SPEC.isLoaded())
			return false;

		boolean flag = SimpleCloudsConfig.SERVER.dimensionWhitelist.get().stream().anyMatch(val -> {
			return level.dimension().location().toString().equals(val);
		});

		if (SimpleCloudsConfig.SERVER.whitelistAsBlacklist.get() ? flag : !flag)
			return true;

		CloudMode mode = SimpleCloudsConfig.SERVER.cloudMode.get();

		switch (mode) {
			case AMBIENT: {
				return true;
			}
			case SINGLE: {
				String rawId = SimpleCloudsConfig.SERVER.singleModeCloudType.get();
				ResourceLocation id = ResourceLocation.tryParse(rawId);
				if (id != null) {
					CloudType type = source.getCloudTypeForId(id);
					if (type != null && type.weatherType() == WeatherType.NONE)
						return true;
				}
			}
			default: {
				return false;
			}
		}
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[level=" + this.level.dimension().location() + "]";
	}
}
