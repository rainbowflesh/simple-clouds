package dev.nonamecrackers2.simpleclouds.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import dev.nonamecrackers2.simpleclouds.client.config.SimpleCloudsClientConfigListeners;
import dev.nonamecrackers2.simpleclouds.client.mesh.LevelOfDetailOptions;
import dev.nonamecrackers2.simpleclouds.client.gui.widget.CyclableButton;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfigListeners;
import dev.nonamecrackers2.simpleclouds.client.gui.widget.config.ConfigListItem;
import dev.nonamecrackers2.simpleclouds.client.world.ClientCloudManager;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.config.util.ConfigHelper;
import dev.nonamecrackers2.simpleclouds.common.packet.impl.update.ApplyServerConfigEditsPayload;
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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;
import net.neoforged.neoforge.network.PacketDistributor;

public class SimpleCloudsConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 190;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 8;
	private static final int PRESET_BUTTON_WIDTH = (BUTTON_WIDTH - BUTTON_SPACING) / 2;
	private final Screen previous;
	private int presetTitleY;

	public SimpleCloudsConfigScreen(Screen previous) {
		super(Component.translatable("gui.simpleclouds.config.title"));
		this.previous = previous;
	}

	@Override
	protected void init() {
		this.clearWidgets();
		int left = this.width / 2 - BUTTON_WIDTH / 2;
		int y = this.height / 4 + 24;

		this.addRenderableWidget(Button.builder(Component.translatable("gui.simpleclouds.config.button.client"),
				b -> this.minecraft.setScreen(new SpecScreen(this, Component.translatable("gui.simpleclouds.config.button.client"),
						SimpleCloudsConfig.CLIENT_SPEC, "client", clientConfigFilter())))
				.pos(left, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += BUTTON_HEIGHT + BUTTON_SPACING;

		Button serverButton = Button.builder(Component.translatable("gui.simpleclouds.config.button.server"),
				b -> this.minecraft.setScreen(new SpecScreen(this, Component.translatable("gui.simpleclouds.config.button.server"),
						SimpleCloudsConfig.SERVER_SPEC, "server", path -> true)))
				.pos(left, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
		serverButton.active = canEditServerConfig();
		if (!serverButton.active) {
			serverButton.setTooltip(Tooltip.create(
					Component.translatable("gui.simpleclouds.config.server.tooltip.disabled")));
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
				Component.translatable("gui.simpleclouds.config.subtitle"), this.width / 2, 30,
				0xFFA0A0A0);
		guiGraphics.drawCenteredString(this.font, Component.translatable("gui.simpleclouds.config.presets.title"),
				this.width / 2, this.presetTitleY, 0xFFADF7FF);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.previous);
	}

	private static boolean canEditServerConfig() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null)
			return false;
		if (mc.hasSingleplayerServer())
			return true;
		return ClientCloudManager.isAvailableServerSide() && mc.player != null && mc.player.getPermissionLevel() >= 2;
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

	private static String configNameKey(String scope, String path) {
		return "gui." + SimpleCloudsConfigScreenNamespace.MODID + ".config." + scope + ".option." + path + ".name";
	}

	private static String configDescriptionKey(String scope, String path) {
		return "gui." + SimpleCloudsConfigScreenNamespace.MODID + ".config." + scope + ".option." + path
				+ ".description";
	}

	private static String configCategoryKey(String scope, String path) {
		return "gui." + SimpleCloudsConfigScreenNamespace.MODID + ".config." + scope + ".category." + path;
	}

	private static String localizedText(String key, String fallback) {
		return I18n.exists(key) ? I18n.get(key) : fallback;
	}

	private void confirmApplyPreset(ClientPreset preset) {
		Popup.createYesNoPopup(this, () -> this.applyPreset(preset), 320,
				preset.title().copy().append("\n\n").append(preset.description()));
	}

	private void applyPreset(ClientPreset preset) {
		preset.apply();
		SimpleCloudsConfig.CLIENT_SPEC.save();
		SimpleCloudsClientConfigListeners.pollNow();
		Popup.createInfoPopup(this, 320,
				Component.translatable("gui.simpleclouds.config.preset.applied", preset.title())
						.append(Component.translatable("gui.simpleclouds.config.preset.applied.notice")
								.withStyle(ChatFormatting.GRAY)));
	}

	private enum ClientPreset {
		HIGH(Component.translatable("simpleclouds.config.preset.high"),
				Component.translatable("simpleclouds.config.preset.high.description")) {
			@Override
			void apply() {
				SimpleCloudsConfig.CLIENT.renderClouds.set(true);
				SimpleCloudsConfig.CLIENT.generateMesh.set(true);
				SimpleCloudsConfig.CLIENT.renderStormFog.set(true);
				SimpleCloudsConfig.CLIENT.renderLodClouds.set(true);
				SimpleCloudsConfig.CLIENT.atmosphericClouds.set(true);
				SimpleCloudsConfig.CLIENT.distantShadows.set(true);
				SimpleCloudsConfig.CLIENT.shadedClouds.set(true);
				SimpleCloudsConfig.CLIENT.transparency.set(false);
				SimpleCloudsConfig.CLIENT.levelOfDetail.set(LevelOfDetailOptions.HIGH);
				SimpleCloudsConfig.CLIENT.shadowDistance.set(4096);
			}
		},
		MEDIUM(Component.translatable("simpleclouds.config.preset.medium"),
				Component.translatable("simpleclouds.config.preset.medium.description")) {
			@Override
			void apply() {
				SimpleCloudsConfig.CLIENT.renderClouds.set(true);
				SimpleCloudsConfig.CLIENT.generateMesh.set(true);
				SimpleCloudsConfig.CLIENT.renderStormFog.set(true);
				SimpleCloudsConfig.CLIENT.renderLodClouds.set(true);
				SimpleCloudsConfig.CLIENT.atmosphericClouds.set(true);
				SimpleCloudsConfig.CLIENT.distantShadows.set(true);
				SimpleCloudsConfig.CLIENT.shadedClouds.set(true);
				SimpleCloudsConfig.CLIENT.transparency.set(false);
				SimpleCloudsConfig.CLIENT.levelOfDetail.set(LevelOfDetailOptions.MEDIUM);
				SimpleCloudsConfig.CLIENT.shadowDistance.set(2500);
			}
		},
		LOW(Component.translatable("simpleclouds.config.preset.low"),
				Component.translatable("simpleclouds.config.preset.low.description")) {
			@Override
			void apply() {
				SimpleCloudsConfig.CLIENT.renderClouds.set(true);
				SimpleCloudsConfig.CLIENT.generateMesh.set(true);
				SimpleCloudsConfig.CLIENT.renderStormFog.set(true);
				SimpleCloudsConfig.CLIENT.levelOfDetail.set(LevelOfDetailOptions.LOW);
				SimpleCloudsConfig.CLIENT.renderLodClouds.set(false);
				SimpleCloudsConfig.CLIENT.transparency.set(false);
				SimpleCloudsConfig.CLIENT.shadedClouds.set(false);
				SimpleCloudsConfig.CLIENT.atmosphericClouds.set(false);
				SimpleCloudsConfig.CLIENT.shadowDistance.set(2500);
				SimpleCloudsConfig.CLIENT.distantShadows.set(false);
			}
		},
		OFF(Component.translatable("simpleclouds.config.preset.off"),
				Component.translatable("simpleclouds.config.preset.off.description")) {
			@Override
			void apply() {
				SimpleCloudsConfig.CLIENT.renderClouds.set(false);
				SimpleCloudsConfig.CLIENT.generateMesh.set(false);
				SimpleCloudsConfig.CLIENT.renderStormFog.set(false);
				SimpleCloudsConfig.CLIENT.renderLodClouds.set(false);
				SimpleCloudsConfig.CLIENT.atmosphericClouds.set(false);
				SimpleCloudsConfig.CLIENT.distantShadows.set(false);
				SimpleCloudsConfig.CLIENT.shadowDistance.set(512);
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

	private static final class SimpleCloudsConfigScreenNamespace {
		private static final String MODID = "simpleclouds";
	}

	private static final class SpecScreen extends Screen {
		private final Screen previous;
		private final ModConfigSpec spec;
		private final String translationScope;
		private final Predicate<String> includePath;
		private final List<ConfigEntry> allConfigEntries = new ArrayList<>();
		private final Set<String> collapsedCategories = new LinkedHashSet<>();
		private @Nullable EntryList list;
		private @Nullable EditBox searchBox;

		private SpecScreen(Screen previous, Component title, ModConfigSpec spec, String translationScope,
				Predicate<String> includePath) {
			super(title);
			this.previous = previous;
			this.spec = spec;
			this.translationScope = translationScope;
			this.includePath = includePath;
		}

		@Override
		protected void init() {
			this.clearWidgets();
			this.allConfigEntries.clear();

			this.searchBox = new EditBox(this.font, this.width / 2 - 140, 32, 280, 20,
					Component.translatable("gui.simpleclouds.config.search"));
			this.searchBox.setHint(Component.translatable("gui.simpleclouds.config.search"));
			this.searchBox.setResponder(value -> this.rebuildList());
			this.addRenderableWidget(this.searchBox);

			this.list = new EntryList(this.minecraft, this.width, this.height - 72, 32, this.height - 40, this.font);
			Map<String, ModConfigSpec.ConfigValue<?>> values = ConfigHelper.getAllValues(this.spec);
			Map<String, ValueSpec> specs = ConfigHelper.getAllSpecs(this.spec);
			values.entrySet().stream().filter(entry -> this.includePath.test(entry.getKey()))
					.sorted(Comparator
							.comparing((Map.Entry<String, ModConfigSpec.ConfigValue<?>> entry) -> ConfigEntry
									.categoryPathOf(entry.getKey()))
							.thenComparing(Map.Entry::getKey))
					.forEach(entry -> this.allConfigEntries.add(
							new ConfigEntry(this.font, this.translationScope, entry.getKey(), entry.getValue(),
									specs.get(entry.getKey()))));
			this.rebuildList();
			this.addRenderableWidget(this.list);

			this.addRenderableWidget(Button.builder(Component.translatable("gui.simpleclouds.config.button.save"), b -> this.saveAndClose())
					.pos(this.width / 2 - 104, this.height - 28).size(100, 20).build());
			this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
					.pos(this.width / 2 + 4, this.height - 28).size(100, 20).build());
		}

		private void saveAndClose() {
			if (this.list == null)
				return;
			boolean remote = this.isRemoteServerSpec();
			for (ConfigEntry entry : this.allConfigEntries) {
				// For remote server specs, don't commit edits to the local ConfigValue yet -
				// the server is the source of truth and may reject the change. Committing
				// here would let the client present an unconfirmed value as if it were
				// already in effect, causing it to drift from the server's actual state.
				Optional<Component> error = entry.applyValue(!remote);
				if (error.isPresent()) {
					Popup.createInfoPopup(this, 340, error.get()).alignLeft();
					return;
				}
			}
			if (remote) {
				Map<String, String> changedValues = this.allConfigEntries.stream().filter(ConfigEntry::hasChanged)
						.collect(Collectors.toMap(ConfigEntry::getPath, ConfigEntry::serializeValue,
								(left, right) -> right,
								java.util.LinkedHashMap::new));
				if (!changedValues.isEmpty())
					PacketDistributor.sendToServer(new ApplyServerConfigEditsPayload(changedValues));
			} else {
				this.spec.save();
				if (this.spec == SimpleCloudsConfig.CLIENT_SPEC)
					SimpleCloudsClientConfigListeners.pollNow();
				else if (this.spec == SimpleCloudsConfig.SERVER_SPEC)
					SimpleCloudsConfigListeners.pollNow();
			}
			this.onClose();
		}

		private boolean isRemoteServerSpec() {
			return this.spec == SimpleCloudsConfig.SERVER_SPEC && ClientCloudManager.isRemoteServerAvailable();
		}

		private void rebuildList() {
			if (this.list == null)
				return;
			this.list.clearConfigEntries();
			String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
			boolean forceExpand = !query.isBlank();
			String currentCategoryPath = null;
			for (ConfigEntry entry : this.allConfigEntries) {
				if (!entry.matchesSearch(query))
					continue;
				String categoryPath = entry.getCategoryPath();
				if (!categoryPath.equals(currentCategoryPath)) {
					boolean collapsed = !forceExpand && this.collapsedCategories.contains(categoryPath);
					this.list.addConfigEntry(
							new CategoryEntry(this.font, entry.getCategoryLabel(), entry.getCategoryDepth(),
									collapsed, () -> this.toggleCategory(categoryPath)));
					currentCategoryPath = categoryPath;
				}
				if (!forceExpand && this.collapsedCategories.contains(categoryPath))
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
			Component subtitle = this.isRemoteServerSpec()
					? Component.translatable("gui.simpleclouds.config.spec.subtitle.remote")
					: Component.translatable("gui.simpleclouds.config.spec.subtitle.local");
			guiGraphics.drawCenteredString(this.font, subtitle, this.width / 2, 22, 0xFFA0A0A0);
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
		private final int depth;
		private final boolean collapsed;
		private final Runnable onToggle;
		private int lastLeft;
		private int lastTop;
		private int lastWidth;
		private int lastHeight;

		private CategoryEntry(Font font, String label, int depth, boolean collapsed, Runnable onToggle) {
			this.font = font;
			this.category = label;
			this.depth = depth;
			this.collapsed = collapsed;
			this.onToggle = onToggle;
		}

		@Override
		public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX,
				int mouseY, boolean hovering, float partialTick) {
			int indent = this.depth * 12;
			int contentLeft = left + indent;
			int contentWidth = width - indent;
			this.lastLeft = left;
			this.lastTop = top;
			this.lastWidth = width;
			this.lastHeight = height;
			guiGraphics.fill(contentLeft, top, contentLeft + contentWidth - 4, top + height, 0x221B3440);
			Component label = Component.literal((this.collapsed ? "+ " : "- ") + this.category)
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
			guiGraphics.drawString(this.font, label, contentLeft + 6, top + height / 2 - this.font.lineHeight / 2,
					0xFFFFFFFF);
			guiGraphics.drawString(this.font, Component.literal(this.collapsed ? "Show" : "Hide"),
					contentLeft + contentWidth - 36 - this.font.width(this.collapsed ? "Show" : "Hide"),
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
		private final String translationScope;
		private final String path;
		private final ModConfigSpec.ConfigValue<Object> value;
		private final ValueSpec spec;
		private final String categoryPath;
		private final String localizedCategoryLabel;
		private final int categoryDepth;
		private final String searchableText;
		private final Component label;
		private final List<Component> tooltip = new ArrayList<>();
		private final List<AbstractWidget> widgets = new ArrayList<>();
		private final Object initialValue;
		private @Nullable Object pendingValue;
		private final @Nullable EditBox textBox;
		private final @Nullable Button booleanButton;
		private final @Nullable CyclableButton<?> enumButton;
		private int lastLeft;
		private int lastTop;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private ConfigEntry(Font font, String translationScope, String path, ModConfigSpec.ConfigValue<?> value,
				ValueSpec spec) {
			this.font = font;
			this.translationScope = translationScope;
			this.path = path;
			this.value = (ModConfigSpec.ConfigValue<Object>) value;
			this.spec = spec;
			this.initialValue = value.get();
			this.categoryPath = categoryPathOf(path);
			this.localizedCategoryLabel = this.localizedCategoryLabel(this.translationScope, this.categoryPath);
			this.categoryDepth = categoryDepthOf(this.categoryPath);
			String localizedLabel = localizedText(configNameKey(this.translationScope, path), humanizeOptionName(path));
			this.label = ConfigListItem.shortenText(Component.literal(localizedLabel), LABEL_WIDTH - 10);
			this.tooltip.add(Component.literal(path).withStyle(ChatFormatting.YELLOW));
			String descriptionFallback = spec != null && spec.getComment() != null ? spec.getComment() : "";
			String localizedDescription = localizedText(configDescriptionKey(this.translationScope, path),
					descriptionFallback);
			if (!localizedDescription.isBlank())
				this.tooltip.add(Component.literal(localizedDescription).withStyle(ChatFormatting.GRAY));
			this.searchableText = (path + " " + localizedLabel + " " + this.localizedCategoryLabel + " "
					+ this.tooltip.stream().map(Component::getString).collect(Collectors.joining(" ")))
					.toLowerCase(Locale.ROOT);

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
				rawButton.setMessageFactory(enumConstant -> {
					Enum<?> e = (Enum<?>) enumConstant;
					String key = "gui." + SimpleCloudsConfigScreenNamespace.MODID + ".enum."
							+ e.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT) + "."
							+ e.name().toLowerCase(Locale.ROOT);
					return I18n.exists(key) ? Component.translatable(key)
							: Component.literal(String.valueOf(enumConstant));
				});
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
			int indent = this.categoryDepth * 12;
			int contentLeft = left + indent;
			int contentWidth = width - indent;
			this.lastLeft = left;
			this.lastTop = top;
			guiGraphics.renderOutline(contentLeft, top, contentWidth - 4, height, 0x55FFFFFF);
			guiGraphics.drawString(this.font, this.label,
					contentLeft + 6, top + height / 2 - this.font.lineHeight / 2,
					0xFFFFFFFF);
			if (mouseX >= contentLeft && mouseX <= contentLeft + LABEL_WIDTH && mouseY >= top
					&& mouseY <= top + height) {
				guiGraphics.renderComponentTooltip(this.font, this.tooltip, mouseX, mouseY);
			}
			AbstractWidget widget = this.widgets.getFirst();
			widget.setPosition(contentLeft + LABEL_WIDTH + 10, top + height / 2 - widget.getHeight() / 2);
			widget.setWidth(contentWidth - LABEL_WIDTH - 18);
			widget.render(guiGraphics, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return super.mouseClicked(mouseX, mouseY, button);
		}

		/**
		 * Validates and stages the entry's edited value.
		 * <p>
		 * When {@code commitLocally} is {@code false} (remote server config edits),
		 * the underlying {@link ModConfigSpec.ConfigValue} is intentionally left
		 * untouched - the client must not present a remote server's config as changed
		 * before the server has confirmed and applied the edit. The parsed value is
		 * still recorded as {@link #pendingValue} so {@link #hasChanged()} and
		 * {@link #serializeValue()} can report on it for the outgoing edit packet.
		 */
		public Optional<Component> applyValue(boolean commitLocally) {
			Object parsed;
			try {
				parsed = this.parseValue();
			} catch (IllegalArgumentException e) {
				return Optional.of(Component.literal(e.getMessage()));
			}
			if (!this.spec.test(parsed)) {
				return Optional.of(Component.translatable("gui.simpleclouds.config.error.invalid_value", this.path)
						.append(this.spec.getComment() == null ? CommonComponents.EMPTY
								: Component.literal("\n\n" + this.spec.getComment()).withStyle(ChatFormatting.GRAY)));
			}
			this.pendingValue = parsed;
			if (commitLocally)
				this.value.set(parsed);
			return Optional.empty();
		}

		public boolean matchesSearch(String query) {
			if (query.isBlank())
				return true;
			return this.searchableText.contains(query);
		}

		public String getCategoryPath() {
			return this.categoryPath;
		}

		public String getPath() {
			return this.path;
		}

		public String getCategoryLabel() {
			return this.localizedCategoryLabel;
		}

		public int getCategoryDepth() {
			return this.categoryDepth;
		}

		public boolean hasChanged() {
			return !Objects.equals(this.initialValue, this.currentValue());
		}

		public String serializeValue() {
			return stringifyValue(this.currentValue());
		}

		/**
		 * The entry's effective value - the staged edit if {@link #applyValue} has run,
		 * otherwise whatever is currently held by the underlying config value.
		 */
		private Object currentValue() {
			return this.pendingValue != null ? this.pendingValue : this.value.get();
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

		private static String humanizeOptionName(String path) {
			int split = path.lastIndexOf('.');
			return humanizeToken(split < 0 ? path : path.substring(split + 1));
		}

		private static String categoryPathOf(String path) {
			int split = path.lastIndexOf('.');
			return split < 0 ? "general" : path.substring(0, split);
		}

		private static int categoryDepthOf(String path) {
			return path.equals("general") ? 0 : path.split("\\.").length - 1;
		}

		private String localizedCategoryLabel(String scope, String path) {
			String fallback;
			if (path.equals("general")) {
				fallback = "General";
			} else {
				int split = path.lastIndexOf('.');
				fallback = humanizeToken(split < 0 ? path : path.substring(split + 1));
			}
			return localizedText(configCategoryKey(scope, path), fallback);
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