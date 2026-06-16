package dev.nonamecrackers2.simpleclouds.client.renderer.pipeline;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudPipelineRenderSteps.CloudColor;
import dev.nonamecrackers2.simpleclouds.common.compat.CompatHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Pipeline variant for when a shader mod (e.g. Iris/Oculus) is running.
 *
 * <p>Differences from {@link DefaultPipeline}:
 * <ul>
 *   <li>Re-enables the depth mask before geometry so shader passes that
 *       disabled it do not silently corrupt cloud depth writes.</li>
 *   <li>Binds the main render target (without VR depth) <em>before</em>
 *       storm fog so the shader post-processing chain sees a clean depth
 *       buffer during the fog pass.</li>
 * </ul>
 */
public class ShaderSupportPipeline extends AbstractCloudsPipeline {
	protected ShaderSupportPipeline() {
	}

	@Override
	public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		ProfilerFiller p = mc.getProfiler();
		CloudColor cloudColor = CloudPipelineRenderSteps.resolveCloudColor(renderer, partialTick);

		if (CompatHelper.areShadersRunning())
			GlStateManager._depthMask(true);

		doStandardRenderPass(mc, renderer, camMat, projMat, partialTick, camX, camY, camZ, frustum, cloudColor, p);

		// Bind before storm fog — shader post-processing expects the main FBO
		// to be active while the fog pass executes.
		mc.getMainRenderTarget().bindWrite(false);

		doStormFog(renderer, camMat, projMat, partialTick, camX, camY, camZ, cloudColor, p);
	}
}
