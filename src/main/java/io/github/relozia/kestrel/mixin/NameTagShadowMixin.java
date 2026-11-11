package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.relozia.kestrel.chat.Players;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public class NameTagShadowMixin {
	// not on the see through pass, that's the faded copy drawn through walls and a shadow there doubles up
	@ModifyArg(method = "prepareText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"), index = 4)
	private static boolean kestrel$shadow(boolean vanilla, @Local(argsOnly = true) NameTagFeatureRenderer.Submit tag) {
		return vanilla || Players.NameTags.shadow() && tag.displayMode() != Font.DisplayMode.SEE_THROUGH;
	}
}
