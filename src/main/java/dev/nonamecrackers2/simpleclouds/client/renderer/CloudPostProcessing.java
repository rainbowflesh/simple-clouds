package dev.nonamecrackers2.simpleclouds.client.renderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.framebuffer.CloudRenderTarget;
import dev.nonamecrackers2.simpleclouds.client.framebuffer.FrameBufferUtils;
import dev.nonamecrackers2.simpleclouds.client.framebuffer.ShadowMapBuffer;
import dev.nonamecrackers2.simpleclouds.client.framebuffer.WeightedBlendingTarget;
import dev.nonamecrackers2.simpleclouds.client.renderer.settings.CloudsRendererSettings;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.ShaderStorageBufferObject;
import dev.nonamecrackers2.simpleclouds.mixin.MixinPostChain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

final class CloudPostProcessing {
    private static final Logger LOGGER = LogManager.getLogger("simpleclouds/CloudPostProcessing");
    private static final ResourceLocation STORM_POST_PROCESSING_LOC = SimpleCloudsMod
            .id("shaders/post/storm_post.json");
    private static final ResourceLocation BLUR_POST_PROCESSING_LOC = ResourceLocation
            .withDefaultNamespace("shaders/post/blur.json");
    private static final ResourceLocation SCREEN_SPACE_WORLD_FOG_LOC = SimpleCloudsMod
            .id("shaders/post/screen_space_world_fog.json");
    private static final ResourceLocation CLOUD_SHADOWS_LOC = SimpleCloudsMod.id("shaders/post/cloud_shadows.json");

    private final Minecraft mc;
    private final CloudsRendererSettings settings;
    private final List<PostChain> postChains = new ArrayList<>();
    private @Nullable RenderTarget cloudTarget;
    private @Nullable WeightedBlendingTarget cloudTransparencyTarget;
    private @Nullable RenderTarget stormFogTarget;
    private @Nullable RenderTarget blurTarget;
    private @Nullable PostChain finalComposite;
    private @Nullable PostChain stormPostProcessing;
    private @Nullable PostChain blurPostProcessing;
    private @Nullable PostChain screenSpaceWorldFog;
    private @Nullable PostChain cloudShadows;

    CloudPostProcessing(Minecraft mc, CloudsRendererSettings settings) {
        this.mc = mc;
        this.settings = settings;
    }

    public @Nullable RenderTarget getCloudTarget() {
        return this.cloudTarget;
    }

    public @Nullable WeightedBlendingTarget getCloudTransparencyTarget() {
        return this.cloudTransparencyTarget;
    }

    public @Nullable RenderTarget getStormFogTarget() {
        return this.stormFogTarget;
    }

    public @Nullable RenderTarget getBlurTarget() {
        return this.blurTarget;
    }

    public boolean hasCloudTarget() {
        return this.cloudTarget != null;
    }

    public boolean hasStormFogTarget() {
        return this.stormFogTarget != null;
    }

    public boolean hasBlurTarget() {
        return this.blurTarget != null;
    }

    public boolean hasTransparencyTarget() {
        return this.cloudTransparencyTarget != null;
    }

    public String describePostChains() {
        return this.postChains.toString();
    }

