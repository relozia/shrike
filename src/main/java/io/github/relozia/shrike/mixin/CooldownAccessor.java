package io.github.relozia.shrike.mixin;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// vanilla only answers "what percent is left", the seconds need the end tick
@Mixin(ItemCooldowns.class)
public interface CooldownAccessor {
	@Accessor("cooldowns")
	Map<Identifier, ?> shrike$cooldowns();

	@Accessor("tickCount")
	int shrike$tick();
}
