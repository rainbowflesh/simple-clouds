package dev.nonamecrackers2.simpleclouds.client.command.profiling;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.core.util.ObjectArrayIterator;

import com.ibm.icu.impl.locale.XCldrStub.ImmutableMap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.cloud.ClientSideCloudTypeManager;
import dev.nonamecrackers2.simpleclouds.client.cloud.spawning.ClientSideCloudSpawningManager;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudTypeSource;
import dev.nonamecrackers2.simpleclouds.common.cloud.spawning.profiling.ProfilingCloudGenerator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import dev.nonamecrackers2.simpleclouds.client.gui.Popup;

public class ProfilingCommands
{
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
	{
		dispatcher.register(Commands.literal(SimpleCloudsMod.MODID).then(Commands.literal("profiling").requires(stack -> true)
				.then(Commands.literal("generator")
						.then(Commands.argument("time", TimeArgument.time(1))
								.executes(ctx -> runGeneratorProfiler(ctx, IntegerArgumentType.getInteger(ctx, "time")))
						)
						.executes(ctx -> runGeneratorProfiler(ctx, 1728000))
				)
		));
	}

	private static int runGeneratorProfiler(CommandContext<CommandSourceStack> ctx, int iterations) throws CommandSyntaxException
	{
		Popup.createYesNoPopup(null, () ->
		{
			Popup primary = Popup.createInfoPopup(null, 300, Component.translatable("gui.simpleclouds.profiler.running"));

			Minecraft mc = Minecraft.getInstance();

			ClientSideCloudTypeManager manager = ClientSideCloudTypeManager.getInstance();
			CloudType[] cloudTypes = manager.getIndexedCloudTypes();
			CloudType[] cachedTypes = Arrays.copyOf(cloudTypes, cloudTypes.length);
			Map<ResourceLocation, CloudType> cachedCloudMap = ImmutableMap.copyOf(manager.getCloudTypes());

			CloudTypeSource wrapper = new CloudTypeSource()
			{
				@Override
				public CloudType[] getIndexedCloudTypes()
				{
					return cachedTypes;
				}

				@Override
				public CloudType getCloudTypeForId(ResourceLocation id)
				{
					return cachedCloudMap.get(id);
				}
			};

			ProfilingCloudGenerator.profile(ClientSideCloudSpawningManager.getClientInstance().getConfig(), wrapper, iterations).exceptionallyAsync(e ->
			{
				ProfilingCloudGenerator.LOGGER.error("Failed to run profiler", e);
				primary.onClose();
				Popup.createInfoPopup(null, 200, Component.translatable("gui.simpleclouds.profiler.failed", e.getMessage()));
				return null;
			}, mc).thenAcceptAsync(results ->
			{
				if (results != null)
				{
					primary.onClose();
					try
					{
						acceptResults(results);
					}
					catch (Exception e)
					{
						Popup.createInfoPopup(null, 200, Component.translatable("gui.simpleclouds.profiler.error", e.getMessage()));
						ProfilingCloudGenerator.LOGGER.error("Error when handling results", e);
					}
				}
			}, mc);
		}, 200, Component.translatable("gui.simpleclouds.profiler.confirm"));

		return 0;
	}

