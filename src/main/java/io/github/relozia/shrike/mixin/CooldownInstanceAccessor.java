package io.github.relozia.shrike.mixin;

import io.github.relozia.shrike.pvp.CooldownEnd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor extends CooldownEnd {
	@Override
	@Accessor("endTime")
	int shrike$end();
}
