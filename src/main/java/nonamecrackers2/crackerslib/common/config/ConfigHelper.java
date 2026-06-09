package dev.nonamecrackers2.simpleclouds.common.config.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.RestartType;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;

public abstract class ConfigHelper {
	public static final Splitter DOT_SPLITTER = Splitter.on('.');
	public static final Joiner DOT_JOINER = Joiner.on('.');
	protected final ModConfigSpec.Builder builder;
	protected final String modid;

	protected ConfigHelper(ModConfigSpec.Builder builder, String modid) {
		this.builder = builder;
		this.modid = modid;
	}

	protected <T> ModConfigSpec.ConfigValue<T> createValue(T value, String name, RestartType restartType,
			String description) {
		return this.defaultProperties(name, description, restartType, value).define(name, value);
	}

	protected ModConfigSpec.ConfigValue<Double> createRangedDoubleValue(double value, double min, double max,
			String name, RestartType restartType, String description) {
		// defineInRange already documents the default and range in its own comment, so
		// don't pass the default here too - it would otherwise be listed twice.
		return this.defaultProperties(name, description, restartType, null).defineInRange(name, value, min, max);
	}

	protected ModConfigSpec.ConfigValue<Integer> createRangedIntValue(int value, int min, int max, String name,
			RestartType restartType, String description) {
		return this.defaultProperties(name, description, restartType, null).defineInRange(name, value, min, max);
	}

	protected ModConfigSpec.ConfigValue<Long> createRangedLongValue(long value, long min, long max, String name,
			RestartType restartType, String description) {
		return this.defaultProperties(name, description, restartType, null).defineInRange(name, value, min, max);
	}

	protected <T extends Enum<T>> ModConfigSpec.ConfigValue<T> createEnumValue(T value, String name,
			RestartType restartType, String description, @SuppressWarnings("unchecked") T... valid) {
		return this.defaultProperties(name, description, restartType, value).defineEnum(name, value, valid);
	}

	protected <T extends Enum<T>> ModConfigSpec.ConfigValue<T> createEnumValue(T value, String name,
			RestartType restartType, String description, Collection<T> valid) {
		return this.defaultProperties(name, description, restartType, value).defineEnum(name, value, valid);
	}

	@SuppressWarnings("unchecked")
	protected <T extends Enum<T>> ModConfigSpec.ConfigValue<T> createEnumValue(T value, String name,
			RestartType restartType, String description, Predicate<T> validator) {
		return this.defaultProperties(name, description, restartType, value).defineEnum(name, value,
				obj -> value.getDeclaringClass().isAssignableFrom(obj.getClass()) && validator.test((T) obj));
	}

	protected <T extends Enum<T>> ModConfigSpec.ConfigValue<T> createEnumValue(T value, String name,
			RestartType restartType, String description) {
		return this.defaultProperties(name, description, restartType, value).defineEnum(name, value);
	}

	@SuppressWarnings("unchecked")
	protected <T> ModConfigSpec.ConfigValue<List<? extends T>> createListValue(Class<T> valueClass,
			Supplier<List<? extends T>> value, Predicate<T> validator, String name, RestartType restartType,
			String description) {
		return this.defaultProperties(name, description, restartType, null).defineListAllowEmpty(split(name), value,
				obj -> valueClass.isAssignableFrom(obj.getClass()) && validator.test((T) obj));
	}

	protected <T> ModConfigSpec.ConfigValue<List<? extends T>> createListValue(Class<T> valueClass,
			Supplier<List<? extends T>> value, Predicate<T> validator, String name, RestartType restartType,
			String description, String valueDescription) {
		return this.createListValue(valueClass, value, validator, name, restartType,
				description + ".\nAllowed values: " + valueDescription);
	}

	protected ModConfigSpec.Builder defaultProperties(String name, String desc, RestartType restartType,
			@Nullable Object defaultValue) {
		if (restartType != RestartType.NONE) {
			this.builder.worldRestart();
			this.builder.comment(desc + ".", "Requires restart.");
		} else {
			this.builder.comment(desc + ".");
		}
		if (defaultValue != null)
			this.builder.comment("Default: " + defaultValue);
		this.builder.translation("gui." + this.modid + ".config." + name + ".description");
		return this.builder;
	}

	private static List<String> split(String path) {
		return Lists.newArrayList(DOT_SPLITTER.split(path));
	}

	public static Map<String, ModConfigSpec.ConfigValue<?>> getAllValues(ModConfigSpec spec) {
		return searchForValues("", spec.getValues().valueMap()).entrySet().stream().collect(Collectors.toMap(
				Map.Entry::getKey, entry -> (ModConfigSpec.ConfigValue<?>) entry.getValue()));
	}

	public static Map<String, ValueSpec> getAllSpecs(ModConfigSpec spec) {
		return searchForValues("", spec.getSpec().valueMap()).entrySet().stream().collect(Collectors.toMap(
				Map.Entry::getKey, entry -> (ValueSpec) entry.getValue()));
	}

	private static Map<String, Object> searchForValues(String previousPath, Map<String, Object> values) {
		Map<String, Object> found = Maps.newLinkedHashMap();
		for (var entry : values.entrySet()) {
			String path = previousPath.isEmpty() ? entry.getKey() : previousPath + "." + entry.getKey();
			Object value = entry.getValue();
			if (value instanceof UnmodifiableConfig config) {
				found.putAll(searchForValues(path, config.valueMap()));
			} else {
				found.put(path, value);
			}
		}
		return found;
	}
}