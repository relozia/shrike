package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.camera.CameraTweaks;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public class PlayerFovMixin {
	@ModifyExpressionValue(
		method = "getFieldOfViewModifier(ZF)F",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D")
	)
	private double shrike$speed(double v) {
		return CameraTweaks.Fov.speedAttr((AbstractClientPlayer) (Object) this, v);
	}

	@ModifyReturnValue(method = "getFieldOfViewModifier(ZF)F", at = @At("RETURN"))
	private float shrike$lock(float v) {
		return CameraTweaks.Fov.lock((AbstractClientPlayer) (Object) this, v);
	}
}
