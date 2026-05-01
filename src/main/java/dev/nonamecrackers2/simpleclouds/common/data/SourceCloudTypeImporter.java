package dev.nonamecrackers2.simpleclouds.common.data;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public final class SourceCloudTypeImporter {
    private static final String RESOURCE_PATH = "assets/" + SimpleCloudsMod.MODID + "/cloud_types";
    private static final Path SOURCE_DIRECTORY = resolveSourceDirectory();

    private SourceCloudTypeImporter() {
    }

    public static List<SourceCloudTypeDefinition> loadCloudTypes() {
        if (!Files.isDirectory(SOURCE_DIRECTORY))
            throw new IllegalStateException("Could not find source cloud type directory '" + SOURCE_DIRECTORY + "'");

        try (Stream<Path> files = Files.list(SOURCE_DIRECTORY)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(SourceCloudTypeImporter::readCloudType)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list source cloud type files from '" + SOURCE_DIRECTORY + "'",
                    e);
        }
    }

    private static SourceCloudTypeDefinition readCloudType(Path path) {
        String fileName = path.getFileName().toString();
        String idPath = fileName.substring(0, fileName.length() - ".json".length());
        ResourceLocation id = SimpleCloudsMod.id(idPath);

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject object = GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), "root");
            CloudType type = CloudType.readFromJson(id, object);
            return new SourceCloudTypeDefinition(id, type, object);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read source cloud type '" + path + "'", e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to parse source cloud type '" + path + "'", e);
        }
    }

    private static Path resolveSourceDirectory() {
        try {
            var resource = SourceCloudTypeImporter.class.getClassLoader().getResource(RESOURCE_PATH);
            if (resource != null && "file".equalsIgnoreCase(resource.getProtocol()))
                return Paths.get(resource.toURI());
        } catch (Exception ignored) {
        }

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("build.gradle")))
                return current.resolve(Path.of("src", "main", "resources", "assets", SimpleCloudsMod.MODID,
                        "cloud_types"));
            current = current.getParent();
        }

        return Path.of(System.getProperty("user.dir"), "src", "main", "resources", "assets", SimpleCloudsMod.MODID,
                "cloud_types").toAbsolutePath().normalize();
    }

    public record SourceCloudTypeDefinition(ResourceLocation id, CloudType type, JsonObject json) {
    }
}