package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.shrike.visual.Animations;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void shrike$legacy(AbstractClientPlayer p, float pt, float xRot, InteractionHand hand, float attack, ItemStack stack, float inv,
		PoseStack ps, SubmitNodeCollector out, int light, CallbackInfo ci) {
		if (Animations.firstPerson((ItemInHandRenderer) (Object) this, p, pt, hand, attack, stack, inv, ps, out, light)) ci.cancel();
	}

	// swing styles on vanilla's own path too (Old Item Positions off, or items 1.7 leaves alone)
	@WrapOperation(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V")
	)
	private void shrike$style(ItemInHandRenderer r, float a, PoseStack ps, int d, HumanoidArm arm, Operation<Void> og, @Local(argsOnly = true) ItemStack stack) {
		if (Animations.styled()) Animations.swing(ps, stack, a, d, false);
		else og.call(r, a, ps, d, arm);
	}
}
