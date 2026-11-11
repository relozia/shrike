package io.github.relozia.shrike.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class ArmorLayerMixin {
	@Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
	private void shrike$hide(PoseStack ps, SubmitNodeCollector out, ItemStack stack, EquipmentSlot slot, int light, HumanoidRenderState s, CallbackInfo ci) {
		if (Boolean.TRUE.equals(s.getData(WorldVisuals.HideArmor.OWN)) && WorldVisuals.HideArmor.hidden(slot)) ci.cancel();
	}
}
