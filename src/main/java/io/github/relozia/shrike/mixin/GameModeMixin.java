package io.github.relozia.shrike.mixin;

import io.github.relozia.shrike.pvp.Coaches;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.component.PiercingWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class GameModeMixin {
	// a spear jab isn't an entity attack, the swap coach needs it on its own for lunge swaps
	@Inject(method = "piercingAttack", at = @At("HEAD"))
	private void shrike$jab(PiercingWeapon weapon, CallbackInfo ci) {
		Coaches.SwapHelper.jab();
	}
}
