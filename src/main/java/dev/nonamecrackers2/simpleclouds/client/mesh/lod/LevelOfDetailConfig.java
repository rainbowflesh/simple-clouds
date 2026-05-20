package dev.nonamecrackers2.simpleclouds.client.mesh.lod;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class LevelOfDetailConfig {
	private final LevelOfDetail[] lods;
	private final int primaryChunkSpan;
	private final int effectiveChunkSpan;
	private List<PreparedChunk> preparedChunks;
	private int primaryChunkCount;

	public LevelOfDetailConfig(int primaryChunkSpan, LevelOfDetail... lods) {
		this.primaryChunkSpan = primaryChunkSpan;
		this.lods = lods;
		int radius = primaryChunkSpan / 2;
		for (var lod : this.lods)
			radius += lod.chunkScale() * lod.spread();
		this.effectiveChunkSpan = radius * 2;
		this.prepareChunks();
	}

	private void prepareChunks() {
		ImmutableList.Builder<PreparedChunk> builder = ImmutableList.builder();

		int currentRadius = this.primaryChunkSpan / 2;
		int primaryChunkCount = 0;
		for (int r = 0; r <= currentRadius; r++) {
			boolean noOuterOcclusion = this.lods.length > 0 && r == currentRadius;
			for (int x = -r; x < r; x++) {
				int northMask = 0;
				int southMask = 0;
				if (noOuterOcclusion) {
					northMask = PreparedChunk.sideMask(PreparedChunk.NEG_Z_SIDE);
					southMask = PreparedChunk.sideMask(PreparedChunk.POS_Z_SIDE);
					if (x == -r) {
						northMask |= PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE);
						southMask |= PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE);
					} else if (x == r - 1) {
						northMask |= PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE);
						southMask |= PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE);
					}
				}
				builder.add(PreparedChunk.create(0, 1, x, 0, -r, northMask));
				builder.add(PreparedChunk.create(0, 1, x, 0, r - 1, southMask));
				primaryChunkCount += 2;
			}
			for (int z = -r + 1; z < r - 1; z++) {
				int westMask = noOuterOcclusion ? PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE) : 0;
				int eastMask = noOuterOcclusion ? PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE) : 0;
				builder.add(PreparedChunk.create(0, 1, -r, 0, z, westMask));
				builder.add(PreparedChunk.create(0, 1, r - 1, 0, z, eastMask));
				primaryChunkCount += 2;
			}
		}

		for (int i = 0; i < this.lods.length; i++) {
			LevelOfDetail config = this.lods[i];
			int chunkCount = 0;
			int lodLevel = i + 1;
			for (int deltaR = 1; deltaR <= config.spread(); deltaR++) {
				boolean noInnerOcclusion = deltaR == 1;
				boolean noOuterOcclusion = i < this.lods.length - 1 && deltaR == config.spread();
				int r = currentRadius / config.chunkScale() + deltaR;
				for (int x = -r; x < r; x++) {
					int northMask = 0;
					int southMask = 0;
					if (noInnerOcclusion) {
						northMask = PreparedChunk.sideMask(PreparedChunk.POS_Z_SIDE);
						southMask = PreparedChunk.sideMask(PreparedChunk.NEG_Z_SIDE);
						if (x == -r) {
							northMask |= PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE);
							southMask |= PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE);
						} else if (x == r - 1) {
							northMask |= PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE);
							southMask |= PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE);
						}
					}
					if (noOuterOcclusion) {
						northMask |= PreparedChunk.sideMask(PreparedChunk.NEG_Z_SIDE);
						southMask |= PreparedChunk.sideMask(PreparedChunk.POS_Z_SIDE);
						if (x == -r) {
							northMask |= PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE);
							southMask |= PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE);
						} else if (x == r - 1) {
							northMask |= PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE);
							southMask |= PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE);
						}
					}
					builder.add(PreparedChunk.create(lodLevel, config.chunkScale(), x, 0, -r, northMask));
					builder.add(PreparedChunk.create(lodLevel, config.chunkScale(), x, 0, r - 1, southMask));
					chunkCount += 2;
				}
				for (int z = -r + 1; z < r - 1; z++) {
					int westMask = noInnerOcclusion ? PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE) : 0;
					int eastMask = noInnerOcclusion ? PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE) : 0;
					if (noOuterOcclusion) {
						westMask |= PreparedChunk.sideMask(PreparedChunk.NEG_X_SIDE);
						eastMask |= PreparedChunk.sideMask(PreparedChunk.POS_X_SIDE);
					}
					builder.add(PreparedChunk.create(lodLevel, config.chunkScale(), -r, 0, z, westMask));
					builder.add(PreparedChunk.create(lodLevel, config.chunkScale(), r - 1, 0, z, eastMask));
					chunkCount += 2;
				}
			}
			currentRadius = currentRadius + config.spread() * config.chunkScale();
			config.setChunkCount(chunkCount);
		}

		this.primaryChunkCount = primaryChunkCount;
		this.preparedChunks = builder.build();
	}

	public List<PreparedChunk> getPreparedChunks() {
		return this.preparedChunks;
	}

	public LevelOfDetail[] getLods() {
		return this.lods;
	}

	public int getPrimaryChunkSpan() {
		return this.primaryChunkSpan;
	}

	public int getEffectiveChunkSpan() {
		return this.effectiveChunkSpan;
	}

	public int getPrimaryChunkCount() {
		return this.primaryChunkCount;
	}
}
