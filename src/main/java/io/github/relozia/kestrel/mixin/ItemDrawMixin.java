package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.pvp.PvpTools;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// every item icon in any gui goes through these two
@Mixin(GuiGraphicsExtractor.class)
public class ItemDrawMixin {
	@Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
	private void kestrel$back(@Nullable LivingEntity owner, @Nullable Level level, ItemStack s, int x, int y, int seed, CallbackInfo ci) {
		PvpTools.HealHighlight.back((GuiGraphicsExtractor) (Object) this, s, x, y);
	}

	@Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
	private void kestrel$cooldown(Font font, ItemStack s, int x, int y, @Nullable String count, CallbackInfo ci) {
		PvpTools.Cooldowns.slot((GuiGraphicsExtractor) (Object) this, s, x, y);
	}
}
