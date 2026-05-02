package dev.nonamecrackers2.simpleclouds.client.renderer.pipeline;

import org.joml.Matrix4f;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudPipelineRenderSteps.CloudColor;
import dev.nonamecrackers2.simpleclouds.client.world.FogRenderMode;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import nonamecrackers2.crackerslib.common.compat.CompatHelper;

public class DefaultPipeline implements CloudsRenderPipeline {
	protected DefaultPipeline() {
	}

	@Override
	public void prepare(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	@Override
	public void afterSky(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		ProfilerFiller p = mc.getProfiler();
		CloudColor cloudColor = CloudPipelineRenderSteps.resolveCloudColor(renderer, partialTick);

		if (SimpleCloudsConfig.CLIENT.atmosphericClouds.get()) {
			CloudPipelineRenderSteps.renderAtmosphericClouds(mc, renderer, camMat, projMat, partialTick, camX, camY,
					camZ, cloudColor, p);
		}

		p.push("clouds");
		CloudPipelineRenderSteps.renderCloudGeometry(mc, renderer, camMat, projMat, partialTick, camX, camY, camZ,
				frustum, cloudColor, p, true, false);
		p.pop();

		if (SimpleCloudsConfig.CLIENT.renderStormFog.get()) {
			p.push("storm_fog");
			CloudPipelineRenderSteps.prepareStormFog(renderer, camMat, projMat, partialTick, camX, camY, camZ,
					cloudColor);
			if (!renderer.shouldUseScreenSpaceStormFog())
				renderer.renderPreparedStormFogOverlay();

			p.pop();
		}

		// Set the frame buffer back to the main one so everything else can render
		// normally
		mc.getMainRenderTarget().bindWrite(CompatHelper.isVrActive());
	}

	@Override
	public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		if (SimpleCloudsConfig.CLIENT.renderStormFog.get() && renderer.shouldUseScreenSpaceStormFog()) {
			renderer.doScreenSpaceWorldFog(camMat, projMat, partialTick);
			mc.getMainRenderTarget().bindWrite(false);
		}

		mc.getProfiler().push("cloud_shadows");
		renderer.doCloudShadowProcessing(camMat, partialTick, projMat, camX, camY, camZ,
				mc.getMainRenderTarget().getDepthTextureId());
		mc.getProfiler().pop();

		mc.getProfiler().push("clouds_composite");
		renderer.doFinalCompositePass(camMat, partialTick, projMat);
		mc.getProfiler().pop();
		mc.getMainRenderTarget().bindWrite(CompatHelper.isVrActive());
	}

	@Override
	public void afterLevel(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		// mc.getProfiler().push("clouds_debug");
		// PoseStack stack = new PoseStack();
		// stack.mulPose(camMat);
		// renderer.translateClouds(stack, camX, camY, camZ);
		// SimpleCloudsRenderer.renderCloudsDebug(renderer.getMeshGenerator(), stack,
		// projMat, partialTick, renderer.getFogStart(), renderer.getFogEnd(), frustum,
		// false, true);
		// mc.getProfiler().pop();
	}
}
