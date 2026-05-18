package dev.nonamecrackers2.simpleclouds.client.dh.event;

import org.joml.Matrix4f;

import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

import dev.nonamecrackers2.simpleclouds.client.dh.SimpleCloudsDhCompatHandler;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.pipeline.CloudsRenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class SimpleCloudsBeforeDhRenderHandler extends DhApiBeforeApplyShaderRenderEvent {
	@Override
	public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
		SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getInstance();
		CloudsRenderPipeline pipeline = renderer.getRenderPipeline();
		Minecraft mc = Minecraft.getInstance();
		Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

		SimpleCloudsDhCompatHandler._markPassComplete(false);

		DhApiRenderParam params = event.value;
		Matrix4f mcProjMat = SimpleCloudsDhCompatHandler.dhMat4ToMc(params.mcProjectionMatrix);
		Matrix4f mcModelView = SimpleCloudsDhCompatHandler.dhMat4ToMc(params.mcModelViewMatrix);
		Matrix4f dhProjMat = SimpleCloudsDhCompatHandler.dhMat4ToMc(params.dhProjectionMatrix);
		Matrix4f dhModelView = SimpleCloudsDhCompatHandler.dhMat4ToMc(params.dhModelViewMatrix);

		SimpleCloudsDhCompatHandler._updateCachedDhState(mcProjMat, mcModelView, dhProjMat, dhModelView);

		int fbo = SimpleCloudsDhCompatHandler._getDhFramebufferId();

		if (SimpleCloudsRenderer.canRenderInDimension(mc.level))
			pipeline.beforeDistantHorizonsApplyShader(mc, renderer, dhModelView, dhProjMat, params.partialTicks,
					camPos.x,
					camPos.y, camPos.z, renderer.getCullFrustum(), fbo);
	}
}
