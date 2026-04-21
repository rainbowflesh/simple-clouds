package dev.nonamecrackers2.simpleclouds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nonamecrackers2.simpleclouds.client.compat.SimpleCloudsCompatHelper;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

@Mixin(value = Options.class, priority = 1020)
public class MixinOptions {
    @Inject(method = "getCloudsType", at = @At("HEAD"), cancellable = true)
    private void simpleclouds$forceDisableShaderClouds_getCloudsType(CallbackInfoReturnable<CloudStatus> ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && SimpleCloudsConfig.CLIENT.renderClouds.get()
                && SimpleCloudsCompatHelper.isIrisShaderPackInUse()
                && SimpleCloudsRenderer.canRenderInDimension(mc.level))
            ci.setReturnValue(CloudStatus.OFF);
    }
}