package io.github.relozia.shrike.mixin;

import io.github.relozia.shrike.gui.Shape;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	// the gui's draws haven't run yet, so the main target is still just the world. glass panels blur that
	@Inject(method = "draw", at = @At("HEAD"))
	private void shrike$blurCopy(CallbackInfo ci) {
		Shape.Blur.capture();
	}
}
