package dev.nonamecrackers2.simpleclouds.client.renderer.pipeline;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.nonamecrackers2.simpleclouds.client.framebuffer.WeightedBlendingTarget;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;

public final class CloudPipelineRenderSteps {
    private CloudPipelineRenderSteps() {
    }

    public static CloudColor resolveCloudColor(SimpleCloudsRenderer renderer, float partialTick) {
        float[] cloudColor = renderer.getCloudColor(partialTick);
        return new CloudColor((float) cloudColor[0], (float) cloudColor[1], (float) cloudColor[2]);
    }

    public static void renderAtmosphericClouds(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat,
            Matrix4f projMat, float partialTick, double camX, double camY, double camZ, CloudColor cloudColor,
            ProfilerFiller profiler) {
        profiler.push("atmospheric_clouds");
        PoseStack stack = new PoseStack();
        stack.mulPose(camMat);
        renderer.getAtmosphericCloudRenderer().render(stack, projMat, partialTick, camX, camY, camZ, cloudColor.r(),
                cloudColor.g(), cloudColor.b());
        mc.getMainRenderTarget().bindWrite(false);
        profiler.pop();
    }

    public static void renderCloudGeometry(Minecraft mc, SimpleCloudsRenderer renderer, Matrix4f camMat,
            Matrix4f projMat, float partialTick, double camX, double camY, double camZ, @Nullable Frustum frustum,
            CloudColor cloudColor, ProfilerFiller profiler, boolean clearTargets, boolean copyDepthFromMain) {
        PoseStack stack = new PoseStack();
        stack.mulPose(camMat);
        stack.pushPose();
        renderer.translateClouds(stack, camX, camY, camZ);

        profiler.push("clouds_opaque");
        RenderTarget cloudTarget = renderer.getCloudTarget();
        if (clearTargets)
            cloudTarget.clear(Minecraft.ON_OSX);
        if (copyDepthFromMain)
            renderer.copyDepthFromMainToClouds();
        cloudTarget.bindWrite(false);

        CloudMeshGenerator generator = renderer.getMeshGenerator();
        SimpleCloudsRenderer.renderCloudsOpaque(generator, stack, projMat, renderer.getFogStart(), renderer.getFogEnd(),
                partialTick, cloudColor.r(), cloudColor.g(), cloudColor.b(),
                SimpleCloudsConfig.CLIENT.frustumCulling.get() ? frustum : null);

        profiler.popPush("clouds_transparent");
        WeightedBlendingTarget transparencyTarget = renderer.getCloudTransparencyTarget();
        if (clearTargets)
            transparencyTarget.clear(Minecraft.ON_OSX);

        if (generator.transparencyEnabled()) {
            renderer.copyDepthFromCloudsToTransparency();
            transparencyTarget.bindWrite(false);
            SimpleCloudsRenderer.renderCloudsTransparency(generator, stack, projMat, renderer.getFogStart(),
                    renderer.getFogEnd(), partialTick, cloudColor.r(), cloudColor.g(), cloudColor.b(),
                    SimpleCloudsConfig.CLIENT.frustumCulling.get() ? frustum : null);
        }

        profiler.pop();
        stack.popPose();
    }

    public static void prepareStormFog(SimpleCloudsRenderer renderer, Matrix4f camMat, Matrix4f projMat,
            float partialTick, double camX, double camY, double camZ, CloudColor cloudColor) {
        renderer.doStormPostProcessing(camMat, partialTick, projMat, camX, camY, camZ, cloudColor.r(), cloudColor.g(),
                cloudColor.b());
        renderer.prepareStormFogBlur(partialTick);
    }

    public static record CloudColor(float r, float g, float b) {
    }
}