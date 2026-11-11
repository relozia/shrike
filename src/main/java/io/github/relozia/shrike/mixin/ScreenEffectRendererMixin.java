package io.github.relozia.shrike.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.shrike.visual.WorldTweaks;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
	@Inject(
		method = "submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void shrike$fire(PoseStack ps, SubmitNodeCollector out, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (!WorldTweaks.FireHeight.active()) return;
		WorldTweaks.FireHeight.submit(ps, out, sprite);
		ci.cancel();
	}

	@Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
	private static void shrike$water(Minecraft mc, PoseStack ps, SubmitNodeCollector out, CallbackInfo ci) {
		if (WorldVisuals.NoRender.hides("Water Overlay")) ci.cancel();
	}
}