	private static void acceptResults(ProfilingCloudGenerator.Results results)
	{
		MutableComponent mainMessage = Component.translatable("gui.simpleclouds.profiler.completed");
		int tickCountElapsed = results.getTotalTicksElapsed();
		mainMessage.append("\n\n");
		mainMessage.append(Component.translatable("gui.simpleclouds.profiler.stat.time_elapsed",
				humanReadableTicks(tickCountElapsed), tickCountElapsed));
		mainMessage.append("\n");
		mainMessage.append(Component.translatable("gui.simpleclouds.profiler.stat.clouds_spawned",
				results.getTotalCloudTypesGenerated()));
		int averageSpawnTime = Math.round(results.getAverageSpawnTime());
		mainMessage.append("\n");
		mainMessage.append(Component.translatable("gui.simpleclouds.profiler.stat.avg_spawn_time",
				humanReadableTicks(averageSpawnTime), averageSpawnTime));
		int averageRainSpawnTime = Math.round(results.getAverageRainSpawnTime());
		mainMessage.append("\n");
		mainMessage.append(Component.translatable("gui.simpleclouds.profiler.stat.avg_rain_spawn_time",
				humanReadableTicks(averageRainSpawnTime), averageRainSpawnTime));
		int averageThunderstormSpawnTime = Math.round(results.getAverageThunderstormSpawnTime());
		mainMessage.append("\n");
		mainMessage.append(Component.translatable("gui.simpleclouds.profiler.stat.avg_thunderstorm_spawn_time",
				humanReadableTicks(averageThunderstormSpawnTime), averageThunderstormSpawnTime));
		mainMessage.append("\n");
		mainMessage.append(createMinMaxInfo("gui.simpleclouds.profiler.stat.clouds_existing", results.getCurrentCloudCountStats()));
		MutableObject<Popup> main = new MutableObject<>();
		Map<ResourceLocation, ProfilingCloudGenerator.CloudStats> individualStats = results.getIndividualStats();
		Consumer<ResourceLocation> valueAcceptor = id -> {
			Popup.createInfoPopup(main.getValue(), 300, createIndividualResults(id, individualStats.get(id))).alignLeft();
		};
		main.setValue(Popup.createOptionListPopup(null, builder -> {
			for (ResourceLocation id : individualStats.keySet())
				builder.addObject(Component.literal(id.toString()), id);
		}, valueAcceptor, 300, 100, mainMessage).alignLeft());
	}
	
	private static Component createIndividualResults(ResourceLocation id, ProfilingCloudGenerator.CloudStats stats)
	{
		MutableComponent message = Component.literal(id.toString());
		message.append(Component.translatable("gui.simpleclouds.profiler.stat.individual.total_spawned",
				stats.getTotalSpawned()));
		int averageSpawnTicks = Math.round(stats.getAverageTicksToSpawn());
		message.append(Component.translatable("gui.simpleclouds.profiler.stat.individual.avg_ticks_to_spawn",
				humanReadableTicks(averageSpawnTicks), averageSpawnTicks));
		message.append("\n");
		message.append(createMinMaxTimeInfo("gui.simpleclouds.profiler.stat.time_over_player", stats.getTimeOverPlayer()));
		message.append("\n");
		message.append(createMinMaxInfo("gui.simpleclouds.profiler.stat.speed", stats.getSpeedStats()));
		message.append("\n");
		message.append(createMinMaxInfo("gui.simpleclouds.profiler.stat.radius", stats.getRadiusStats()));
		message.append("\n");
		message.append(createMinMaxInfo("gui.simpleclouds.profiler.stat.stretch_factor", stats.getStretchFactorStats()));
		message.append("\n");
		message.append(createMinMaxTimeInfo("gui.simpleclouds.profiler.stat.exist_time", stats.getExistTicks()));
		message.append("\n");
		message.append(createMinMaxTimeInfo("gui.simpleclouds.profiler.stat.grow_time", stats.getGrowTicks()));
		return message.withStyle(ChatFormatting.YELLOW);
	}

	private static Component createMinMaxTimeInfo(String titleKey, ProfilingCloudGenerator.MinMax minMax)
	{
		return Component.translatable("gui.simpleclouds.profiler.stat.minmax",
				Component.translatable(titleKey),
				humanReadableTicks(minMax.getMin()),
				humanReadableTicks(minMax.getMax()),
				humanReadableTicks(minMax.getAvg()));
	}

	private static Component createMinMaxInfo(String titleKey, ProfilingCloudGenerator.MinMax minMax)
	{
		return Component.translatable("gui.simpleclouds.profiler.stat.minmax",
				Component.translatable(titleKey),
				String.format("%.2f", (float) minMax.getMin()),
				String.format("%.2f", (float) minMax.getMax()),
				String.format("%.3f", (float) minMax.getAvg()));
	}

	private static String humanReadableTicks(float ticks)
	{
		Iterator<Pair<Character, Float>> units = new ObjectArrayIterator<>(Pair.of('s', 20.0F), Pair.of('m', 60.0F), Pair.of('h', 60.0F), Pair.of('d', 24.0F));
		char prevUnit;
		Pair<Character, Float> current = Pair.of('t', 1.0F);
		do
		{
			ticks /= current.getRight();
			prevUnit = current.getLeft();
			if (!units.hasNext())
				break;
			current = units.next();
		}
		while (ticks <= -current.getRight() || ticks >= current.getRight());
		return String.format("%.1f%c", ticks, prevUnit);
	}
}
