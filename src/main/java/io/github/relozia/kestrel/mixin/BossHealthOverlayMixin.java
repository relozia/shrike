package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.hud.VanillaWidgets;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
	@Shadow @Final private Map<UUID, LerpingBossEvent> events;
	@Shadow @Final private Minecraft minecraft;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void kestrel$hide(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (VanillaWidgets.BossBars.INSTANCE.hidden()) ci.cancel();
	}

	// same loop as vanilla's so the box stops where vanilla stops (a third of the screen down)
	@Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;nextStratum()V", shift = At.Shift.AFTER))
	private void kestrel$push(GuiGraphicsExtractor g, CallbackInfo ci) {
		int y = 12, n = 0, w = 182;
		for (LerpingBossEvent e : events.values()) {
			n++;
			w = Math.max(w, minecraft.font.width(e.getName()));
			y += 19;
			if (y >= g.guiHeight() / 3) break;
		}
		int cx = g.guiWidth() / 2;
		VanillaWidgets.BossBars.INSTANCE.push(g, cx - w / 2, 3, cx + w / 2, 12 + 19 * (n - 1) + 5);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void kestrel$pop(GuiGraphicsExtractor g, CallbackInfo ci) {
		VanillaWidgets.BossBars.INSTANCE.pop(g);
	}
}
