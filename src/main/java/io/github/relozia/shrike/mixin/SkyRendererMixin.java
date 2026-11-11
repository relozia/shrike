package io.github.relozia.shrike.mixin;

import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// sun and moon quads are a unit square scaled up to 30 and 20, only x and z are the size (y is the up axis)
@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	@ModifyArg(method = "renderSun", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;scale(FFF)Lorg/joml/Matrix4f;"), index = 0)
	private float shrike$sunX(float v) {
		return v * WorldVisuals.Sky.sunSize();
	}

	@ModifyArg(method = "renderSun", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;scale(FFF)Lorg/joml/Matrix4f;"), index = 2)
	private float shrike$sunZ(float v) {
		return v * WorldVisuals.Sky.sunSize();
	}

	@ModifyArg(method = "renderMoon", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;scale(FFF)Lorg/joml/Matrix4f;"), index = 0)
	private float shrike$moonX(float v) {
		return v * WorldVisuals.Sky.moonSize();
	}

	@ModifyArg(method = "renderMoon", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;scale(FFF)Lorg/joml/Matrix4f;"), index = 2)
	private float shrike$moonZ(float v) {
		return v * WorldVisuals.Sky.moonSize();
	}
}
