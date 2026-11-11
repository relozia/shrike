package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientClockManager.class)
public class ClockMixin {
	@ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
	private long shrike$time(long ticks) {
		return WorldVisuals.Sky.clock(ticks);
	}
}
