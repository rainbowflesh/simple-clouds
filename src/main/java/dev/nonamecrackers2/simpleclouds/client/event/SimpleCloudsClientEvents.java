package dev.nonamecrackers2.simpleclouds.client.event;

import java.awt.Color;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.client.cloud.ClientSideCloudTypeManager;
import dev.nonamecrackers2.simpleclouds.client.cloud.spawning.ClientSideCloudSpawningManager;
import dev.nonamecrackers2.simpleclouds.client.config.SimpleCloudsClientConfigListeners;
import dev.nonamecrackers2.simpleclouds.client.command.ClientCloudCommandHelper;
import dev.nonamecrackers2.simpleclouds.client.command.profiling.ProfilingCommands;
import dev.nonamecrackers2.simpleclouds.client.compat.SimpleCloudsCompatHelper;
import dev.nonamecrackers2.simpleclouds.client.gui.CloudPreviewerScreen;
import dev.nonamecrackers2.simpleclouds.client.mesh.LevelOfDetailOptions;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.GenerationInterval;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.MultiRegionCloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.SingleRegionCloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsDebugOverlayRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.WorldEffects;
import dev.nonamecrackers2.simpleclouds.client.renderer.settings.CloudsRendererSettings;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import dev.nonamecrackers2.simpleclouds.client.world.ClientCloudManager;
import dev.nonamecrackers2.simpleclouds.client.world.FogRenderMode;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudTypeDataManager;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.level.LevelEvent;

public class SimpleCloudsClientEvents {
	public static void registerOverlays(RegisterGuiLayersEvent event) {
		event.registerBelow(VanillaGuiLayers.DEBUG_OVERLAY, SimpleCloudsMod.id("simple_clouds_debug"),
				SimpleCloudsDebugOverlayRenderer::render);
	}