    public void initialize(ResourceManager manager, RenderTarget mainTarget, boolean highPrecisionDepth,
            int stormFogResolutionDivisor, ShadowMapBuffer stormFogShadowMap, @Nullable ShadowMapBuffer shadowMap,
            ShaderStorageBufferObject lightningBoltPositions) {
        this.destroyTargets();
        this.createTargets(mainTarget, highPrecisionDepth, stormFogResolutionDivisor);
        this.destroyPostChains();

        this.stormPostProcessing = this.createPostChain(manager, STORM_POST_PROCESSING_LOC, this.stormFogTarget,
                pass -> {
                    EffectInstance effect = pass.getEffect();
                    effect.setSampler("ShadowMap", stormFogShadowMap::getDepthTexId);
                    effect.setSampler("ShadowMapColor", stormFogShadowMap::getColorTexId);
                    effect.setSampler("DepthSampler", () -> this.cloudTarget.getDepthTextureId());
                    lightningBoltPositions.optionalBindToProgram("LightningBolts", effect.getId());
                });

        this.blurPostProcessing = this.createPostChain(manager, BLUR_POST_PROCESSING_LOC, this.blurTarget);

        this.screenSpaceWorldFog = this.createPostChain(manager, SCREEN_SPACE_WORLD_FOG_LOC, mainTarget, pass -> {
            EffectInstance effect = pass.getEffect();
            effect.setSampler("StormFogSampler", () -> this.blurTarget.getColorTextureId());
            effect.setSampler("CloudDepthSampler", () -> this.cloudTarget.getDepthTextureId());
        });

        ResourceLocation compositeLocation = this.settings.useTransparency()
                ? SimpleCloudsRenderer.FINAL_COMPOSITE_LOC
                : SimpleCloudsRenderer.FINAL_COMPOSITE_NO_TRANSPARENCY_LOC;
        this.finalComposite = this.createPostChain(manager, compositeLocation, mainTarget, pass -> {
            EffectInstance effect = pass.getEffect();
            effect.setSampler("MainDepthSampler", mainTarget::getDepthTextureId);
            effect.setSampler("CloudsDepthTexture", () -> this.cloudTarget.getDepthTextureId());
            if (this.settings.useTransparency()) {
                effect.setSampler("AccumTexture", () -> this.cloudTransparencyTarget.getColorTextureId());
                effect.setSampler("RevealageTexture", () -> this.cloudTransparencyTarget.getRevealageTextureId());
            }
            effect.setSampler("CloudsTexture", () -> this.cloudTarget.getColorTextureId());
        });

        if (shadowMap != null) {
            this.cloudShadows = this.createPostChain(manager, CLOUD_SHADOWS_LOC, mainTarget, pass -> {
                EffectInstance effect = pass.getEffect();
                effect.setSampler("ShadowMap", shadowMap::getDepthTexId);
                effect.setSampler("ShadowMapColor", shadowMap::getColorTexId);
                effect.safeGetUniform("ShadowSpan")
                        .set((float) Math.min(shadowMap.getViewWidth(), shadowMap.getViewHeight()));
            });
        } else {
            this.cloudShadows = null;
        }
    }

    public void resizeTargets(RenderTarget mainTarget, int stormFogResolutionDivisor) {
        int width = mainTarget.width;
        int height = mainTarget.height;

        if (this.cloudTarget != null)
            this.cloudTarget.resize(width, height, Minecraft.ON_OSX);

        if (this.cloudTransparencyTarget != null)
            this.cloudTransparencyTarget.resize(width, height, Minecraft.ON_OSX);

        if (this.stormFogTarget != null) {
            this.stormFogTarget.resize(width / stormFogResolutionDivisor, height / stormFogResolutionDivisor,
                    Minecraft.ON_OSX);
            this.stormFogTarget.setFilterMode(GL11.GL_LINEAR);
        }

        if (this.blurTarget != null) {
            this.blurTarget.resize(width, height, Minecraft.ON_OSX);
            this.blurTarget.setFilterMode(GL11.GL_LINEAR);
        }

        for (PostChain chain : this.postChains) {
            RenderTarget chainTarget = ((MixinPostChain) chain).simpleclouds$getScreenTarget();
            chain.resize(chainTarget.width, chainTarget.height);
        }
    }

    public void close() {
        this.destroyTargets();
        this.destroyPostChains();
    }

    public void doBlurPostProcessing(float partialTick) {
        if (this.blurPostProcessing == null)
            return;

        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        this.blurPostProcessing.process(partialTick);
        RenderSystem.depthMask(true);
    }

    public void prepareStormFogBlur(float partialTick) {
        if (this.blurTarget == null || this.stormFogTarget == null)
            return;

        Window window = this.mc.getWindow();
        this.blurTarget.clear(Minecraft.ON_OSX);
        this.blurTarget.bindWrite(true);
        FrameBufferUtils.blitTargetPreservingAlpha(this.stormFogTarget, window.getWidth(), window.getHeight());
        this.doBlurPostProcessing(partialTick);
    }

    public void renderPreparedStormFogOverlay() {
        if (this.blurTarget == null)
            return;

        Window window = this.mc.getWindow();
        this.mc.getMainRenderTarget().bindWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE);
        this.blurTarget.blitToScreen(window.getWidth(), window.getHeight(), false);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public void doScreenSpaceWorldFog(float partialTick, Consumer<EffectInstance> effectConsumer) {
        this.processChain(this.screenSpaceWorldFog, partialTick, () -> {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            RenderSystem.depthMask(false);
        }, effectConsumer);
    }

