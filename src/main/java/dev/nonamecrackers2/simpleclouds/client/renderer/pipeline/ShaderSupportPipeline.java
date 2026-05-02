package dev.nonamecrackers2.simpleclouds.client.renderer.pipeline;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudPipelineRenderSteps.CloudColor;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import nonamecrackers2.crackerslib.common.compat.CompatHelper;

public class ShaderSupportPipeline implements CloudsRenderPipeline {
	protected ShaderSupportPipeline() {
	}

	@Override
	public void prepare(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	@Override
	public void afterSky(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	@Override
	public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		ProfilerFiller p = mc.getProfiler();
		CloudColor cloudColor = CloudPipelineRenderSteps.resolveCloudColor(renderer, partialTick);

		if (CompatHelper.areShadersRunning())
			GlStateManager._depthMask(true);

		p.push("clouds");
		CloudPipelineRenderSteps.renderCloudGeometry(mc, renderer, camMat, projMat, partialTick, camX, camY, camZ,
				frustum, cloudColor, p, true, true);
		p.pop();

		p.push("cloud_shadows");
		renderer.doCloudShadowProcessing(camMat, partialTick, projMat, camX, camY, camZ,
				mc.getMainRenderTarget().getDepthTextureId());
		p.pop();

		p.push("clouds_composite");
		renderer.doFinalCompositePass(camMat, partialTick, projMat);
		p.pop();

		mc.getMainRenderTarget().bindWrite(false);

		if (SimpleCloudsConfig.CLIENT.renderStormFog.get()) {
			p.push("storm_fog");
			CloudPipelineRenderSteps.prepareStormFog(renderer, camMat, projMat, partialTick, camX, camY, camZ,
					cloudColor);
			if (renderer.shouldUseScreenSpaceStormFog()) {
				renderer.doScreenSpaceWorldFog(camMat, projMat, partialTick);
				mc.getMainRenderTarget().bindWrite(false);
			} else {
				renderer.renderPreparedStormFogOverlay();
			}

			p.pop();
		}
	}

	@Override
	public void afterLevel(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}
}
