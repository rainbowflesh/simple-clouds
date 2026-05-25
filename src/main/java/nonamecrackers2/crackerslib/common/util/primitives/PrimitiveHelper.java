package dev.nonamecrackers2.simpleclouds.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec2;

public final class PrimitiveHelper {
	private PrimitiveHelper() {
	}

	public static CompoundTag vec2ToTag(Vec2 vec) {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("x", vec.x);
		tag.putFloat("y", vec.y);
		return tag;
	}

	public static Vec2 vec2FromTag(CompoundTag tag) {
		return new Vec2(tag.getFloat("x"), tag.getFloat("y"));
	}
}