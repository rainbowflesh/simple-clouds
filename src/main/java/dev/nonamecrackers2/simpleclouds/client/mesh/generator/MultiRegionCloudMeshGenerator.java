package dev.nonamecrackers2.simpleclouds.client.mesh.generator;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL42;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.mesh.lod.LevelOfDetail;
import dev.nonamecrackers2.simpleclouds.client.mesh.lod.LevelOfDetailConfig;
import dev.nonamecrackers2.simpleclouds.client.mesh.lod.PreparedChunk;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.BindingManager;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.ShaderStorageBufferObject;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudInfo;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudGetter;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.noise.AbstractNoiseSettings;
import dev.nonamecrackers2.simpleclouds.common.noise.NoiseSettings;
import net.minecraft.CrashReportCategory;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class MultiRegionCloudMeshGenerator extends CloudMeshGenerator {
	private static final Logger LOGGER = LogManager.getLogger("simpleclouds/MultiRegionCloudMeshGenerator");

	private static final ResourceLocation REGION_GENERATOR_LOC = SimpleCloudsMod.id("cloud_regions");
	private static final String LOD_SCALES_NAME = "LodScales";
	private static final String CLOUD_REGIONS_NAME = "CloudRegions";
	public static final int MAX_CLOUD_TYPES = 64;
	private static final int BYTES_PER_REGION = 32;
	private int requiredRegionTexSize;
	private CloudGetter cloudGetter = CloudGetter.EMPTY;
	private CloudInfo[] cachedTypes = new CloudInfo[0];
	private final Map<CloudInfo, Integer> cloudTypeIndices = new IdentityHashMap<>();
	private @Nullable ComputeShader regionTextureGenerator;
	private int cloudRegionTextureId = -1;
	private int cloudRegionImageBinding = -1;
	private boolean updateCloudTypes;
	private int currentCloudFormationCount;
	private int currentCloudFormationCapacity;

	protected MultiRegionCloudMeshGenerator(boolean fadeNearOrigin, boolean shadedClouds, LevelOfDetailConfig lodConfig,
			Supplier<Integer> meshGenIntervalCalculator, boolean useTransparency, boolean fixedMeshDataSectionSize) {
		super(CloudMeshGenerator.MAIN_CUBE_MESH_GENERATOR, 0, fadeNearOrigin, shadedClouds, lodConfig,
				meshGenIntervalCalculator, useTransparency, fixedMeshDataSectionSize);
	}

	public void setCloudGetter(CloudGetter getter) {
		this.cloudGetter = Objects.requireNonNull(getter, "Cloud getter cannot be null");
		this.updateCloudTypes();
	}

	public int getCloudRegionTextureId() {
		return this.cloudRegionTextureId;
	}

	public void updateCloudTypes() {
		this.updateCloudTypes = true;
	}

	public int getTotalCloudTypes() {
		return this.cachedTypes.length;
	}

	public int getCloudFormationCount() {
		return this.currentCloudFormationCount;
	}

	public int getCloudFormationCapacity() {
		return this.currentCloudFormationCapacity;
	}

	@Override
	protected void setupShader() {
		super.setupShader();

		this.cachedTypes = new CloudInfo[0];
		this.cloudTypeIndices.clear();
		this.updateCloudTypes = false;

		this.shader.createAndBindSSBO(NOISE_LAYERS_NAME, GL15.GL_STATIC_DRAW)
				.allocateBuffer(AbstractNoiseSettings.Param.values().length * 4 * MAX_NOISE_LAYERS * MAX_CLOUD_TYPES);
		this.shader.createAndBindSSBO(LAYER_GROUPINGS_NAME, GL15.GL_STATIC_DRAW)
				.allocateBuffer(CloudInfo.BYTES_PER_TYPE * MAX_CLOUD_TYPES);

		this.uploadCloudTypeData();
	}

	@Override
	protected void initExtra(ResourceManager manager) throws IOException {
		// Cloud region texture generator compute shader
		// This texture is a 2D array texture, with a texture for each level of detail.
		// The red channel contains the index for a cloud type in the main mesh compute
		// shader, and
		// the green channel contains an "edge fade" value for smooth cloud region
		// boundaries.
		// When generating the cloud mesh, the main mesh compute shader samples this
		// array texture
		// depending on what LOD it is generating for to determine what cloud type to
		// construct

		// Create the compute shader

		this.currentCloudFormationCount = 0;
		this.currentCloudFormationCapacity = 0;
		this.requiredRegionTexSize = 0;

		if (this.regionTextureGenerator != null)
			this.regionTextureGenerator.close();

		var params = ImmutableMap.of("EDGE_FADE_FACTOR", String.valueOf(SimpleCloudsConstants.REGION_EDGE_FADE_FACTOR));
		this.regionTextureGenerator = ComputeShader.loadShader(REGION_GENERATOR_LOC, manager, 16, 16,
				this.lodConfig.getLods().length + 1, params);

		ShaderStorageBufferObject lodScales = this.regionTextureGenerator.createAndBindSSBO(LOD_SCALES_NAME,
				GL15.GL_STATIC_READ);
		int lodScalesSize = this.lodConfig.getLods().length * 4 + 4;
		lodScales.allocateBuffer(lodScalesSize);
		lodScales.writeData(b -> {
			b.putFloat(1.0F); // Primary chunk scale
			for (LevelOfDetail l : this.lodConfig.getLods())
				b.putFloat((float) l.chunkScale());
			b.rewind();
		}, lodScalesSize, false);

		// Data for the cloud regions in world
		this.regionTextureGenerator.createAndBindSSBO(CLOUD_REGIONS_NAME, GL15.GL_STATIC_READ);
		this.ensureCloudRegionCapacity(1);

		// Create the cloud region 2D array texture

		// Here we calculate the maximum size we need for this array texture,
		// ensuring each block in the mesh will have a value to read in this texture
		// when doing mesh generation
		int prevSpan = this.lodConfig.getPrimaryChunkSpan();
		int prevScale = 1;
		int largestSpan = prevSpan;
		for (LevelOfDetail config : this.lodConfig.getLods()) {
			int scale = config.chunkScale();
			int div = scale / prevScale;
			prevScale = scale;
			prevSpan = prevSpan / div + config.spread() * 2;
			if (prevSpan > largestSpan)
				largestSpan = prevSpan;
		}
		this.requiredRegionTexSize = largestSpan * SimpleCloudsConstants.CHUNK_SIZE;

		if (this.cloudRegionTextureId != -1) {
			TextureUtil.releaseTextureId(this.cloudRegionTextureId);
			this.cloudRegionTextureId = -1;
		}

		this.cloudRegionTextureId = TextureUtil.generateTextureId();
		GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, this.cloudRegionTextureId);
		GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_RG32F, this.requiredRegionTexSize,
				this.requiredRegionTexSize, this.lodConfig.getLods().length + 1, 0, GL30.GL_RG, GL11.GL_FLOAT,
				(IntBuffer) null);
		GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);

		// Assign an image unit to it so any shader can access it
		if (this.cloudRegionImageBinding != -1)
			BindingManager.freeImageUnit(this.cloudRegionImageBinding);
		this.cloudRegionImageBinding = BindingManager.getAvailableImageUnit();
		BindingManager.useImageUnit(this.cloudRegionImageBinding);
		GL42.glBindImageTexture(this.cloudRegionImageBinding, this.cloudRegionTextureId, 0, true, 0, GL15.GL_WRITE_ONLY,
				GL30.GL_RG32F);
		this.regionTextureGenerator.setImageUnit("regionTexture", this.cloudRegionImageBinding);

		this.runRegionGenerator(0.0F, 0.0F, 1.0F);

		// Update the main mesh shader to use this texture
		this.shader.setSampler2DArray("RegionsSampler", this.cloudRegionTextureId, 0);
		this.shader.forUniform("RegionsTexSize", (id, loc) -> {
			GL41.glProgramUniform1i(id, loc, this.requiredRegionTexSize);
		});

		LOGGER.debug("Created cloud region texture generator with size {}x{}x{}", this.requiredRegionTexSize,
				this.requiredRegionTexSize, this.lodConfig.getLods().length + 1);
	}

	@Override
	protected CloudMeshGenerator.ChunkGenSettings determineChunkGenSettings(float minX, float minZ, float maxX,
			float maxZ) {
		int largestEndHeight = 0;
		boolean empty = true;
		float centerX = (minX + maxX) * 0.5F;
		float centerZ = (minZ + maxZ) * 0.5F;

		Pair<CloudType, Float> typeAt = this.cloudGetter.getCloudTypeAtPosition(minX, minZ);
		if (typeAt.getRight() < 1.0F)
			empty = false;
		largestEndHeight = typeAt.getLeft().noiseConfig().getEndHeight();

		typeAt = this.cloudGetter.getCloudTypeAtPosition(minX, maxZ);
		if (typeAt.getRight() < 1.0F)
			empty = false;
		largestEndHeight = Math.max(largestEndHeight, typeAt.getLeft().noiseConfig().getEndHeight());

		typeAt = this.cloudGetter.getCloudTypeAtPosition(maxX, minZ);
		if (typeAt.getRight() < 1.0F)
			empty = false;
		largestEndHeight = Math.max(largestEndHeight, typeAt.getLeft().noiseConfig().getEndHeight());

		typeAt = this.cloudGetter.getCloudTypeAtPosition(maxX, maxZ);
		if (typeAt.getRight() < 1.0F)
			empty = false;
		largestEndHeight = Math.max(largestEndHeight, typeAt.getLeft().noiseConfig().getEndHeight());

		typeAt = this.cloudGetter.getCloudTypeAtPosition(centerX, centerZ);
		if (typeAt.getRight() < 1.0F)
			empty = false;
		largestEndHeight = Math.max(largestEndHeight, typeAt.getLeft().noiseConfig().getEndHeight());

		if (empty || largestEndHeight == 0)
			return skip();
		else
			return heights(0, largestEndHeight);
	}

	@Override
	protected void generateChunk(CloudMeshGenerator.ChunkGenTask task) {
		this.shader.forUniform("RegionSampleOffset", (id, loc) -> {
			PreparedChunk chunk = task.chunk().getChunkInfo();
			GL41.glProgramUniform2f(id, loc,
					chunk.x() * (float) SimpleCloudsConstants.CHUNK_SIZE + (float) this.requiredRegionTexSize / 2.0F,
					chunk.z() * (float) SimpleCloudsConstants.CHUNK_SIZE + (float) this.requiredRegionTexSize / 2.0F);
		});
		this.shader.setSampler2DArray("RegionsSampler", this.cloudRegionTextureId, 0);

		super.generateChunk(task);
	}

	private void runRegionGenerator(float meshOffsetX, float meshOffsetZ, float partialTick) {
		if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid())
			return;

		this.uploadCloudRegionData(meshOffsetX, meshOffsetZ, partialTick);
		this.regionTextureGenerator.forUniform("Offset", (id, loc) -> {
			GL41.glProgramUniform2f(id, loc, meshOffsetX, meshOffsetZ);
		});
		this.regionTextureGenerator.dispatchAndWait(this.requiredRegionTexSize / 16, this.requiredRegionTexSize / 16,
				1);
	}

	private void uploadCloudRegionData(float meshOffsetX, float meshOffsetZ, float partialTick) {
		if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid())
			return;

		List<CloudRegion> regions = this.cloudGetter.getClouds();
		int regionDataSize = 0;
		for (CloudRegion region : regions) {
			CloudInfo type = this.cloudGetter.getCloudTypeForId(region.getCloudTypeId());
			if (this.cloudTypeIndices.containsKey(type))
				regionDataSize++;
		}

		int count = regionDataSize;
		this.currentCloudFormationCount = regionDataSize;
		this.ensureCloudRegionCapacity(Math.max(1, count));

		if (count > 0) {
			List<CloudRegion> selectedRegions = regions.stream()
					.filter(region -> this.cloudTypeIndices
							.containsKey(this.cloudGetter.getCloudTypeForId(region.getCloudTypeId())))
					.sorted(Comparator.comparingDouble(region -> {
						float dx = region.getPosX(partialTick) - meshOffsetX;
						float dz = region.getPosZ(partialTick) - meshOffsetZ;
						return dx * dx + dz * dz;
					}))
					.limit(count)
					.toList();

			ShaderStorageBufferObject regionsBuffer = this.regionTextureGenerator
					.getShaderStorageBuffer(CLOUD_REGIONS_NAME);
			regionsBuffer.writeData(b -> {
				for (CloudRegion region : selectedRegions) {
					Integer typeIndex = this.cloudTypeIndices
							.get(this.cloudGetter.getCloudTypeForId(region.getCloudTypeId()));
					if (typeIndex == null)
						continue;

					Matrix2f transform = region.createTransform(partialTick);
					b.putFloat(region.getPosX(partialTick));
					b.putFloat(region.getPosZ(partialTick));
					b.putFloat(typeIndex.floatValue());
					b.putFloat(region.getRadius(partialTick));
					b.putFloat(transform.m00);
					b.putFloat(transform.m01);
					b.putFloat(transform.m10);
					b.putFloat(transform.m11);
				}
				b.rewind();
			}, count * BYTES_PER_REGION, false);
		}

		this.regionTextureGenerator.forUniform("TotalCloudRegions", (id, loc) -> {
			GL41.glProgramUniform1i(id, loc, count);
		});
	}

	private void uploadCloudTypeData() {
		RenderSystem.assertOnRenderThreadOrInit();

		if (this.shader != null && this.shader.isValid()) {
			var toCopy = this.cloudGetter.getIndexedCloudTypes();
			if (toCopy.length > MAX_CLOUD_TYPES)
				LOGGER.warn("Cloud type count exceeds the maximum. Not all cloud types will render.");
			int copySize = Math.min(MAX_CLOUD_TYPES, toCopy.length);
			this.cachedTypes = Arrays.copyOf(toCopy, copySize);
			this.cloudTypeIndices.clear();
			for (int i = 0; i < this.cachedTypes.length; i++)
				this.cloudTypeIndices.put(this.cachedTypes[i], i);

			LOGGER.debug("Uploading cloud type noise data...");

			this.shader.getShaderStorageBuffer(LAYER_GROUPINGS_NAME).writeData(b -> {
				int previousLayerIndex = 0;
				for (CloudInfo type : this.cachedTypes) {
					previousLayerIndex = type.packToBuffer(b, previousLayerIndex);
				}
				b.rewind();
			}, CloudInfo.BYTES_PER_TYPE * this.cachedTypes.length, false);

			this.shader.getShaderStorageBuffer(NOISE_LAYERS_NAME).writeData(b -> {
				for (CloudInfo type : this.cachedTypes) {
					NoiseSettings settings = type.noiseConfig();
					float[] packed = settings.packForShader();
					for (int j = 0; j < packed.length
							&& j < AbstractNoiseSettings.Param.values().length * MAX_NOISE_LAYERS; j++)
						b.putFloat(packed[j]);
				}
				b.rewind();
			}, AbstractNoiseSettings.Param.values().length * 4 * MAX_NOISE_LAYERS * this.cachedTypes.length, false);
		}
	}

	@Override
	protected int prepareMeshGen(double originX, double originY, double originZ, float meshGenOffsetX,
			float meshGenOffsetZ, @Nullable Frustum frustum, int interval, float partialTick) {
		if (this.updateCloudTypes) {
			this.uploadCloudTypeData();
			this.updateCloudTypes = false;
		}

		this.runRegionGenerator(meshGenOffsetX, meshGenOffsetZ, partialTick);

		return super.prepareMeshGen(originX, originY, originZ, meshGenOffsetX, meshGenOffsetZ, frustum, interval,
				partialTick);
	}

	@Override
	protected void onOffGen() {
		super.onOffGen();

		if (this.regionTextureGenerator != null)
			this.regionTextureGenerator.getShaderStorageBuffer("CloudRegions").readData(buf -> {
			}, BYTES_PER_REGION * Math.max(1, this.currentCloudFormationCapacity));
	}

	@Override
	public void close() {
		super.close();

		this.currentCloudFormationCount = 0;
		this.currentCloudFormationCapacity = 0;
		this.requiredRegionTexSize = 0;
		this.updateCloudTypes = false;
		this.cloudGetter = CloudGetter.EMPTY;
		this.cachedTypes = new CloudInfo[0];

		if (this.regionTextureGenerator != null) {
			this.regionTextureGenerator.close();
			this.regionTextureGenerator = null;
		}

		if (this.cloudRegionTextureId != -1) {
			TextureUtil.releaseTextureId(this.cloudRegionTextureId);
			this.cloudRegionTextureId = -1;
		}

		if (this.cloudRegionImageBinding != -1) {
			BindingManager.freeImageUnit(this.cloudRegionImageBinding);
			this.cloudRegionImageBinding = -1;
		}
	}

	@Override
	public void fillReport(CrashReportCategory category) {
		category.setDetail("Cloud Types",
				"(" + this.cachedTypes.length + ") " + Joiner.on(", ").join(this.cachedTypes));
		category.setDetail("Cloud Regions", this.cloudGetter.getClouds().size());
		category.setDetail("Cloud Formation Capacity", this.currentCloudFormationCapacity);
		category.setDetail("Cloud Formations", this.currentCloudFormationCount);
		super.fillReport(category);
	}

	private void ensureCloudRegionCapacity(int requiredCapacity) {
		if (this.regionTextureGenerator == null || !this.regionTextureGenerator.isValid())
			return;
		if (requiredCapacity <= 0)
			requiredCapacity = 1;
		if (requiredCapacity == this.currentCloudFormationCapacity)
			return;

		this.regionTextureGenerator.getShaderStorageBuffer(CLOUD_REGIONS_NAME)
				.allocateBuffer(requiredCapacity * BYTES_PER_REGION);
		this.currentCloudFormationCapacity = requiredCapacity;
	}
}
