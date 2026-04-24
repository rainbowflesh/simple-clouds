package dev.nonamecrackers2.simpleclouds.client.mesh.generator;

import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;

import net.minecraft.util.Mth;

final class CloudMeshTaskScheduler {
	private final List<CloudMeshGenerator.ChunkGenTask> completedTasks = Lists.newArrayList();
	private final Queue<CloudMeshGenerator.ChunkGenTask> pendingTasks = Queues.newArrayDeque();
	private int meshGenInterval = 1;
	private int tasksPerTick;

	public void clear() {
		this.pendingTasks.clear();
		this.completedTasks.clear();
		this.meshGenInterval = 1;
		this.tasksPerTick = 0;
	}

	public boolean hasPendingTasks() {
		return !this.pendingTasks.isEmpty();
	}

	public boolean hasCompletedTasks() {
		return !this.completedTasks.isEmpty();
	}

	public int getPendingTaskCount() {
		return this.pendingTasks.size();
	}

	public int getMeshGenInterval() {
		return this.meshGenInterval;
	}

	public int getTasksPerTick() {
		return this.tasksPerTick;
	}

	public List<CloudMeshGenerator.ChunkGenTask> getCompletedTasks() {
		return this.completedTasks;
	}

	public void clearCompletedTasks() {
		this.completedTasks.clear();
	}

	public void queueTask(CloudMeshGenerator.ChunkGenTask task) {
		this.pendingTasks.add(task);
	}

	public void scheduleNextBatch(Supplier<Integer> intervalCalculator, IntFunction<Integer> batchPreparer) {
		this.meshGenInterval = intervalCalculator.get();
		if (this.meshGenInterval <= 0)
			throw new RuntimeException("Mesh gen interval is <= 0");
		this.tasksPerTick = batchPreparer.apply(this.meshGenInterval);
	}

	public int prepareTasks(int chunkCount, int genInterval, IntPredicate taskQueuer) {
		int queuedChunkCount = 0;
		for (int i = 0; i < chunkCount; i++) {
			if (taskQueuer.test(i))
				queuedChunkCount++;
		}
		return Mth.ceil((float) queuedChunkCount / (float) genInterval);
	}

	public void executeAllPendingTasks(Consumer<CloudMeshGenerator.ChunkGenTask> taskRunner) {
		this.executePendingTasks(this.pendingTasks.size(), taskRunner);
	}

	public void executeScheduledTasks(Consumer<CloudMeshGenerator.ChunkGenTask> taskRunner) {
		this.executePendingTasks(this.tasksPerTick, taskRunner);
	}

	private void executePendingTasks(int maxTasks, Consumer<CloudMeshGenerator.ChunkGenTask> taskRunner) {
		for (int i = 0; i < maxTasks; i++) {
			CloudMeshGenerator.ChunkGenTask task = this.pendingTasks.poll();
			if (task == null)
				break;
			taskRunner.accept(task);
			this.completedTasks.add(task);
		}
	}
}