package dev.nonamecrackers2.simpleclouds.client.dh.pipeline;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.WorldEffects;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudPipelineRenderSteps;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudPipelineRenderSteps.CloudColor;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudsRenderPipeline;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.mixin.MixinRenderTargetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;

public class DhSupportPipeline implements CloudsRenderPipeline {
	public static final DhSupportPipeline INSTANCE = new DhSupportPipeline();

	private DhSupportPipeline() {
	}

	@Override
	public void prepare(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	@Override
	public void afterSky(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
		if (SimpleCloudsConfig.CLIENT.atmosphericClouds.get()) {
			ProfilerFiller p = mc.getProfiler();
			CloudColor cloudColor = CloudPipelineRenderSteps.resolveCloudColor(renderer, partialTick);
			CloudPipelineRenderSteps.renderAtmosphericClouds(mc, renderer, camMat, projMat, partialTick, camX, camY,
					camZ, cloudColor, p);
		}
	}

	@Override
	public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	@Override
	public void afterLevel(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
			float partialTick, double camX, double camY, double camZ, Frustum frustum) {
	}

	@Override
	public void beforeDistantHorizonsApplyShader(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f modelViewMat,
			Matrix4f projMat, float partialTick, double camX, double camY, double camZ, Frustum frustum, int dhFbo) {
		RenderTarget cloudTarget = renderer.getCloudTarget();
		cloudTarget.clear(Minecraft.ON_OSX);
		RenderTarget transparencyTarget = renderer.getCloudTransparencyTarget();
		transparencyTarget.clear(Minecraft.ON_OSX);

		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, dhFbo);
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
				((MixinRenderTargetAccessor) cloudTarget).simpleclouds$getFrameBufferId());
		GL30.glBlitFramebuffer(0, 0, cloudTarget.width, cloudTarget.height, 0, 0, cloudTarget.width, cloudTarget.height,
				GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
				((MixinRenderTargetAccessor) transparencyTarget).simpleclouds$getFrameBufferId());
		GL30.glBlitFramebuffer(0, 0, cloudTarget.width, cloudTarget.height, 0, 0, transparencyTarget.width,
				transparencyTarget.height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dhFbo);
	}

	@Override
	public void afterDistantHorizonsRender(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f modelViewMat,
			Matrix4f projMat, float partialTick, double camX, double camY, double camZ, Frustum frustum, int dhFbo) {
		CloudColor cloudColor = CloudPipelineRenderSteps.resolveCloudColor(renderer, partialTick);

		ProfilerFiller p = mc.getProfiler();

		p.push("clouds");
		CloudPipelineRenderSteps.renderCloudGeometry(mc, renderer, modelViewMat, projMat, partialTick, camX, camY,
				camZ, frustum, cloudColor, p, false, false);

		p.push("cloud_shadows");
		renderer.doCloudShadowProcessing(modelViewMat, partialTick, projMat, camX, camY, camZ,
				renderer.getCloudTarget().getDepthTextureId());
		p.pop();

		p.push("clouds_composite");
		renderer.doFinalCompositePass(modelViewMat, partialTick, projMat);
		p.pop();

		p.pop();

		Matrix4f oldMcProjMat = RenderSystem.getProjectionMatrix();

		if (SimpleCloudsConfig.CLIENT.renderStormFog.get()) {
			p.push("storm_fog");
			CloudPipelineRenderSteps.prepareStormFog(renderer, modelViewMat, projMat, partialTick, camX, camY, camZ,
					cloudColor);
			// DH exposes its own LOD depth, but this path does not keep a separate
			// scene-depth
			// texture for the screen-space fog composite. Using the prepared overlay here
			// keeps
			// the far horizon occluded instead of letting the screen-space pass skip it.
			renderer.renderPreparedStormFogOverlay();

			p.pop();
		}

		mc.getMainRenderTarget().bindWrite(false);

		// Kind of messed up, but we need to render to the main frame buffer but use the
		// Distant Horizons LOD depth. We just temporarily use the copy of the depth
		// buffer
		// we have in the cloud framebuffer and swap back after
		GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
				renderer.getCloudTarget().getDepthTextureId(), 0);
		RenderSystem.setProjectionMatrix(projMat, VertexSorting.DISTANCE_TO_ORIGIN); // Make minecraft use the DH proj
																						// mat

		// We can then render whatever we want to the main MC framebuffer while using DH
		// LOD depth
		PoseStack stack = new PoseStack();
		stack.mulPose(modelViewMat);
		stack.pushPose();
		stack.translate(-camX, -camY, -camZ);
		renderLightning(renderer.getWorldEffectsManager(), renderer, mc, stack, partialTick, camX, camY, camZ);
		stack.popPose();

		// mc.getProfiler().push("clouds_debug");
		// stack.pushPose();
		// renderer.translateClouds(stack, camX, camY, camZ);
		// SimpleCloudsRenderer.renderCloudsDebug(renderer.getMeshGenerator(), stack,
		// projMat, partialTick, renderer.getFogStart(), renderer.getFogEnd(), frustum,
		// false, true);
		// stack.popPose();
		// mc.getProfiler().pop();

		RenderSystem.setProjectionMatrix(oldMcProjMat, VertexSorting.DISTANCE_TO_ORIGIN);
		GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
				mc.getMainRenderTarget().getDepthTextureId(), 0);
	}

	private static void renderLightning(WorldEffects effects, SimpleCloudsRenderer renderer, Minecraft mc,
			PoseStack stack, float partialTick, double camX, double camY, double camZ) {
		Tesselator tesselator = Tesselator.getInstance();
		RenderSystem.enableBlend();
		RenderSystem.enableDepthTest();

		if (effects.hasLightningToRender()) {
			float cachedFogStart = RenderSystem.getShaderFogStart();
			RenderSystem.setShaderFogStart(Float.MAX_VALUE);
			BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			RenderSystem.setShader(GameRenderer::getRendertypeLightningShader);
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

			effects.forLightning(bolt -> {
				if (bolt.getPosition().distance((float) camX, (float) camY,
						(float) camZ) <= SimpleCloudsConstants.CLOSE_THUNDER_CUTOFF && bolt.getFade(partialTick) > 0.5F)
					mc.level.setSkyFlashTime(2);
				float dist = bolt.getPosition().distance((float) camX, (float) camY, (float) camZ);
				bolt.render(stack, builder, partialTick, 1.0F, 1.0F, 1.0F, renderer.getFadeFactorForDistance(dist));
			});

			MeshData data = builder.build();
			if (data != null)
				BufferUploader.drawWithShader(data);

			RenderSystem.setShaderFogStart(cachedFogStart);

			RenderSystem.defaultBlendFunc();
		}

		RenderSystem.disableBlend();
	}
}
