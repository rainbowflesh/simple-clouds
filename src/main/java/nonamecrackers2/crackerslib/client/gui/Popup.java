package dev.nonamecrackers2.simpleclouds.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class Popup extends Screen {
	private static final int PADDING = 12;
	private final @Nullable Screen parent;
	private final int textWidth;
	private final Component message;
	private final PopupContent content;
	private boolean alignLeft;

	private Popup(@Nullable Screen parent, int textWidth, Component message, PopupContent content) {
		super(CommonComponents.EMPTY);
		this.parent = parent;
		this.textWidth = textWidth;
		this.message = message;
		this.content = content;
	}

	public Popup alignLeft() {
		this.alignLeft = true;
		return this;
	}

	public @Nullable Screen getParent() {
		return this.parent;
	}

	@Override
	protected void init() {
		this.content.init(this);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		if (this.parent != null)
			this.parent.render(guiGraphics, -1, -1, partialTick);
		guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);
		int boxWidth = Math.min(this.width - 40, Math.max(this.textWidth + PADDING * 2, 220));
		int boxHeight = Math.max(110, this.content.getHeight(this));
		int left = (this.width - boxWidth) / 2;
		int top = (this.height - boxHeight) / 2;
		guiGraphics.fill(left, top, left + boxWidth, top + boxHeight, 0xE0202020);
		guiGraphics.renderOutline(left, top, boxWidth, boxHeight, 0xFFFFFFFF);

		List<FormattedCharSequence> lines = this.font.split(this.message, boxWidth - PADDING * 2);
		int textX = this.alignLeft ? left + PADDING : left + boxWidth / 2;
		int textY = top + PADDING;
		for (FormattedCharSequence line : lines) {
			if (this.alignLeft)
				guiGraphics.drawString(this.font, line, textX, textY, 0xFFFFFFFF);
			else
				guiGraphics.drawCenteredString(this.font, line, textX, textY, 0xFFFFFFFF);
			textY += this.font.lineHeight + 2;
		}

		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(this.parent);
	}

	public static void clearQueue() {
	}

	public static Popup createInfoPopup(@Nullable Screen parent, int width, Component message) {
		return createInfoPopup(parent, width, message, () -> {
		});
	}

	public static Popup createInfoPopup(@Nullable Screen parent, int width, Component message, Runnable onContinue) {
		Popup popup = new Popup(parent, width, message, screen -> {
			Button button = Button.builder(CommonComponents.GUI_CONTINUE, b -> {
				onContinue.run();
				screen.onClose();
			}).size(100, 20).build();
			button.setPosition((screen.width - button.getWidth()) / 2, screen.height / 2 + 30);
			screen.addRenderableWidget(button);
		});
		Minecraft.getInstance().setScreen(popup);
		return popup;
	}

	public static Popup createYesNoPopup(@Nullable Screen parent, Runnable onYes, int width, Component message) {
		return createYesNoPopup(parent, onYes, () -> {
		}, width, message);
	}

	public static Popup createYesNoPopup(@Nullable Screen parent, Runnable onYes, Runnable onNo, int width,
			Component message) {
		Popup popup = new Popup(parent, width, message, screen -> {
			GridLayout layout = new GridLayout().columnSpacing(8);
			GridLayout.RowHelper row = layout.createRowHelper(2);
			row.addChild(Button.builder(CommonComponents.GUI_YES, b -> {
				onYes.run();
				screen.onClose();
			}).width(90).build());
			row.addChild(Button.builder(CommonComponents.GUI_NO, b -> {
				onNo.run();
				screen.onClose();
			}).width(90).build());
			layout.arrangeElements();
			FrameLayout.centerInRectangle(layout, 0, screen.height / 2 + 18, screen.width, 30);
			layout.visitWidgets(screen::addRenderableWidget);
		});
		Minecraft.getInstance().setScreen(popup);
		return popup;
	}

	public static Popup createTextFieldPopup(@Nullable Screen parent, Consumer<String> onSubmit, int width,
			Component message) {
		Popup popup = new Popup(parent, width, message, screen -> {
			EditBox box = new EditBox(screen.font, 0, 0, Math.min(width, screen.width - 80), 20,
					CommonComponents.EMPTY);
			box.setPosition((screen.width - box.getWidth()) / 2, screen.height / 2 + 8);
			screen.addRenderableWidget(box);
			screen.setInitialFocus(box);
			screen.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
				onSubmit.accept(box.getValue());
				screen.onClose();
			}).pos((screen.width - 100) / 2, box.getY() + 30).width(100).build());
		});
		Minecraft.getInstance().setScreen(popup);
		return popup;
	}

	public static <T> Popup createOptionListPopup(@Nullable Screen parent,
			Consumer<OptionListBuilder<T>> builderConsumer,
			Consumer<T> onSelected, int width, int height, Component message) {
		OptionListBuilder<T> builder = new OptionListBuilder<>();
		builderConsumer.accept(builder);
		Popup popup = new Popup(parent, width, message, screen -> {
			int buttonWidth = Math.min(width, screen.width - 80);
			int y = Math.max(screen.height / 2 - height / 2 + 10, 60);
			for (Option<T> option : builder.options) {
				Button button = Button.builder(option.label, b -> {
					onSelected.accept(option.value);
					screen.onClose();
				}).size(buttonWidth, 20).build();
				button.setPosition((screen.width - buttonWidth) / 2, y);
				screen.addRenderableWidget(button);
				y += 24;
			}
			screen.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> screen.onClose())
					.pos((screen.width - 100) / 2, Math.min(screen.height - 40, y + 6)).width(100).build());
		});
		Minecraft.getInstance().setScreen(popup);
		return popup;
	}

	private interface PopupContent {
		void init(Popup screen);

		default int getHeight(Popup screen) {
			return Mth.clamp(
					140 + screen.font.split(screen.message, screen.textWidth).size() * (screen.font.lineHeight + 2),
					110, screen.height - 40);
		}
	}

	public static final class OptionListBuilder<T> {
		private final List<Option<T>> options = new ArrayList<>();

		public void addObject(Component label, T value) {
			this.options.add(new Option<>(label, value));
		}
	}

	private record Option<T>(Component label, T value) {
	}
}