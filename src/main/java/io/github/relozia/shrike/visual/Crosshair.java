package io.github.relozia.shrike.visual;

import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

// draw-your-own crosshair on a 15x15 grid. vanilla's attack indicator under it is untouched
// issues:
//   - vanilla's crosshair inverts the colors behind it, a custom one is a flat color. outline helps on bright blocks
public class Crosshair extends Module {
	static Crosshair INSTANCE;
	public static final Map<String, String> PRESETS = new LinkedHashMap<>();

	static {
		PRESETS.put("Plus", shape((x, y) -> x == 7 && Math.abs(y - 7) <= 4 || y == 7 && Math.abs(x - 7) <= 4));
		PRESETS.put("Gap Plus", shape((x, y) -> (x == 7 || y == 7) && Math.abs(x - 7) + Math.abs(y - 7) >= 2 && Math.abs(x - 7) <= 5 && Math.abs(y - 7) <= 5));
		PRESETS.put("Dot", shape((x, y) -> Math.abs(x - 7) <= 1 && Math.abs(y - 7) <= 1 && !(Math.abs(x - 7) == 1 && Math.abs(y - 7) == 1)));
		PRESETS.put("Circle", shape((x, y) -> {
			double d = Math.hypot(x - 7, y - 7);
			return d > 3.5 && d < 4.6 || x == 7 && y == 7;
		}));
		PRESETS.put("X", shape((x, y) -> Math.abs(x - 7) == Math.abs(y - 7) && x != 7 && Math.abs(x - 7) <= 4));
		PRESETS.put("T", shape((x, y) -> y == 7 && Math.abs(x - 7) <= 4 && x != 7 || x == 7 && y > 8 && y <= 11));
	}

	static String shape(BiPredicate<Integer, Integer> on) {
		StringBuilder sb = new StringBuilder();
		for (int y = 0; y < Setting.Grid.N; y++) for (int x = 0; x < Setting.Grid.N; x++) sb.append(on.test(x, y) ? '#' : '.');
		return sb.toString();
	}

	public final Setting.Grid pixels = add(new Setting.Grid("Pixels", PRESETS.get("Gap Plus")));
	private final Setting.Color color = add(new Setting.Color("Color", 0xFFFFFFFF,
		0xFFFFFFFF, 0xFF57D163, 0xFFE0703A, 0xFF5B8DEF, 0xFF2EC4B6, 0xFFE5484D));
	private final Setting.Slider opacity = add(new Setting.Slider("Opacity", 100, 20, 100, 5, "%"));
	private final Setting.Toggle outline = add(new Setting.Toggle("Outline", true));
	private final Setting.Slider size = add(new Setting.Slider("Pixel Size", 1, 1, 4, 1, "x"));

	public Crosshair() {
		super("crosshair", "Crosshair", Category.HUD, '\ue0ac', "Your own crosshair, drawn on a pixel grid.");
		INSTANCE = this;
	}

	// true means we drew it and vanilla's sprite should be skipped
	public static boolean draw(GuiGraphicsExtractor g) {
		Crosshair c = INSTANCE;
		if (c == null || !c.on()) return false;
		int s = c.size.get().intValue(), n = Setting.Grid.N;
		int x0 = (g.guiWidth() - n * s) / 2, y0 = (g.guiHeight() - n * s) / 2;
		int col = ARGB.color(Math.round(c.opacity.get().floatValue() / 100 * 255), c.color.get() & 0xFFFFFF);
		paint(g, c.pixels, x0, y0, s, col, c.outline.on());
		return true;
	}

	// outline first (every empty neighbour of a filled pixel), then the pixels on top
	public static void paint(GuiGraphicsExtractor g, Setting.Grid px, int x0, int y0, int s, int col, boolean outline) {
		int n = Setting.Grid.N;
		if (outline) {
			int dark = ARGB.color(ARGB.alpha(col) * 3 / 4, 0);
			for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
				if (px.at(x, y)) continue;
				boolean edge = x > 0 && px.at(x - 1, y) || x < n - 1 && px.at(x + 1, y) || y > 0 && px.at(x, y - 1) || y < n - 1 && px.at(x, y + 1);
				if (edge) g.fill(x0 + x * s, y0 + y * s, x0 + x * s + s, y0 + y * s + s, dark);
			}
		}
		for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
			if (px.at(x, y)) g.fill(x0 + x * s, y0 + y * s, x0 + x * s + s, y0 + y * s + s, col);
		}
	}
}
