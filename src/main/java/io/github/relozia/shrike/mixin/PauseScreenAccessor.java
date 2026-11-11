package io.github.relozia.shrike.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PauseScreen.class)
public interface PauseScreenAccessor {
	@Accessor("disconnectButton")
	@Nullable Button shrike$disconnect();
}