    public void doFinalCompositePass(float partialTick, Consumer<EffectInstance> effectConsumer) {
        this.processChain(this.finalComposite, partialTick, () -> {
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            RenderSystem.depthMask(false);
        }, effectConsumer);
    }

    public void doStormPostProcessing(float partialTick, Consumer<EffectInstance> effectConsumer) {
        if (this.stormFogTarget == null)
            return;

        this.processChain(this.stormPostProcessing, partialTick, () -> {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            RenderSystem.depthMask(false);
            this.stormFogTarget.clear(Minecraft.ON_OSX);
            this.stormFogTarget.bindWrite(true);
        }, effectConsumer);
    }

    public void doCloudShadowProcessing(float partialTick, Consumer<EffectInstance> effectConsumer) {
        this.processChain(this.cloudShadows, partialTick, () -> {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            RenderSystem.depthMask(false);
        }, effectConsumer);
    }

    private void createTargets(RenderTarget mainTarget, boolean highPrecisionDepth, int stormFogResolutionDivisor) {
        this.cloudTarget = new CloudRenderTarget(mainTarget.width, mainTarget.height, Minecraft.ON_OSX,
                highPrecisionDepth);
        this.cloudTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);

        this.cloudTransparencyTarget = new WeightedBlendingTarget(mainTarget.width, mainTarget.height,
                Minecraft.ON_OSX, highPrecisionDepth);

        this.stormFogTarget = new TextureTarget(mainTarget.width / stormFogResolutionDivisor,
                mainTarget.height / stormFogResolutionDivisor, false, Minecraft.ON_OSX);
        this.stormFogTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.stormFogTarget.setFilterMode(GL11.GL_LINEAR);

        this.blurTarget = new TextureTarget(mainTarget.width, mainTarget.height, false, Minecraft.ON_OSX);
        this.blurTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.blurTarget.setFilterMode(GL11.GL_LINEAR);
    }

    private void destroyTargets() {
        if (this.cloudTarget != null)
            this.cloudTarget.destroyBuffers();
        if (this.cloudTransparencyTarget != null)
            this.cloudTransparencyTarget.destroyBuffers();
        if (this.stormFogTarget != null)
            this.stormFogTarget.destroyBuffers();
        if (this.blurTarget != null)
            this.blurTarget.destroyBuffers();

        this.cloudTarget = null;
        this.cloudTransparencyTarget = null;
        this.stormFogTarget = null;
        this.blurTarget = null;
    }

    private void destroyPostChains() {
        this.postChains.forEach(PostChain::close);
        this.postChains.clear();
        this.finalComposite = null;
        this.stormPostProcessing = null;
        this.blurPostProcessing = null;
        this.screenSpaceWorldFog = null;
        this.cloudShadows = null;
    }

    private @Nullable PostChain createPostChain(ResourceManager manager, ResourceLocation loc, RenderTarget target) {
        return this.createPostChain(manager, loc, target, pass -> {
        });
    }

    private @Nullable PostChain createPostChain(ResourceManager manager, ResourceLocation loc, RenderTarget target,
            Consumer<PostPass> passConsumer) {
        try {
            PostChain chain = new PostChain(this.mc.getTextureManager(), manager, target, loc);
            chain.resize(target.width, target.height);
            for (PostPass pass : ((MixinPostChain) chain).simpleclouds$getPostPasses())
                passConsumer.accept(pass);
            this.postChains.add(chain);
            return chain;
        } catch (JsonSyntaxException e) {
            LOGGER.warn("Failed to parse post shader: {}", loc, e);
        } catch (IOException e) {
            LOGGER.warn("Failed to load post shader: {}", loc, e);
        }

        return null;
    }

    private void processChain(@Nullable PostChain chain, float partialTick, Runnable stateSetup,
            Consumer<EffectInstance> effectConsumer) {
        if (chain == null)
            return;

        stateSetup.run();
        for (PostPass pass : ((MixinPostChain) chain).simpleclouds$getPostPasses())
            effectConsumer.accept(pass.getEffect());
        chain.process(partialTick);
        RenderSystem.depthMask(true);
    }
}