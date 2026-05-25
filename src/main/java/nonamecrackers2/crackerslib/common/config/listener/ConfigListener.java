package dev.nonamecrackers2.simpleclouds.common.config.listener;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import com.google.common.collect.ImmutableList;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigListener {
	private final ModConfig.Type type;
	private final String modid;
	private final List<OptionListener<?>> values;

	private ConfigListener(ModConfig.Type type, String modid, List<OptionListener<?>> values) {
		this.type = type;
		this.modid = modid;
		this.values = values;
	}

	private void onConfigEvent(ModConfigEvent event) {
		if (!event.getConfig().getModId().equals(this.modid) || event.getConfig().getType() != this.type)
			return;
		if (event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading)
			this.poll();
	}

	public void poll() {
		for (OptionListener<?> value : this.values)
			value.poll();
	}

	public static Builder builder(ModConfig.Type type, String modid) {
		return new Builder(type, modid);
	}

	private static final class OptionListener<T> {
		private final ModConfigSpec.ConfigValue<T> value;
		private final BiConsumer<T, T> onChanged;
		private T cachedValue;
		private boolean initialized;

		private OptionListener(ModConfigSpec.ConfigValue<T> value, BiConsumer<T, T> onChanged) {
			this.value = value;
			this.onChanged = onChanged;
		}

		private void poll() {
			T newValue = this.value.get();
			if (!this.initialized) {
				this.cachedValue = newValue;
				this.initialized = true;
				return;
			}

			if (!Objects.equals(this.cachedValue, newValue)) {
				T oldValue = this.cachedValue;
				this.cachedValue = newValue;
				this.onChanged.accept(oldValue, newValue);
			}
		}
	}

	public static class Builder {
		private final ModConfig.Type type;
		private final String modid;
		private final ImmutableList.Builder<OptionListener<?>> values = ImmutableList.builder();

		private Builder(ModConfig.Type type, String modid) {
			this.type = type;
			this.modid = modid;
		}

		public <T> Builder addListener(ModConfigSpec.ConfigValue<T> value, BiConsumer<T, T> onChanged) {
			this.values.add(new OptionListener<>(value, onChanged));
			return this;
		}

		public ConfigListener build() {
			return new ConfigListener(this.type, this.modid, this.values.build());
		}

		public ConfigListener buildAndRegister() {
			ConfigListener listener = this.build();
			IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();
			modBus.addListener(listener::onConfigEvent);
			return listener;
		}
	}
}