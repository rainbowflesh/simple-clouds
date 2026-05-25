package dev.nonamecrackers2.simpleclouds.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

public abstract class Screen3D extends Screen {
	protected Screen3D(Component title, float unusedCameraSensitivity, float unusedFarPlane) {
		super(title);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		this.render3D(guiGraphics.pose(), buffers, mouseX, mouseY, partialTick);
		buffers.endBatch();
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	protected abstract void render3D(PoseStack stack, MultiBufferSource buffers, int mouseX, int mouseY,
			float partialTick);
}