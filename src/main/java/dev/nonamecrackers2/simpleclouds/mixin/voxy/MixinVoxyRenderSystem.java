// dev.nonamecrackers2.simpleclouds.mixin.voxy.MixinVoxyRenderSystem
package dev.nonamecrackers2.simpleclouds.mixin.voxy;

import dev.nonamecrackers2.simpleclouds.client.voxy.VoxyDepthCapture;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.cortex.voxy.client.core.rendering.Viewport;

@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
public class MixinVoxyRenderSystem {

    @Inject(method = "renderOpaque", at = @At("HEAD"))
    private void onRenderOpaqueHead(Viewport viewport, CallbackInfo ci) {
        if (viewport == null)
            return;
        // Capture the FBO active at HEAD — this is what Voxy renders "into"
        // (or rather, what Iris has set up as the current draw target)
        VoxyDepthCapture.capturedFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        VoxyDepthCapture.irisWasActive = isIrisShaderActive();
    }

    @Inject(method = "renderOpaque", at = @At("TAIL"))
    private void onRenderOpaqueTail(Viewport viewport, CallbackInfo ci) {
        if (viewport == null)
            return;
        VoxyDepthCapture.captureFrame = Minecraft.getInstance().getFrameTimeNs();
    }

    private static boolean isIrisShaderActive() {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
                    .getMethod("getInstance").invoke(null);
            return (boolean) api.getClass()
                    .getMethod("isShaderPackInUse").invoke(api);
        } catch (Exception e) {
            return false;
        }
    }
}
