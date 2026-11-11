package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// No Render's fog removal. world fog includes the render distance fade, that's the one hiding chunk edges
@Mixin(FogRenderer.class)
public class FogRendererMixin {
	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private FogData shrike$noFog(FogData fog, Camera camera) {
		String which = switch (camera.getFluidInCamera()) {
			case WATER -> "Water";
			case LAVA -> "Lava";
			case POWDER_SNOW -> "Powder Snow";
			default -> "World";
		};
		if (!WorldVisuals.NoRender.noFog(which)) return fog;
		fog.environmentalStart = fog.environmentalEnd = fog.skyEnd = fog.cloudEnd = Float.MAX_VALUE;
		if (which.equals("World")) fog.renderDistanceStart = fog.renderDistanceEnd = Float.MAX_VALUE;
		return fog;
	}
}
