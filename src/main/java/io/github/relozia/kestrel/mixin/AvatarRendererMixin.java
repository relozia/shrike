package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.visual.Animations;
import io.github.relozia.kestrel.visual.WorldVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	// vanilla's BLOCK arm pose is 1.7's sword block arm to the number (x * 0.5 - 3pi/10, turned 30 in),
	// so it just goes on the sword arm and the shield goes away
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void kestrel$swordBlock(Avatar e, AvatarRenderState s, float pt, CallbackInfo ci) {
		s.setData(WorldVisuals.HideArmor.OWN, e == Minecraft.getInstance().player);
		boolean right = s.mainArm == HumanoidArm.RIGHT;
		if (!Animations.swordBlock(s.isUsingItem, s.useItemHand, s.getMainHandItemStack(), right ? s.leftHandItemStack : s.rightHandItemStack)) return;
		if (right) {
			s.rightArmPose = HumanoidModel.ArmPose.BLOCK;
			s.leftArmPose = HumanoidModel.ArmPose.EMPTY;
			s.leftHandItemState.clear();
		} else {
			s.leftArmPose = HumanoidModel.ArmPose.BLOCK;
			s.rightArmPose = HumanoidModel.ArmPose.EMPTY;
			s.rightHandItemState.clear();
		}
	}
}
