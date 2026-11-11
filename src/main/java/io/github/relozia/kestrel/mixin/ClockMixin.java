package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.kestrel.visual.WorldVisuals;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientClockManager.class)
public class ClockMixin {
	@ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
	private long kestrel$time(long ticks) {
		return WorldVisuals.Sky.clock(ticks);
	}
}
