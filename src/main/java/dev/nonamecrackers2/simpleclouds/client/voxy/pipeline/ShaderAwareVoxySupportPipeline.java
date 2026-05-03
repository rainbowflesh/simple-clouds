package dev.nonamecrackers2.simpleclouds.client.voxy.pipeline;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

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
import dev.nonamecrackers2.simpleclouds.client.voxy.VoxyDepthCapture;
import dev.nonamecrackers2.simpleclouds.client.world.FogRenderMode;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.FogType;
import nonamecrackers2.crackerslib.common.compat.CompatHelper;

import java.nio.IntBuffer;

/**
 * Shader-aware wrapper around {@link VoxySupportPipeline}.
 *
 * When Iris shaders are active, Voxy renders into Iris's gbuffer FBO rather
 * than the vanilla main render target. This means
 * {@code copyDepthFromMainToClouds()}
 * gets stale/incomplete depth that doesn't include Voxy LOD geometry.
 *
 * This pipeline resolves Voxy's depth attachment from the currently-bound FBO
 * at the time {@code afterLevel} fires, then merges it into the cloud target
 * before cloud geometry is drawn — mirroring the technique used by
 * {@code ShaderAwareDhPipeline} for Distant Horizons.
 */
public class ShaderAwareVoxySupportPipeline implements CloudsRenderPipeline {

    public static final ShaderAwareVoxySupportPipeline INSTANCE = new ShaderAwareVoxySupportPipeline();

    // Delegate for non-shader path
    private final CloudsRenderPipeline vanilla = VoxySupportPipeline.INSTANCE;

    // ── Depth-merge fullscreen-triangle program ──────────────────────────────
    private static int depthMergeProgram = -1;
    private static int depthMergeVao = -1;
    private static int depthMergeVbo = -1;
    private static int depthMergeSamplerLoc = -1;

    // Scratch texture for renderbuffer → texture copy
    private static int scratchDepthTex = -1;
    private static int scratchDepthW = -1;
    private static int scratchDepthH = -1;

    private ShaderAwareVoxySupportPipeline() {
    }

    // ── Iris detection (reflection, no hard dep) ─────────────────────────────

    private static Boolean irisPresent;

