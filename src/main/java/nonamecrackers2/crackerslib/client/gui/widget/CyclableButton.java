package dev.nonamecrackers2.simpleclouds.client.gui.widget;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class CyclableButton<T> extends AbstractButton {
	private final List<T> values;
	private int index;
	private Consumer<T> responder = value -> {
	};
	private Function<T, Component> messageFactory = value -> Component.literal(String.valueOf(value));

	public CyclableButton(int x, int y, int width, List<T> values, T initialValue) {
		super(x, y, width, 20, CommonComponents.EMPTY);
		this.values = List.copyOf(values);
		this.index = Math.max(0, this.values.indexOf(initialValue));
		this.updateMessage();
	}

	public void setResponder(Consumer<T> responder) {
		this.responder = Objects.requireNonNull(responder);
	}

	public void setMessageFactory(Function<T, Component> messageFactory) {
		this.messageFactory = Objects.requireNonNull(messageFactory);
		this.updateMessage();
	}

	public T getValue() {
		return this.values.get(this.index);
	}

	public void setValue(T value) {
		int found = this.values.indexOf(value);
		if (found >= 0) {
			this.index = found;
			this.updateMessage();
		}
	}

	@Override
	public void onPress() {
		if (this.values.isEmpty())
			return;
		this.index = (this.index + 1) % this.values.size();
		this.updateMessage();
		this.responder.accept(this.getValue());
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		this.defaultButtonNarrationText(narrationElementOutput);
	}

	private void updateMessage() {
		this.setMessage(this.messageFactory.apply(this.getValue()));
	}
}