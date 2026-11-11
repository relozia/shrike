package io.github.relozia.shrike.mixin;

import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Button.class)
public interface ButtonAccessor {
	@Accessor("onPress")
	Button.OnPress shrike$onPress();

	@Mutable
	@Accessor("onPress")
	void shrike$setOnPress(Button.OnPress p);
}
