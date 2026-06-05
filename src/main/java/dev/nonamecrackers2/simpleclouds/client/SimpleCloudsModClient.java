package dev.nonamecrackers2.simpleclouds.client;

import dev.nonamecrackers2.simpleclouds.client.config.SimpleCloudsClientConfigListeners;
import dev.nonamecrackers2.simpleclouds.client.event.SimpleCloudsClientEvents;
import dev.nonamecrackers2.simpleclouds.client.gui.SimpleCloudsConfigScreen;
import dev.nonamecrackers2.simpleclouds.client.keybind.SimpleCloudsKeybinds;
import dev.nonamecrackers2.simpleclouds.client.packet.handler.SimpleCloudsClientPacketHandlerImpl;
import dev.nonamecrackers2.simpleclouds.common.packet.SimpleCloudsPayloadRegistrar;
import dev.nonamecrackers2.simpleclouds.common.packet.handler.EmptySimpleCloudsServerPacketHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public class SimpleCloudsModClient {
	public static void init(IEventBus modBus, IEventBus forgeBus) {
		modBus.addListener(SimpleCloudsClientEvents::registerReloadListeners);
		modBus.addListener(SimpleCloudsKeybinds::registerKeyMappings);
		modBus.addListener(SimpleCloudsClientEvents::registerOverlays);
		modBus.addListener(SimpleCloudsModClient::registerPayloads);
	}

	public static void registerConfigListeners() {
		SimpleCloudsClientConfigListeners.registerListener();
	}

	public static void registerConfigScreen(ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(IConfigScreenFactory) (modContainer, modListScreen) -> new SimpleCloudsConfigScreen(modListScreen));
	}

	public static void registerPayloads(RegisterPayloadHandlersEvent event) {
		SimpleCloudsPayloadRegistrar.register(event, SimpleCloudsClientPacketHandlerImpl.INSTANCE,
				EmptySimpleCloudsServerPacketHandler.INSTANCE);
	}
}
