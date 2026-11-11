package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.setting.Setting;
import io.github.relozia.kestrel.sound.Sounds;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	// 1 = pressed, held keys repeating (2) don't count again
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void kestrel$keyPress(long handle, int action, KeyEvent e, CallbackInfo ci) {
		if (action != 1) return;
		Sounds.KeySounds.key();
		if (Minecraft.getInstance().gui.screen() == null) Setting.Bind.press(e.key());
	}
}
