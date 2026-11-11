package io.github.relozia.kestrel.gui;

import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.config.Config;
import io.github.relozia.kestrel.hud.HudWidget;
import io.github.relozia.kestrel.module.KestrelSettings;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.module.Modules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

// drag widgets around. a toolbar at the bottom middle: Done, snapping and the grid, a list of every widget, then
// Kestrel's settings and close. snaps to the screen's edges and middle, and to other widgets' edges with a small gap
// issues:
//   - dragged widgets stay solid, only a faint outline marks where it came from. widgets draw their own colors,
//     there's no one place to fade a whole widget
//   - the toolbar jumps to the top while you drag something over it, a widget can't be dropped under it
public class HudEditorScreen extends Screen {
	private static final char I_CHECK = '\ue06c', I_GEAR = '\ue154', I_CLOSE = '\ue1b2', I_LIST = '\ue529';
	private static final int GRID = 8;
	private static final float BAR_W = 520, BAR_H = 52, LIST_W = 260, LIST_ROW = 30;

	private final Screen parent;
	private HudWidget drag;
	private double offX, offY;
	private float fromX, fromY;
	private float guideX = -1, guideY = -1;

	private boolean listOpen;
	private float listScroll;
	private HudWidget peek;

	// toolbar space: the menu's scale, so it matches the menu's size. top is eased when it dodges a drag
	private float k, bx, by, top = -1;
	private long last = Util.getMillis();
	private record Hit(float x, float y, float w, float h, Runnable r) {}
	private final List<Hit> hits = new ArrayList<>();

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// for the whole time the editor is open, not just while drawing: clicks and drags happen between
	// frames and an empty widget (server in singleplayer) still has to be grabbable
	@Override
	protected void init() {
		HudWidget.editing = true;
	}

	@Override
	public void removed() {
		HudWidget.editing = false;
	}

	private List<HudWidget> widgets() {
		List<HudWidget> out = new ArrayList<>();
		for (Module m : Modules.all()) {
			if (m instanceof HudWidget w && w.on()) for (HudWidget p : w.boxes()) if (p.visible()) out.add(p);
		}
		return out;
	}

	private HudWidget at(double mx, double my) {
		List<HudWidget> ws = widgets();
		// last drawn is on top, so check back to front
		for (int i = ws.size() - 1; i >= 0; i--) {
			HudWidget w = ws.get(i);
			if (Ui.in(mx, my, w.px(width), w.py(height), Math.round(w.w() * w.scale), Math.round(w.h() * w.scale))) return w;
		}
		return null;
	}

	// ---------- toolbar ----------

	private void place() {
		var win = minecraft.getWindow();
		k = KestrelScreen.menuScale(win.getWidth(), win.getHeight()) / (float) win.getGuiScale();
		float w = BAR_W * k, h = BAR_H * k;
		// down by the hotbar normally, up top while a dragged widget would land under it
		boolean up = drag != null && drag.py(height) + drag.h() * drag.scale > height - h - 40 * k
			&& drag.px(width) < width / 2f + w / 2 && drag.px(width) + drag.w() * drag.scale > width / 2f - w / 2;
		float goal = up ? 16 * k : height - h - 16 * k;
		long now = Util.getMillis();
		float dt = Math.min(0.1f, (now - last) / 1000f);
		last = now;
		top = top < 0 ? goal : top + (goal - top) * (1 - (float) Math.exp(-dt * 14));
		bx = (width - w) / 2;
		by = top;
	}

	private boolean over(double mx, double my, float x, float y, float w, float h) {
		float lx = (float) (mx - bx) / k, ly = (float) (my - by) / k;
		return lx >= x && ly >= y && lx < x + w && ly < y + h;
	}

