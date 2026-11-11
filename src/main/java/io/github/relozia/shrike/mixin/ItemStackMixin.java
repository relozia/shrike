package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public class ItemStackMixin {
	// shiny potions, how they looked before 1.19.4, and the enchanted apple without its glint
	@ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
	private boolean shrike$foil(boolean foil) {
		return WorldVisuals.Glint.foil((ItemStack) (Object) this, foil);
	}
}