    private static boolean irisShaderActive() {
        if (irisPresent == null) {
            try {
                Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisPresent = true;
            } catch (ClassNotFoundException e) {
                irisPresent = false;
            }
        }
        if (!irisPresent)
            return false;
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
                    .getMethod("getInstance").invoke(null);
            return (boolean) api.getClass()
                    .getMethod("isShaderPackInUse").invoke(api);
        } catch (Exception e) {
            return false;
        }
    }

    // ── PoseStack helper ─────────────────────────────────────────────────────

    private static PoseStack poseStackFromMatrix(Matrix4f mat) {
        PoseStack stack = new PoseStack();
        stack.last().pose().set(mat);
        return stack;
    }

    // ── Pipeline hooks ───────────────────────────────────────────────────────

    @Override
    public void prepare(Minecraft mc, SimpleCloudsRenderer renderer,
            Matrix4f viewMat, Matrix4f projMat, float partialTick,
            double camX, double camY, double camZ, Frustum frustum) {
        vanilla.prepare(mc, renderer, viewMat, projMat, partialTick, camX, camY, camZ, frustum);
    }

    @Override
    public void afterSky(Minecraft mc, SimpleCloudsRenderer renderer,
            Matrix4f viewMat, Matrix4f projMat, float partialTick,
            double camX, double camY, double camZ, Frustum frustum) {
        vanilla.afterSky(mc, renderer, viewMat, projMat, partialTick, camX, camY, camZ, frustum);
    }

    @Override
    public void beforeWeather(Minecraft mc, SimpleCloudsRenderer renderer,
            Matrix4f viewMat, Matrix4f projMat, float partialTick,
            double camX, double camY, double camZ, Frustum frustum) {
        vanilla.beforeWeather(mc, renderer, viewMat, projMat, partialTick, camX, camY, camZ, frustum);
    }

    // 只修改 afterLevel 里的 shader 分支，其余不变
    @Override
    public void afterLevel(Minecraft mc, SimpleCloudsRenderer renderer,
            Matrix4f viewMat, Matrix4f projMat, float partialTick,
            double camX, double camY, double camZ, Frustum frustum) {

        // 检查捕获是否是本帧的（Voxy 可能因 shadow pass 等跳过）
        boolean voxyRenderedThisFrame = VoxyDepthCapture.captureFrame == mc.getFrameTimeNs();

        // 非 Iris 或 Voxy 本帧未渲染 → 走原版逻辑
        if (!VoxyDepthCapture.irisWasActive || !voxyRenderedThisFrame) {
            vanilla.afterLevel(mc, renderer, viewMat, projMat,
                    partialTick, camX, camY, camZ, frustum);
            return;
        }

        // ── Iris + Voxy 路径 ────────────────────────────────────────────────
        ProfilerFiller p = mc.getProfiler();
        float[] cloudCol = renderer.getCloudColor(partialTick);
        float cloudR = cloudCol[0], cloudG = cloudCol[1], cloudB = cloudCol[2];

        p.push("clouds");
        PoseStack cloudStack = poseStackFromMatrix(viewMat);
        renderer.translateClouds(cloudStack, camX, camY, camZ);

        p.push("clouds_opaque");
        RenderTarget cloudTarget = renderer.getCloudTarget();
        cloudTarget.clear(Minecraft.ON_OSX);

        // Iris composite 已经将 Voxy 深度合并到 main RT 的情况下，
        // 直接用 copyDepthFromMainToClouds 就足够。
        // 若 Iris composite 在 afterLevel 之后才跑（较新版本 Iris），
        // 则用 capturedFbo 尝试抓 Voxy 渲染时的 FBO 深度作为补充。
        renderer.copyDepthFromMainToClouds(); // baseline: vanilla + already-composited Voxy

        // 尝试将 capturedFbo 的深度额外合并（处理 Iris composite 还没跑的情况）
        if (VoxyDepthCapture.capturedFbo != 0
                && VoxyDepthCapture.capturedFbo != mc.getMainRenderTarget().frameBufferId) {
            mergeVoxyDepthIntoCloudTarget(cloudTarget, VoxyDepthCapture.capturedFbo);
        }

        cloudTarget.bindWrite(false);
        CloudMeshGenerator generator = renderer.getMeshGenerator();
        SimpleCloudsRenderer.renderCloudsOpaque(
                generator, cloudStack, projMat,
                renderer.getFogStart(), renderer.getFogEnd(),
                partialTick, cloudR, cloudG, cloudB,
                SimpleCloudsConfig.CLIENT.frustumCulling.get() ? frustum : null);
        renderer.copyDepthFromCloudsToMain();

        p.popPush("clouds_transparent");
        WeightedBlendingTarget transparencyTarget = renderer.getCloudTransparencyTarget();
        transparencyTarget.clear(Minecraft.ON_OSX);
        if (generator.transparencyEnabled()) {
            renderer.copyDepthFromCloudsToTransparency();
            transparencyTarget.bindWrite(false);
            SimpleCloudsRenderer.renderCloudsTransparency(
                    generator, cloudStack, projMat,
                    renderer.getFogStart(), renderer.getFogEnd(),
                    partialTick, cloudR, cloudG, cloudB,
                    SimpleCloudsConfig.CLIENT.frustumCulling.get() ? frustum : null);
        }
        p.pop();

        p.push("clouds_composite");
        renderer.doFinalCompositePass(viewMat, partialTick, projMat);
        p.pop();
        p.pop(); // "clouds"

        // storm fog（同原版）
        if (SimpleCloudsConfig.CLIENT.renderStormFog.get()) {
            p.push("storm_fog");
            renderer.doStormPostProcessing(viewMat, partialTick, projMat,
                    camX, camY, camZ, cloudR, cloudG, cloudB);
            RenderTarget blurTarget = renderer.getBlurTarget();
            blurTarget.clear(Minecraft.ON_OSX);
            blurTarget.bindWrite(true);
            FrameBufferUtils.blitTargetPreservingAlpha(renderer.getStormFogTarget(),
                    mc.getWindow().getWidth(), mc.getWindow().getHeight());
            renderer.doBlurPostProcessing(partialTick);
            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ZERO,
                    GlStateManager.DestFactor.ONE);
            renderer.getBlurTarget().blitToScreen(
                    mc.getWindow().getWidth(), mc.getWindow().getHeight(), false);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(projMat, VertexSorting.DISTANCE_TO_ORIGIN);
            p.pop();
        }

        mc.getMainRenderTarget().bindWrite(CompatHelper.isVrActive());
    }
    // ── Depth utilities ───────────────────────────────────────────────────────

    /**
     * Copy the vanilla main render target's depth into the cloud target.
     * Uses copyTexSubImage2D to avoid format-mismatch issues.
     */
    private static boolean copyVanillaDepthToCloudTarget(RenderTarget cloudTarget, RenderTarget source) {
        if (cloudTarget == null || source == null)
            return false;
        int cloudDepthTex = cloudTarget.getDepthTextureId();
        if (cloudDepthTex <= 0)
            return false;

        GlStateManager._getError();
        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        IntBuffer vp = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        int copyW = Math.min(cloudTarget.width, source.width);
        int copyH = Math.min(cloudTarget.height, source.height);
        GL11.glViewport(0, 0, copyW, copyH);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, cloudDepthTex);
        GL11.glReadBuffer(GL11.GL_NONE);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, copyW, copyH);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
        GL11.glViewport(vp.get(0), vp.get(1), vp.get(2), vp.get(3));

        return GlStateManager._getError() == GL11.GL_NO_ERROR;
    }

    /**
     * Resolve the depth attachment of {@code voxyFbo} to a texture ID.
     * Handles both TEXTURE and RENDERBUFFER attachment types.
     */
    private static int resolveVoxyDepthTexture(int voxyFbo, int fallbackW, int fallbackH) {
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, voxyFbo);

        int attachType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);

        int depthTex = -1;

        if (attachType == GL11.GL_TEXTURE) {
            // Best case: depth is already a texture
            depthTex = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

        } else if (attachType == GL30.GL_RENDERBUFFER) {
            // Renderbuffer: must copy into a scratch texture
            int rbName = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (rbName > 0) {
                int prevRb = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rbName);
                int w = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_WIDTH);
                int h = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_HEIGHT);
                if (w <= 0)
                    w = fallbackW;
                if (h <= 0)
                    h = fallbackH;
                ensureScratchDepthTexture(w, h);

                int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, voxyFbo);
                GL11.glReadBuffer(GL11.GL_NONE);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, scratchDepthTex);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, prevRb);

                depthTex = scratchDepthTex;
            }
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        return depthTex;
    }

    /**
     * Merge Voxy's depth into the cloud target via a fullscreen-triangle shader
     * using GL_LEQUAL depth test — identical strategy to ShaderAwareDhPipeline.
     */
    private static boolean mergeVoxyDepthIntoCloudTarget(RenderTarget cloudTarget, int voxyFbo) {
        int voxyDepthTex = resolveVoxyDepthTexture(voxyFbo, cloudTarget.width, cloudTarget.height);
        if (voxyDepthTex <= 0)
            return false;
        if (!ensureDepthMergeProgram())
            return false;

        GlStateManager._getError();
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        IntBuffer vp = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        // Bind cloud target FBO directly
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, cloudTarget.frameBufferId);
        GL11.glViewport(0, 0, cloudTarget.width, cloudTarget.height);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL); // keep closer (vanilla OR voxy)
        GL11.glColorMask(false, false, false, false);

        GL20.glUseProgram(depthMergeProgram);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, voxyDepthTex);
        if (depthMergeSamplerLoc >= 0)
            GL20.glUniform1i(depthMergeSamplerLoc, 0);

        GL30.glBindVertexArray(depthMergeVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        // Restore state
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        if (!wasDepth)
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (wasBlend)
            GL11.glEnable(GL11.GL_BLEND);
        else
            GL11.glDisable(GL11.GL_BLEND);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(vp.get(0), vp.get(1), vp.get(2), vp.get(3));

        return GlStateManager._getError() == GL11.GL_NO_ERROR;
    }

    // ── GL resource helpers ───────────────────────────────────────────────────

    /**
     * Fullscreen-triangle depth passthrough program (same as
     * ShaderAwareDhPipeline).
     */
    private static boolean ensureDepthMergeProgram() {
        if (depthMergeProgram != -1)
            return true;

        String vert = "#version 150\nin vec2 aPos;out vec2 vUv;"
                + "void main(){vUv=aPos*0.5+0.5;gl_Position=vec4(aPos,0.0,1.0);}";
        String frag = "#version 150\nin vec2 vUv;uniform sampler2D uDepth;"
                + "void main(){gl_FragDepth=texture(uDepth,vUv).r;}";

        int v = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(v, vert);
        GL20.glCompileShader(v);
        if (GL20.glGetShaderi(v, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            System.err.println("[VoxySC] depth-merge vert failed: " + GL20.glGetShaderInfoLog(v));
            GL20.glDeleteShader(v);
            return false;
        }
        int f = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(f, frag);
        GL20.glCompileShader(f);
        if (GL20.glGetShaderi(f, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            System.err.println("[VoxySC] depth-merge frag failed: " + GL20.glGetShaderInfoLog(f));
            GL20.glDeleteShader(v);
            GL20.glDeleteShader(f);
            return false;
        }
        depthMergeProgram = GL20.glCreateProgram();
        GL20.glAttachShader(depthMergeProgram, v);
        GL20.glAttachShader(depthMergeProgram, f);
        GL20.glLinkProgram(depthMergeProgram);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if (GL20.glGetProgrami(depthMergeProgram, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            System.err.println("[VoxySC] depth-merge link failed: " + GL20.glGetProgramInfoLog(depthMergeProgram));
            GL20.glDeleteProgram(depthMergeProgram);
            depthMergeProgram = -1;
            return false;
        }
        depthMergeSamplerLoc = GL20.glGetUniformLocation(depthMergeProgram, "uDepth");

        // Upload the classic 3-vertex screen triangle
        depthMergeVao = GL30.glGenVertexArrays();
        depthMergeVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(depthMergeVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, depthMergeVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
                new float[] { -1f, -1f, 3f, -1f, -1f, 3f }, GL15.GL_STATIC_DRAW);
        int posLoc = GL20.glGetAttribLocation(depthMergeProgram, "aPos");
        GL20.glEnableVertexAttribArray(posLoc);
        GL20.glVertexAttribPointer(posLoc, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0);
        GL30.glBindVertexArray(0);
        return true;
    }

    private static void ensureScratchDepthTexture(int w, int h) {
        if (scratchDepthTex == -1)
            scratchDepthTex = GL11.glGenTextures();
        if (w == scratchDepthW && h == scratchDepthH)
            return;
        scratchDepthW = w;
        scratchDepthH = h;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, scratchDepthTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F,
                w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
    }
}