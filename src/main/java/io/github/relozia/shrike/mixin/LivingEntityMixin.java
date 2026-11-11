package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.visual.Animations;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	// only how long your own swing takes to draw, attacks and mining don't read it
	@ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
	private int shrike$swingSpeed(int v) {
		return (Object) this instanceof Player p ? Animations.swingDuration(p, v) : v;
	}
}
