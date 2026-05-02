package dev.nonamecrackers2.simpleclouds.common.data;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public final class SourceCloudTypeImporter {
    private static final String RESOURCE_PATH = "assets/" + SimpleCloudsMod.MODID + "/cloud_types";

    private SourceCloudTypeImporter() {
    }

    public static List<SourceCloudTypeDefinition> loadCloudTypes() {
        URL resource = SourceCloudTypeImporter.class.getClassLoader().getResource(RESOURCE_PATH);
        if (resource != null) {
            try (ResolvedDirectory directory = resolveBundledDirectory(resource)) {
                return loadCloudTypesFromDirectory(directory.path());
            } catch (Exception e) {
                Path fallbackDirectory = resolveSourceDirectory();
                if (Files.isDirectory(fallbackDirectory))
                    return loadCloudTypesFromDirectory(fallbackDirectory);

                throw new IllegalStateException(
                        "Failed to load bundled cloud types from classpath resource '" + RESOURCE_PATH + "'", e);
            }
        }

        Path sourceDirectory = resolveSourceDirectory();
        return loadCloudTypesFromDirectory(sourceDirectory);
    }

    private static List<SourceCloudTypeDefinition> loadCloudTypesFromDirectory(Path directory) {
        if (!Files.isDirectory(directory))
            throw new IllegalStateException("Could not find source cloud type directory '" + directory + "'");

        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(SourceCloudTypeImporter::readCloudType)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list source cloud type files from '" + directory + "'",
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

    private static ResolvedDirectory resolveBundledDirectory(URL resource) throws Exception {
        URI uri = resource.toURI();
        if ("file".equalsIgnoreCase(uri.getScheme()))
            return new ResolvedDirectory(Paths.get(uri), null);

        if ("jar".equalsIgnoreCase(uri.getScheme())) {
            String raw = uri.toString();
            int separatorIndex = raw.indexOf("!/");
            if (separatorIndex < 0)
                throw new IllegalStateException("Malformed jar resource URI '" + raw + "'");

            URI jarUri = URI.create(raw.substring(0, separatorIndex));
            String entryPath = raw.substring(separatorIndex + 1);

            try {
                FileSystem fileSystem = FileSystems.newFileSystem(jarUri, Map.of());
                return new ResolvedDirectory(fileSystem.getPath(entryPath), fileSystem);
            } catch (FileSystemAlreadyExistsException e) {
                return new ResolvedDirectory(FileSystems.getFileSystem(jarUri).getPath(entryPath), null);
            }
        }

        throw new IllegalStateException("Unsupported resource URI scheme '" + uri.getScheme() + "'");
    }

    private record ResolvedDirectory(Path path, FileSystem ownedFileSystem) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (this.ownedFileSystem != null)
                this.ownedFileSystem.close();
        }
    }

    public record SourceCloudTypeDefinition(ResourceLocation id, CloudType type, JsonObject json) {
    }
}