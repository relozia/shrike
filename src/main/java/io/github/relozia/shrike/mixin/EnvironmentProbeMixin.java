package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// the camera's probe, what sky / fog / clouds are drawn with
@Mixin(EnvironmentAttributeProbe.class)
public class EnvironmentProbeMixin {
	@ModifyReturnValue(method = "getValue", at = @At("RETURN"))
	private <V> V shrike$colors(V v, EnvironmentAttribute<V> attribute, float pt) {
		return WorldVisuals.Sky.attribute(attribute, v);
	}
}
