package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.qol.Qol;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public class ItemTooltipMixin {
	@ModifyReturnValue(method = "getTooltipImage", at = @At("RETURN"))
	private Optional<TooltipComponent> shrike$shulker(Optional<TooltipComponent> v, ItemStack s) {
		if (v.isPresent()) return v;
		Qol.ShulkerItems p = Qol.ShulkerPreview.preview(s);
		return p == null ? v : Optional.of(p);
	}
}