	public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
		CloudTypeDataManager manager = ClientSideCloudTypeManager.getInstance().getClientSideDataManager();
		event.registerReloadListener(manager);
		ClientSideCloudSpawningManager.optionalInitializeOnClient(manager);
		event.registerReloadListener(ClientSideCloudSpawningManager.getClientInstance());
		SimpleCloudsRenderer.initialize(CloudsRendererSettings.DEFAULT);
		event.registerReloadListener((ResourceManagerReloadListener) (m -> {
			ComputeShader.destroyCompiledShaders();
		}));
		event.registerReloadListener(
				SimpleCloudsCompatHelper.getRendererReloadListener(SimpleCloudsRenderer.getInstance()));
		CloudPreviewerScreen.addCloudMeshListener(event);
	}

	@SubscribeEvent
	public static void registerClientCommands(RegisterClientCommandsEvent event) {
		ClientCloudCommandHelper.register(event.getDispatcher());
		ProfilingCommands.register(event.getDispatcher());
	}

	@SubscribeEvent
	public static void onLevelLoad(LevelEvent.Load event) {
		if (event.getLevel().isClientSide())
			SimpleCloudsRenderer.getInstance().getWorldEffectsManager().reset();
	}

	@SubscribeEvent
	public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		CloudManager.get(event.getPlayer().level()).onPlayerJoin(event.getPlayer());
		SimpleCloudsRenderer.getInstance().requestReload();
	}

	@SubscribeEvent
	public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientSideCloudTypeManager.getInstance().clearSynced();
		SimpleCloudsRenderer.getInstance().getWorldEffectsManager().reset();
	}

	@SubscribeEvent
	public static void modifyFog(ViewportEvent.RenderFog event) {
		if (event.getMode() == FogMode.FOG_TERRAIN
				&& Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() == FogType.NONE) {
			if (SimpleCloudsConfig.CLIENT.fogMode.get() == FogRenderMode.OFF) {
				FogRenderer.setupNoFog();
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getInstance();
			WorldEffects effects = renderer.getWorldEffectsManager();
			float partialTick = (float) event.getPartialTick();
			float storminess = Mth.sqrt(effects.getDarkenFactor(partialTick, 2.0F));
			float insideCloudFactor = effects.getInsideCloudFactor(mc.gameRenderer.getMainCamera().getPosition().x,
					mc.gameRenderer.getMainCamera().getPosition().y, mc.gameRenderer.getMainCamera().getPosition().z);
			RenderSystem.setShaderFogStart(RenderSystem.getShaderFogStart() * storminess);
			if (insideCloudFactor > 0.0F) {
				RenderSystem.setShaderFogStart(Mth.lerp(insideCloudFactor, RenderSystem.getShaderFogStart(), 0.0F));
				RenderSystem.setShaderFogEnd(Mth.lerp(insideCloudFactor, RenderSystem.getShaderFogEnd(),
						WorldEffects.getInsideCloudMaxVisibility()));
			}
		}
	}

	@SubscribeEvent
	public static void modifyFogColor(ViewportEvent.ComputeFogColor event) {
		if (SimpleCloudsConfig.CLIENT.fogMode.get() != FogRenderMode.OFF
				&& Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() == FogType.NONE) {
			Minecraft mc = Minecraft.getInstance();
			SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getInstance();
			WorldEffects effects = renderer.getWorldEffectsManager();
			float partialTick = (float) event.getPartialTick();
			Color finalCol = effects.calculateFogColor(event.getRed(), event.getGreen(), event.getBlue(), partialTick);
			float red = (float) finalCol.getRed() / 255.0F;
			float green = (float) finalCol.getGreen() / 255.0F;
			float blue = (float) finalCol.getBlue() / 255.0F;
			float insideCloudFactor = effects.getInsideCloudFactor(mc.gameRenderer.getMainCamera().getPosition().x,
					mc.gameRenderer.getMainCamera().getPosition().y, mc.gameRenderer.getMainCamera().getPosition().z);
			if (insideCloudFactor > 0.0F) {
				float[] cloudColor = renderer.getCloudColor(partialTick);
				float blend = insideCloudFactor * WorldEffects.getInsideCloudFogColorBlend();
				red = Mth.lerp(blend, red, cloudColor[0]);
				green = Mth.lerp(blend, green, cloudColor[1]);
				blue = Mth.lerp(blend, blue, cloudColor[2]);
			}
			event.setRed(red);
			event.setGreen(green);
			event.setBlue(blue);
		}
	}

	@SubscribeEvent
	public static void onRenderDebugOverlay(CustomizeGuiOverlayEvent.DebugText event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getDebugOverlay().showDebugScreen()) {
			SimpleCloudsRenderer renderer = SimpleCloudsRenderer.getInstance();
			List<String> text = event.getRight();
			text.add("");
			text.add(ChatFormatting.GREEN + SimpleCloudsMod.MODID + ": " + SimpleCloudsMod.getModVersion());
			text.add(renderer.getClientCloudManagerString());
			if (SimpleCloudsRenderer.canRenderInDimension(mc.level)) {
				CloudMeshGenerator generator = renderer.getMeshGenerator();

				var meshGenResult = generator.getMeshGenStatus();
				CloudMeshGenerator.MeshGenStatus opaqueStatus = meshGenResult.getLeft();
				CloudMeshGenerator.MeshGenStatus transparentStatus = meshGenResult.getRight();
				text.add((opaqueStatus.isErroneous() ? ChatFormatting.RED : "") + "Mesh status opaque: "
						+ opaqueStatus.getName());
				text.add((transparentStatus.isErroneous() ? ChatFormatting.RED : "") + "Mesh status transparent: "
						+ transparentStatus.getName());

				String opaqueGeomInfo = humanReadableByteCountSI(generator.getOpaqueBufferBytesUsed()) + "/"
						+ humanReadableByteCountSI(generator.getOpaqueBufferSize());
				String transparentGeomInfo = humanReadableByteCountSI(generator.getTransparentBufferBytesUsed()) + "/"
						+ humanReadableByteCountSI(generator.getTransparentBufferSize());
				text.add("O: " + opaqueGeomInfo + " | T: " + transparentGeomInfo);

				int interval = generator.getMeshGenInterval();
				text.add("Mesh gen frames: " + interval + "; Effective FPS: " + mc.getFps() / interval);

				text.add("Frustum culling: " + (SimpleCloudsConfig.CLIENT.frustumCulling.get() ? "ON" : "OFF"));

				boolean flag = ClientCloudManager.isAvailableServerSide();
				text.add("Server-side: " + (flag ? ChatFormatting.GREEN : ChatFormatting.RED) + flag);

				CloudMode mode = renderer.getSettings().getCurrentCloudMode();
				text.add("Cloud mode: " + mode);

				if (generator instanceof SingleRegionCloudMeshGenerator singleGenerator) {
					text.add("Fade start: " + singleGenerator.getFadeStart() + "; Fade end: "
							+ singleGenerator.getFadeEnd());
					if (singleGenerator.getCloudType() instanceof CloudType type)
						text.add("Cloud type: " + type.id());
				} else if (generator instanceof MultiRegionCloudMeshGenerator multiRegion) {
					text.add("Cloud types: " + ClientSideCloudTypeManager.getInstance().getCloudTypes().size());
					int visibleFormations = multiRegion.getVisibleCloudFormationCount();
					int totalFormations = multiRegion.getTotalCloudFormationCount();
					String formationText = "Cloud formations: " + visibleFormations + "/" + totalFormations;
					text.add(formationText);
				}

				if (mc.level != null) {
					CloudManager<ClientLevel> manager = CloudManager.get(mc.level);

					text.add("Speed: " + round(manager.getCloudSpeed()) + "; Height: " + manager.getCloudHeight());
					text.add("Scroll XYZ: " + round(manager.getScrollX()) + " / " + round(manager.getScrollY()) + " / "
							+ round(manager.getScrollZ()));

					WorldEffects effects = renderer.getWorldEffectsManager();
					text.add(buildCurrentCloudDebugLine(renderer, mc));
					text.add(buildAtmosphericCloudDebugLine(renderer, mc));

					String vanillaWeatherOverrideAppend = manager.shouldUseVanillaWeather()
							? " (Vanilla Weather Enabled)"
							: "";
					text.add(buildWeatherStatusDebugLine(manager, mc) + vanillaWeatherOverrideAppend);
					text.add("Storminess: " + round(effects.getStorminessAtCamera()) + vanillaWeatherOverrideAppend);
				}
			} else {
				text.add(ChatFormatting.RED + "Disabled in this dimension");
			}
		}
	}

	// https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java#:~:text=public%20static%20String%20humanReadableByteCountSI,1000.0%2C%20ci.current())%3B%0A%7D
	private static String humanReadableByteCountSI(long bytes) {
		if (-1000 < bytes && bytes < 1000)
			return bytes + " B";
		CharacterIterator ci = new StringCharacterIterator("kMGTPE");
		while (bytes <= -999_950 || bytes >= 999_950) {
			bytes /= 1000;
			ci.next();
		}
		return String.format("%.1f %cB", bytes / 1000.0, ci.current());
	}

	private static float round(float val) {
		return (float) Math.round(val * 100.0F) / 100.0F;
	}

	private static @Nullable Entity getDebugEntity(Minecraft mc) {
		return Objects.requireNonNullElse(mc.player, mc.getCameraEntity());
	}

	private static String buildCurrentCloudDebugLine(SimpleCloudsRenderer renderer, Minecraft mc) {
		Entity entity = getDebugEntity(mc);
		if (entity == null)
			return "Current cloud: unavailable";

		CloudType type = renderer.getCurrentCloudType(entity.getX(), entity.getZ());
		if (type == null)
			return "Current cloud: unavailable";
		return "Current cloud: " + type.id();
	}

	private static String buildAtmosphericCloudDebugLine(SimpleCloudsRenderer renderer, Minecraft mc) {
		if (!SimpleCloudsConfig.CLIENT.atmosphericClouds.get())
			return "Atmospheric clouds: disabled";

		Entity entity = getDebugEntity(mc);
		if (entity == null)
			return "Atmospheric clouds: unavailable";

		CloudType type = renderer.getCurrentCloudType(entity.getX(), entity.getZ());
		if (type != null && type != SimpleCloudsConstants.EMPTY && type.suppressesAtmosphericClouds())
			return "Atmospheric clouds: suppressed by " + type.id();
		return "Atmospheric clouds: active";
	}

	private static String buildWeatherStatusDebugLine(CloudManager<ClientLevel> manager, Minecraft mc) {
		var entity = Objects.requireNonNullElse(mc.player, mc.getCameraEntity());
		if (entity == null)
			return "Cloud weather: unavailable";

		float x = (float) entity.getX();
		float y = (float) entity.getY();
		float z = (float) entity.getZ();
		var thunder = manager.getThunderCloudTypeAtWorldPos(x, z);
		float thunderLevel = manager.getThunderLevel(x, y, z);
		if (thunderLevel > 0.0F && thunder.getLeft() != SimpleCloudsConstants.EMPTY)
			return "Cloud weather: thundering from " + thunder.getLeft().id() + " (fade "
					+ round(thunder.getRight()) + ")";

		var rain = manager.getRainCloudTypeAtWorldPos(x, z);
		float rainLevel = manager.getRainLevel(x, y, z);
		if (rainLevel > 0.0F && rain.getLeft() != SimpleCloudsConstants.EMPTY)
			return "Cloud weather: raining from " + rain.getLeft().id() + " (fade " + round(rain.getRight())
					+ ")";

		return "Cloud weather: none";
	}
}
