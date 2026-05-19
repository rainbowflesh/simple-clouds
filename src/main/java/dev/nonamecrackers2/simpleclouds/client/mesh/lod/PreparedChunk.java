package dev.nonamecrackers2.simpleclouds.client.mesh.lod;

import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.common.cloud.SimpleCloudsConstants;
import net.minecraft.world.phys.AABB;

public record PreparedChunk(int lodLevel, int lodScale, int x, int y, int z, int noOcclusionSideMask, AABB bounds) {
	public static final int NEG_X_SIDE = 0;
	public static final int POS_X_SIDE = 1;
	public static final int NEG_Y_SIDE = 2;
	public static final int POS_Y_SIDE = 3;
	public static final int NEG_Z_SIDE = 4;
	public static final int POS_Z_SIDE = 5;

	public static int sideMask(int... sides) {
		int mask = 0;
		for (int side : sides)
			mask |= 1 << side;
		return mask;
	}

	public static PreparedChunk create(int lodLevel, int lodScale, int x, int y, int z, int noOcclusionSideMask) {
		float chunkSize = (float) SimpleCloudsConstants.CHUNK_SIZE * lodScale;
		float maxY = (float) CloudMeshGenerator.VERTICAL_CHUNK_SPAN * (float) SimpleCloudsConstants.CHUNK_SIZE;
		float offsetX = (float) x * chunkSize;
		float offsetZ = (float) z * chunkSize;
		AABB bounds = new AABB(offsetX, -320.0F, offsetZ, offsetX + chunkSize, maxY, offsetZ + chunkSize);
		return new PreparedChunk(lodLevel, lodScale, x, y, z, noOcclusionSideMask, bounds);
	}
}
