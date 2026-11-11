package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.relozia.kestrel.visual.WorldTweaks;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapMixin {
	// ordinal 0 is the gamma read, ordinal 1 right after it is the darkness effect option
	@ModifyExpressionValue(
		method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0)
	)
	private float kestrel$gamma(float vanilla) {
		return WorldTweaks.Fullbright.gamma(vanilla);
	}
}
