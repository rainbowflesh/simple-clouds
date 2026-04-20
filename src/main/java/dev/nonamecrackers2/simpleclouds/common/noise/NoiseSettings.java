package dev.nonamecrackers2.simpleclouds.common.noise;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;

public interface NoiseSettings {
	@SuppressWarnings("rawtypes")
	public static final List<Decoder> VALID_DECODERS = ImmutableList.of(StaticNoiseSettings.CODEC,
			StaticLayeredNoise.CODEC);
	public static final Codec<NoiseSettings> CODEC = new Codec<>() {
		@SuppressWarnings("unchecked")
		@Override
		public <T> DataResult<Pair<NoiseSettings, T>> decode(DynamicOps<T> ops, T input) {
			for (Decoder<NoiseSettings> decoder : VALID_DECODERS) {
				DataResult<Pair<NoiseSettings, T>> result = decoder.decode(ops, input);
				if (result.result().isPresent())
					return result;
			}
			return DataResult.error(() -> "Could not decode noise settings; unknown type");
		}

		@Override
		public <T> DataResult<T> encode(NoiseSettings input, DynamicOps<T> ops, T prefix) {
			return input.encode(ops, prefix);
		}
	};
	public static final NoiseSettings EMPTY = new NoiseSettings() {
		@Override
		public float[] packForShader() {
			return new float[0];
		}

		@Override
		public int layerCount() {
			return 0;
		}

		@Override
		public <T> DataResult<T> encode(DynamicOps<T> ops, T prefix) {
			return DataResult.success(ops.emptyList());
		}

		@Override
		public int getStartHeight() {
			return 0;
		}

		@Override
		public int getEndHeight() {
			return 0;
		}
	};

	<T> DataResult<T> encode(DynamicOps<T> ops, T prefix);

	float[] packForShader();

	int layerCount();

	int getStartHeight();

	int getEndHeight();

	default int getHeightRange() {
		return Math.max(0, this.getEndHeight() - this.getStartHeight());
	}

	default float[] packForShaderRelativeToStart() {
		int startHeight = this.getStartHeight();
		if (startHeight == 0)
			return this.packForShader();

		int valueCount = AbstractNoiseSettings.Param.values().length;
		float[] packed = new float[valueCount * this.layerCount()];
		int nextIndex = 0;
		if (this instanceof AbstractLayeredNoise<?> layered) {
			for (NoiseSettings layer : layered.getNoiseLayers()) {
				if (layer instanceof AbstractNoiseSettings<?> settings)
					nextIndex = packLayerRelativeToStart(settings, packed, nextIndex, startHeight);
			}
			return packed;
		}
		if (this instanceof AbstractNoiseSettings<?> settings)
			packLayerRelativeToStart(settings, packed, 0, startHeight);
		else
			return this.packForShader();
		return packed;
	}

	private static int packLayerRelativeToStart(AbstractNoiseSettings<?> settings, float[] packed, int nextIndex,
			int startHeight) {
		for (AbstractNoiseSettings.Param param : AbstractNoiseSettings.Param.values()) {
			float value = settings.getParam(param);
			if (param == AbstractNoiseSettings.Param.HEIGHT_OFFSET)
				value -= (float) startHeight;
			packed[nextIndex++] = value;
		}
		return nextIndex;
	}
}
