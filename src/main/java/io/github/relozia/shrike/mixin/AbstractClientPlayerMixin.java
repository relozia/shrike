package io.github.relozia.shrike.mixin;

import io.github.relozia.shrike.visual.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
	// only you wear Shrike's cosmetics, other players keep whatever their own skin says
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void shrike$cosmetics(CallbackInfoReturnable<PlayerSkin> cir) {
		if ((Object) this == Minecraft.getInstance().player) cir.setReturnValue(Cosmetics.skin(cir.getReturnValue()));
	}
}
