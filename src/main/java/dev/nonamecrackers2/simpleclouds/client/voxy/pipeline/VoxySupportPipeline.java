package dev.nonamecrackers2.simpleclouds.client.voxy.pipeline;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;

import dev.nonamecrackers2.simpleclouds.client.framebuffer.FrameBufferUtils;
import dev.nonamecrackers2.simpleclouds.client.framebuffer.WeightedBlendingTarget;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudsRenderPipeline;
import dev.nonamecrackers2.simpleclouds.client.world.FogRenderMode;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.FogType;
import nonamecrackers2.crackerslib.common.compat.CompatHelper;

public class VoxySupportPipeline implements CloudsRenderPipeline {
    public static final VoxySupportPipeline INSTANCE = new VoxySupportPipeline();

    private VoxySupportPipeline() {
    }

    @Override
    public void prepare(Minecraft mc, SimpleCloudsRenderer renderer, PoseStack stack,
            Matrix4f projMat, float partialTick,
            double camX, double camY, double camZ, Frustum frustum) {
    }

    // -----------------------------------------------------------------------
    // afterSky — renders ONLY the atmospheric clouds
    // -----------------------------------------------------------------------
    @Override
    public void afterSky(Minecraft mc, SimpleCloudsRenderer renderer, PoseStack stack,
            Matrix4f projMat, float partialTick,
            double camX, double camY, double camZ, Frustum frustum) {
        if (SimpleCloudsConfig.CLIENT.atmosphericClouds.get()) {
            float[] cloudCol = renderer.getCloudColor(partialTick);
            mc.getProfiler().push("atmospheric_clouds");
            renderer.getAtmosphericCloudRenderer().render(
                    stack, projMat, partialTick, camX, camY, camZ,
                    cloudCol[0], cloudCol[1], cloudCol[2]);
            mc.getMainRenderTarget().bindWrite(false);
            mc.getProfiler().pop();
        }
    }

    // -----------------------------------------------------------------------
    // beforeWeather — identical to DefaultPipeline (screen-space fog only).
    // -----------------------------------------------------------------------
    @Override
    public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer, PoseStack stack,
            Matrix4f projMat, float partialTick,
            double camX, double camY, double camZ, Frustum frustum) {
        if (SimpleCloudsConfig.CLIENT.fogMode.get() == FogRenderMode.SCREEN_SPACE
                && mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.NONE) {
            renderer.doScreenSpaceWorldFog(stack, projMat, partialTick);
            mc.getMainRenderTarget().bindWrite(false);
        }
    }

    // -----------------------------------------------------------------------
    // afterLevel — fires at TAIL of renderLevel, after Voxy has rendered.
    // -----------------------------------------------------------------------
    @Override
    public void afterLevel(Minecraft mc, SimpleCloudsRenderer renderer, PoseStack stack,
            Matrix4f projMat, float partialTick,
            double camX, double camY, double camZ, Frustum frustum) {
        ProfilerFiller p = mc.getProfiler();

        float[] cloudCol = renderer.getCloudColor(partialTick);
        float cloudR = cloudCol[0];
        float cloudG = cloudCol[1];
        float cloudB = cloudCol[2];

        // -- Volumetric cloud geometry --------------------------------------
        p.push("clouds");

        stack.pushPose();
        renderer.translateClouds(stack, camX, camY, camZ);

        p.push("clouds_opaque");

        RenderTarget cloudTarget = renderer.getCloudTarget();
        cloudTarget.clear(Minecraft.ON_OSX);

        renderer.copyDepthFromMainToClouds();

        cloudTarget.bindWrite(false);

        CloudMeshGenerator generator = renderer.getMeshGenerator();
        SimpleCloudsRenderer.renderCloudsOpaque(
                generator, stack, projMat,
                renderer.getFogStart(), renderer.getFogEnd(),
                partialTick, cloudR, cloudG, cloudB,
                SimpleCloudsConfig.CLIENT.frustumCulling.get() ? frustum : null);

        // Write cloud depth back so particles/GUI rendered after have correct depth
        renderer.copyDepthFromCloudsToMain();

        p.popPush("clouds_transparent");

        WeightedBlendingTarget transparencyTarget = renderer.getCloudTransparencyTarget();
        transparencyTarget.clear(Minecraft.ON_OSX);

        if (generator.transparencyEnabled()) {
            renderer.copyDepthFromCloudsToTransparency();
            transparencyTarget.bindWrite(false);
            SimpleCloudsRenderer.renderCloudsTransparency(
                    generator, stack, projMat,
                    renderer.getFogStart(), renderer.getFogEnd(),
                    partialTick, cloudR, cloudG, cloudB,
                    SimpleCloudsConfig.CLIENT.frustumCulling.get() ? frustum : null);
        }

        p.pop();
        stack.popPose();

        p.push("clouds_composite");
        renderer.doFinalCompositePass(stack, partialTick, projMat);
        p.pop();

        p.pop();

        // -- Storm fog ------------------------------------------------------
        if (SimpleCloudsConfig.CLIENT.renderStormFog.get()) {
            p.push("storm_fog");
            renderer.doStormPostProcessing(
                    stack, partialTick, projMat,
                    camX, camY, camZ, cloudR, cloudG, cloudB);

            RenderTarget blurTarget = renderer.getBlurTarget();
            blurTarget.clear(Minecraft.ON_OSX);
            blurTarget.bindWrite(true);
            FrameBufferUtils.blitTargetPreservingAlpha(
                    renderer.getStormFogTarget(),
                    mc.getWindow().getWidth(),
                    mc.getWindow().getHeight());
            renderer.doBlurPostProcessing(partialTick);

            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ZERO,
                    GlStateManager.DestFactor.ONE);
            renderer.getBlurTarget().blitToScreen(
                    mc.getWindow().getWidth(),
                    mc.getWindow().getHeight(), false);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            // blitToScreen corrupts the projection matrix
            RenderSystem.setProjectionMatrix(projMat, VertexSorting.DISTANCE_TO_ORIGIN);

            p.pop();
        }

        mc.getMainRenderTarget().bindWrite(CompatHelper.isVrActive());
    }
}