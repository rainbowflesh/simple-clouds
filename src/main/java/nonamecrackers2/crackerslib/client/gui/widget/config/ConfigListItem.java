package dev.nonamecrackers2.simpleclouds.client.gui.widget.config;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public interface ConfigListItem {
	static Component shortenText(Component name, int allowedWidth) {
		Minecraft mc = Minecraft.getInstance();
		String text = name.getString();
		if (mc == null || mc.font.width(text) <= allowedWidth)
			return name;
		String shortened = mc.font.plainSubstrByWidth(text, Math.max(0, allowedWidth - mc.font.width("...")));
		return Component.literal(shortened + "...");
	}
}