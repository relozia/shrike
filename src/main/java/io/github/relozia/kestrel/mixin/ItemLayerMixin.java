package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.kestrel.visual.Animations;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// the one spot an item model's display transform (first person, gui, ground...) gets applied
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public class ItemLayerMixin {
	@WrapOperation(
		method = "applyTransform",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/cuboid/ItemTransform;apply(ZLcom/mojang/blaze3d/vertex/PoseStack$Pose;)V")
	)
	private void kestrel$legacy(ItemTransform t, boolean left, PoseStack.Pose pose, Operation<Void> og) {
		if (!Animations.layer(t, pose)) og.call(t, left, pose);
	}
}
