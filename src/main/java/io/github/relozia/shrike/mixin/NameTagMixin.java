package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.relozia.shrike.chat.Players;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// every nametag (players, named mobs, the bubbles) is placed here
@Mixin(SubmitNodeCollection.class)
public class NameTagMixin {
	@ModifyArgs(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void shrike$size(Args a) {
		float k = Players.NameTags.scale();
		a.set(0, (float) a.get(0) * k);
		a.set(1, (float) a.get(1) * k);
		a.set(2, (float) a.get(2) * k);
	}

	@ModifyExpressionValue(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;getBackgroundOpacity(F)F"))
	private float shrike$bg(float vanilla) {
		return Players.NameTags.bg(vanilla);
	}
}
