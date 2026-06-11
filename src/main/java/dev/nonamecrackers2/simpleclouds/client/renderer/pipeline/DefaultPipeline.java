package dev.nonamecrackers2.simpleclouds.client.renderer.pipeline;

import org.joml.Matrix4f;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudPipelineRenderSteps.CloudColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import dev.nonamecrackers2.simpleclouds.common.compat.CompatHelper;

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

		p.push("atmospheric_clouds");
		renderer.renderAtmosphericClouds(camMat, projMat, partialTick, camX, camY, camZ, cloudColor.r(),
				cloudColor.g(), cloudColor.b());
		p.pop();
	}

	@Override
	public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		ProfilerFiller p = mc.getProfiler();
		CloudColor cloudColor = CloudPipelineRenderSteps.resolveCloudColor(renderer, partialTick);

		p.push("clouds");
		CloudPipelineRenderSteps.renderCloudGeometry(mc, renderer, camMat, projMat, partialTick, camX, camY, camZ,
				frustum, cloudColor, p, true, true);
		p.pop();

		p.push("cloud_shadows");
		renderer.doCloudShadowProcessing(camMat, partialTick, projMat, camX, camY, camZ,
				mc.getMainRenderTarget().getDepthTextureId());
		p.pop();

		p.push("clouds_composite");
		renderer.doFinalCompositePass(camMat, partialTick, projMat,
				mc.getMainRenderTarget()::getDepthTextureId,
				renderer.shouldUseSceneDepthOcclusion(camX, camY, camZ));
		p.pop();

		if (renderer.shouldRenderStormFog(partialTick)) {
			p.push("storm_fog");
			CloudPipelineRenderSteps.prepareStormFog(renderer, camMat, projMat, partialTick, camX, camY, camZ,
					cloudColor);
			renderer.doScreenSpaceWorldFog(camMat, projMat, partialTick);
			p.pop();
		}

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
