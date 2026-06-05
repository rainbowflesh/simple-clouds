package dev.nonamecrackers2.simpleclouds.server.packet.handler;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.config.util.ConfigHelper;
import dev.nonamecrackers2.simpleclouds.common.packet.handler.SimpleCloudsServerPacketHandler;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.ApplyServerConfigEditsPayload;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.NotifyServerConfigEditResultPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SimpleCloudsServerPacketHandlerImpl implements SimpleCloudsServerPacketHandler {
    public static final SimpleCloudsServerPacketHandlerImpl INSTANCE = new SimpleCloudsServerPacketHandlerImpl();

    private SimpleCloudsServerPacketHandlerImpl() {
    }

    @Override
    public void handleApplyServerConfigEditsPayload(ApplyServerConfigEditsPayload packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player))
            return;
        if (!player.hasPermissions(2)) {
            PacketDistributor.sendToPlayer(player,
                    new NotifyServerConfigEditResultPayload(false,
                            "You do not have permission to edit the server config."));
            return;
        }

        Map<String, ModConfigSpec.ConfigValue<?>> values = ConfigHelper.getAllValues(SimpleCloudsConfig.SERVER_SPEC);
        Map<String, ValueSpec> specs = ConfigHelper.getAllSpecs(SimpleCloudsConfig.SERVER_SPEC);
        Map<ModConfigSpec.ConfigValue<?>, Object> parsedValues = new LinkedHashMap<>();

        try {
            for (var entry : packet.changedValues().entrySet()) {
                ModConfigSpec.ConfigValue<?> value = values.get(entry.getKey());
                ValueSpec spec = specs.get(entry.getKey());
                if (value == null || spec == null)
                    throw new IllegalArgumentException("Unknown server config option: " + entry.getKey());
                Object parsed = parseValue(value.get(), entry.getValue(), entry.getKey());
                if (!spec.test(parsed))
                    throw new IllegalArgumentException("Invalid value for server config option: " + entry.getKey());
                parsedValues.put(value, parsed);
            }

            for (var entry : parsedValues.entrySet())
                setValue(entry.getKey(), entry.getValue());

            SimpleCloudsConfig.SERVER_SPEC.save();
            PacketDistributor.sendToPlayer(player,
                    new NotifyServerConfigEditResultPayload(true, "Saved Simple Clouds server config."));
        } catch (IllegalArgumentException e) {
            PacketDistributor.sendToPlayer(player,
                    new NotifyServerConfigEditResultPayload(false, e.getMessage()));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void setValue(ModConfigSpec.ConfigValue<?> value, Object parsed) {
        ((ModConfigSpec.ConfigValue) value).set(parsed);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object parseValue(Object currentValue, String rawValue, String path) {
        String raw = rawValue.trim();
        try {
            if (currentValue instanceof Boolean)
                return Boolean.parseBoolean(raw);
            if (currentValue instanceof Integer)
                return Integer.parseInt(raw);
            if (currentValue instanceof Long)
                return Long.parseLong(raw);
            if (currentValue instanceof Double)
                return Double.parseDouble(raw);
            if (currentValue instanceof Float)
                return Float.parseFloat(raw);
            if (currentValue instanceof Enum<?> enumValue)
                return Enum.valueOf((Class<? extends Enum>) enumValue.getDeclaringClass(),
                        raw.toUpperCase(Locale.ROOT));
            if (currentValue instanceof List<?>) {
                if (raw.isBlank())
                    return List.of();
                return Arrays.stream(raw.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
            }
            return raw;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not parse a value for server config option: " + path);
        }
    }
}