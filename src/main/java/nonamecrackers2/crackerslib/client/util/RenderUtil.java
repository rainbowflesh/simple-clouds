package dev.nonamecrackers2.simpleclouds.client.util;

public final class RenderUtil {
	private RenderUtil() {
	}

	public static boolean isMouseInBounds(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}