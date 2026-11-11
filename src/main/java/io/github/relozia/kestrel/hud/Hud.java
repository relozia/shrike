package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.gui.KestrelScreen;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.module.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class Hud {
	private Hud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		// the editor draws the widgets itself, drawing them here too doubles them up. the menu and its sub screens
		// (word lists, item picker, waypoints...) hide the hud too
		Screen s = mc.gui.screen();
		if (mc.gui.hud.isHidden() || s != null && s.getClass().getPackageName().equals(KestrelScreen.class.getPackageName())) return;
		int sw = g.guiWidth(), sh = g.guiHeight();
		for (Module m : Modules.all()) {
			if (m instanceof HudWidget w && w.on()) for (HudWidget p : w.boxes()) if (p.visible()) p.render(g, sw, sh);
		}
	}
}
