package dev.nonamecrackers2.simpleclouds.client.renderer;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.client.renderer.lightning.LightningBolt;
import dev.nonamecrackers2.simpleclouds.client.sound.AdjustableAttenuationSoundInstance;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.init.SimpleCloudsSounds;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.phys.Vec3;
import nonamecrackers2.crackerslib.common.compat.CompatHelper;

public class WorldEffects {
	public static final float EFFECTS_STRENGTH_MULTIPLER = 1.2F;
	private static final int VISUAL_LIGHTNING_INTERVAL_MIN = 12;
	private static final int VISUAL_LIGHTNING_INTERVAL_MAX = 50;
	private static final int LIGHTNING_BASE_COLOR = 0xFFFFFFFF;
	public static final int RAIN_SCAN_WIDTH = 32;
	public static final int RAIN_SCAN_HEIGHT = 8;
	public static final int RAIN_HEIGHT_OFFSET = 8;
	public static final int RAIN_SOUND_INTERVAL_MODIFIER = 100;
	public static final SimpleWeightedRandomList<Integer> LIGHTNING_COLORS = SimpleWeightedRandomList.<Integer>builder()
			.add(0xFFFFFFFF, 30) // White
			.add(0xFF8C80FF, 13) // Blue
			.add(0xFF8C80FF, 12) // Purple
			.add(0xFFF0FFB4, 10) // Yellow
			.add(0xFFFFB4BE, 5) // Red
			.build();
	// private static final int RAINY_WATER_COLOR = 0xFF303030;
	private final Minecraft mc;
	private final SimpleCloudsRenderer renderer;
	private @Nullable CloudType typeAtCamera;
	private float fadeAtCamera;
	private float storminessAtCamera;
	private float storminessSmoothed;
	private float storminessSmoothedO;
	private int nextVisualLightning;
	private final List<LightningBolt> lightningBolts = Lists.newArrayList();
	private final RandomSource random = RandomSource.create();

	protected WorldEffects(Minecraft mc, SimpleCloudsRenderer renderer) {
		this.mc = mc;
		this.renderer = renderer;
	}

	public void updateCameraWeatherStatus(double camX, double camY, double camZ) {
		CloudManager<ClientLevel> manager = CloudManager.get(this.mc.level);
		CloudManager.WeatherSample weather = manager.sampleWeatherAtWorldPos((float) camX, (float) camY,
				(float) camZ);
		CloudType type = weather.darkeningType();
		this.typeAtCamera = type;
		this.fadeAtCamera = weather.darkeningFade();

		if (!manager.shouldUseVanillaWeather() && type.weatherType().causesDarkening()) {
			float verticalFade = 1.0F - Mth.clamp(
					((float) camY - manager.getStormStartHeight(type)) / SimpleCloudsConstants.RAIN_VERTICAL_FADE, 0.0F,
					1.0F);
			float factor = Mth.clamp((1.0F - weather.darkeningFade()) * 3.0F, 0.0F, 1.0F);
			this.storminessAtCamera = type.storminess() * factor * verticalFade;
		} else {
			this.storminessAtCamera = 0.0F;
		}

		if (!manager.shouldUseVanillaWeather()) {
			this.mc.level.setRainLevel(weather.rainLevel());
			this.mc.level.setThunderLevel(weather.thunderLevel());
		}
	}

	public void renderPost(Matrix4f camMat, float partialTick, double camX, double camY, double camZ, float scale) {
		this.updateCameraWeatherStatus(camX, camY, camZ);
	}

	public boolean hasLightningToRender() {
		return !this.lightningBolts.isEmpty();
	}

	public void forLightning(Consumer<LightningBolt> consumer) {
		this.lightningBolts.forEach(consumer);
	}