	private void bar(GuiGraphicsExtractor g, int mx, int my) {
		hits.clear();
		KestrelSettings ks = Modules.SETTINGS;
		boolean solid = ks.menuStyle.get().equals("Solid");
		g.pose().pushMatrix();
		g.pose().translate(bx, by);
		g.pose().scale(k, k);
		if (!solid) Shape.glassPx(g, 0.8f, 0.8f, BAR_W - 1.6f, BAR_H - 1.6f, 15, 1, 14);
		Shape.shadow(g, 0, 4, BAR_W, BAR_H, 16, 10, 0, 0x40000000);
		Shape.rect(g, 0, 0, BAR_W, BAR_H, 16, solid ? 0xF510141A : 0xB010141A);
		Shape.outline(g, 0, 0, BAR_W, BAR_H, 16, 0x26DCE6F5, 1);

		// Done is the one filled button, it's the way out you want most
		boolean hd = over(mx, my, 8, 8, 88, 36);
		Shape.rect(g, 8, 8, 88, 36, 10, ARGB.color(hd ? 0xFF : 0xDD, Ui.accent()));
		Ui.glyph(g, I_CHECK, 28, 26, 13, 0xFFFFFFFF);
		Ui.label(g, "Done", true, 15, 40, 31, 0xFFFFFFFF, 'l');
		hit(8, 8, 88, 36, () -> minecraft.gui.setScreen(parent));
		Shape.rect(g, 106, 14, 1, 24, 0, 0x1FDCE6F5);

		toggle(g, mx, my, 116, "Snap", ks.editorSnap.on(), ks.editorSnap::flip);
		toggle(g, mx, my, 220, "Grid", ks.editorGrid.on(), ks.editorGrid::flip);

		boolean hl = listOpen || over(mx, my, 324, 8, 112, 36);
		Shape.rect(g, 324, 8, 112, 36, 10, listOpen ? 0x22FFFFFF : hl ? 0x17FFFFFF : 0x0AFFFFFF);
		Ui.glyph(g, I_LIST, 342, 26, 13, listOpen ? Ui.accent() : 0xFFC9D0DA);
		Ui.label(g, "Widgets", true, 14, 356, 31, 0xFFF1F4F8, 'l');
		hit(324, 8, 112, 36, () -> {
			listOpen = !listOpen;
			listScroll = 0;
		});
		Shape.rect(g, 444, 14, 1, 24, 0, 0x1FDCE6F5);

		iconButton(g, mx, my, 452, I_GEAR, () -> minecraft.gui.setScreen(KestrelScreen.settingsPage()));
		iconButton(g, mx, my, 486, I_CLOSE, () -> minecraft.gui.setScreen(null));
		g.pose().popMatrix();
		if (listOpen) list(g, mx, my);
	}

	private void toggle(GuiGraphicsExtractor g, int mx, int my, float x, String label, boolean on, Runnable flip) {
		if (over(mx, my, x, 8, 96, 36)) Shape.rect(g, x, 8, 96, 36, 10, 0x12FFFFFF);
		Ui.label(g, label, true, 14, x + 12, 31, on ? 0xFFF1F4F8 : 0xFFA1ABB8, 'l');
		Shape.rect(g, x + 52, 16, 34, 20, 10, on ? Ui.accent() : 0x30FFFFFF);
		Shape.rect(g, x + 55 + (on ? 14 : 0), 19, 14, 14, 7, 0xFFFFFFFF);
		hit(x, 8, 96, 36, () -> {
			flip.run();
			Config.dirty();
		});
	}

	private void iconButton(GuiGraphicsExtractor g, int mx, int my, float x, char ic, Runnable r) {
		boolean h = over(mx, my, x, 10, 30, 32);
		if (h) Shape.rect(g, x, 10, 30, 32, 9, 0x17FFFFFF);
		Ui.glyph(g, ic, x + 15, 26, 14, h ? 0xFFF1F4F8 : 0xFFA1ABB8);
		hit(x, 10, 30, 32, r);
	}

	private void hit(float x, float y, float w, float h, Runnable r) {
		hits.add(new Hit(x, y, w, h, r));
	}

	// every widget, on or off, sorted by name. hovering one outlines it on screen so you can find it
	private List<HudWidget> all() {
		List<HudWidget> out = new ArrayList<>();
		for (Module m : Modules.all()) if (m instanceof HudWidget w) out.add(w);
		out.sort(Comparator.comparing(w -> w.name.toLowerCase(Locale.ROOT)));
		return out;
	}

	private float listH(int n) {
		return Math.min(n * LIST_ROW, 300) + 12;
	}

	// above the toolbar, or under it once the toolbar went to the top
	private float listY(float h) {
		return top < height / 2f ? BAR_H + 8 : -h - 8;
	}

