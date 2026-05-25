package dev.nonamecrackers2.simpleclouds.common.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class PacketHelper {
	private PacketHelper() {
	}

	public static <T extends Enum<T>> StreamCodec<ByteBuf, T> enumStreamCodec(Class<T> enumClass) {
		T[] values = enumClass.getEnumConstants();
		return StreamCodec.of((buffer, value) -> buffer.writeInt(value.ordinal()), buffer -> {
			int ordinal = buffer.readInt();
			if (ordinal < 0 || ordinal >= values.length)
				return values[0];
			return values[ordinal];
		});
	}
}