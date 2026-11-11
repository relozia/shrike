package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.visual.ScreenTweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
	// the dark gradient behind inventories, chests, the pause menu. title screen menus never call this
	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void kestrel$background(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (ScreenTweaks.Background.draw(g)) ci.cancel();
	}
}
