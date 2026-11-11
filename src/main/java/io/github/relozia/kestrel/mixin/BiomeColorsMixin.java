package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.visual.WorldVisuals;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// one flat color everywhere, biome blending included. chunks are baked with it, Sky rebuilds them on change
@Mixin(BiomeColors.class)
public class BiomeColorsMixin {
	@Inject(method = "getAverageGrassColor", at = @At("HEAD"), cancellable = true)
	private static void kestrel$grass(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int c = WorldVisuals.Sky.tint(0);
		if (c != -1) cir.setReturnValue(c);
	}

	@Inject(method = "getAverageFoliageColor", at = @At("HEAD"), cancellable = true)
	private static void kestrel$foliage(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int c = WorldVisuals.Sky.tint(1);
		if (c != -1) cir.setReturnValue(c);
	}

	@Inject(method = "getAverageWaterColor", at = @At("HEAD"), cancellable = true)
	private static void kestrel$water(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int c = WorldVisuals.Sky.tint(2);
		if (c != -1) cir.setReturnValue(c);
	}
}
