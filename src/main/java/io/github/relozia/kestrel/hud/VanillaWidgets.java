package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

// scoreboard, boss bars, titles, action bar. the hooks that feed them vanilla's boxes are in HudMixin
// and BossHealthOverlayMixin, defaults are roughly vanilla's spots for once they get dragged
public final class VanillaWidgets {
	private VanillaWidgets() {}

	public static class Scoreboard extends VanillaWidget {
		public static Scoreboard INSTANCE;

		private final Setting.Toggle numbers = add(new Setting.Toggle("Hide Numbers", true));
		private final Setting.Slider opacity = add(new Setting.Slider("Background Opacity", 30, 0, 90, 5, "%"));
		private final Setting.Toggle f3 = add(new Setting.Toggle("Hide While F3", true));

		public Scoreboard() {
			super("Scoreboard", '\ue1d1', "Move the sidebar, hide the red numbers, change its background.", 1, 0.5f, 90, 110);
			INSTANCE = this;
		}

		public boolean hideNumbers() {
			return on() && numbers.on();
		}

		// the f3 text runs down the right side straight through the sidebar
		public boolean hidden() {
			return on() && f3.on() && Minecraft.getInstance().debugEntries.isOverlayVisible();
		}

		// header gets 10% more, like vanilla's 0.3 and 0.4
		public int bg(int vanilla, boolean header) {
			if (!on()) return vanilla;
			float a = Mth.clamp((opacity.get().floatValue() + (header ? 10 : 0)) / 100f, 0, 1);
			return ARGB.color(Math.round(a * 255), 0);
		}
	}

	public static class BossBars extends VanillaWidget {
		public static BossBars INSTANCE;

		private final Setting.Toggle hide = add(new Setting.Toggle("Hide", false));

		public BossBars() {
			super("Boss Bars", '\ue221', "Move the boss bars, or hide them.", 0.5f, 0, 182, 14);
			INSTANCE = this;
		}

		public boolean hidden() {
			return on() && hide.on();
		}
	}

	public static class Title extends VanillaWidget {
		public static Title INSTANCE;

		public Title() {
			super("Titles", '\ue198', "Move the big title and subtitle text.", 0.5f, 0.5f, 200, 80);
			INSTANCE = this;
		}
	}

	public static class ActionBar extends VanillaWidget {
		public static ActionBar INSTANCE;

		public ActionBar() {
			super("Action Bar", '\ue185', "Move the text that shows up above the hotbar.", 0.5f, 0.78f, 120, 13);
			INSTANCE = this;
		}
	}
}