	public void renderLightning(float partialTick, double camX, double camY, double camZ) {
		Tesselator tesselator = Tesselator.getInstance();
		RenderSystem.depthMask(Minecraft.useShaderTransparency() || CompatHelper.areShadersRunning());
		RenderSystem.colorMask(true, true, true, true);
		RenderSystem.enableBlend();
		RenderSystem.enableDepthTest();

		if (this.hasLightningToRender()) {
			float currentFogStart = RenderSystem.getShaderFogStart();
			RenderSystem.setShaderFogStart(Float.MAX_VALUE);
			RenderSystem.applyModelViewMatrix();
			BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			RenderSystem.setShader(GameRenderer::getRendertypeLightningShader);
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			PoseStack stack = new PoseStack();
			stack.pushPose();
			stack.translate(-camX, -camY, -camZ);
			for (LightningBolt bolt : this.lightningBolts) {
				if (bolt.getPosition().distance((float) camX, (float) camY,
						(float) camZ) <= SimpleCloudsConstants.CLOSE_THUNDER_CUTOFF && bolt.getFade(partialTick) > 0.5F)
					this.mc.level.setSkyFlashTime(2);
				float dist = bolt.getPosition().distance((float) camX, (float) camY, (float) camZ);
				bolt.render(stack, builder, partialTick, 1.0F, 1.0F, 1.0F, this.getLightningVisibility(dist),
						this.mc.level, camX, camY, camZ);
			}
			stack.popPose();
			MeshData meshData = builder.build();
			if (meshData != null)
				BufferUploader.drawWithShader(meshData);
			RenderSystem.applyModelViewMatrix();
			RenderSystem.setShaderFogStart(currentFogStart);
		}

		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
	}

	public float getLightningVisibility(float distance) {
		float fade = this.renderer.getFadeFactorForDistance(distance);
		if (!Float.isFinite(fade))
			return 1.0F;
		return Mth.clamp(Math.max(0.35F, fade), 0.35F, 1.0F);
	}

