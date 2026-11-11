package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// the horizon is the plain air fog in a world with the overworld sky, the nether's haze stays its own
@Mixin(AtmosphericFogEnvironment.class)
public class FogColorMixin {
	@ModifyReturnValue(method = "getBaseColor", at = @At("RETURN"))
	private int shrike$horizon(int c, ClientLevel level) {
		int h = WorldVisuals.Sky.horizon();
		return h != -1 && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD ? h : c;
	}
}
