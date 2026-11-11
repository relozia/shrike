package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.pvp.CooldownEnd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor extends CooldownEnd {
	@Override
	@Accessor("endTime")
	int kestrel$end();
}