	private void list(GuiGraphicsExtractor g, int mx, int my) {
		List<HudWidget> ws = all();
		float h = listH(ws.size()), ly = listY(h), lx = 324 + 112 - LIST_W;
		g.pose().pushMatrix();
		g.pose().translate(bx, by);
		g.pose().scale(k, k);
		Shape.shadow(g, lx, ly + 4, LIST_W, h, 12, 10, 0, 0x50000000);
		Shape.rect(g, lx, ly, LIST_W, h, 12, 0xF516191F);
		Shape.outline(g, lx, ly, LIST_W, h, 12, 0x33DCE6F5, 1);
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, ws.size() * LIST_ROW - (h - 12)));
		g.enableScissor(Math.round(lx), Math.round(ly + 6), Math.round(lx + LIST_W), Math.round(ly + h - 6));
		peek = null;
		for (int i = 0; i < ws.size(); i++) {
			HudWidget w = ws.get(i);
			float ry = ly + 6 + i * LIST_ROW - listScroll;
			if (ry + LIST_ROW < ly || ry > ly + h) continue;
			boolean hv = over(mx, my, lx + 6, ry, LIST_W - 12, LIST_ROW) && over(mx, my, lx, ly + 6, LIST_W, h - 12);
			if (hv) {
				Shape.rect(g, lx + 6, ry, LIST_W - 12, LIST_ROW, 7, 0x1AFFFFFF);
				if (w.on()) peek = w;
			}
			Ui.label(g, w.name, true, 13.5f, lx + 16, ry + 19.5f, w.on() ? 0xFFF1F4F8 : 0xFF7D8796, 'l');
			float sx = lx + LIST_W - 16 - 30;
			Shape.rect(g, sx, ry + 8, 30, 14, 7, w.on() ? Ui.accent() : 0x30FFFFFF);
			Shape.rect(g, sx + 2 + (w.on() ? 16 : 0), ry + 10, 10, 10, 5, 0xFFFFFFFF);
			// half hidden rows at the edges stay clickable only on their visible part
			float t = Math.max(ry, ly + 6), b = Math.min(ry + LIST_ROW, ly + h - 6);
			if (b > t) hit(lx + 6, t, LIST_W - 12, b - t, w::toggle);
		}
		g.disableScissor();
		g.pose().popMatrix();
	}

	private boolean overList(double mx, double my) {
		float h = listH(all().size());
		return over(mx, my, 324 + 112 - LIST_W, listY(h), LIST_W, h);
	}

	// ---------- rendering ----------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		// nothing, the whole point is seeing where things sit over the game
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		place();
		if (Modules.SETTINGS.editorGrid.on()) {
			// every 8 gui px, a bit stronger every 32 and on the middle lines
			for (int x = 0; x < width; x += GRID) Shape.rect(g, x, 0, 1, height, 0, x % 32 == 0 ? 0x1CFFFFFF : 0x0CFFFFFF);
			for (int y = 0; y < height; y += GRID) Shape.rect(g, 0, y, width, 1, 0, y % 32 == 0 ? 0x1CFFFFFF : 0x0CFFFFFF);
			Shape.rect(g, width / 2f, 0, 1, height, 0, 0x26FFFFFF);
			Shape.rect(g, 0, height / 2f, width, 1, 0, 0x26FFFFFF);
		}
		boolean onBar = over(mx, my, 0, 0, BAR_W, BAR_H) || listOpen && overList(mx, my);
		HudWidget hov = drag != null ? drag : onBar ? null : at(mx, my);
		if (drag != null) {
			int ww = Math.round(drag.w() * drag.scale), hh = Math.round(drag.h() * drag.scale);
			Shape.outline(g, fromX - 1, fromY - 1, ww + 2, hh + 2, 6, 0x40FFFFFF, 1);
		}
		for (HudWidget w : widgets()) {
			w.render(g, width, height);
			int x = w.px(width), y = w.py(height), ww = Math.round(w.w() * w.scale), hh = Math.round(w.h() * w.scale);
			// every widget gets a faint frame so the ones without text can still be found, the one you're on gets the accent
			boolean lit = w == hov || w == peek;
			Shape.outline(g, x - 2, y - 2, ww + 4, hh + 4, 6, lit ? ARGB.color(0.8f, Ui.accent()) : 0x1EFFFFFF, 1);
		}
		int guide = ARGB.color(0.7f, Ui.accent());
		if (guideX >= 0) Shape.rect(g, guideX, 0, 1, height, 0, guide);
		if (guideY >= 0) Shape.rect(g, 0, guideY, width, 1, 0, guide);
		if (widgets().isEmpty()) {
			Component c = Ui.t("No widgets are on yet, turn some on under Widgets");
			Ui.text(g, c, (width - Ui.width(c)) / 2, height / 2 - 20, Ui.TEXT);
		}
		bar(g, mx, my);
	}

	// ---------- input ----------

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (over(e.x(), e.y(), h.x, h.y, h.w, h.h)) {
				if (e.button() == 0) {
					Ui.sound("click");
					h.r.run();
				}
				return true;
			}
		}
		if (listOpen) {
			// a click anywhere else just closes the list, it shouldn't also grab the widget under it
			listOpen = false;
			return true;
		}
		HudWidget w = at(e.x(), e.y());
		if (w == null) return true;
		if (e.button() == 1) {
			w.bg.flip();
			Ui.sound("click");
		} else if (e.button() == 0) {
			drag = w;
			offX = e.x() - w.px(width);
			offY = e.y() - w.py(height);
			fromX = w.px(width);
			fromY = w.py(height);
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (drag == null) return true;
		KestrelSettings ks = Modules.SETTINGS;
		float gs = (float) minecraft.getWindow().getGuiScale();
		// thresholds in screen pixels: 5 to the screen, 2 to another widget with a 6 pixel gap
		float screen = 5 / gs, near = 2 / gs, gap = 6 / gs;
		float ww = drag.w() * drag.scale, hh = drag.h() * drag.scale;
		float nx = (float) (e.x() - offX), ny = (float) (e.y() - offY);
		guideX = guideY = -1;

		float[] sx = null, sy = null;
		if (ks.editorSnap.on()) {
			List<float[]> xs = new ArrayList<>(List.of(new float[] {0, screen}, new float[] {width / 2f, screen}, new float[] {width, screen}));
			List<float[]> ys = new ArrayList<>(List.of(new float[] {0, screen}, new float[] {height / 2f, screen}, new float[] {height, screen}));
			for (HudWidget o : widgets()) {
				if (o == drag) continue;
				float ox = o.px(width), oy = o.py(height), ow = Math.round(o.w() * o.scale), oh = Math.round(o.h() * o.scale);
				for (float v : new float[] {ox, ox + ow / 2, ox + ow, ox - gap, ox + ow + gap}) xs.add(new float[] {v, near});
				for (float v : new float[] {oy, oy + oh / 2, oy + oh, oy - gap, oy + oh + gap}) ys.add(new float[] {v, near});
			}
			sx = snap(nx, ww, xs);
			sy = snap(ny, hh, ys);
		}
		// a snap line wins, the grid only rounds whatever didn't snap
		if (sx != null) {
			nx = sx[0];
			guideX = sx[1];
		} else if (ks.editorGrid.on()) {
			nx = Math.round(nx / GRID) * GRID;
		}
		if (sy != null) {
			ny = sy[0];
			guideY = sy[1];
		} else if (ks.editorGrid.on()) {
			ny = Math.round(ny / GRID) * GRID;
		}

		drag.moveTo(nx, ny, width, height);
		Config.dirty();
		return true;
	}

	// tries the widget's start, middle and end against every line, closest one inside its threshold wins.
	// returns {new position, line} or null
	private static float[] snap(float pos, float size, List<float[]> lines) {
		float best = Float.MAX_VALUE;
		float[] out = null;
		float[] edges = {0, size / 2, size};
		for (float edge : edges) {
			for (float[] l : lines) {
				float d = Math.abs(pos + edge - l[0]);
				if (d <= l[1] && d < best) {
					best = d;
					out = new float[] {l[0] - edge, l[0]};
				}
			}
		}
		return out;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		drag = null;
		guideX = guideY = -1;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		if (listOpen && overList(x, y)) {
			listScroll -= (float) sy * LIST_ROW * 2;
			return true;
		}
		HudWidget w = at(x, y);
		if (w == null) return true;
		int ox = w.px(width), oy = w.py(height);
		w.scale = Mth.clamp(Math.round((w.scale + (float) sy * 0.05f) * 20) / 20f, 0.65f, 1.75f);
		// keep the top left corner where it was, otherwise it drifts toward the middle as it grows
		w.moveTo(ox, oy, width, height);
		Config.dirty();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.isEscape()) {
			if (listOpen) listOpen = false;
			else minecraft.gui.setScreen(parent);
			return true;
		}
		if (KeyMappingHelper.getBoundKeyOf(Kestrel.menuKey).getValue() == e.key()) {
			minecraft.gui.setScreen(null);
			return true;
		}
		return super.keyPressed(e);
	}
}
