package dev.nonamecrackers2.simpleclouds.common.data;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.config.util.ConfigHelper;
import dev.nonamecrackers2.simpleclouds.common.noise.AbstractNoiseSettings;
import net.minecraft.data.PackOutput;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class SimpleCloudsLangProvider extends LanguageProvider {
	public SimpleCloudsLangProvider(PackOutput output) {
		super(output, SimpleCloudsMod.MODID, "en_us");
	}

	@Override
	protected void addTranslations() {
		this.addConfigTranslations("client", SimpleCloudsConfig.CLIENT_SPEC);
		this.addConfigTranslations("server", SimpleCloudsConfig.SERVER_SPEC);

		this.add("gui.simpleclouds.cloud_previewer.title", "Cloud Previewer");
		this.add("gui.simpleclouds.cloud_previewer.button.title", "Cloud Previewer");
		this.add("gui.simpleclouds.cloud_previewer.button.add_layer.title", "Add Layer");
		this.add("gui.simpleclouds.cloud_previewer.button.remove_layer.title", "Remove Layer");
		this.add("gui.simpleclouds.cloud_previewer.button.toggle_preview.title", "Toggle Preview");
		this.add("gui.simpleclouds.cloud_previewer.current_layer", "Current Layer: %s");
		this.add("simpleclouds.key.openGenPreviewer", "Open Cloud Gen Previewer");
		this.add("simpleclouds.key.openDebug", "Open Debug Screen");
		this.add("simpleclouds.key.categories.main",
				ModList.get().getModContainerById(SimpleCloudsMod.MODID).get().getModInfo().getDisplayName());
		for (AbstractNoiseSettings.Param parameter : AbstractNoiseSettings.Param.values()) {
			String key = "gui.simpleclouds.noise_settings.param." + parameter.toString().toLowerCase() + ".name";
			String[] splitted = parameter.toString().toLowerCase().split("_");
			for (int i = 0; i < splitted.length; i++)
				splitted[i] = StringUtils.capitalize(splitted[i]);
			this.add(key, StringUtils.join(splitted, " "));
		}
		this.add("gui.simpleclouds.enum.cloudmode.default", "Simple Clouds");
		this.add("gui.simpleclouds.enum.cloudmode.single", "Single");
		this.add("gui.simpleclouds.enum.cloudmode.ambient", "Vanilla Fallback");
		this.add("simpleclouds.config.preset.high", "High");
		this.add("simpleclouds.config.preset.high.description",
				"Restores the fuller Simple Clouds presentation with high detail, distant cloud coverage, storm fog, and terrain shadows enabled.");
		this.add("simpleclouds.config.preset.medium", "Medium");
		this.add("simpleclouds.config.preset.medium.description",
				"Balances visuals and performance by lowering level of detail slightly and slowing mesh generation a bit.");
		this.add("simpleclouds.config.preset.low", "Low");
		this.add("simpleclouds.config.preset.low.description",
				"Cuts distant cloud coverage and some extra visual features for weaker systems while keeping clouds visible.");
		this.add("simpleclouds.config.preset.off", "Off");
		this.add("simpleclouds.config.preset.off.description",
				"Disables Simple Clouds visuals for this player, including cloud rendering, storm fog, and atmospheric cloud layers.");
		this.add("gui.simpleclouds.noise_settings.param.range", "Range: %s - %s");
		this.add("gui.simpleclouds.cloud_previewer.button.previous_layer.title", "Previous layer");
		this.add("gui.simpleclouds.cloud_previewer.button.next_layer.title", "Next layer");
		this.add("gui.simpleclouds.cloud_previewer.warning.too_many_cubes", "Warning: Too many cubes");
		this.add("gui.simpleclouds.cloud_previewer.weather_type.title", "Weather Type");
		this.add("gui.simpleclouds.cloud_previewer.storminess.title", "Storminess");
		this.add("gui.simpleclouds.cloud_previewer.storm_start.title", "Storm Start Level");
		this.add("gui.simpleclouds.cloud_previewer.storm_fade_distance.title", "Storm Fade Distance");
		this.add("gui.simpleclouds.cloud_previewer.load.title", "Load");
		this.add("gui.simpleclouds.cloud_previewer.export.title", "Export");
		this.add("gui.simpleclouds.cloud_previewer.popup.select.cloud_type", "Select a cloud type:");
		this.add("gui.simpleclouds.cloud_previewer.popup.export.cloud_type",
				"What would you like to name your cloud type?");
		this.add("gui.simpleclouds.cloud_previewer.popup.export.exists",
				"A file with that name already exists. Would you like to override it?");
		this.add("gui.simpleclouds.cloud_previewer.popup.exported.cloud_type",
				"Your cloud type has been exported to %s");
		this.add("gui.simpleclouds.cloud_previewer.info",
				"Welcome to the cloud previewer!\n\nAdd, remove, and customize noise layers seen in the left of the screen to create custom cloud types. Use the load button in the bottom right to load existing cloud types to edit them, and use the export button to export your cloud types as JSON files.");
		this.add("gui.simpleclouds.unknown_or_invalid_client_side_cloud_type.info",
				"Unknown or invalid cloud type '%s'. Please pick a valid cloud type. \n\nValid cloud types are as follows:\n\n%s");
		this.add("command.simpleclouds.scroll.get", "The current cloud scroll position is [x: %s, y: %s, z: %s]");
		this.add("command.simpleclouds.speed.get", "The current cloud speed is %s");
		this.add("command.simpleclouds.seed.get", "The current cloud seed is %s");
		this.add("command.simpleclouds.reinitialize", "Clouds have been reset");
		this.add("command.simpleclouds.direction.get",
				"Clouds are currently moving in direction [x: %s, y: %s, z: %s] (%s)");
		this.add("command.simpleclouds.direction.set",
				"Successfully set cloud direction to [x: %s, y: %s, z: %s] (%s)");
		this.add("command.simpleclouds.height.get", "Cloud height is set to %s");
		this.add("command.simpleclouds.height.set", "Set cloud height to %s");
		this.add("commands.simpleclouds.notClientSideOnly",
				"Client cloud commands can only be used when connected to servers that do not have Simple Clouds installed. If you're connected on singleplayer, or you are an operator on a dedicated server with Simple Clouds installed, please use '/simpleclouds clouds'");
		this.add("commands.simpleclouds.client.configReferal",
				"This option is overridden by a config setting. Please use the config screen to change it.");
		this.add("command.simpleclouds.weather.override",
				"Simple Clouds is overriding vanilla weather, and the /weather command is disabled. To use vanilla weather, please do either of the following:\n1. Set the cloud mode in the SERVER config to 'Vanilla Fallback'.\n2. Set the cloud mode in the SERVER config to 'Single', and set the single mode cloud type to a cloud type that has no weather associated with it (e.g. simpleclouds:itty_bitty)");
		this.add("command.simpleclouds.clouds.spawn", "Spawned cloud %s at [%s, %s]");
		this.add("command.simpleclouds.clouds.spawn.fail", "Too many cloud formations close by!");
		this.add("command.simpleclouds.clouds.clear", "Removed %s cloud formations");
		this.add("command.simpleclouds.clouds.clear.fail", "No cloud formations to remove");
		this.add("command.simpleclouds.clouds.refresh", "Refreshing clouds");
		this.add("command.simpleclouds.clouds.get", "Cloud type %s is at [%s, %s] and has weather type '%s'");
		this.add("command.simpleclouds.clouds.get.empty", "No cloud type is at that position");
		this.add("command.simpleclouds.clouds.count", "Found %s cloud formations [%s]");
		this.add("commands.simpleclouds.cloudType.notFound", "Unknown cloud type '%s'");
		this.add("gui.simpleclouds.debug.title", "Simple Clouds Debug");
		this.add("simpleclouds.subtitle.distant_thunder", "Distant Thunder Roars");
		this.add("simpleclouds.subtitle.close_thunder", "Thunder Roars");
		this.add("gui.simpleclouds.error_screen.title", "Simple Clouds Error");
		this.add("gui.simpleclouds.error_screen.description", "An error occured while initializing Simple Clouds.");
		this.add("gui.simpleclouds.error_screen.no_errors", "There are no errors? What?");
		this.add("gui.simpleclouds.error.recommendations",
				"Please try updating your graphics drivers. If the issue persists, please make a bug report on the Simple Clouds repository, linked below. Make sure to include the crash report with your issue.");
		this.add("gui.simpleclouds.error.opengl",
				"The currently selected display adapter does not support Simple Clouds.\n\nSimple Clouds only supports display adapters that have OpenGL 4.3+ capabilities.\n\nPlease make sure:\n1. Your drivers are up to date\n2. You are not using integrated graphics instead of a discrete GPU to run Minecraft.\n\nIf you are a MacOS user, Simple Clouds IS NOT SUPPORTED as the OS is stuck on OpenGL 4.1. Unfortunately, this is a limitation that Simple Clouds cannot work around.");
		this.add("gui.simpleclouds.error.unknown",
				"Please make a bug report on the mod's GitHub repository, linked below. Make sure to include the crash report and latest.log file with your issue.");
		this.add("gui.simpleclouds.error.couldNotLoadMeshScript",
				"Failed to load the mesh compute shader. Please make a bug report on the mod's GitHub repository, linked below. Make sure to include the crash report and latest.log file with your issue.\n\nTo developers: If you are modifying the cube_mesh.comp file using a resource pack and have made an error, this message will appear on start up. Please see the latest.log for more details.");
		this.add("gui.simpleclouds.error.compat.dh_oculus",
				"Simple Clouds does not currently support shaders with Distant Horizons. Please either remove Oculus/Iris to play with Simple Clouds, or remove Simple Clouds to play with shaders.");
		this.add("gui.simpleclouds.error.coreShadersInitialization",
				"An error occured while initializing core shaders. If you are modifying them, please see the log for more details. If you are a user, please report this on the Simple Clouds GitHub.");
		this.add("gui.simpleclouds.error_screen.button.crash_report", "Crash Report");
		this.add("gui.simpleclouds.error_screen.multiple",
				"More than one error has occured. Please see the 'crash-reports' folder for more information.");
		this.add("gui.simpleclouds.notice.title", "Simple Clouds Notice");
		this.add("gui.simpleclouds.notice.close.title", "Close");
		this.add("gui.simpleclouds.notice.vivecraft",
				"Vivecraft support is experimental. Please expect lower framerates, instability, and glitches/visual artifacts. Report bugs and issues on the official GitHub issue tracker.");

		// Config screen
		this.add("gui.simpleclouds.config.title", "Simple Clouds Config");
		this.add("gui.simpleclouds.config.subtitle", "Native NeoForge config access for Simple Clouds.");
		this.add("gui.simpleclouds.config.button.client", "Client Config");
		this.add("gui.simpleclouds.config.button.server", "Server Config");
		this.add("gui.simpleclouds.config.button.save", "Save");
		this.add("gui.simpleclouds.config.server.tooltip.disabled",
				"Server config can only be edited in singleplayer or by op'd players on a Simple Clouds server.");
		this.add("gui.simpleclouds.config.presets.title", "Client Presets");
		this.add("gui.simpleclouds.config.preset.applied", "Applied preset: %s");
		this.add("gui.simpleclouds.config.preset.applied.notice",
				"\n\nSome changes may require a renderer or resource reload to fully take effect.");
		this.add("gui.simpleclouds.config.search", "Search config");
		this.add("gui.simpleclouds.config.spec.subtitle.remote",
				"Search, then edit grouped config sections. Changes are sent to the server when saved.");
		this.add("gui.simpleclouds.config.spec.subtitle.local",
				"Search, then edit grouped config sections. Changes write directly to disk.");
		this.add("gui.simpleclouds.config.error.invalid_value", "Invalid value for %s.");

		// Info screen
		this.add("gui.simpleclouds.info.button.github", "GitHub");
		this.add("gui.simpleclouds.info.opengl_version", "OpenGL %s");

		// Cloud previewer
		this.add("gui.simpleclouds.cloud_previewer.label.server_side", " (Server Side)");

		// Profiler
		this.add("gui.simpleclouds.profiler.running", "Running profiler...");
		this.add("gui.simpleclouds.profiler.confirm",
				"You are about to run the cloud generator profiler. This may take a moment. Do you wish to continue?");
		this.add("gui.simpleclouds.profiler.failed", "Profiler failed. Please see log for details.\n\n%s");
		this.add("gui.simpleclouds.profiler.error",
				"An unknown error occurred. See log for more details.\n\n%s");
		this.add("gui.simpleclouds.profiler.completed",
				"Profiler completed. Below is a list of cloud types that spawned. Select a cloud type to see its individual stats.");
		this.add("gui.simpleclouds.profiler.stat.time_elapsed", "Total time elapsed: %s (%s ticks)");
		this.add("gui.simpleclouds.profiler.stat.clouds_spawned", "Total clouds spawned: %s");
		this.add("gui.simpleclouds.profiler.stat.avg_spawn_time", "Average spawn time: %s (%s ticks)");
		this.add("gui.simpleclouds.profiler.stat.avg_rain_spawn_time", "Average rain spawn time: %s (%s ticks)");
		this.add("gui.simpleclouds.profiler.stat.avg_thunderstorm_spawn_time",
				"Average thunderstorm spawn time: %s (%s ticks)");
		this.add("gui.simpleclouds.profiler.stat.individual.total_spawned", "\n\nTotal spawned: %s");
		this.add("gui.simpleclouds.profiler.stat.individual.avg_ticks_to_spawn",
				"\n\nAverage ticks to spawn: %s (%s ticks)");
		this.add("gui.simpleclouds.profiler.stat.minmax", "%s; min: %s, max: %s, avg: %s");
		this.add("gui.simpleclouds.profiler.stat.clouds_existing", "Clouds existing at once");
		this.add("gui.simpleclouds.profiler.stat.time_over_player", "Time over player");
		this.add("gui.simpleclouds.profiler.stat.speed", "Speed");
		this.add("gui.simpleclouds.profiler.stat.radius", "Radius");
		this.add("gui.simpleclouds.profiler.stat.stretch_factor", "Stretch factor");
		this.add("gui.simpleclouds.profiler.stat.exist_time", "Exist time");
		this.add("gui.simpleclouds.profiler.stat.grow_time", "Grow time");
	}

	private void addConfigTranslations(String scope, ModConfigSpec spec) {
		Set<String> categories = new LinkedHashSet<>();
		categories.add("general");
		for (var entry : ConfigHelper.getAllSpecs(spec).entrySet()) {
			String path = entry.getKey();
			var valueSpec = entry.getValue();
			this.add("gui." + SimpleCloudsMod.MODID + ".config." + scope + ".option." + path + ".name",
					humanizeOptionName(path));
			String comment = valueSpec.getComment();
			if (comment != null && !comment.isBlank()) {
				this.add("gui." + SimpleCloudsMod.MODID + ".config." + scope + ".option." + path + ".description",
						comment);
			}
			categories.add(categoryPathOf(path));
		}
		for (String category : categories) {
			this.add("gui." + SimpleCloudsMod.MODID + ".config." + scope + ".category." + category,
					humanizeCategoryPath(category));
		}
	}

	private static String categoryPathOf(String path) {
		int split = path.lastIndexOf('.');
		return split < 0 ? "general" : path.substring(0, split);
	}

	private static String humanizeCategoryPath(String path) {
		if (path.equals("general"))
			return "General";
		return Arrays.stream(path.split("\\."))
				.map(SimpleCloudsLangProvider::humanizeToken)
				.reduce((left, right) -> left + " / " + right)
				.orElse(path);
	}

	private static String humanizePath(String path) {
		return Arrays.stream(path.split("\\."))
				.map(SimpleCloudsLangProvider::humanizeToken)
				.reduce((left, right) -> left + " / " + right)
				.orElse(path);
	}

	private static String humanizeOptionName(String path) {
		int split = path.lastIndexOf('.');
		return humanizeToken(split < 0 ? path : path.substring(split + 1));
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
