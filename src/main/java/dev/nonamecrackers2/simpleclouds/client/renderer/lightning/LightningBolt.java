package dev.nonamecrackers2.simpleclouds.client.renderer.lightning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class LightningBolt {
	private static final int TOTAL_TIME = 60;
	private static final int STAY_DURATION = 10;
	private static final int FADE_IN_TIME = 2;
	private static final int FADE_OUT_TIME = 20;
	private static final float FLASH_INTENSITY = 2.0F;
	private static final float RESTRUCTURE_FADE_LIMIT = 0.1F;
	private static final int BRANCH_SEQUENCE_FADE_DURATION = 5;
	private static final float BRANCH_OCCLUSION_EPSILON_SQ = 0.04F;
	public static final int MAX_DEPTH = 16;
	public static final int MAX_BRANCHES = 8;
	public static final float MINIMUM_PITCH_ALLOWED = 0.0F;
	public static final float MAXIMUM_PITCH_ALLOWED = 180.0F;
	private final RandomSource random;
	private final int totalDepth;
	private final int branchCount;
	private final float maxBranchLength;
	private final float maxWidth;
	private final float maxPitch;
	private final float minPitch;
	private final Vector3f startPosition;
	private final Vector3f targetPosition;
	private final List<Vector3f> mainTrunkPoints;
	private List<LightningBolt.Branch> root;
	private int tickCount;
	private float fade;
	private float fadeO;
	private float r;
	private float g;
	private float b;

	public LightningBolt(RandomSource random, Vector3f startPosition, Vector3f targetPosition, int depth,
			int branchCount, float maxBranchLength, float maxWidth, float minimumPitch, float maximumPitch, float r,
			float g, float b) {
		this.random = random;
		this.totalDepth = Mth.clamp(depth, 1, MAX_DEPTH);
		this.branchCount = Mth.clamp(branchCount, 1, MAX_BRANCHES);
		this.maxBranchLength = maxBranchLength;
		this.maxWidth = maxWidth;
		this.minPitch = Mth.clamp(minimumPitch, MINIMUM_PITCH_ALLOWED, MAXIMUM_PITCH_ALLOWED);
		this.maxPitch = Mth.clamp(maximumPitch, minimumPitch, MAXIMUM_PITCH_ALLOWED);
		this.startPosition = startPosition;
		this.targetPosition = targetPosition;
		this.mainTrunkPoints = buildMainTrunkPoints(random, new Vector3f(targetPosition).sub(startPosition).length(),
				maxWidth);
		this.root = buildBranchesWithChildren(random, depth, 0, branchCount, maxBranchLength, maxWidth, maxWidth,
				maximumPitch, minimumPitch);
		this.r = r;
		this.g = g;
		this.b = b;
	}

	private static List<Vector3f> buildMainTrunkPoints(RandomSource random, float strikeLength, float maxWidth) {
		List<Vector3f> points = Lists.newArrayList();
		points.add(new Vector3f());
		if (strikeLength <= 0.01F) {
			points.add(new Vector3f(0.0F, -1.0F, 0.0F));
			return points;
		}

		int requiredBendCount = 4;
		int bendCount = requiredBendCount + random.nextInt(2);
		float maxLateralOffset = Math.max(maxWidth * 0.75F, Mth.clamp(strikeLength * 0.03F, 6.0F, 18.0F));
		int[] requiredDirections = shuffleRequiredDirections(random);
		float previousX = 0.0F;
		float previousZ = 0.0F;
		for (int i = 1; i <= bendCount; i++) {
			float progress = (float) i / (float) (bendCount + 1);
			float offsetLimit = Mth.sin(progress * (float) Math.PI) * maxLateralOffset;
			float x;
			float z;
			if (i <= requiredBendCount) {
				Vector3f directionalOffset = createDirectionalOffset(random, requiredDirections[i - 1], offsetLimit);
				x = directionalOffset.x;
				z = directionalOffset.z;
			} else {
				x = nextZigZagOffset(random, previousX, offsetLimit);
				z = nextZigZagOffset(random, previousZ, offsetLimit);
			}
			points.add(new Vector3f(x, -strikeLength * progress, z));
			previousX = x;
			previousZ = z;
		}

		points.add(new Vector3f(0.0F, -strikeLength, 0.0F));
		return points;
	}

	private static int[] shuffleRequiredDirections(RandomSource random) {
		int[] directions = new int[] { 0, 1, 2, 3 };
		for (int i = directions.length - 1; i > 0; i--) {
			int swapIndex = random.nextInt(i + 1);
			int value = directions[i];
			directions[i] = directions[swapIndex];
			directions[swapIndex] = value;
		}
		return directions;
	}

	private static Vector3f createDirectionalOffset(RandomSource random, int direction, float limit) {
		if (limit <= 0.01F)
			return new Vector3f();

		float primary = limit * (0.45F + random.nextFloat() * 0.4F);
		float secondary = limit * (0.1F + random.nextFloat() * 0.25F);
		float secondarySign = random.nextBoolean() ? 1.0F : -1.0F;
		return switch (direction) {
			case 0 -> new Vector3f(primary, 0.0F, secondary * secondarySign);
			case 1 -> new Vector3f(-primary, 0.0F, secondary * secondarySign);
			case 2 -> new Vector3f(secondary * secondarySign, 0.0F, primary);
			case 3 -> new Vector3f(secondary * secondarySign, 0.0F, -primary);
			default -> new Vector3f();
		};
	}

	private static float nextZigZagOffset(RandomSource random, float previousOffset, float limit) {
		if (limit <= 0.01F)
			return 0.0F;

		float sign;
		if (Math.abs(previousOffset) <= 0.1F)
			sign = random.nextBoolean() ? 1.0F : -1.0F;
		else
			sign = -Math.signum(previousOffset);
		if (random.nextFloat() < 0.2F)
			sign *= -1.0F;

		float magnitude = limit * (0.35F + random.nextFloat() * 0.65F);
		return Mth.clamp(sign * magnitude, -limit, limit);
	}

	private static float calculateWidthAtDepth(int maxDepth, int desiredDepth, float maxWidth) {
		float width = maxWidth;
		for (int i = 0; i < desiredDepth; i++) {
			if (width <= 0.5F)
				return 0.5F;
			width = width - maxWidth / (float) (maxDepth + 1);
		}
		return width;
	}

	private static List<LightningBolt.Branch> buildBranchesWithChildren(RandomSource random, int totalDepth,
			int currentDepth, int branchCount, float maxBranchLength, float maxWidth, float width, float minPitch,
			float maxPitch) {
		if (currentDepth >= totalDepth)
			return Lists.newArrayList();
		List<LightningBolt.Branch> branches = Lists.newArrayList();
		for (int i = 0; i < branchCount; i++) {
			float pitch = (maxPitch - minPitch) * random.nextFloat() + minPitch;
			float yaw = 360.0F * random.nextFloat();
			float length = maxBranchLength / 4.0F + maxBranchLength * random.nextFloat();
			float nextWidth = Math.max(0.5F, width - maxWidth / (float) (totalDepth + 1));
			int range = Mth.floor(
					(float) totalDepth - 1.0F / (float) totalDepth * ((float) currentDepth * (float) currentDepth));
			int nextBranchCount = range <= 0 ? 0 : Math.min(random.nextInt(range), branchCount);
			if ((float) currentDepth / (float) totalDepth < 0.5F)
				nextBranchCount = Math.max(nextBranchCount, 1);
			List<LightningBolt.Branch> children = buildBranchesWithChildren(random, totalDepth, currentDepth + 1,
					nextBranchCount, maxBranchLength, maxWidth, nextWidth, minPitch, maxPitch);
			LightningBolt.Branch branch = new LightningBolt.Branch(children, pitch, yaw, width, length);
			branches.add(branch);
		}
		return branches;
	}

	private static @Nullable List<LightningBolt.Branch> getBranchesAtDepth(List<LightningBolt.Branch> root, int atDepth,
			int currentDepth) {
		if (currentDepth == atDepth)
			return root;
		for (LightningBolt.Branch branch : root) {
			var list = getBranchesAtDepth(branch.branches, atDepth, currentDepth + 1);
			if (list != null)
				return list;
		}
		return null;
	}

	private List<LightningBolt.Branch> getBranchesAtDepth(int depth) {
		var branches = getBranchesAtDepth(this.root, depth, 0);
		if (branches == null)
			return Lists.newArrayList();
		else
			return branches;
	}

	public void tick() {
		this.tickCount++;

		if (this.tickCount < TOTAL_TIME) {
			this.fadeO = this.fade;
			this.fade = 1.0F;
			if (this.tickCount < STAY_DURATION)
				this.fade = Math.min(1.0F, (float) this.tickCount / (float) FADE_IN_TIME);
			else
				this.fade = Math.max(0.0F, 1.0F - (float) (this.tickCount - STAY_DURATION) / (float) FADE_OUT_TIME);

			this.fade = this.fade * (float) Math.pow((double) this.random.nextFloat(), (double) FLASH_INTENSITY);

			if (this.fade > RESTRUCTURE_FADE_LIMIT) {
				int maxDepth = this.totalDepth
						- Mth.floor((float) this.totalDepth * ((float) this.tickCount / (float) TOTAL_TIME));
				int depth = this.totalDepth - (maxDepth <= 1 ? 0 : this.random.nextInt(maxDepth));
				float width = calculateWidthAtDepth(this.totalDepth, depth, this.maxWidth);
				List<LightningBolt.Branch> branches = this.getBranchesAtDepth(depth);
				if (!branches.isEmpty()) {
					int index = branches.size() <= 1 ? 0 : this.random.nextInt(branches.size());
					LightningBolt.Branch branch = branches.get(index);
					branch.setBranches(
							buildBranchesWithChildren(this.random, this.totalDepth - depth, 0, this.branchCount,
									this.maxBranchLength, this.maxWidth, width, this.minPitch, this.maxPitch));
				}
			}
		}
	}

	public boolean isDead() {
		return this.tickCount > TOTAL_TIME;
	}

	public void render(PoseStack stack, VertexConsumer consumer, float partialTick, float r, float g, float b,
			float a, @Nullable ClientLevel level, double camX, double camY, double camZ) {
		float alpha = Mth.lerp(partialTick, this.fadeO, this.fade) * a;
		if (alpha <= 0.01F)
			return;

		Vector3f strikeVector = new Vector3f(this.targetPosition).sub(this.startPosition);
		float strikeLength = strikeVector.length();
		if (strikeLength <= 0.01F)
			return;
		strikeVector.normalize();
		OcclusionContext occlusionContext = level == null ? null
				: new OcclusionContext(level, new Vec3(camX, camY, camZ), new HashMap<>());

		stack.pushPose();
		stack.translate(this.startPosition.x, this.startPosition.y, this.startPosition.z);
		stack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, -1.0F, 0.0F), strikeVector));
		renderMainChannel(stack, consumer, this.mainTrunkPoints, this.maxWidth, r * this.r, g * this.g, b * this.b,
				alpha);

		float animFactor = ((float) this.tickCount + partialTick) / (float) BRANCH_SEQUENCE_FADE_DURATION;
		int depth = Mth.floor((float) this.totalDepth * animFactor);

		for (LightningBolt.Branch branch : this.root)
			this.renderBranch(depth, 0, new Vector3f(), stack, consumer, r * this.r, g * this.g, b * this.b, alpha,
					branch, occlusionContext, camX, camY, camZ);

		stack.popPose();
	}

	public Vector3f getPosition() {
		return this.startPosition;
	}

	public Vector3f getTargetPosition() {
		return this.targetPosition;
	}

	public float getFade(float partialTick) {
		return Mth.lerp(partialTick, this.fadeO, this.fade);
	}

	private void renderBranch(int maxDepth, int currentDepth, Vector3f offset, PoseStack stack,
			VertexConsumer consumer, float r, float g, float b, float a, LightningBolt.Branch branch,
			@Nullable OcclusionContext occlusionContext, double camX, double camY, double camZ) {
		if (currentDepth > maxDepth)
			return;
		stack.pushPose();
		stack.translate(offset.x, offset.y, offset.z);
		stack.mulPose(Axis.YP.rotationDegrees(branch.yaw));
		stack.mulPose(Axis.XP.rotationDegrees(branch.pitch));
		Matrix4f mat = stack.last().pose();
		Vector3f startPos = transformPosition(mat, 0.0F, 0.0F, 0.0F);
		startPos.add((float) camX, (float) camY, (float) camZ);
		Vector3f endPos = transformPosition(mat, 0.0F, -branch.length, 0.0F);
		endPos.add((float) camX, (float) camY, (float) camZ);
		boolean occluded = occlusionContext != null && isBranchSegmentOccluded(occlusionContext, startPos, endPos);
		if (!occluded) {
			int layers = 4;
			for (int i = 0; i < layers; i++) {
				float factor = (float) i / (float) layers;
				float width = branch.width - 4.0F * (branch.width / 4.0F) * factor;
				if (width <= 0.05F)
					continue;
				float length = branch.length - factor;
				float startingY = -factor * 0.5F;
				float alpha = (float) (i + 1) / (float) layers * 0.5F;
				lightningBoltSection(mat, consumer, startingY, width, length, r, g, b, alpha * a);
			}
		}
		float yawRadians = branch.yaw * ((float) Math.PI / 180.0F);
		float pitchRadians = (90.0F - branch.pitch) * ((float) Math.PI / 180.0F);
		float pitchCos = Mth.cos(pitchRadians);
		Vector3f end = new Vector3f(Mth.sin(yawRadians) * pitchCos, Mth.sin(pitchRadians),
				Mth.cos(yawRadians) * pitchCos).mul(-branch.length).add(offset);
		stack.popPose();
		for (LightningBolt.Branch child : branch.branches)
			this.renderBranch(maxDepth, currentDepth + 1, end, stack, consumer, r, g, b, a, child, occlusionContext,
					camX, camY, camZ);
	}

	private static Vector3f transformPosition(Matrix4f matrix, float x, float y, float z) {
		Vector3f transformed = new Vector3f(x, y, z);
		matrix.transformPosition(transformed);
		return transformed;
	}

	private static boolean isBranchSegmentOccluded(OcclusionContext context, Vector3f worldStart, Vector3f worldEnd) {
		return !isPointVisible(context, worldStart) && !isPointVisible(context, worldEnd);
	}

	private static boolean isPointVisible(OcclusionContext context, Vector3f point) {
		BlockPos blockPos = BlockPos.containing(point.x, point.y, point.z);
		long key = blockPos.asLong();
		Boolean cached = context.visibilityCache.get(key);
		if (cached != null)
			return cached;

		Vec3 target = new Vec3(point.x, point.y, point.z);
		double pointDistSq = context.cameraPos.distanceToSqr(target);
		if (pointDistSq <= BRANCH_OCCLUSION_EPSILON_SQ) {
			context.visibilityCache.put(key, Boolean.TRUE);
			return true;
		}

		BlockHitResult hit = context.level.clip(
				new ClipContext(context.cameraPos, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
						CollisionContext.empty()));
		boolean visible;
		if (hit.getType() == HitResult.Type.MISS) {
			visible = true;
		} else {
			double hitDistSq = context.cameraPos.distanceToSqr(hit.getLocation());
			visible = hitDistSq + BRANCH_OCCLUSION_EPSILON_SQ >= pointDistSq;
		}

		context.visibilityCache.put(key, visible);
		return visible;
	}

	private static record OcclusionContext(ClientLevel level, Vec3 cameraPos, Map<Long, Boolean> visibilityCache) {
	}

	private static void renderMainChannel(PoseStack stack, VertexConsumer consumer, List<Vector3f> trunkPoints,
			float maxWidth, float r, float g, float b, float a) {
		int segments = trunkPoints.size() - 1;
		if (segments <= 0)
			return;

		for (int i = 0; i < segments; i++) {
			Vector3f start = trunkPoints.get(i);
			Vector3f end = trunkPoints.get(i + 1);
			Vector3f segmentVector = new Vector3f(end).sub(start);
			float length = segmentVector.length();
			if (length <= 0.01F)
				continue;

			float widthScale = 1.0F - (float) i / (float) segments * 0.35F;
			float baseWidth = Math.max(2.5F, maxWidth * 0.55F * widthScale);

			stack.pushPose();
			stack.translate(start.x, start.y, start.z);
			stack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, -1.0F, 0.0F), segmentVector.normalize()));
			Matrix4f poseMatrix = stack.last().pose();
			int layers = 5;
			for (int layer = 0; layer < layers; layer++) {
				float factor = (float) layer / (float) layers;
				float width = Math.max(0.5F, baseWidth * (1.0F - factor * 0.7F));
				float alpha = ((float) (layer + 1) / (float) layers) * 0.7F * a;
				lightningBoltSection(poseMatrix, consumer, 0.0F, width, length, r, g, b, alpha);
			}
			stack.popPose();
		}
	}

	private static void lightningBoltSection(Matrix4f poseMatrix, VertexConsumer consumer, float yStart, float width,
			float length, float r, float g, float b, float a) {
		float halfWidth = width / 2.0F;
		consumer.addVertex(poseMatrix, +halfWidth, yStart, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart - length, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, +halfWidth, yStart - length, -halfWidth).setColor(r, g, b, a);

		consumer.addVertex(poseMatrix, +halfWidth, yStart - length, halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart - length, halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart, halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, +halfWidth, yStart, halfWidth).setColor(r, g, b, a);

		consumer.addVertex(poseMatrix, -halfWidth, yStart - length, halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart - length, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart, halfWidth).setColor(r, g, b, a);

		consumer.addVertex(poseMatrix, halfWidth, yStart, halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, halfWidth, yStart, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, halfWidth, yStart - length, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, halfWidth, yStart - length, halfWidth).setColor(r, g, b, a);

		consumer.addVertex(poseMatrix, -halfWidth, yStart - length, halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, halfWidth, yStart - length, halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, halfWidth, yStart - length, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart - length, -halfWidth).setColor(r, g, b, a);

		consumer.addVertex(poseMatrix, -halfWidth, yStart, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, halfWidth, yStart, -halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, halfWidth, yStart, halfWidth).setColor(r, g, b, a);
		consumer.addVertex(poseMatrix, -halfWidth, yStart, halfWidth).setColor(r, g, b, a);
	}

	public static class Branch {
		private List<LightningBolt.Branch> branches;
		private final float pitch;
		private final float yaw;
		private final float width;
		private final float length;

		public Branch(List<LightningBolt.Branch> branches, float pitch, float yaw, float width, float length) {
			this.branches = branches;
			this.pitch = pitch;
			this.yaw = yaw;
			this.width = width;
			this.length = length;
		}

		private void setBranches(List<LightningBolt.Branch> branches) {
			this.branches = branches;
		}
	}
}