	public void spawnLightning(BlockPos pos, BlockPos targetPos, boolean onlySound, int seed, int depth,
			int branchCount,
			float maxBranchLength, float maxWidth, float minimumPitch, float maximumPitch) {
		Camera camera = this.mc.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.getPosition();
		Vector3f vec = new Vector3f((float) pos.getX() + 0.5F, (float) pos.getY() + 0.5F, (float) pos.getZ() + 0.5F);
		Vector3f targetVec = new Vector3f((float) targetPos.getX() + 0.5F, (float) targetPos.getY() + 0.5F,
				(float) targetPos.getZ() + 0.5F);
		if (targetVec.y >= vec.y)
			targetVec.y = vec.y - 1.0F;

		CloudManager<ClientLevel> manager = CloudManager.get(this.mc.level);
		if (manager.getCloudMode() == CloudMode.AMBIENT) // Prevent lightning from spawning where no clouds are using
															// AMBIENT mode
		{
			float dist = Vector2f.distance(vec.x, vec.z, (float) cameraPos.x, (float) cameraPos.z);
			if (dist < SimpleCloudsConstants.AMBIENT_MODE_FADE_END)
				return;
		}

		SoundEvent sound = SimpleCloudsSounds.DISTANT_THUNDER.get();
		int attenuation = SimpleCloudsConfig.CLIENT.thunderAttenuationDistance.get();
		float dist = vec.distance((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
		if (dist < SimpleCloudsConstants.CLOSE_THUNDER_CUTOFF) {
			sound = SimpleCloudsSounds.CLOSE_THUNDER.get();
			attenuation = SimpleCloudsConstants.CLOSE_THUNDER_CUTOFF;
		}
		float fade = 1.0F - Math.min(Math.max(dist - (float) SimpleCloudsConstants.THUNDER_PITCH_FULL_DIST, 0.0F)
				/ ((float) SimpleCloudsConstants.THUNDER_PITCH_MINIMUM_DIST
						- (float) SimpleCloudsConstants.THUNDER_PITCH_FULL_DIST),
				1.0F);
		float distanceScale = this.getLightningGeometryScale(dist);
		RandomSource random = RandomSource.create((long) seed);
		var lightningInfo = manager.getThunderCloudTypeAtWorldPos((float) pos.getX() + 0.5F, (float) pos.getZ() + 0.5F);
		float strikeIntensity = CloudManager.getLightningStrikeIntensity(lightningInfo.getLeft(),
				lightningInfo.getRight());
		AdjustableAttenuationSoundInstance instance = new AdjustableAttenuationSoundInstance(sound, SoundSource.WEATHER,
				1.0F + this.random.nextFloat() * 4.0F, 0.5F + fade * 0.5F, random, (double) pos.getX() + 0.5D,
				(float) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, attenuation);
		int time = Mth.floor(dist / SimpleCloudsConstants.SOUND_METERS_PER_SECOND) * 20;
		this.mc.getSoundManager().playDelayed(instance, time);
		if (!onlySound) {
			int color = this.resolveLightningColor(random, strikeIntensity);
			float r = (float) FastColor.ARGB32.red(color) / 255.0F;
			float g = (float) FastColor.ARGB32.green(color) / 255.0F;
			float b = (float) FastColor.ARGB32.blue(color) / 255.0F;
			this.lightningBolts.add(new LightningBolt(random, vec, targetVec, depth, branchCount,
					maxBranchLength * Mth.lerp(distanceScale, 1.0F, 1.8F),
					maxWidth * Mth.lerp(distanceScale, 1.0F, 4.0F), minimumPitch, maximumPitch, r, g, b));
		}
	}

	private float getLightningGeometryScale(float distance) {
		float start = (float) SimpleCloudsConstants.CLOSE_THUNDER_CUTOFF;
		float end = Math.max(start + 1.0F, this.renderer.getFogEnd());
		return Mth.clamp((distance - start) / (end - start), 0.0F, 1.0F);
	}

	private int resolveLightningColor(RandomSource random, float strikeIntensity) {
		if (!SimpleCloudsConfig.CLIENT.lightningColorVariation.get())
			return LIGHTNING_BASE_COLOR;
		int targetColor = LIGHTNING_COLORS.getRandomValue(random).get();
		float blend = Mth.clamp(strikeIntensity, 0.0F, 1.0F);
		int r = Mth.floor(Mth.lerp(blend, (float) FastColor.ARGB32.red(LIGHTNING_BASE_COLOR),
				(float) FastColor.ARGB32.red(targetColor)));
		int g = Mth.floor(Mth.lerp(blend, (float) FastColor.ARGB32.green(LIGHTNING_BASE_COLOR),
				(float) FastColor.ARGB32.green(targetColor)));
		int b = Mth.floor(Mth.lerp(blend, (float) FastColor.ARGB32.blue(LIGHTNING_BASE_COLOR),
				(float) FastColor.ARGB32.blue(targetColor)));
		return FastColor.ARGB32.color(255, r, g, b);
	}
	//
	// public void modifyLightMapTexture(float partialTick, int pixelX, int pixelY,
	// Vector3f color)
	// {
	// }

	public float getStorminessAtCamera() {
		return this.storminessAtCamera;
	}

	public boolean isInsideCloudVolume(double camX, double camY, double camZ) {
		if (this.mc.level == null)
			return false;

		CloudManager<ClientLevel> manager = CloudManager.get(this.mc.level);
		Pair<CloudType, Float> result = manager.getCloudTypeAtWorldPos((float) camX, (float) camZ);
		CloudType type = result.getLeft();
		if (type == SimpleCloudsConstants.EMPTY)
			return false;

		int cloudHeightRange = type.noiseConfig().getHeightRange();
		if (cloudHeightRange <= 0)
			return false;

		float cloudBottom = manager.getCloudHeight()
				+ (float) type.noiseConfig().getStartHeight() * (float) SimpleCloudsConstants.CLOUD_SCALE;
		float cloudTop = manager.getCloudHeight()
				+ (float) type.noiseConfig().getEndHeight() * (float) SimpleCloudsConstants.CLOUD_SCALE;
		return result.getRight() < 1.0F && camY >= cloudBottom && camY <= cloudTop;
	}

	public float getInsideCloudFactor(double camX, double camY, double camZ) {
		if (!SimpleCloudsConfig.CLIENT.insideCloudFog.get())
			return 0.0F;
		if (this.mc.level == null)
			return 0.0F;

		CloudManager<ClientLevel> manager = CloudManager.get(this.mc.level);
		Pair<CloudType, Float> result = manager.getCloudTypeAtWorldPos((float) camX, (float) camZ);
		CloudType type = result.getLeft();
		if (type == SimpleCloudsConstants.EMPTY)
			return 0.0F;
		int cloudHeightRange = type.noiseConfig().getHeightRange();
		if (cloudHeightRange <= 0)
			return 0.0F;

		float horizontalFade = SimpleCloudsConfig.CLIENT.insideCloudFogHorizontalFade.get().floatValue();
		float horizontalFactor = 1.0F - Mth.clamp(result.getRight() / horizontalFade, 0.0F, 1.0F);
		if (horizontalFactor <= 0.0F)
			return 0.0F;

		float cloudBottom = manager.getCloudHeight()
				+ (float) type.noiseConfig().getStartHeight() * (float) SimpleCloudsConstants.CLOUD_SCALE;
		float cloudTop = manager.getCloudHeight()
				+ (float) type.noiseConfig().getEndHeight() * (float) SimpleCloudsConstants.CLOUD_SCALE;
		float verticalFactor = bandLerp((float) camY, cloudBottom, cloudTop,
				SimpleCloudsConfig.CLIENT.insideCloudFogVerticalFadeDistance.get().floatValue());
		return horizontalFactor * verticalFactor;
	}

	public static float getInsideCloudMaxVisibility() {
		return SimpleCloudsConfig.CLIENT.insideCloudFogMaxVisibility.get().floatValue();
	}

	public static float getInsideCloudFogColorBlend() {
		return SimpleCloudsConfig.CLIENT.insideCloudFogColorBlend.get().floatValue();
	}

	public void tick() {
		var lightning = this.lightningBolts.iterator();
		while (lightning.hasNext()) {
			LightningBolt bolt = lightning.next();
			if (bolt.isDead())
				lightning.remove();
			bolt.tick();
		}

		this.tickVisualLightning();

		this.storminessSmoothedO = this.storminessSmoothed;
		this.storminessSmoothed += (this.storminessAtCamera - this.storminessSmoothed) / 25.0F;
	}

	private void tickVisualLightning() {
		if (this.mc.level == null || this.renderer.getMeshGenerator() == null)
			return;

		if (this.nextVisualLightning > 0) {
			this.nextVisualLightning--;
			return;
		}

		CloudManager<ClientLevel> manager = CloudManager.get(this.mc.level);
		Camera camera = this.mc.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.getPosition();
		int visibleRadius = Math.max(SimpleCloudsConstants.CLOSE_THUNDER_CUTOFF + 1,
				this.renderer.getMeshGenerator().getCloudAreaMaxRadius() * SimpleCloudsConstants.CLOUD_SCALE);
		float strongestStrikeIntensity = 0.0F;

		for (int i = 0; i < SimpleCloudsConstants.LIGHTNING_SPAWN_ATTEMPTS; i++) {
			float angle = this.random.nextFloat() * ((float) Math.PI * 2.0F);
			float dist = Mth.lerp(Mth.sqrt(this.random.nextFloat()),
					(float) SimpleCloudsConstants.CLOSE_THUNDER_CUTOFF, (float) visibleRadius);
			int x = Mth.floor((float) cameraPos.x + Mth.cos(angle) * dist);
			int z = Mth.floor((float) cameraPos.z + Mth.sin(angle) * dist);
			var info = manager.getThunderCloudTypeAtWorldPos((float) x + 0.5F, (float) z + 0.5F);
			CloudType type = info.getLeft();
			float fade = info.getRight();
			if (!CloudManager.isValidLightning(type, fade, this.random))
				continue;
			strongestStrikeIntensity = CloudManager.getLightningStrikeIntensity(type, fade);
			Pair<BlockPos, BlockPos> strikePositions = this.createVisualLightningStrikePositions(manager, type, x, z);

			this.spawnLightning(strikePositions.getLeft(), strikePositions.getRight(), false,
					this.random.nextInt(),
					3, 2, 220.0F + this.random.nextFloat() * 180.0F, 16.0F + this.random.nextFloat() * 6.0F,
					35.0F, 85.0F);
			break;
		}

		this.nextVisualLightning = this.sampleVisualLightningInterval(strongestStrikeIntensity);
	}

	private int sampleVisualLightningInterval(float strikeIntensity) {
		int sampled = Mth.randomBetweenInclusive(this.random, VISUAL_LIGHTNING_INTERVAL_MIN,
				VISUAL_LIGHTNING_INTERVAL_MAX);
		return Math.max(1, Mth.floor(Mth.lerp(Mth.clamp(strikeIntensity, 0.0F, 1.0F), (float) sampled,
				(float) VISUAL_LIGHTNING_INTERVAL_MIN)));
	}

	private Pair<BlockPos, BlockPos> createVisualLightningStrikePositions(CloudManager<ClientLevel> manager,
			CloudType type, int x, int z) {
		if (type.cloudLayers().size() > 1 && this.random.nextFloat() < 0.5F) {
			float stormStart = manager.getStormStartHeight(type);
			float cloudTop = manager.getCloudTopHeight(type);
			float elevatedStartMin = Mth.lerp(0.35F, stormStart, cloudTop);
			float elevatedStartMax = cloudTop - 4.0F;
			if (elevatedStartMax > elevatedStartMin + 2.0F) {
				int startY = Mth.floor(Mth.lerp(this.random.nextFloat(), elevatedStartMin, elevatedStartMax));
				float maxDrop = Math.max(10.0F, (float) startY - stormStart - 2.0F);
				float drop = Mth.lerp(this.random.nextFloat(), 8.0F, maxDrop);
				int targetY = Mth.floor(Math.max(stormStart + 2.0F, (float) startY - drop));
				if (targetY < startY)
					return Pair.of(new BlockPos(x, startY, z), new BlockPos(x, targetY, z));
			}
		}

		return Pair.of(new BlockPos(x, (int) manager.getStormStartHeight(type), z),
				manager.getLightningTargetPos(type, x, z));
	}

	public Color calculateFogColor(float defaultR, float defaultG, float defaultB, float partialTick) {
		float lerp = this.getDarkenFactor(partialTick);
		return hsbLerp(defaultR, defaultG, defaultB, 0.68F, 0.2F, -0.05F, lerp);
	}

	public Color calculateSkyColor(float defaultR, float defaultG, float defaultB, float partialTick) {
		float lerp = this.getDarkenFactor(partialTick);
		return hsbLerp(defaultR, defaultG, defaultB, 0.63F, 0.1F, 0.05F, lerp);
	}

	// TODO: Better lerping
	private static Color hsbLerp(float r, float g, float b, float targetHue, float targetSaturation,
			float targetBrightness, float lerp) {
		float[] hsbFog = Color.RGBtoHSB((int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F), null);
		if (targetHue < hsbFog[0])
			targetHue += 1.0F;
		float hue = Mth.lerp(lerp, targetHue, hsbFog[0]);
		float sat = Mth.clamp(Mth.lerp(lerp, targetSaturation, hsbFog[1]), 0.0F, 1.0F);
		float bright = Mth.clamp(Mth.lerp(lerp, targetBrightness, hsbFog[2]), 0.0F, 1.0F);
		return Color.getHSBColor(hue, sat, bright);
	}

	public void reset() {
		this.lightningBolts.clear();
		this.typeAtCamera = null;
		this.fadeAtCamera = 0.0F;
		this.storminessAtCamera = 0.0F;
		this.storminessSmoothed = 0.0F;
		this.storminessSmoothedO = 0.0F;
		this.nextVisualLightning = 0;
	}

	public @Nullable CloudType getCloudTypeAtCamera() {
		return this.typeAtCamera;
	}

	public float getFadeRegionAtCamera() {
		return this.fadeAtCamera;
	}

	public float getStorminessSmoothed(float partialTick) {
		return Mth.lerp(partialTick, this.storminessSmoothedO, this.storminessSmoothed);
	}

	public float getDarkenFactor(float partialTick, float strength) {
		return Mth.clamp(1.0F - this.getStorminessSmoothed(partialTick) * strength, 0.1F, 1.0F);
	}

	public float getDarkenFactor(float partialTick) {
		return this.getDarkenFactor(partialTick, EFFECTS_STRENGTH_MULTIPLER);
	}

	private static float bandLerp(float y, float minY, float maxY, float fadeDistance) {
		if (maxY < minY) {
			float oldMin = minY;
			minY = maxY;
			maxY = oldMin;
		}
		if (y < minY || y > maxY)
			return 0.0F;
		if (fadeDistance <= 0.0F)
			return 1.0F;
		float distanceFromNearestBoundary = Math.min(y - minY, maxY - y);
		return Mth.clamp(distanceFromNearestBoundary / fadeDistance, 0.0F, 1.0F);
	}

	public List<LightningBolt> getLightningBolts() {
		return this.lightningBolts;
	}
}
