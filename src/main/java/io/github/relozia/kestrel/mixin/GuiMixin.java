package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.gui.KestrelTitleScreen;
import io.github.relozia.kestrel.visual.ScreenTweaks;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// every way back to the title screen: setScreen(new TitleScreen()) from disconnects and death screens (HEAD), and
// setScreen(null) with no world loaded, which builds one inside (STORE). swapping right before the field write
// doesn't work, the old screen is already loaded to be stored by then and the stored one and the init'd one differ
@Mixin(Gui.class)
public class GuiMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen kestrel$titleIn(Screen s) {
		return swap(s);
	}

	@ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true)
	private Screen kestrel$titleBuilt(Screen s) {
		return swap(s);
	}

	// ours is a TitleScreen too, it goes through untouched
	private static Screen swap(Screen s) {
		return s instanceof TitleScreen && !(s instanceof KestrelTitleScreen) && ScreenTweaks.Title.active() ? new KestrelTitleScreen() : s;
	}
}
