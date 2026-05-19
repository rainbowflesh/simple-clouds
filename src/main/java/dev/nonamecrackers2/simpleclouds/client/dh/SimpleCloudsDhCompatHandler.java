package dev.nonamecrackers2.simpleclouds.client.dh;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderPassEvent;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

import dev.nonamecrackers2.simpleclouds.client.dh.event.SimpleCloudsAfterDhRenderHandler;
import dev.nonamecrackers2.simpleclouds.client.dh.event.SimpleCloudsBeforeDhRenderHandler;
import dev.nonamecrackers2.simpleclouds.client.dh.event.SimpleCloudsDhForgeEvents;
import dev.nonamecrackers2.simpleclouds.client.dh.event.SimpleCloudsDhSetupHandler;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;

public class SimpleCloudsDhCompatHandler {
	private static final Logger LOGGER = LogManager.getLogger("simpleclouds/SimpleCloudsDhCompatHandler");
	private static final float EARTH_RADIUS_BLOCKS = 6_371_000.0F;
	// Cached DH stuff, internal use
	private static Matrix4f mcProjMat;
	private static Matrix4f mcModelViewMat;
	private static Matrix4f dhProjMat;
	private static Matrix4f dhModelViewMat;
	private static int dhFramebufferId;

	private static boolean passComplete;

	public static void _updateCachedDhState(Matrix4f mcProjMat, Matrix4f mcModelViewMat, Matrix4f dhProjMat,
			Matrix4f dhModelViewMat) {
		SimpleCloudsDhCompatHandler.mcProjMat = mcProjMat;
		SimpleCloudsDhCompatHandler.mcModelViewMat = mcModelViewMat;
		SimpleCloudsDhCompatHandler.dhProjMat = dhProjMat;
		SimpleCloudsDhCompatHandler.dhModelViewMat = dhModelViewMat;
	}

	public static void _updateDhFramebufferId(int id) {
		dhFramebufferId = id;
	}

	public static void _markPassComplete(boolean flag) {
		passComplete = flag;
	}

	public static boolean _isPassComplete() {
		return passComplete;
	}

	public static Matrix4f _getMcProjMat() {
		return Objects.requireNonNull(mcProjMat, "Cached MC projection matrix not set");
	}

	public static Matrix4f _getMcModelViewMat() {
		return Objects.requireNonNull(mcModelViewMat, "Cached MC model view matrix not set");
	}

	public static Matrix4f _getDhProjMat() {
		return Objects.requireNonNull(dhProjMat, "Cached DH projection matrix not set");
	}

	public static Matrix4f _getDhModelViewMat() {
		return Objects.requireNonNull(dhModelViewMat, "Cached DH model view matrix not set");
	}

	public static int _getDhFramebufferId() {
		if (dhFramebufferId == 0)
			throw new IllegalStateException("DH FBO not set");
		return dhFramebufferId;
	}

	public static boolean shouldUseDhRendering() {
		if (DhApi.Delayed.configs == null)
			return true;
		return DhApi.Delayed.configs.graphics().renderingEnabled().getValue()
				&& DhApi.Delayed.configs.graphics().genericRendering().renderingEnabled().getValue();
	}

	public static float getEarthCurvatureRadius() {
		if (DhApi.Delayed.configs == null)
			return 0.0F;
		Integer ratio = DhApi.Delayed.configs.graphics().earthCurvatureRatio().getValue();
		if (ratio == null || ratio == 0)
			return 0.0F;
		return EARTH_RADIUS_BLOCKS / (float) ratio.intValue();
	}

	private static void requestRendererReloadForDhStateChange(String source, boolean enabled) {
		LOGGER.debug("Distant Horizons {} changed to {}", source, enabled);
		_updateDhFramebufferId(0);
		_updateCachedDhState(null, null, null, null);
		_markPassComplete(true);
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> SimpleCloudsRenderer.getInstance().requestReload());
	}

	public static void initialize() {
		LOGGER.debug("Distant Horizons detected");

		DhApiEventRegister.on(DhApiBeforeApplyShaderRenderEvent.class, new SimpleCloudsBeforeDhRenderHandler());
		DhApiEventRegister.on(DhApiAfterRenderEvent.class, new SimpleCloudsAfterDhRenderHandler());
		DhApiEventRegister.on(DhApiBeforeRenderPassEvent.class, new SimpleCloudsDhSetupHandler());

		IDhApiConfigValue<Boolean> val = DhApi.Delayed.configs.graphics().genericRendering().cloudRenderingEnabled();
		val.setValue(false);
		val.addChangeListener(b -> {
			if (b)
				val.setValue(false);
		});

		DhApi.Delayed.configs.graphics().renderingEnabled()
				.addChangeListener(enabled -> requestRendererReloadForDhStateChange("rendering", enabled));
		DhApi.Delayed.configs.graphics().genericRendering().renderingEnabled()
				.addChangeListener(enabled -> requestRendererReloadForDhStateChange("generic rendering", enabled));

		NeoForge.EVENT_BUS.register(SimpleCloudsDhForgeEvents.class);
	}

	public static Matrix4f dhMat4ToMc(DhApiMat4f mat4) {
		return new Matrix4f(
				mat4.m00, mat4.m01, mat4.m02, mat4.m03,
				mat4.m10, mat4.m11, mat4.m12, mat4.m13,
				mat4.m20, mat4.m21, mat4.m22, mat4.m23,
				mat4.m30, mat4.m31, mat4.m32, mat4.m33).transpose();
	}
}
