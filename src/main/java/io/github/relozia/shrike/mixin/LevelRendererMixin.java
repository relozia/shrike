package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.shrike.visual.WorldTweaks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	private static final String HIT_OUTLINE = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V";

	// ordinal 1 is the normal outline. ordinal 0 only runs for the high contrast accessibility option, left alone
	@WrapOperation(
		method = "submitBlockOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
		at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1)
	)
	private void shrike$outline(
		LevelRenderer lr, PoseStack ps, SubmitNodeCollector out, RenderType type, BlockOutlineRenderState state, int color, float width, boolean afterTerrain,
		Operation<Void> og
	) {
		if (!WorldTweaks.BlockOverlay.active()) {
			og.call(lr, ps, out, type, state, color, width, afterTerrain);
			return;
		}
		WorldTweaks.BlockOverlay.fill(ps, out, state.shape());
		og.call(lr, ps, out, type, state, WorldTweaks.BlockOverlay.color(), WorldTweaks.BlockOverlay.width(), afterTerrain);
	}
}
