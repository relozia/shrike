package io.github.relozia.shrike.mixin;

import io.github.relozia.shrike.pvp.PvpTools;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	// Q on a locked hotbar slot
	@Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
	private void shrike$lockedDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
		if (PvpTools.LockSlot.locked(((LocalPlayer) (Object) this).getInventory().getSelectedSlot())) cir.setReturnValue(false);
	}
}
