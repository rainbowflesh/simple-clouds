package dev.nonamecrackers2.simpleclouds.client.util;

import net.minecraft.Util;

public final class GUIUtils {
	private GUIUtils() {
	}

	public static void openLink(String url) {
		Util.getPlatform().openUri(url);
	}
}