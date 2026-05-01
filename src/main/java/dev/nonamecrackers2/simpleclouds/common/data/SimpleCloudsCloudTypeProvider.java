package dev.nonamecrackers2.simpleclouds.common.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

public class SimpleCloudsCloudTypeProvider implements DataProvider {
	private final PackOutput.PathProvider pathProvider;

	public SimpleCloudsCloudTypeProvider(PackOutput output) {
		this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "cloud_types");
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		List<CompletableFuture<?>> futures = Lists.newArrayList();
		Set<ResourceLocation> ids = Sets.newHashSet();

		for (var definition : SourceCloudTypeImporter.loadCloudTypes()) {
			if (!ids.add(definition.id()))
				throw new IllegalArgumentException("Duplicate cloud type " + definition.id());

			futures.add(DataProvider.saveStable(output, definition.json(), this.pathProvider.json(definition.id())));
		}

		return CompletableFuture.allOf(futures.toArray(i -> new CompletableFuture[i]));
	}

	@Override
	public String getName() {
		return "Cloud Types";
	}
}
