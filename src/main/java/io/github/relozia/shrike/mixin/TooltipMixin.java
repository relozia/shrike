package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.relozia.shrike.visual.ScreenTweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public class TooltipMixin {
	// every positioner (mouse, widget, menu) goes through here, so one wrap covers all tooltips
	@WrapOperation(
		method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;")
	)
	private Vector2ic shrike$scroll(ClientTooltipPositioner p, int sw, int sh, int x, int y, int w, int h, Operation<Vector2ic> op) {
		return ScreenTweaks.Tooltips.position(op.call(p, sw, sh, x, y, w, h), sw, sh, w, h);
	}
}
