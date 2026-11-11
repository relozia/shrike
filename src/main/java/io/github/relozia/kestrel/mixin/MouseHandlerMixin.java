package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.relozia.kestrel.camera.Freelook;
import io.github.relozia.kestrel.camera.Zoom;
import io.github.relozia.kestrel.qol.Qol;
import io.github.relozia.kestrel.sound.Sounds;
import io.github.relozia.kestrel.track.Clicks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	// CPS has to see every click, polling the attack key once a frame misses fast ones at low fps
	@Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"))
	private void kestrel$click(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		// 1 = press. clicks in menus aren't combat clicks
		if (action == 1 && minecraft.gui.screen() == null) Clicks.press(info.button());
		if (action == 1) Sounds.KeySounds.click();
	}

	// freelook takes the mouse movement for the camera, zoom slows it down
	@WrapOperation(method = "turnPlayer(D)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void kestrel$turn(LocalPlayer p, double dx, double dy, Operation<Void> og) {
		if (Freelook.turn(dx, dy)) return;
		double s = Zoom.sensitivity();
		og.call(p, dx * s, dy * s);
	}

	// while zoomed the wheel changes the zoom instead of the hotbar slot
	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void kestrel$scroll(long handle, double x, double y, CallbackInfo ci) {
		if (minecraft.gui.screen() == null && Zoom.scroll(y)) ci.cancel();
	}

	@Inject(method = "grabMouse", at = @At("TAIL"))
	private void kestrel$regrab(CallbackInfo ci) {
		Qol.ModernKeybinds.regrabbed(minecraft);
	}
}
