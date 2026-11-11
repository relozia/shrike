package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.relozia.kestrel.visual.Animations;
import io.github.relozia.kestrel.visual.WorldVisuals;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Shadow public Options options;
	@Shadow public @Nullable LocalPlayer player;

	// while using an item vanilla drains attack clicks in an empty loop, this sees each one go by
	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
	private boolean kestrel$usingClick(KeyMapping k, Operation<Boolean> og) {
		boolean clicked = og.call(k);
		if (clicked && k == options.keyAttack && player != null && player.isUsingItem()) Animations.usingClick(player);
		return clicked;
	}

	// the client side outline only, the server still has its glowing flag
	@ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
	private boolean kestrel$noGlow(boolean v) {
		return v && !WorldVisuals.NoRender.hides("Glow");
	}
}
