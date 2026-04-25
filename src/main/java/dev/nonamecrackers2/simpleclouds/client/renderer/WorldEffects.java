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
	private final List<LightningBolt> lightningBolts = Lists.newArrayList();
	private final RandomSource random = RandomSource.create();

	protected WorldEffects(Minecraft mc, SimpleCloudsRenderer renderer) {
		this.mc = mc;
		this.renderer = renderer;
	}

	public void updateCameraWeatherStatus(double camX, double camY, double camZ) {
		CloudManager<ClientLevel> manager = CloudManager.get(this.mc.level);
		Pair<CloudType, Float> result = manager.getCloudTypeAtWorldPos((float) camX, (float) camZ);
		CloudType type = result.getLeft();
		this.typeAtCamera = type;
		this.fadeAtCamera = result.getRight();

		if (!manager.shouldUseVanillaWeather() && type.weatherType().causesDarkening()) {
			float verticalFade = 1.0F - Mth.clamp(
					((float) camY - manager.getStormStartHeight(type)) / SimpleCloudsConstants.RAIN_VERTICAL_FADE, 0.0F,
					1.0F);
			float factor = Mth.clamp((1.0F - result.getRight()) * 3.0F, 0.0F, 1.0F);
			this.storminessAtCamera = type.storminess() * factor * verticalFade;
		} else {
			this.storminessAtCamera = 0.0F;
		}

		if (!manager.shouldUseVanillaWeather()) {
			float rainLevel = manager.getRainLevel((float) camX, (float) camY, (float) camZ);
			float thunderLevel = manager.getThunderLevel((float) camX, (float) camY, (float) camZ);
			this.mc.level.setRainLevel(rainLevel);
			this.mc.level.setThunderLevel(thunderLevel);
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
				bolt.render(stack, builder, partialTick, 1.0F, 1.0F, 1.0F,
						this.renderer.getFadeFactorForDistance(dist));
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

	public void spawnLightning(BlockPos pos, boolean onlySound, int seed, int depth, int branchCount,
			float maxBranchLength, float maxWidth, float minimumPitch, float maximumPitch) {
		Camera camera = this.mc.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.getPosition();
		Vector3f vec = new Vector3f((float) pos.getX() + 0.5F, (float) pos.getY() + 0.5F, (float) pos.getZ() + 0.5F);

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
		RandomSource random = RandomSource.create((long) seed);
		AdjustableAttenuationSoundInstance instance = new AdjustableAttenuationSoundInstance(sound, SoundSource.WEATHER,
				1.0F + this.random.nextFloat() * 4.0F, 0.5F + fade * 0.5F, random, (double) pos.getX() + 0.5D,
				(float) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, attenuation);
		int time = Mth.floor(dist / SimpleCloudsConstants.SOUND_METERS_PER_SECOND) * 20;
		this.mc.getSoundManager().playDelayed(instance, time);
		if (!onlySound) {
			float r = 1.0F;
			float g = 1.0F;
			float b = 1.0F;
			if (SimpleCloudsConfig.CLIENT.lightningColorVariation.get()) {
				int color = LIGHTNING_COLORS.getRandomValue(random).get();
				r = (float) FastColor.ARGB32.red(color) / 255.0F;
				g = (float) FastColor.ARGB32.green(color) / 255.0F;
				b = (float) FastColor.ARGB32.blue(color) / 255.0F;
			}
			this.lightningBolts.add(new LightningBolt(random, vec, depth, branchCount, maxBranchLength, maxWidth,
					minimumPitch, maximumPitch, r, g, b));
		}
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

		this.storminessSmoothedO = this.storminessSmoothed;
		this.storminessSmoothed += (this.storminessAtCamera - this.storminessSmoothed) / 25.0F;
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
