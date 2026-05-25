package dev.nonamecrackers2.simpleclouds.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import dev.nonamecrackers2.simpleclouds.client.mesh.LevelOfDetailOptions;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.GenerationInterval;
import dev.nonamecrackers2.simpleclouds.client.gui.widget.CyclableButton;
import dev.nonamecrackers2.simpleclouds.client.gui.widget.config.ConfigListItem;
import dev.nonamecrackers2.simpleclouds.client.world.ClientCloudManager;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.config.util.ConfigHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;

public class SimpleCloudsConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 190;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 8;
	private static final int PRESET_BUTTON_WIDTH = (BUTTON_WIDTH - BUTTON_SPACING) / 2;
	private final Screen previous;
	private int presetTitleY;

	public SimpleCloudsConfigScreen(Screen previous) {
		super(Component.literal("Simple Clouds Config"));
		this.previous = previous;
	}

	@Override
	protected void init() {
		this.clearWidgets();
		int left = this.width / 2 - BUTTON_WIDTH / 2;
		int y = this.height / 4 + 24;

		this.addRenderableWidget(Button.builder(Component.literal("Client Config"),
				b -> this.minecraft.setScreen(new SpecScreen(this, Component.literal("Client Config"),
						SimpleCloudsConfig.CLIENT_SPEC, clientConfigFilter())))
				.pos(left, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += BUTTON_HEIGHT + BUTTON_SPACING;

		Button serverButton = Button.builder(Component.literal("Server Config"),
				b -> this.minecraft.setScreen(new SpecScreen(this, Component.literal("Server Config"),
						SimpleCloudsConfig.SERVER_SPEC, path -> true)))
				.pos(left, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
		serverButton.active = canEditServerConfig();
		if (!serverButton.active) {
			serverButton.setTooltip(Tooltip.create(
					Component.literal(
							"Server config can only be edited from the main menu or an integrated singleplayer world.")));
		}
		this.addRenderableWidget(serverButton);
		y += BUTTON_HEIGHT + BUTTON_SPACING;

		this.addRenderableWidget(Button.builder(Component.translatable("gui.simpleclouds.cloud_previewer.button.title"),
				b -> this.minecraft.setScreen(new CloudPreviewerScreen(this))).size(BUTTON_WIDTH, BUTTON_HEIGHT)
				.pos(left, y).build());
		y += BUTTON_HEIGHT + BUTTON_SPACING;

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
				.pos(left, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += BUTTON_HEIGHT + 14;

		this.presetTitleY = y;
		y += this.font.lineHeight + 6;

		int presetIndex = 0;
		for (ClientPreset preset : ClientPreset.values()) {
			int column = presetIndex % 2;
			int row = presetIndex / 2;
			Button button = Button.builder(preset.title(), b -> this.confirmApplyPreset(preset))
					.pos(left + column * (PRESET_BUTTON_WIDTH + BUTTON_SPACING),
							y + row * (BUTTON_HEIGHT + 6))
					.size(PRESET_BUTTON_WIDTH, BUTTON_HEIGHT).build();
			button.setTooltip(Tooltip.create(preset.description()));
			this.addRenderableWidget(button);
			presetIndex++;
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
		guiGraphics.drawCenteredString(this.font,
				Component.literal("Native NeoForge config access for Simple Clouds."), this.width / 2, 30,
				0xFFA0A0A0);
		guiGraphics.drawCenteredString(this.font, Component.literal("Client Presets"), this.width / 2,
				this.presetTitleY,
				0xFFADF7FF);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.previous);
	}

	private static boolean canEditServerConfig() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || mc.hasSingleplayerServer();
	}

	private static Predicate<String> clientConfigFilter() {
		Set<String> hidden = new LinkedHashSet<>();
		hidden.add(pathOf(SimpleCloudsConfig.CLIENT.showCloudPreviewerInfoPopup));
		hidden.add(pathOf(SimpleCloudsConfig.CLIENT.showVivecraftNotice));
		if (ClientCloudManager.isRemoteServerAvailable() || ClientCloudManager.isAvailableServerSide()) {
			hidden.add(pathOf(SimpleCloudsConfig.CLIENT.cloudMode));
			hidden.add(pathOf(SimpleCloudsConfig.CLIENT.singleModeCloudType));
			hidden.add(pathOf(SimpleCloudsConfig.CLIENT.cloudSeed));
			hidden.add(pathOf(SimpleCloudsConfig.CLIENT.useSpecificSeed));
			hidden.add(pathOf(SimpleCloudsConfig.CLIENT.whitelistAsBlacklist));
			hidden.add(pathOf(SimpleCloudsConfig.CLIENT.dimensionWhitelist));
		}
		return path -> !hidden.contains(path);
	}

	private static String pathOf(ModConfigSpec.ConfigValue<?> value) {
		return ConfigHelper.DOT_JOINER.join(value.getPath());
	}

	private void confirmApplyPreset(ClientPreset preset) {
		Popup.createYesNoPopup(this, () -> this.applyPreset(preset), 320,
				preset.title().copy().append("\n\n").append(preset.description()));
	}

	private void applyPreset(ClientPreset preset) {
		preset.apply();
		SimpleCloudsConfig.CLIENT_SPEC.save();
		Popup.createInfoPopup(this, 320,
				Component.literal("Applied preset: ").append(preset.title())
						.append(Component.literal(
								"\n\nSome changes may require a renderer or resource reload to fully take effect.")
								.withStyle(ChatFormatting.GRAY)));
	}

	private enum ClientPreset {
		MEDIUM(Component.translatable("simpleclouds.config.preset.medium"),
				Component.translatable("simpleclouds.config.preset.medium.description")) {
			@Override
			void apply() {
				SimpleCloudsConfig.CLIENT.framesToGenerateMesh.set(10);
				SimpleCloudsConfig.CLIENT.generationInterval.set(GenerationInterval.STATIC);
				SimpleCloudsConfig.CLIENT.levelOfDetail.set(LevelOfDetailOptions.MEDIUM);
				SimpleCloudsConfig.CLIENT.shadowDistance.set(2500);
			}
		},
		LOW(Component.translatable("simpleclouds.config.preset.low"),
				Component.translatable("simpleclouds.config.preset.low.description")) {
			@Override
			void apply() {
				SimpleCloudsConfig.CLIENT.framesToGenerateMesh.set(20);
				SimpleCloudsConfig.CLIENT.generationInterval.set(GenerationInterval.DYNAMIC);
				SimpleCloudsConfig.CLIENT.levelOfDetail.set(LevelOfDetailOptions.LOW);
				SimpleCloudsConfig.CLIENT.renderLodClouds.set(false);
				SimpleCloudsConfig.CLIENT.transparency.set(false);
				SimpleCloudsConfig.CLIENT.atmosphericClouds.set(false);
				SimpleCloudsConfig.CLIENT.shadowDistance.set(2500);
				SimpleCloudsConfig.CLIENT.distantShadows.set(false);
			}
		},
		ULTRA_LOW(Component.translatable("simpleclouds.config.preset.ultra_low"),
				Component.translatable("simpleclouds.config.preset.ultra_low.description")) {
			@Override
			void apply() {
				SimpleCloudsConfig.CLIENT.framesToGenerateMesh.set(20);
				SimpleCloudsConfig.CLIENT.generationInterval.set(GenerationInterval.DYNAMIC);
				SimpleCloudsConfig.CLIENT.levelOfDetail.set(LevelOfDetailOptions.LOW);
				SimpleCloudsConfig.CLIENT.renderLodClouds.set(false);
				SimpleCloudsConfig.CLIENT.transparency.set(false);
				SimpleCloudsConfig.CLIENT.renderStormFog.set(false);
				SimpleCloudsConfig.CLIENT.atmosphericClouds.set(false);
				SimpleCloudsConfig.CLIENT.shadowDistance.set(1000);
				SimpleCloudsConfig.CLIENT.distantShadows.set(false);
			}
		},
		CLASSIC_STYLE(Component.translatable("simpleclouds.config.preset.classic_style"),
				Component.translatable("simpleclouds.config.preset.classic_style.description")) {
			@Override
			void apply() {
				SimpleCloudsConfig.CLIENT.transparency.set(false);
				SimpleCloudsConfig.CLIENT.cubeNormals.set(true);
				SimpleCloudsConfig.CLIENT.shadedClouds.set(false);
				SimpleCloudsConfig.CLIENT.atmosphericClouds.set(false);
			}
		};

		private final Component title;
		private final Component description;

		ClientPreset(Component title, Component description) {
			this.title = title;
			this.description = description;
		}

		Component title() {
			return this.title;
		}

		Component description() {
			return this.description;
		}

		abstract void apply();
	}

	private static final class SpecScreen extends Screen {
		private final Screen previous;
		private final ModConfigSpec spec;
		private final Predicate<String> includePath;
		private final List<ConfigEntry> allConfigEntries = new ArrayList<>();
		private final Set<String> collapsedCategories = new LinkedHashSet<>();
		private @Nullable EntryList list;
		private @Nullable EditBox searchBox;

		private SpecScreen(Screen previous, Component title, ModConfigSpec spec, Predicate<String> includePath) {
			super(title);
			this.previous = previous;
			this.spec = spec;
			this.includePath = includePath;
		}

		@Override
		protected void init() {
			this.clearWidgets();
			this.allConfigEntries.clear();

			this.searchBox = new EditBox(this.font, this.width / 2 - 140, 32, 280, 20,
					Component.literal("Search config"));
			this.searchBox.setHint(Component.literal("Search config"));
			this.searchBox.setResponder(value -> this.rebuildList());
			this.addRenderableWidget(this.searchBox);

			this.list = new EntryList(this.minecraft, this.width, this.height - 72, 32, this.height - 40, this.font);
			Map<String, ModConfigSpec.ConfigValue<?>> values = ConfigHelper.getAllValues(this.spec);
			Map<String, ValueSpec> specs = ConfigHelper.getAllSpecs(this.spec);
			values.entrySet().stream().filter(entry -> this.includePath.test(entry.getKey()))
					.sorted(Comparator.comparing(Map.Entry::getKey))
					.forEach(entry -> this.allConfigEntries.add(
							new ConfigEntry(this.font, entry.getKey(), entry.getValue(), specs.get(entry.getKey()))));
			this.rebuildList();
			this.addRenderableWidget(this.list);

			this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> this.saveAndClose())
					.pos(this.width / 2 - 104, this.height - 28).size(100, 20).build());
			this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
					.pos(this.width / 2 + 4, this.height - 28).size(100, 20).build());
		}

		private void saveAndClose() {
			if (this.list == null)
				return;
			for (ConfigEntry entry : this.allConfigEntries) {
				Optional<Component> error = entry.applyValue();
				if (error.isPresent()) {
					Popup.createInfoPopup(this, 340, error.get()).alignLeft();
					return;
				}
			}
			this.spec.save();
			this.onClose();
		}

		private void rebuildList() {
			if (this.list == null)
				return;
			this.list.clearConfigEntries();
			String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
			boolean forceExpand = !query.isBlank();
			String currentCategory = null;
			for (ConfigEntry entry : this.allConfigEntries) {
				if (!entry.matchesSearch(query))
					continue;
				String category = entry.getCategoryLabel();
				if (!category.equals(currentCategory)) {
					boolean collapsed = !forceExpand && this.collapsedCategories.contains(category);
					this.list.addConfigEntry(new CategoryEntry(this.font, category, collapsed,
							() -> this.toggleCategory(category)));
					currentCategory = category;
				}
				if (!forceExpand && this.collapsedCategories.contains(category))
					continue;
				this.list.addConfigEntry(entry);
			}
		}

		private void toggleCategory(String category) {
			if (!this.collapsedCategories.add(category))
				this.collapsedCategories.remove(category);
			this.rebuildList();
		}

		@Override
		public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
			this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
			super.render(guiGraphics, mouseX, mouseY, partialTick);
			guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
			guiGraphics.drawCenteredString(this.font,
					Component.literal("Search, then edit grouped config sections. Changes write directly to disk."),
					this.width / 2, 22,
					0xFFA0A0A0);
		}

		@Override
		public void onClose() {
			this.minecraft.setScreen(this.previous);
		}
	}

	private static final class EntryList extends ContainerObjectSelectionList<BaseEntry> {
		private EntryList(Minecraft minecraft, int width, int height, int y0, int y1, Font font) {
			super(minecraft, width, height, y0, Math.max(28, font.lineHeight + 12));
		}

		private void addConfigEntry(BaseEntry entry) {
			super.addEntry(entry);
		}

		private void clearConfigEntries() {
			this.clearEntries();
		}

		@Override
		public int getRowWidth() {
			return Math.min(this.width - 40, 420);
		}

		@Override
		protected int getScrollbarPosition() {
			return this.getX() + this.width - 8;
		}
	}

	private abstract static class BaseEntry extends ContainerObjectSelectionList.Entry<BaseEntry> {
		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}

	private static final class CategoryEntry extends BaseEntry {
		private final Font font;
		private final String category;
		private final boolean collapsed;
		private final Runnable onToggle;
		private int lastLeft;
		private int lastTop;
		private int lastWidth;
		private int lastHeight;

		private CategoryEntry(Font font, String label, boolean collapsed, Runnable onToggle) {
			this.font = font;
			this.category = label;
			this.collapsed = collapsed;
			this.onToggle = onToggle;
		}

		@Override
		public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX,
				int mouseY, boolean hovering, float partialTick) {
			this.lastLeft = left;
			this.lastTop = top;
			this.lastWidth = width;
			this.lastHeight = height;
			guiGraphics.fill(left, top, left + width - 4, top + height, 0x221B3440);
			Component label = Component.literal((this.collapsed ? "+ " : "- ") + this.category)
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
			guiGraphics.drawString(this.font, label, left + 6, top + height / 2 - this.font.lineHeight / 2, 0xFFFFFFFF);
			guiGraphics.drawString(this.font, Component.literal(this.collapsed ? "Show" : "Hide"),
					left + width - 36 - this.font.width(this.collapsed ? "Show" : "Hide"),
					top + height / 2 - this.font.lineHeight / 2, 0xFF9FD3E0);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 0 && mouseX >= this.lastLeft && mouseX <= this.lastLeft + this.lastWidth - 4
					&& mouseY >= this.lastTop && mouseY <= this.lastTop + this.lastHeight) {
				this.onToggle.run();
				return true;
			}
			return false;
		}
	}

	private static final class ConfigEntry extends BaseEntry {
		private static final int LABEL_WIDTH = 170;
		private final Font font;
		private final String path;
		private final ModConfigSpec.ConfigValue<Object> value;
		private final ValueSpec spec;
		private final Component label;
		private final List<Component> tooltip = new ArrayList<>();
		private final List<AbstractWidget> widgets = new ArrayList<>();
		private final Object initialValue;
		private final @Nullable EditBox textBox;
		private final @Nullable Button booleanButton;
		private final @Nullable CyclableButton<?> enumButton;
		private int lastLeft;
		private int lastTop;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private ConfigEntry(Font font, String path, ModConfigSpec.ConfigValue<?> value, ValueSpec spec) {
			this.font = font;
			this.path = path;
			this.value = (ModConfigSpec.ConfigValue<Object>) value;
			this.spec = spec;
			this.initialValue = value.get();
			this.label = ConfigListItem.shortenText(Component.literal(humanizePath(path)), LABEL_WIDTH - 10);
			this.tooltip.add(Component.literal(path).withStyle(ChatFormatting.YELLOW));
			if (spec != null && spec.getComment() != null && !spec.getComment().isBlank())
				this.tooltip.add(Component.literal(spec.getComment()).withStyle(ChatFormatting.GRAY));

			EditBox builtTextBox = null;
			Button builtBooleanButton = null;
			CyclableButton<?> builtEnumButton = null;
			if (this.initialValue instanceof Boolean boolValue) {
				builtBooleanButton = Button.builder(booleanLabel(boolValue), b -> {
					boolean newValue = !Boolean.parseBoolean(b.getMessage().getString());
					b.setMessage(booleanLabel(newValue));
				}).width(120).build();
				this.widgets.add(builtBooleanButton);
			} else if (this.initialValue instanceof Enum<?> enumValue) {
				CyclableButton rawButton = new CyclableButton<>(0, 0, 140,
						Arrays.asList(enumValue.getDeclaringClass().getEnumConstants()), enumValue);
				builtEnumButton = rawButton;
				this.widgets.add(rawButton);
			} else {
				builtTextBox = new EditBox(font, 0, 0, 180, 20, CommonComponents.EMPTY);
				builtTextBox.setMaxLength(512);
				builtTextBox.setValue(stringifyValue(this.initialValue));
				this.widgets.add(builtTextBox);
			}
			this.textBox = builtTextBox;
			this.booleanButton = builtBooleanButton;
			this.enumButton = builtEnumButton;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.widgets;
		}

		@Override
		public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX,
				int mouseY, boolean hovering, float partialTick) {
			this.lastLeft = left;
			this.lastTop = top;
			guiGraphics.renderOutline(left, top, width - 4, height, 0x55FFFFFF);
			guiGraphics.drawString(this.font, this.label, left + 6, top + height / 2 - this.font.lineHeight / 2,
					0xFFFFFFFF);
			if (mouseX >= left && mouseX <= left + LABEL_WIDTH && mouseY >= top && mouseY <= top + height) {
				guiGraphics.renderComponentTooltip(this.font, this.tooltip, mouseX, mouseY);
			}
			AbstractWidget widget = this.widgets.getFirst();
			widget.setPosition(left + LABEL_WIDTH + 10, top + height / 2 - widget.getHeight() / 2);
			widget.setWidth(width - LABEL_WIDTH - 18);
			widget.render(guiGraphics, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return super.mouseClicked(mouseX, mouseY, button);
		}

		public Optional<Component> applyValue() {
			Object parsed;
			try {
				parsed = this.parseValue();
			} catch (IllegalArgumentException e) {
				return Optional.of(Component.literal(e.getMessage()));
			}
			if (!this.spec.test(parsed)) {
				return Optional.of(Component.literal("Invalid value for " + this.path + ".")
						.append(this.spec.getComment() == null ? CommonComponents.EMPTY
								: Component.literal("\n\n" + this.spec.getComment()).withStyle(ChatFormatting.GRAY)));
			}
			this.value.set(parsed);
			return Optional.empty();
		}

		public boolean matchesSearch(String query) {
			if (query.isBlank())
				return true;
			String haystack = (this.path + " "
					+ this.tooltip.stream().map(Component::getString).collect(Collectors.joining(" ")))
					.toLowerCase(Locale.ROOT);
			return haystack.contains(query);
		}

		public String getCategoryLabel() {
			int split = this.path.lastIndexOf('.');
			if (split < 0)
				return "General";
			String categoryPath = this.path.substring(0, split);
			return Arrays.stream(categoryPath.split("\\."))
					.map(ConfigEntry::humanizeToken)
					.collect(Collectors.joining(" / "));
		}

		private Object parseValue() {
			if (this.booleanButton != null) {
				return this.booleanButton.getMessage().getString().equals("true");
			}
			if (this.enumButton != null) {
				return this.enumButton.getValue();
			}
			String raw = this.textBox == null ? "" : this.textBox.getValue().trim();
			try {
				if (this.initialValue instanceof Integer)
					return Integer.parseInt(raw);
				if (this.initialValue instanceof Long)
					return Long.parseLong(raw);
				if (this.initialValue instanceof Double)
					return Double.parseDouble(raw);
				if (this.initialValue instanceof Float)
					return Float.parseFloat(raw);
				if (this.initialValue instanceof List<?>) {
					if (raw.isBlank())
						return List.of();
					return Arrays.stream(raw.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
				}
				return raw;
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Could not parse a value for " + this.path + ".");
			}
		}

		private static Component booleanLabel(boolean value) {
			return Component.literal(Boolean.toString(value));
		}

		private static String stringifyValue(Object value) {
			if (value instanceof List<?> list)
				return String.join(", ", list.stream().map(String::valueOf).toList());
			return String.valueOf(value);
		}

		private static String humanizePath(String path) {
			return Arrays.stream(path.split("\\."))
					.map(ConfigEntry::humanizeToken)
					.reduce((left, right) -> left + " / " + right)
					.orElse(path);
		}

		private static String humanizeToken(String token) {
			String spaced = token.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
			String[] words = spaced.split(" ");
			StringBuilder builder = new StringBuilder();
			for (String word : words) {
				if (word.isBlank())
					continue;
				if (!builder.isEmpty())
					builder.append(' ');
				builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
				if (word.length() > 1)
					builder.append(word.substring(1));
			}
			return builder.toString();
		}
	}
}