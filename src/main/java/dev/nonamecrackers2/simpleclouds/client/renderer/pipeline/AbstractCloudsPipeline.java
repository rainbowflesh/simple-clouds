package dev.nonamecrackers2.simpleclouds.client.renderer.pipeline;

import org.joml.Matrix4f;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudPipelineRenderSteps.CloudColor;
import dev.nonamecrackers2.simpleclouds.common.compat.CompatHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Base implementation of {@link CloudsRenderPipeline} that provides the
 * standard DEFAULT rendering behaviour. Subclasses override only the methods or
 * steps that differ for their particular compat target.
 */
public abstract class AbstractCloudsPipeline implements CloudsRenderPipeline {

	@Override
	public void prepare(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	/**
	 * Renders atmospheric (high-altitude) clouds. Override to suppress or relocate
	 * this step (e.g. Voxy defers it to {@link #afterLevel}).
	 */
	@Override
	public void afterSky(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		CloudColor cloudColor = CloudPipelineRenderSteps.resolveCloudColor(renderer, partialTick);
		ProfilerFiller p = mc.getProfiler();
		p.push("atmospheric_clouds");
		renderer.renderAtmosphericClouds(camMat, projMat, partialTick, camX, camY, camZ, cloudColor.r(), cloudColor.g(),
				cloudColor.b());
		p.pop();
	}

	/**
	 * Runs the full standard cloud render pass: geometry → shadows → composite →
	 * storm fog → bind main target. Override to suppress (DH, Voxy) or to insert
	 * compat-specific state changes around the shared helpers.
	 */
	@Override
	public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		ProfilerFiller p = mc.getProfiler();
		CloudColor cloudColor = CloudPipelineRenderSteps.resolveCloudColor(renderer, partialTick);

		doStandardRenderPass(mc, renderer, camMat, projMat, partialTick, camX, camY, camZ, frustum, cloudColor, p);
		doStormFog(renderer, camMat, projMat, partialTick, camX, camY, camZ, cloudColor, p);
		mc.getMainRenderTarget().bindWrite(CompatHelper.isVrActive());
	}

	@Override
	public void afterLevel(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	/**
	 * Geometry, shadow, and composite passes against the main render target depth.
	 * Shared by the standard pipeline and shader-support pipeline.
	 */
	protected final void doStandardRenderPass(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat,
			Matrix4f projMat, float partialTick, double camX, double camY, double camZ, Frustum frustum,
			CloudColor cloudColor, ProfilerFiller p) {
		p.push("clouds");
		CloudPipelineRenderSteps.renderCloudGeometry(mc, renderer, camMat, projMat, partialTick, camX, camY, camZ,
				frustum, cloudColor, p, true, true);
		p.pop();

		p.push("cloud_shadows");
		renderer.doCloudShadowProcessing(camMat, partialTick, projMat, camX, camY, camZ,
				mc.getMainRenderTarget().getDepthTextureId());
		p.pop();

		p.push("clouds_composite");
		renderer.doFinalCompositePass(camMat, partialTick, projMat, mc.getMainRenderTarget()::getDepthTextureId,
				renderer.shouldUseSceneDepthOcclusion(camX, camY, camZ));
		p.pop();
	}

	/**
	 * Storm-fog post-processing pass. No-op when storm fog is not active.
	 */
	protected final void doStormFog(SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, CloudColor cloudColor, ProfilerFiller p) {
		if (renderer.shouldRenderStormFog(partialTick)) {
			p.push("storm_fog");
			CloudPipelineRenderSteps.prepareStormFog(renderer, camMat, projMat, partialTick, camX, camY, camZ,
					cloudColor);
			renderer.doScreenSpaceWorldFog(camMat, projMat, partialTick);
			p.pop();
		}
	}
}
