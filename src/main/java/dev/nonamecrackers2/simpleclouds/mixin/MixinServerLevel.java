package dev.nonamecrackers2.simpleclouds.mixin;

import static dev.nonamecrackers2.simpleclouds.common.event.TickChunks.rainAndSnowVanillaCompatibility;

import java.util.function.ToDoubleFunction;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nonamecrackers2.simpleclouds.common.world.CloudData;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManagerHolder;
import dev.nonamecrackers2.simpleclouds.common.world.ServerCloudManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.DimensionDataStorage;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements CloudManagerHolder<ServerLevel> {
	@Unique
	private ServerCloudManager cloudManager;
	@Shadow
	@Final
	private MinecraftServer server;

	@Inject(method = "<init>", at = @At("TAIL"))
	public void simpleclouds$createCloudManager_init(CallbackInfo ci) {
		this.cloudManager = new ServerCloudManager((ServerLevel) (Object) this);
		// Do this so we hide the world seed
		this.cloudManager.init(RandomSource.create(this.server.getWorldData().worldGenOptions().seed()).nextLong());
		this.getDataStorage().computeIfAbsent(CloudData.factory(this.cloudManager), CloudData.ID);
	}

	@Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
	public void simpleclouds$disableWeatherCycle_advanceWeatherCycle(CallbackInfo ci) {
		if (!this.cloudManager.shouldUseVanillaWeather()) {
			this.resetWeatherCycle();
			ci.cancel();
		}
	}

	@Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
	public void simpleclouds$localizedWeather_isRaining(CallbackInfoReturnable<Boolean> ci) {
		if (!this.cloudManager.shouldUseVanillaWeather())
			ci.setReturnValue(this.simpleclouds$anyPlayerWeatherMatches(player -> this.cloudManager.getRainLevel(
					(float) player.getX(), (float) player.getY(), (float) player.getZ())));
	}

	@Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
	public void simpleclouds$localizedWeather_isThundering(CallbackInfoReturnable<Boolean> ci) {
		if (!this.cloudManager.shouldUseVanillaWeather())
			ci.setReturnValue(this.simpleclouds$anyPlayerWeatherMatches(player -> this.cloudManager.getThunderLevel(
					(float) player.getX(), (float) player.getY(), (float) player.getZ())));
	}

	@Inject(method = "tickChunk", at = @At(value = "RETURN"))
	public void simpleclouds$localizedWeatherHandlePrecipitation(LevelChunk chunk, int tickSpeed, CallbackInfo ci) {
		CloudManager<?> manager = CloudManager.get((Level) (Object) this);
		if (!manager.shouldUseVanillaWeather()) {
			rainAndSnowVanillaCompatibility((ServerLevel) (Object) this, chunk);
		}
	}

	@Shadow
	protected abstract void resetWeatherCycle();

	@Unique
	private boolean simpleclouds$anyPlayerWeatherMatches(ToDoubleFunction<ServerPlayer> weatherGetter) {
		return ((ServerLevel) (Object) this).players().stream().filter(player -> !player.isSpectator())
				.anyMatch(player -> weatherGetter.applyAsDouble(player) > 0.0D);
	}

	@Override
	public ServerCloudManager getCloudManager() {
		return this.cloudManager;
	}

	@Shadow
	public abstract DimensionDataStorage getDataStorage();
}
