package dev.nonamecrackers2.simpleclouds.client.voxy.pipeline;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.AbstractCloudsPipeline;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudPipelineRenderSteps;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.compat.CompatHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Pipeline for Voxy compatibility.
 *
 * <p>Voxy renders terrain after the vanilla sky pass, so cloud geometry must
 * be deferred to {@link #afterLevel} to composite correctly on top of Voxy's
 * LOD terrain. Consequently:
 * <ul>
 *   <li>{@link #afterSky} is suppressed — atmospheric clouds are rendered
 *       inline in {@link #afterLevel} instead.</li>
 *   <li>{@link #beforeWeather} is suppressed — Voxy has not rendered yet at
 *       that point.</li>
 *   <li>{@link #afterLevel} runs the full pass: atmospheric clouds, opaque
 *       geometry (with explicit depth copy), composite, and storm fog.</li>
 * </ul>
 */
public class VoxySupportPipeline extends AbstractCloudsPipeline {

	public static final VoxySupportPipeline INSTANCE = new VoxySupportPipeline();

	private VoxySupportPipeline() {
	}

	/** Suppressed — atmospheric clouds are rendered in {@link #afterLevel}. */
	@Override
	public void afterSky(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f viewMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	/** Suppressed — Voxy has not rendered its terrain yet at this point. */
	@Override
	public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f viewMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	@Override
	public void afterLevel(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f viewMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		ProfilerFiller p = mc.getProfiler();
		float[] cloudCol = renderer.getCloudColor(partialTick);
		float cloudR = cloudCol[0];
		float cloudG = cloudCol[1];
		float cloudB = cloudCol[2];
		Matrix4f cloudWorldMat = renderer.createCloudWorldMatrix();

		p.push("clouds");

		p.push("atmospheric_clouds");
		renderer.renderAtmosphericClouds(viewMat, projMat, partialTick, camX, camY, camZ, cloudR, cloudG, cloudB);
		p.pop();

		// Some renderer methods (renderCloudsOpaque/Transparency, translateClouds)
		// still take a PoseStack in 1.21.1.
		PoseStack cloudStack = poseStackFromMatrix(viewMat);
		renderer.translateClouds(cloudStack, camX, camY, camZ);

		p.push("clouds_opaque");
		RenderTarget cloudTarget = renderer.getCloudTarget();
		cloudTarget.clear(Minecraft.ON_OSX);
		boolean useSceneDepthOcclusion = renderer.shouldUseSceneDepthOcclusion(camX, camY, camZ);
		if (useSceneDepthOcclusion)
			renderer.copyDepthFromMainToClouds();
		cloudTarget.bindWrite(false);
		CloudMeshGenerator generator = renderer.getMeshGenerator();
		SimpleCloudsRenderer.renderCloudsOpaque(generator, cloudStack, projMat, renderer.getFogStart(),
				renderer.getFogEnd(), partialTick, cloudR, cloudG, cloudB,
				SimpleCloudsConfig.CLIENT.frustumCulling.get() ? frustum : null, viewMat, cloudWorldMat, camX, camY,
				camZ);
		// Voxy modifies scene depth; copy cloud depth back so the composite pass uses
		// up-to-date occlusion data.
		renderer.copyDepthFromCloudsToMain();

		p.push("clouds_composite");
		renderer.doFinalCompositePass(viewMat, partialTick, projMat,
				mc.getMainRenderTarget()::getDepthTextureId, useSceneDepthOcclusion);
		p.pop();

		p.pop(); // "clouds"

		if (renderer.shouldRenderStormFog(partialTick)) {
			p.push("storm_fog");
			renderer.doStormPostProcessing(viewMat, partialTick, projMat, camX, camY, camZ, cloudR, cloudG, cloudB);
			renderer.prepareStormFogBlur(partialTick);
			renderer.doScreenSpaceWorldFog(viewMat, projMat, partialTick);
			p.pop();
		}

		mc.getMainRenderTarget().bindWrite(CompatHelper.isVrActive());
	}

	private static PoseStack poseStackFromMatrix(Matrix4f mat) {
		PoseStack stack = new PoseStack();
		stack.last().pose().set(mat);
		return stack;
	}
}
