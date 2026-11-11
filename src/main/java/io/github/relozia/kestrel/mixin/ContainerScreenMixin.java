package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.pvp.PvpTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {
	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void kestrel$lock(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		if (PvpTools.LockSlot.click(slot, button, input)) ci.cancel();
	}

	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void kestrel$lockIcon(GuiGraphicsExtractor g, Slot slot, int mx, int my, CallbackInfo ci) {
		PvpTools.LockSlot.icon(g, slot);
	}
}
