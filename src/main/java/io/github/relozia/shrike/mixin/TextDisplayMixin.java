package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.visual.Cosmetics;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Display.TextDisplay.class)
public class TextDisplayMixin {
	// the renderer reads the split lines from here every frame, the badge goes onto the server's name tag for you
	@ModifyReturnValue(method = "cacheDisplay", at = @At("RETURN"))
	private Display.TextDisplay.CachedInfo shrike$badge(Display.TextDisplay.CachedInfo info) {
		return Cosmetics.badgeLines((Display.TextDisplay) (Object) this, info);
	}
}
