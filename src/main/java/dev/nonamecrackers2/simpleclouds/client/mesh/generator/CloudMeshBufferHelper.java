package dev.nonamecrackers2.simpleclouds.client.mesh.generator;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;

import dev.nonamecrackers2.simpleclouds.client.mesh.chunk.MeshChunk;
import dev.nonamecrackers2.simpleclouds.client.mesh.lod.PreparedChunk;
import dev.nonamecrackers2.simpleclouds.client.shader.buffer.ShaderStorageBufferObject;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import net.minecraft.util.Mth;

final class CloudMeshBufferHelper {
    private CloudMeshBufferHelper() {
    }

    public static ChunkMeshLayout buildChunkMeshes(List<PreparedChunk> preparedChunks, int opaqueBufferSize,
            int transparentBufferSize, boolean useTransparency, boolean useFixedMeshDataSectionSize) {
        ImmutableList.Builder<MeshChunk> meshChunks = ImmutableList.builder();
        int totalPreparedChunks = preparedChunks.size();
        int opaqueBytesPerChunk = Mth.ceil((float) opaqueBufferSize / (float) totalPreparedChunks);
        int transparentBytesPerChunk = Mth.ceil((float) transparentBufferSize / (float) totalPreparedChunks);
        if (!useFixedMeshDataSectionSize) {
            opaqueBytesPerChunk *= 4;
            transparentBytesPerChunk *= 4;
        }

        int maxOpaqueElements = Mth.floor((float) opaqueBytesPerChunk / (float) CloudMeshGenerator.BYTES_PER_SIDE_INFO);
        int maxTransparentElements = Mth
                .floor((float) transparentBytesPerChunk / (float) CloudMeshGenerator.BYTES_PER_CUBE_INFO);
        int opaqueElementOffset = 0;
        int transparentElementOffset = 0;
        for (PreparedChunk chunk : preparedChunks) {
            meshChunks.add(new MeshChunk(chunk, maxOpaqueElements, opaqueElementOffset,
                    CloudMeshGenerator.BYTES_PER_SIDE_INFO, maxTransparentElements, transparentElementOffset,
                    CloudMeshGenerator.BYTES_PER_CUBE_INFO, useTransparency));
            opaqueElementOffset += maxOpaqueElements;
            transparentElementOffset += maxTransparentElements;
        }

        return new ChunkMeshLayout(meshChunks.build(), opaqueBytesPerChunk, transparentBytesPerChunk);
    }

    public static int createBuffers(ComputeShader shader, int totalChunks, boolean useFixedMeshDataSectionSize,
            String totalCounterName, String countPerChunkName, String elementInfoBufferName, int maxSize) {
        if (!useFixedMeshDataSectionSize) {
            ShaderStorageBufferObject totalCountBuffer = shader.createAndBindSSBO(totalCounterName,
                    GL15.GL_DYNAMIC_COPY);
            totalCountBuffer.allocateBuffer(4);
            totalCountBuffer.writeData(b -> b.putInt(0, 0), 4, false);
        }

        int bufferSize = shader.createAndBindSSBO(elementInfoBufferName, GL15.GL_DYNAMIC_COPY).allocateBuffer(maxSize);

        int countPerChunkBufferSize = totalChunks * 4;
        ShaderStorageBufferObject countPerChunkBuffer = shader.createAndBindSSBO(countPerChunkName,
                GL15.GL_DYNAMIC_COPY);
        countPerChunkBuffer.allocateBuffer(countPerChunkBufferSize);
        countPerChunkBuffer.writeData(b -> {
            for (int i = 0; i < totalChunks; i++)
                b.putInt(0);
            b.rewind();
        }, countPerChunkBufferSize, false);

        return bufferSize;
    }

    public static CloudMeshGenerator.MeshGenStatus copyFixedChunkData(int copyBufferId, int copyBufferSizeBytes,
            Collection<MeshChunk> chunks, Function<MeshChunk, Integer> byteOffsetPerChunk,
            Function<MeshChunk, Integer> chunkBufferId, Function<MeshChunk, Integer> bytesToCopyPerChunk,
            Function<MeshChunk, Integer> bufferSizeBytesPerChunk) {
        CloudMeshGenerator.MeshGenStatus result = CloudMeshGenerator.MeshGenStatus.NORMAL;

        GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, copyBufferId);

        for (MeshChunk chunk : chunks) {
            int bytesToCopy = bytesToCopyPerChunk.apply(chunk);
            if (bytesToCopy > 0) {
                int maxSize = bufferSizeBytesPerChunk.apply(chunk);
                if (bytesToCopy > maxSize) {
                    bytesToCopy = maxSize;
                    result = CloudMeshGenerator.MeshGenStatus.CHUNK_OVERFLOW;
                }

                int byteOffset = byteOffsetPerChunk.apply(chunk);
                if (byteOffset + bytesToCopy > copyBufferSizeBytes) {
                    bytesToCopy = copyBufferSizeBytes - byteOffset;
                    if (bytesToCopy <= 0)
                        continue;
                }

                GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, chunkBufferId.apply(chunk));
                GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, byteOffset, 0,
                        bytesToCopy);
            }
        }

        return result;
    }

    public static CloudMeshGenerator.MeshGenStatus copyPackedChunkData(int copyBufferId, int copyBufferSizeBytes,
            Collection<MeshChunk> chunks, Function<MeshChunk, Integer> chunkBufferId,
            Function<MeshChunk, Integer> bytesToCopyPerChunk, Function<MeshChunk, Integer> bufferSizeBytesPerChunk) {
        CloudMeshGenerator.MeshGenStatus result = CloudMeshGenerator.MeshGenStatus.NORMAL;

        GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, copyBufferId);

        int currentBytes = 0;
        for (MeshChunk chunk : chunks) {
            int totalBytes = bytesToCopyPerChunk.apply(chunk);
            if (totalBytes > 0) {
                int lastBytesOffset = totalBytes;
                int maxSize = bufferSizeBytesPerChunk.apply(chunk);
                if (lastBytesOffset > maxSize) {
                    lastBytesOffset = maxSize;
                    result = CloudMeshGenerator.MeshGenStatus.CHUNK_OVERFLOW;
                }

                boolean stop = false;
                if (currentBytes + lastBytesOffset > copyBufferSizeBytes) {
                    lastBytesOffset = copyBufferSizeBytes - currentBytes;
                    if (lastBytesOffset <= 0)
                        return CloudMeshGenerator.MeshGenStatus.MESH_POOL_OVERFLOW;
                    stop = true;
                }

                GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, chunkBufferId.apply(chunk));
                GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, currentBytes, 0,
                        lastBytesOffset);

                currentBytes += totalBytes;
                if (stop)
                    return CloudMeshGenerator.MeshGenStatus.MESH_POOL_OVERFLOW;
            }
        }

        return result;
    }

    static record ChunkMeshLayout(List<MeshChunk> chunks, int opaqueBytesPerChunk, int transparentBytesPerChunk) {
    }
}