package io.github.relozia.shrike.visual;

import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.gui.Ui;
import io.github.relozia.shrike.mixin.GuiRenderStateAccessor;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.level.GameType;
import org.joml.Vector2i;
import org.joml.Vector2ic;

// hotbar, the background behind inventories and long tooltips
public final class ScreenTweaks {
	private ScreenTweaks() {}

	public static class Hotbar extends Module {
		static Hotbar INSTANCE;

		private final Setting.Choice labels = add(new Setting.Choice("Slot Labels", "Numbers", "Off", "Numbers", "Keybinds"));
		private final Setting.Toggle smooth = add(new Setting.Toggle("Smooth Selection", true));
		private final Setting.Slider speed = add(new Setting.Slider("Slide Speed", 60, 10, 100, 5, "%"));

		private float cur = Float.NaN;
		private long last;

		public Hotbar() {
			super("hotbar_tweaks", "Hotbar Tweaks", Category.HUD, '\ue42c', "Slot numbers or keybinds on the hotbar, and a smooth selection slide.");
			INSTANCE = this;
			HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Shrike.id("hotbar_labels"), (g, dt) -> labels(g));
		}

		// eased toward vanilla's x every frame. NaN until the first frame so it doesn't slide in from 0 on join
		public static int selX(int target) {
			Hotbar h = INSTANCE;
			if (h == null || !h.on() || !h.smooth.on()) return target;
			long now = Util.getMillis();
			float dt = Math.min(0.1f, (now - h.last) / 1000f);
			h.last = now;
			if (Float.isNaN(h.cur) || Math.abs(target - h.cur) > 200) h.cur = target;
			float k = 1 - (float) Math.pow(1 - h.speed.get() / 110.0, dt * 60);
			h.cur += (target - h.cur) * k;
			return Math.round(h.cur);
		}

		// otherwise turning it back on later slides from wherever the box was when it got turned off
		@Override
		protected void disable() {
			cur = Float.NaN;
		}

		private void labels(GuiGraphicsExtractor g) {
			Minecraft mc = Minecraft.getInstance();
			if (!on() || labels.get().equals("Off") || mc.player == null || mc.gui.hud.isHidden()) return;
			if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
			for (int i = 0; i < 9; i++) {
				String s = labels.get().equals("Keybinds") ? Setting.Bind.label(KeyMappingHelper.getBoundKeyOf(mc.options.keyHotbarSlots[i])) : Integer.toString(i + 1);
				Component c = Ui.small(s);
				int x = g.guiWidth() / 2 - 90 + i * 20 + 3, y = g.guiHeight() - 20;
				// shrinks to the 16px inside the slot instead of cutting, "LShift" still reads at 0.6
				float k = Math.min(1, 16f / Ui.width(c));
				g.pose().pushMatrix();
				g.pose().translate(x, y);
				g.pose().scale(k, k);
				Ui.text(g, c, 0, 0, 0xB0FFFFFF);
				g.pose().popMatrix();
			}
		}
	}

	// Shrike's main menu (gui/ShrikeTitleScreen) in place of vanilla's, off gives vanilla's back. swapped in by GuiMixin
	public static class Title extends Module {
		static Title INSTANCE;

		public final Setting.Toggle quickJoin = add(new Setting.Toggle("Quick Join", true));

		public Title() {
			super("title_screen", "Title Screen", Category.VISUAL, '\ue3c5', "Shrike's own main menu, with your most recent servers right next to the buttons.");
			INSTANCE = this;
			startOn();
		}

		public static boolean active() {
			return INSTANCE != null && INSTANCE.on();
		}

		public static boolean quickJoin() {
			return active() && INSTANCE.quickJoin.on();
		}
	}

	public static class Background extends Module {
		static Background INSTANCE;

		private final Setting.Choice mode = add(new Setting.Choice("Mode", "Blur", "Blur", "Dark", "Clear"));
		private final Setting.Slider blur = add(new Setting.Slider("Blur Strength", 5, 1, 10, 1, ""));
		private final Setting.Slider dark = add(new Setting.Slider("Darkness", 25, 0, 90, 5, "%"));

		public Background() {
			super("screen_background", "Screen Background", Category.VISUAL, '\ue529', "Blur or clear the background behind inventories and chests.");
			INSTANCE = this;
		}

		// true = handled, skip vanilla's dark gradient. blur goes through vanilla's own blur pass, only if
		// nothing else blurred this frame, the game throws if you blur twice
		public static boolean draw(GuiGraphicsExtractor g) {
			Background b = INSTANCE;
			if (b == null || !b.on()) return false;
			String m = b.mode.get();
			if (m.equals("Blur") && ((GuiRenderStateAccessor) g.guiRenderState).shrike$blurAt() == Integer.MAX_VALUE) {
				g.blurBeforeThisStratum();
			}
			if (!m.equals("Clear")) g.fill(0, 0, g.guiWidth(), g.guiHeight(), ARGB.color(Math.round(b.dark.get().floatValue() / 100 * 255), 0));
			return true;
		}
	}

	// own radius for inventories and chests only, the title screen menus keep the one from video settings.
	// vanilla copies the option into the frame's render state before the gui is built, that's where this goes
	public static int blurRadius(int vanilla) {
		Background b = Background.INSTANCE;
		Screen s = Minecraft.getInstance().gui.screen();
		return b != null && b.on() && b.mode.get().equals("Blur") && s != null && s.isInGameUi() ? b.blur.get().intValue() : vanilla;
	}

	public static class Tooltips extends Module {
		static Tooltips INSTANCE;

		private int ox, oy, key;
		private long lastDraw;
		private boolean overflow;

		public Tooltips() {
			super("scrollable_tooltips", "Scrollable Tooltips", Category.UTILITY, '\ue45f', "Scroll tooltips that don't fit on screen, shift scrolls sideways.");
			INSTANCE = this;
			ScreenEvents.AFTER_INIT.register((mc, screen, w, h) ->
				ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hs, vs) -> !scroll(hs, vs)));
		}

		// a different tooltip (size changes) or one that wasn't shown for a bit starts from vanilla's spot again
		public static Vector2ic position(Vector2ic v, int sw, int sh, int w, int h) {
			Tooltips t = INSTANCE;
			if (t == null || !t.on()) return v;
			long now = Util.getMillis();
			int k = w * 31 + h;
			if (k != t.key || now - t.lastDraw > 150) {
				t.ox = 0;
				t.oy = 0;
			}
			t.key = k;
			t.lastDraw = now;
			t.overflow = h > sh - 8 || w > sw - 8;
			return new Vector2i(v.x() + t.ox, v.y() + t.oy);
		}

		// only takes the wheel when the tooltip under the mouse actually doesn't fit, otherwise
		// creative tabs and scroll lists keep working like normal
		static boolean scroll(double hs, double vs) {
			Tooltips t = INSTANCE;
			if (t == null || !t.on() || !t.overflow || Util.getMillis() - t.lastDraw > 150) return false;
			if (Minecraft.getInstance().hasShiftDown()) t.ox += (int) Math.round((vs + hs) * 12);
			else t.oy += (int) Math.round(vs * 12);
			return true;
		}
	}
}
