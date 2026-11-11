package io.github.relozia.shrike.gui;

import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.config.Blacklist;
import io.github.relozia.shrike.config.Config;
import io.github.relozia.shrike.config.Profiles;
import io.github.relozia.shrike.hud.HudWidget;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.module.Modules;
import io.github.relozia.shrike.nav.Nav;
import io.github.relozia.shrike.setting.Setting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

// the right shift menu: a rail on the left (search, categories, waypoints / hud editor / settings), the module list
// in the middle and the open module's settings as a third column. under ~1180 layout px there's no room for three,
// then the settings slide over the list instead
// everything is laid out in screen pixels at the menu scale and scaled into gui space once
// issues:
//   - the hud is hidden while the menu is open, it doesn't fade
//   - search is click to type, no caret or selection
//   - module descriptions are cut to one line in the list, the settings header shows the whole thing
public class ShrikeScreen extends Screen {
	// text, cool greys so the accent is the only warm thing on screen
	static final int PRIMARY = 0xFFF1F4F8, SECONDARY = 0xFFC9D0DA, MUTED = 0xFFA1ABB8, FAINT = 0xFF7D8796;
	private static final int SURFACE = 0x0AFFFFFF, SOFT = 0x10FFFFFF, S_HOVER = 0x17FFFFFF, S_ACTIVE = 0x22FFFFFF;
	private static final int OUTLINE = 0x1CDCE6F5, O_HOVER = 0x30DCE6F5;
	private static final int POPUP = 0xF516191F, POPUP_LINE = 0x33DCE6F5;

	// lucide
	private static final char I_SEARCH = '\ue151', I_STAR = '\ue176', I_GRID = '\ue0ff', I_PIN = '\ue111', I_LAYERS = '\ue529',
		I_GEAR = '\ue154', I_BACK = '\ue06e', I_CHECK = '\ue06c', I_RESET = '\ue148', I_KEY = '\ue284', I_DOWN = '\ue06d',
		I_USER = '\ue468';

	private static final float RAIL = 236, ROW = 60, SROW = 46;

	// remembered between opens: which view, the category and what you typed
	private static int lastTab, lastChip;
	private static String lastQuery = "";

	private int tab = lastTab, chip = lastChip, prevChip = -1;
	private String query = lastQuery;
	private Module page, prevPage;

	private float vis;
	private boolean closing;
	private long last = Util.getMillis();
	private float dt;
	private final Map<Object, Float> anim = new HashMap<>();

	private float scroll, scrollGoal, pageScroll, pageGoal, wpScroll, wpScrollGoal, proScroll, proScrollGoal;
	private float maxScroll, maxPage, maxWp, maxPro;
	// which of the two side views the crossfade should draw, so leaving profiles doesn't flash the waypoints
	private int sideTab = 1;
	// Profiles.all() lists the folder, which is not something to do every frame
	private List<String> proList = List.of();
	private @Nullable String proRenaming;

	// layout space: the window in screen pixels, scaled about its middle. the columns are read by the menu test
	private float vw, vh, eff, sx, sy, sw, sh;
	private float lx, lw, px, pw;
	private boolean wide;
	private float mx, my;

	private Object focus;
	private Setting<?> dropdown;
	private float ddx, ddy, ddw, ddScroll;
	private Setting.Color colorEdit;
	private float cpx, cpy;
	private int colorDrag;
	private float cqx, cqy;
	private Setting.Bind listening;
	private Setting.Slider dragSlider;
	private float dragTrackX, dragTrackW;

	private final Field wpSearch = new Field(), wpName = new Field(), wpX = new Field(), wpY = new Field(), wpZ = new Field();
	private final Field proName = new Field();
	private String wpDim = "minecraft:overworld";
	private Nav.Waypoint wpEditing;
	private String wpStatus = "";
	private long wpStatusAt;

	private record Hit(float x, float y, float w, float h, Clicker c) {}

	private interface Clicker {
		void click(int button);
	}

	// filled while drawing, so clicks always land on what was actually drawn. checked back to front
	private final List<Hit> hits = new ArrayList<>();

	private static final class Field {
		String text = "";
	}

	public ShrikeScreen() {
		super(Component.literal("Shrike"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		if (vis == 0) Ui.sound("open");
	}

	@Override
	public void removed() {
		lastTab = tab;
		lastChip = chip;
		lastQuery = query;
		Ui.alpha = 1;
	}

	private void close() {
		if (closing) return;
		closing = true;
		Ui.sound("close");
	}

	@Override
	public void onClose() {
		close();
	}

	// ---------- easing ----------

	// eases toward the target, rate is how quickly (about 1/rate seconds to get most of the way)
	private float ease(Object key, float target, float rate) {
		float v = anim.getOrDefault(key, target);
		v += (target - v) * (1 - (float) Math.exp(-dt * rate));
		if (Math.abs(target - v) < 0.001f) v = target;
		anim.put(key, v);
		return v;
	}

	private float hover(Object key, boolean over) {
		return smooth(ease(key, over ? 1 : 0, 9));
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	private static int mix(int a, int b, float t) {
		return ARGB.srgbLerp(Mth.clamp(t, 0, 1), a, b);
	}

	private static int tint(int alpha) {
		return ARGB.color(alpha, Ui.accent());
	}

	private boolean in(float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private void hit(float x, float y, float w, float h, Clicker c) {
		hits.add(new Hit(x, y, w, h, c));
	}

	private void click(float x, float y, float w, float h, boolean live, Runnable r) {
		if (live) hit(x, y, w, h, b -> {
			if (b == 0) {
				Ui.sound("click");
				r.run();
			}
		});
	}

	private boolean solid() {
		return Modules.SETTINGS.menuStyle.get().equals("Solid");
	}

	private int pc(int blur, int solidColor) {
		return solid() ? solidColor : blur;
	}

	// ---------- drawing, layout pixels ----------

	private static String fit(String s, boolean bold, float size, float max) {
		if (Ui.tw(s, bold, size) <= max) return s;
		while (s.length() > 1 && Ui.tw(s + "...", bold, size) > max) s = s.substring(0, s.length() - 1);
		return s.stripTrailing() + "...";
	}

	private void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int c) {
		Shape.rect(g, x, y, w, h, r, Ui.c(c));
	}

	private void line(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int c, float t) {
		Shape.outline(g, x, y, w, h, r, Ui.c(c), t);
	}

	private void scissor(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
	}

	// pill switch, the knob slides and the track fills with the accent
	private void toggle(GuiGraphicsExtractor g, Object key, float x, float y, boolean on, float hv) {
		float t = smooth(ease(key, on ? 1 : 0, 14));
		rect(g, x, y, 40, 22, 11, mix(mix(0x26FFFFFF, 0x33FFFFFF, hv), Ui.accent(), t));
		rect(g, x + 3 + 18 * t, y + 3, 16, 16, 8, mix(0xFFB9C1CC, 0xFFFFFFFF, Math.max(t, hv)));
	}

	private void button(GuiGraphicsExtractor g, String key, float x, float y, float w, float h, String label, boolean live, Runnable r) {
		float hv = hover(key, live && in(x, y, w, h));
		rect(g, x, y, w, h, 8, mix(SOFT, S_ACTIVE, hv));
		line(g, x, y, w, h, 8, mix(OUTLINE, O_HOVER, hv), 1);
		Ui.label(g, label, true, 13.5f, x + w / 2, y + h / 2 + 4.5f, mix(SECONDARY, PRIMARY, hv), 'c');
		click(x, y, w, h, live, r);
	}

	private void checker(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		for (int i = 0; i * 6 < w; i++) for (int j = 0; j * 6 < h; j++) {
			rect(g, x + i * 6, y + j * 6, Math.min(6, w - i * 6), Math.min(6, h - j * 6), 0, (i + j) % 2 == 0 ? 0xFF3C3F44 : 0xFF2A2C30);
		}
	}

	// ---------- frame ----------

	// the menu's scale from the Menu Scale setting, the hud editor's toolbar uses it too
	static float menuScale(float vw, float vh) {
		float auto = Math.round(Mth.clamp((float) Math.sqrt(vw * vh / (2560f * 1440f)), 0.5f, 1.2f) * 20) / 20f;
		float conf = switch (Modules.SETTINGS.menuScale.get()) {
			case "80%" -> auto * 0.8f;
			case "120%" -> auto * 1.12f;
			default -> auto;
		};
		conf = Mth.clamp(conf, 0.35f, 1.6f);
		return 0.35f + smooth((conf - 0.35f) / 1.25f) * 1.25f;
	}

	// straight to the Shrike settings, the editor toolbar's gear
	static ShrikeScreen settingsPage() {
		ShrikeScreen k = new ShrikeScreen();
		k.tab = 0;
		k.page = Modules.SETTINGS;
		k.anim.put("page", 1f);
		return k;
	}

	private void layout() {
		var win = minecraft.getWindow();
		vw = win.getWidth();
		vh = win.getHeight();
		eff = menuScale(vw, vh);
		float lwv = vw / eff, lhv = vh / eff;
		sw = Mth.clamp(lwv * 0.74f, 900, 1480);
		sh = Mth.clamp(lhv * 0.76f, 560, 900);
		// never wider than the window, small windows at a big menu scale would cut it off
		sw = Math.min(sw, lwv - 24);
		sh = Math.min(sh, lhv - 24);
		sx = (vw - sw) / 2;
		sy = (vh - sh) / 2;
		wide = sw >= 1180;
		lx = sx + RAIL;
		lw = wide ? (sw - RAIL) * 0.47f : sw - RAIL;
		px = wide ? lx + lw : lx;
		pw = sx + sw - px;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int x, int y, float a) {
		// in a world it stays sharp around the menu, only the shell blurs what's behind it. from the title screen
		// there's no world, the panorama goes there instead of black
		if (minecraft.level == null) extractPanorama(g, a);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int gmx, int gmy, float a) {
		long now = Util.getMillis();
		dt = Math.min(0.1f, (now - last) / 1000f);
		last = now;
		vis = Mth.clamp(vis + (closing ? -dt : dt) / 0.28f, 0, 1);
		if (closing && vis <= 0) {
			minecraft.gui.setScreen(null);
			return;
		}
		layout();
		// rises a little as it fades in, no zoom
		float rise = 22 * (1 - smooth(vis));
		float gs = (float) minecraft.getWindow().getGuiScale();
		mx = vw / 2 + (gmx * gs - vw / 2) / eff;
		my = vh / 2 + (gmy * gs - vh / 2) / eff - rise;
		hits.clear();
		Ui.alpha = smooth(vis);
		scroll += (scrollGoal - scroll) * (1 - (float) Math.exp(-dt * 14));
		pageScroll += (pageGoal - pageScroll) * (1 - (float) Math.exp(-dt * 14));
		wpScroll += (wpScrollGoal - wpScroll) * (1 - (float) Math.exp(-dt * 14));
		proScroll += (proScrollGoal - proScroll) * (1 - (float) Math.exp(-dt * 14));

		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 2f);
		g.pose().scale(eff / gs, eff / gs);
		g.pose().translate(-vw / 2, -vh / 2 + rise);

		shell(g);
		rail(g);
		float tabT = smooth(ease("tab", tab == 0 ? 0 : 1, 10));
		if (tabT < 0.999f) fade(g, 1 - tabT, () -> modules(g, tab == 0));
		if (tabT > 0.001f) fade(g, tabT, () -> {
			if (sideTab == 2) profiles(g, tab == 2);
			else waypoints(g, tab == 1);
		});
		popups(g);
		g.pose().popMatrix();
		if (dragSlider != null) slide();
	}

	private void fade(GuiGraphicsExtractor g, float alpha, Runnable r) {
		float keep = Ui.alpha;
		Ui.alpha = keep * alpha;
		r.run();
		Ui.alpha = keep;
	}

	private void shell(GuiGraphicsExtractor g) {
		if (!solid()) Shape.glassPx(g, sx + 0.9f, sy + 0.9f, sw - 1.8f, sh - 1.8f, 21, Ui.alpha, 16);
		Shape.shadow(g, sx, sy + 6, sw, sh, 22, 14, 0, Ui.c(0x40000000));
		rect(g, sx, sy, sw, sh, 22, pc(0xA810141A, 0xF510141A));
		// the rail is a darker strip, drawn wider and cut at its edge so only the outer corners round
		scissor(g, sx, sy, RAIL, sh);
		rect(g, sx, sy, RAIL + 30, sh, 22, pc(0x2E000000, 0x1C000000));
		g.disableScissor();
		rect(g, sx + RAIL, sy + 18, 1, sh - 36, 0, 0x14DCE6F5);
		if (wide && tab == 0) rect(g, px, sy + 18, 1, sh - 36, 0, 0x14DCE6F5);
		line(g, sx, sy, sw, sh, 22, pc(0x26DCE6F5, 0x38DCE6F5), 1);
	}

	// ---------- rail ----------

	private static final String[] CHIPS = chips();

	private static String[] chips() {
		List<String> c = new ArrayList<>(List.of("All", "Favorites"));
		for (Category cat : Category.values()) c.add(cat.label);
		return c.toArray(String[]::new);
	}

	private static char chipIcon(int i) {
		return i == 0 ? I_GRID : i == 1 ? I_STAR : Category.values()[i - 2].icon;
	}

	private void rail(GuiGraphicsExtractor g) {
		Ui.logo(g, sx + 22, sy + 26, 24, Ui.accent());
		Ui.label(g, "Shrike", true, 21, sx + 54, sy + 45, PRIMARY, 'l');

		float fx = sx + 16, fy = sy + 68, fw = RAIL - 32;
		boolean focused = "search".equals(focus);
		float hp = focused ? 1 : hover("search", in(fx, fy, fw, 36));
		rect(g, fx, fy, fw, 36, 10, mix(0x14000000, SOFT, hp));
		line(g, fx, fy, fw, 36, 10, focused ? tint(0x99) : mix(OUTLINE, O_HOVER, hp), 1);
		Ui.glyph(g, I_SEARCH, fx + 18, fy + 18, 13, focused ? PRIMARY : FAINT);
		if (query.isEmpty() && !focused) Ui.label(g, "Search", false, 14.5f, fx + 34, fy + 23, FAINT, 'l');
		else Ui.label(g, fit(query + (focused ? "_" : ""), false, 14.5f, fw - 44), false, 14.5f, fx + 34, fy + 23, PRIMARY, 'l');
		hit(fx, fy, fw, 36, b -> {
			focus = "search";
			if (tab != 0) setTab(0);
		});

		float y = sy + 122;
		for (int i = 0; i < CHIPS.length; i++) {
			int c = i;
			railItem(g, "chip" + i, y, chipIcon(i), CHIPS[i], Integer.toString(count(i)), tab == 0 && chip == i && query.isEmpty(), () -> {
				if (chip != c) {
					prevChip = chip;
					chip = c;
					anim.put("chipT", 0f);
				}
				query = "";
				scroll = scrollGoal = 0;
				setTab(0);
			});
			y += 38;
		}

		float by = sy + sh - 14 - 38 * 4;
		rect(g, sx + 20, by - 10, RAIL - 40, 1, 0, 0x12DCE6F5);
		railItem(g, "rwp", by, I_PIN, "Waypoints", null, tab == 1, () -> setTab(1));
		railItem(g, "rhud", by + 38, I_LAYERS, "Edit HUD", null, false, () -> minecraft.gui.setScreen(new HudEditorScreen(this)));
		railItem(g, "rpro", by + 76, I_USER, "Profiles", null, tab == 2, () -> setTab(2));
		railItem(g, "rset", by + 114, I_GEAR, "Settings", null, tab == 0 && page == Modules.SETTINGS, () -> {
			setTab(0);
			openPage(Modules.SETTINGS);
		});
	}

	private void railItem(GuiGraphicsExtractor g, String key, float y, char ic, String label, String count, boolean active, Runnable r) {
		float x = sx + 12, w = RAIL - 24, h = 34;
		float hv = hover(key, in(x, y, w, h)), act = smooth(ease(key + "a", active ? 1 : 0, 12));
		if (hv > 0.01f || act > 0.01f) rect(g, x, y, w, h, 9, mix(ARGB.multiplyAlpha(S_HOVER, hv), S_ACTIVE, act));
		if (act > 0.01f) rect(g, x + 1, y + 9, 3, 16, 1.5f, ARGB.multiplyAlpha(Ui.accent(), act));
		Ui.glyph(g, ic, x + 22, y + h / 2, 14, mix(mix(MUTED, PRIMARY, hv), Ui.accent(), act));
		Ui.label(g, label, act > 0.5f, 15, x + 42, y + h / 2 + 5, mix(mix(SECONDARY, PRIMARY, hv), PRIMARY, act), 'l');
		if (count != null) Ui.label(g, count, false, 12.5f, x + w - 12, y + h / 2 + 4.5f, FAINT, 'r');
		click(x, y, w, h, true, r);
	}

	private void setTab(int t) {
		if (tab == t) return;
		tab = t;
		if (t != 0) sideTab = t;
		if (t == 2) {
			proList = Profiles.all();
			proRenaming = null;
			proName.text = "";
			proScrollGoal = 0;
		}
		closePopups();
	}

	// ---------- module list ----------

	private int count(int which) {
		int n = 0;
		for (Module m : Modules.all()) if (m != Modules.SETTINGS && matches(m, which)) n++;
		return n;
	}

	private static boolean matches(Module m, int which) {
		return which == 0 || which == 1 && m.fav() || which >= 2 && m.cat == Category.values()[which - 2];
	}

	private List<Module> mods(int which) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Module> out = new ArrayList<>();
		for (Module m : Modules.all()) {
			if (m == Modules.SETTINGS) continue;
			// search looks through everything, the category is only for browsing
			if (!q.isEmpty() ? m.name.toLowerCase(Locale.ROOT).contains(q) || m.desc.toLowerCase(Locale.ROOT).contains(q) : matches(m, which)) out.add(m);
		}
		out.sort(Comparator.comparing(m -> m.name.toLowerCase(Locale.ROOT)));
		return out;
	}

	private void modules(GuiGraphicsExtractor g, boolean live) {
		float st = smooth(ease("page", page != null ? 1 : 0, 11));
		if (wide) {
			list(g, live);
			settingsColumn(g, live);
			return;
		}
		// narrow: the settings slide over the list from the right
		// the list fades all the way out, anything left under the page would read through the glass
		if (st < 0.999f) fade(g, 1 - st, () -> list(g, live && page == null));
		if (st > 0.001f) {
			g.pose().pushMatrix();
			g.pose().translate(48 * (1 - st), 0);
			settingsColumn(g, live && page != null);
			g.pose().popMatrix();
		}
	}

	private void list(GuiGraphicsExtractor g, boolean live) {
		List<Module> ms = mods(chip);
		int on = 0;
		for (Module m : ms) if (m.on()) on++;
		String title = query.isBlank() ? CHIPS[chip] : "Results";
		Ui.label(g, title, true, 25, lx + 28, sy + 48, PRIMARY, 'l');
		Ui.label(g, ms.size() + (ms.size() == 1 ? " module, " : " modules, ") + on + " on", false, 13.5f, lx + 28 + Ui.tw(title, true, 25) + 12, sy + 47, FAINT, 'l');

		float vx = lx + 14, vy = sy + 70, vww = lw - 28, vhh = sy + sh - 14 - vy;
		float chipT = smooth(ease("chipT", 1, 11));
		scissor(g, vx, vy, vww, vhh);
		if (prevChip >= 0 && chipT < 0.999f && query.isBlank()) {
			// the old category's rows drop away while the new ones come up
			fade(g, 1 - chipT, () -> rows(g, mods(prevChip), vx, vy + 14 * chipT, vww, vhh, false));
			fade(g, chipT, () -> rows(g, ms, vx, vy + 14 * (1 - chipT), vww, vhh, live));
		} else {
			rows(g, ms, vx, vy, vww, vhh, live);
		}
		g.disableScissor();
		if (live) maxScroll = Math.max(0, ms.size() * (ROW + 4) + 8 - vhh);
	}

	private void rows(GuiGraphicsExtractor g, List<Module> ms, float vx, float vy, float vww, float vhh, boolean live) {
		if (ms.isEmpty()) {
			Ui.label(g, query.isBlank() ? "Nothing here yet" : "No modules match \"" + query.trim() + "\"", true, 16, vx + vww / 2, vy + vhh / 2 - 4, SECONDARY, 'c');
			Ui.label(g, query.isBlank() ? "Star a module in its settings to pin it here" : "Search checks names and descriptions", false, 13.5f, vx + vww / 2, vy + vhh / 2 + 18, FAINT, 'c');
			return;
		}
		boolean inView = in(vx, vy, vww, vhh);
		for (int i = 0; i < ms.size(); i++) {
			Module m = ms.get(i);
			float x = vx, y = vy + 4 + i * (ROW + 4) - scroll, w = vww;
			if (y + ROW < vy - 2 || y > vy + vhh + 2) continue;
			boolean over = live && inView && in(x, y, w, ROW);
			float hv = hover(m, over), sel = smooth(ease("sel" + m.name, page == m ? 1 : 0, 12));
			float on = smooth(ease("on" + m.name, m.on() ? 1 : 0, 12));
			if (hv > 0.01f || sel > 0.01f) rect(g, x, y, w, ROW, 12, mix(ARGB.multiplyAlpha(S_HOVER, hv), S_ACTIVE, sel));
			if (sel > 0.01f) rect(g, x + 1, y + 16, 3, ROW - 32, 1.5f, ARGB.multiplyAlpha(Ui.accent(), sel));

			rect(g, x + 14, y + 13, 34, 34, 10, mix(SOFT, tint(0x2E), on));
			Ui.glyph(g, m.icon, x + 31, y + 30, 16, mix(SECONDARY, Ui.accent(), on));
			float nx = x + 60;
			Ui.label(g, m.name, true, 16.5f, nx, y + 27, mix(SECONDARY, PRIMARY, Math.max(on, hv)), 'l');
			if (m.fav()) Ui.glyph(g, I_STAR, nx + Ui.tw(m.name, true, 16.5f) + 12, y + 22, 10, FAINT);
			boolean blocked = m.blocked();
			// a module can be fine while one of its options isn't, and a dimmed setting with nothing explaining
			// it just looks broken
			String sub = blocked ? blockedBy() : anyBlocked(m) ? "Some settings " + blockedBy().toLowerCase(Locale.ROOT) : m.desc;
			Ui.label(g, fit(sub, false, 13, w - 60 - 72), false, 13, nx, y + 45, MUTED, 'l');

			float tx = x + w - 16 - 40, ty = y + (ROW - 22) / 2;
			// a blocked row still shows the switch where you left it, just dimmed and dead, so it reads as
			// "the server won't have this" rather than "your setting got wiped"
			if (blocked) fade(g, 0.35f, () -> toggle(g, "sw" + m.name, tx, ty, m.enabled(), 0));
			else toggle(g, "sw" + m.name, tx, ty, m.on(), hover("swh" + m.name, over && in(tx - 6, ty - 8, 52, 38)));
			if (live && inView) {
				hit(x, y, w, ROW, b -> {
					if (b == 0 || b == 1) {
						openPage(m);
						Ui.sound("click");
					} else if (b == 2) {
						// middle click: straight to listening for its key
						openPage(m);
						listening = m.key;
					}
				});
				hit(tx - 6, ty - 8, 52, 38, b -> {
					if (b == 0 && !m.blocked()) {
						m.toggle();
						Ui.sound(m.on() ? "toggle_on" : "toggle_off");
					}
				});
			}
		}
	}

	private void openPage(Module m) {
		if (page != m) {
			prevPage = page;
			anim.put("pageT", 0f);
		}
		page = m;
		pageScroll = pageGoal = 0;
		closePopups();
	}

	private void back() {
		prevPage = page;
		page = null;
		closePopups();
	}

	private void closePopups() {
		dropdown = null;
		colorEdit = null;
		listening = null;
	}

	// ---------- settings column ----------

	private void settingsColumn(GuiGraphicsExtractor g, boolean live) {
		// wide shows nothing once closed, narrow keeps the old page while it slides away
		Module shown = page != null ? page : wide ? null : prevPage;
		if (shown == null) {
			Ui.logo(g, px + pw / 2 - 22, sy + sh / 2 - 44, 44, 0x30DCE6F5);
			Ui.label(g, "Pick a module to see its settings", false, 14.5f, px + pw / 2, sy + sh / 2 + 22, FAINT, 'c');
			return;
		}
		// switching modules: the new page comes up a few px while it fades in
		float t = smooth(ease("pageT", 1, 12));
		float keep = Ui.alpha;
		Ui.alpha = keep * t;
		g.pose().pushMatrix();
		g.pose().translate(0, 10 * (1 - t));
		page(g, shown, live);
		g.pose().popMatrix();
		Ui.alpha = keep;
	}

	private void page(GuiGraphicsExtractor g, Module m, boolean live) {
		float x = px + 26, w = pw - 52;
		float hx = x;
		if (!wide) {
			float bx = px + 18, by = sy + 26;
			float hv = hover("back", live && in(bx, by, 30, 30));
			if (hv > 0.01f) rect(g, bx, by, 30, 30, 8, ARGB.multiplyAlpha(S_HOVER, hv));
			Ui.glyph(g, I_BACK, bx + 15, by + 15, 14, mix(MUTED, PRIMARY, hv));
			click(bx, by, 30, 30, live, this::back);
			hx = bx + 40;
		}
		boolean own = m == Modules.SETTINGS;
		rect(g, hx, sy + 22, 40, 40, 11, m.on() || own ? tint(0x2E) : SOFT);
		Ui.glyph(g, own ? I_GEAR : m.icon, hx + 20, sy + 42, 19, m.on() || own ? Ui.accent() : SECONDARY);
		Ui.label(g, own ? "Shrike Settings" : m.name, true, 23, hx + 54, sy + 43, PRIMARY, 'l');
		Ui.label(g, fit(m.desc, false, 13.5f, px + pw - 26 - hx - 54), false, 13.5f, hx + 54, sy + 62, MUTED, 'l');

		float y = sy + 80;
		if (!own) {
			// enabled, the key and hold / toggle, then the star
			float hv = hover("pen", live && in(x, y, 138, 34));
			rect(g, x, y, 138, 34, 9, mix(SOFT, S_HOVER, hv));
			Ui.label(g, m.on() ? "Enabled" : "Disabled", true, 14, x + 12, y + 22, m.on() ? PRIMARY : SECONDARY, 'l');
			toggle(g, "pens", x + 138 - 46, y + 6, m.on(), hv);
			click(x, y, 138, 34, live, () -> {
				m.toggle();
				Ui.sound(m.on() ? "toggle_on" : "toggle_off");
			});

			boolean wait = listening == m.key;
			String k = wait ? "Press a key" : m.key.bound() ? m.key.keyName() : "No key";
			float kx = x + 146, kw = Math.max(96, Ui.tw(k, true, 14) + 44);
			float kh = hover("pkey", live && in(kx, y, kw, 34));
			rect(g, kx, y, kw, 34, 9, wait ? tint(0x33) : mix(SOFT, S_HOVER, kh));
			if (wait) line(g, kx, y, kw, 34, 9, tint(0xAA), 1);
			Ui.glyph(g, I_KEY, kx + 16, y + 17, 12, wait ? Ui.accent() : FAINT);
			Ui.label(g, k, true, 14, kx + 30, y + 22, wait || m.key.bound() ? PRIMARY : MUTED, 'l');
			if (live) hit(kx, y, kw, 34, b -> {
				if (b == 0) {
					listening = wait ? null : m.key;
					Ui.sound("click");
				} else if (b == 1) {
					m.key.set(-1);
				}
			});

			// two segment hold / toggle, only means something with a key
			float gx = kx + kw + 8;
			rect(g, gx, y, 136, 34, 9, 0x14000000);
			float hs = smooth(ease("phold", m.hold ? 1 : 0, 14));
			rect(g, gx + 3 + 65 * hs, y + 3, 65, 28, 7, S_ACTIVE);
			for (int i = 0; i < 2; i++) {
				boolean hold = i == 1;
				float bx = gx + 3 + 65 * i;
				Ui.label(g, hold ? "Hold" : "Toggle", true, 13.5f, bx + 32.5f, y + 21.5f, m.hold == hold ? PRIMARY : FAINT, 'c');
				click(bx, y, 65, 34, live, () -> {
					m.hold = hold;
					Config.dirty();
				});
			}

			float fx = x + w - 34;
			float fh = hover("pfav", live && in(fx, y, 34, 34));
			rect(g, fx, y, 34, 34, 9, mix(SOFT, S_HOVER, fh));
			Ui.glyph(g, I_STAR, fx + 17, y + 17, 14, m.fav() ? Ui.accent() : mix(FAINT, SECONDARY, fh));
			click(fx, y, 34, 34, live, () -> m.setFav(!m.fav()));
			y += 50;
		}

		float ly = y, lh = sy + sh - 14 - ly;
		boolean widget = m instanceof HudWidget;
		List<Setting<?>> ss = m.settings().stream().filter(Setting::visible).toList();
		float total = 8 + SROW * (ss.size() + (widget ? 1 : 0));
		if (live) maxPage = Math.max(0, total - lh);
		if (ss.isEmpty() && !widget) {
			Ui.label(g, "Nothing to set up", true, 15, px + pw / 2, ly + lh / 2 - 6, SECONDARY, 'c');
			Ui.label(g, "It just works once it's on", false, 13.5f, px + pw / 2, ly + lh / 2 + 16, FAINT, 'c');
			return;
		}
		scissor(g, px + 1, ly, pw - 2, lh);
		float ry = ly + 4 - pageScroll;
		boolean rowsLive = live && in(px, ly, pw, lh) && dropdown == null && colorEdit == null;
		for (int i = 0; i < ss.size(); i++) {
			Setting<?> s = ss.get(i);
			float h = SROW;
			if (ry + h > ly - 2 && ry < ly + lh + 2) {
				row(g, s, x - 10, ry, w + 20, h, rowsLive);
				if (i < ss.size() - 1 || widget) rect(g, x, ry + h - 0.5f, w, 1, 0, 0x0FDCE6F5);
			}
			ry += h;
		}
		if (widget && ry < ly + lh + 2) {
			float hv = hover("edithud", rowsLive && in(x - 10, ry, w + 20, SROW));
			if (hv > 0.01f) rect(g, x - 10, ry, w + 20, SROW, 10, ARGB.multiplyAlpha(SURFACE, hv));
			Ui.label(g, "Position and size", true, 15, x, ry + 28, mix(SECONDARY, PRIMARY, hv), 'l');
			button(g, "edithudb", x + w - 92, ry + 9, 92, 28, "Edit HUD", rowsLive, () -> minecraft.gui.setScreen(new HudEditorScreen(this)));
		}
		g.disableScissor();
	}

	// one row: the name on the left, its control on the right. rows aren't boxed, just a line between them
	// a setting the server pins is drawn dimmed and with `live` off, which is what every control in here already
	// checks before registering a click, so none of them need to know about blocking
	private void row(GuiGraphicsExtractor g, Setting<?> s, float x, float y, float w, float h, boolean live) {
		if (!s.blocked()) {
			rowBody(g, s, x, y, w, h, live);
			return;
		}
		fade(g, 0.4f, () -> rowBody(g, s, x, y, w, h, false));
	}

	private void rowBody(GuiGraphicsExtractor g, Setting<?> s, float x, float y, float w, float h, boolean live) {
		boolean over = live && in(x, y, w, h);
		float hv = hover(s, over || dropdown == s || colorEdit == s || listening == s);
		if (hv > 0.01f) rect(g, x, y + 2, w, h - 4, 10, ARGB.multiplyAlpha(SURFACE, hv));
		float right = x + w - 10, cy = y + h / 2;
		float cw = Math.min(220, w * 0.46f);
		Ui.label(g, fit(s.name, true, 15, w - cw - 60), true, 15, x + 10, cy + 5, mix(SECONDARY, PRIMARY, hv), 'l');
		float left;

		if (s instanceof Setting.Toggle t) {
			toggle(g, s.name + "#t", right - 40, cy - 11, t.on(), hv);
			left = right - 40;
			if (live) hit(x, y, w, h, b -> {
				if (b == 0) {
					t.flip();
					Ui.sound(t.on() ? "toggle_on" : "toggle_off");
				}
			});
		} else if (s instanceof Setting.Slider sl) {
			// the value sits inside the bar, the fill is how far along it is
			boolean dragging = dragSlider == sl;
			float bx = right - cw, by = cy - 11;
			float p = ease(s.name + "#p", (float) sl.pct(), 30);
			float bh = hover(s.name + "#h", dragging || live && in(bx, by, cw, 22));
			rect(g, bx, by, cw, 22, 7, mix(0x14000000, 0x22000000, bh));
			line(g, bx, by, cw, 22, 7, mix(OUTLINE, O_HOVER, bh), 1);
			if (p > 0.001f) rect(g, bx, by, Math.max(10, cw * p), 22, 7, tint(dragging ? 0xB0 : 0x80));
			Ui.label(g, sl.shown(), true, 13, bx + cw / 2, cy + 4.5f, PRIMARY, 'c');
			left = bx;
			if (live) hit(bx - 4, by - 8, cw + 8, 38, b -> {
				if (b == 0) {
					dragSlider = sl;
					dragTrackX = bx;
					dragTrackW = cw;
					slide();
				}
			});
		} else if (s instanceof Setting.Choice || s instanceof Setting.Multi) {
			boolean open = dropdown == s;
			float fx = right - cw, fy = cy - 15;
			float fh = hover(s.name + "#f", open || live && in(fx, fy, cw, 30));
			rect(g, fx, fy, cw, 30, 9, mix(SOFT, S_HOVER, fh));
			line(g, fx, fy, cw, 30, 9, open ? tint(0x99) : mix(OUTLINE, O_HOVER, fh), 1);
			String v;
			if (s instanceof Setting.Choice ch) {
				v = ch.get();
			} else {
				Setting.Multi m = (Setting.Multi) s;
				List<String> on = new ArrayList<>();
				for (String o : m.options) if (m.has(o)) on.add(o);
				v = on.isEmpty() ? "" : on.size() == 1 ? on.get(0) : on.get(0) + " +" + (on.size() - 1);
			}
			Ui.label(g, v.isEmpty() ? "None" : fit(v, true, 13.5f, cw - 40), true, 13.5f, fx + 12, cy + 4.5f, v.isEmpty() ? FAINT : PRIMARY, 'l');
			// flips over while open
			float op = smooth(ease(s.name + "#o", open ? 1 : 0, 14));
			g.pose().pushMatrix();
			g.pose().translate(fx + cw - 16, cy);
			g.pose().rotate((float) Math.PI * op);
			Ui.glyph(g, I_DOWN, 0, 0, 11, MUTED);
			g.pose().popMatrix();
			left = fx;
			if (live) hit(fx, fy, cw, 30, b -> {
				if (b != 0) return;
				dropdown = open ? null : s;
				ddx = fx;
				ddy = fy + 34;
				ddw = cw;
				ddScroll = 0;
				anim.put("dd", 0f);
				Ui.sound("click");
			});
		} else if (s instanceof Setting.Color col) {
			float bx = right - 36, by = cy - 11;
			if (ARGB.alpha(col.get()) < 255) checker(g, bx, by, 36, 22);
			Shape.rect(g, bx, by, 36, 22, 7, Ui.c(col.get()));
			line(g, bx, by, 36, 22, 7, 0x30FFFFFF, 1);
			String hex = String.format(Locale.ROOT, col.alpha ? "#%08X" : "#%06X", col.alpha ? col.get() : col.get() & 0xFFFFFF);
			Ui.label(g, hex, false, 12.5f, bx - 10, cy + 4.5f, FAINT, 'r');
			left = bx - 10 - Ui.tw(hex, false, 12.5f);
			if (live) hit(x, y, w, h, b -> {
				if (b != 0) return;
				colorEdit = colorEdit == col ? null : col;
				cpx = bx + 36;
				cpy = by + 28;
				anim.put("cp", 0f);
				Ui.sound("click");
			});
		} else if (s instanceof Setting.Bind bd) {
			boolean wait = listening == bd;
			String k = wait ? "Press a key" : bd.bound() ? bd.keyName() : "None";
			float kw = Math.max(64, Ui.tw(k, true, 13.5f) + 26), kx = right - kw, ky = cy - 14;
			rect(g, kx, ky, kw, 28, 8, wait ? tint(0x33) : mix(SOFT, S_HOVER, hv));
			// keycap: a slightly darker bottom lip
			rect(g, kx + 2, ky + 26, kw - 4, 2, 1, wait ? tint(0x66) : 0x22000000);
			line(g, kx, ky, kw, 28, 8, wait ? tint(0xAA) : OUTLINE, 1);
			Ui.label(g, k, true, 13.5f, kx + kw / 2, cy + 4, wait || bd.bound() ? PRIMARY : FAINT, 'c');
			left = kx;
			if (live) hit(x, y, w, h, b -> {
				if (b == 0) {
					listening = wait ? null : bd;
					Ui.sound("click");
				}
			});
		} else {
			// words, items, the crosshair grid and plain buttons: a button on the right
			String label = s instanceof Setting.Action a ? a.get() : "Edit";
			float bw = Ui.tw(label, true, 13.5f) + 28, bx = right - bw;
			left = bx;
			if (s instanceof Setting.Items it) {
				List<Item> l = it.items();
				int n = Math.min(6, l.size());
				for (int i = 0; i < n; i++) {
					g.pose().pushMatrix();
					g.pose().translate(bx - 12 - (n - i) * 18, cy - 8);
					g.item(new ItemStack(l.get(i)), 0, 0);
					g.pose().popMatrix();
				}
				left = bx - 12 - n * 18;
			} else if (s instanceof Setting.Words wd) {
				String lb = wordsLabel(wd);
				Ui.label(g, lb, false, 13, bx - 10, cy + 4.5f, FAINT, 'r');
				left = bx - 10 - Ui.tw(lb, false, 13);
			}
			button(g, s.name + "#b", bx, cy - 14, bw, 28, label, live, () -> {
				if (s instanceof Setting.Action a) a.run.run();
				else if (s instanceof Setting.Words wd) minecraft.gui.setScreen(new WordsScreen(this, wd));
				else if (s instanceof Setting.Items it) minecraft.gui.setScreen(new ItemPickerScreen(this, it));
				else if (s instanceof Setting.Grid gr) minecraft.gui.setScreen(new CrosshairEditorScreen(this, gr));
			});
		}
		reset(g, s, left, cy, live);
	}

	// the little undo arrow next to anything that's not at its default
	private void reset(GuiGraphicsExtractor g, Setting<?> s, float leftOf, float cy, boolean live) {
		if (!s.changed() || s instanceof Setting.Action) return;
		float x = leftOf - 28, y = cy - 11;
		boolean over = live && in(x, y, 22, 22);
		Ui.glyph(g, I_RESET, x + 11, y + 11, 12, over ? PRIMARY : FAINT);
		if (live) hit(x, y, 22, 22, b -> {
			if (b == 0) {
				s.reset();
				Ui.sound("click");
			}
		});
	}

	private static String wordsLabel(Setting.Words w) {
		List<String> v = w.get();
		if (v.isEmpty()) return "None";
		String first = String.join(", ", v.subList(0, Math.min(2, v.size())));
		if (first.length() > 22) first = first.substring(0, 21) + "...";
		return v.size() > 2 ? first + " +" + (v.size() - 2) : first;
	}

	private void slide() {
		float f = Mth.clamp((mx - dragTrackX) / dragTrackW, 0, 1);
		dragSlider.set(dragSlider.min + f * (dragSlider.max - dragSlider.min));
	}

	// ---------- popups: dropdown, color ----------

	private void popups(GuiGraphicsExtractor g) {
		if (dropdown != null) dropdownPopup(g);
		if (colorEdit != null) colorPopup(g);
	}

	private static List<String> options(Setting<?> s) {
		return s instanceof Setting.Choice c ? c.options : ((Setting.Multi) s).options;
	}

	private void dropdownPopup(GuiGraphicsExtractor g) {
		Setting<?> s = dropdown;
		List<String> opts = options(s);
		float ih = 30, h = ih * Math.min(7, opts.size()) + 8, x = ddx, w = ddw;
		// opens upward when there's no room under the field
		float y = ddy + h > sy + sh - 8 ? ddy - 38 - h : ddy;
		float op = smooth(ease("dd", 1, 16));
		hit(0, 0, vw, vh, b -> dropdown = null);
		float keep = Ui.alpha;
		Ui.alpha = keep * op;
		g.pose().pushMatrix();
		g.pose().translate(0, -6 * (1 - op));
		Shape.shadow(g, x, y + 4, w, h, 10, 10, 0, Ui.c(0x50000000));
		rect(g, x, y, w, h, 10, POPUP);
		line(g, x, y, w, h, 10, POPUP_LINE, 1);
		scissor(g, x, y + 4, w, h - 8);
		for (int i = 0; i < opts.size(); i++) {
			String o = opts.get(i);
			float iy = y + 4 + i * ih - ddScroll;
			if (iy + ih <= y || iy >= y + h) continue;
			boolean over = in(x + 4, iy, w - 8, ih) && in(x, y + 4, w, h - 8);
			float hv = hover("ddi" + s.name + o, over);
			if (hv > 0.01f) rect(g, x + 4, iy, w - 8, ih, 7, ARGB.multiplyAlpha(S_ACTIVE, hv));
			boolean sel = s instanceof Setting.Choice c ? c.get().equals(o) : ((Setting.Multi) s).has(o);
			Ui.label(g, fit(o, true, 13.5f, w - 50), true, 13.5f, x + 14, iy + ih / 2 + 4.5f, sel ? PRIMARY : mix(MUTED, PRIMARY, hv), 'l');
			if (sel) Ui.glyph(g, I_CHECK, x + w - 20, iy + ih / 2, 12, Ui.accent());
			float top = Math.max(iy, y + 4);
			hit(x, top, w, Math.min(iy + ih, y + h - 4) - top, b -> {
				if (b != 0) {
					dropdown = null;
					return;
				}
				Ui.sound("click");
				if (s instanceof Setting.Choice c) {
					c.set(o);
					dropdown = null;
				} else {
					// a multi select stays open, you usually pick a few
					((Setting.Multi) s).toggle(o);
				}
			});
		}
		g.disableScissor();
		g.pose().popMatrix();
		Ui.alpha = keep;
	}

	private static float[] hsv(int c) {
		float r = ARGB.red(c) / 255f, gg = ARGB.green(c) / 255f, b = ARGB.blue(c) / 255f;
		float max = Math.max(r, Math.max(gg, b)), min = Math.min(r, Math.min(gg, b)), d = max - min, h = 0;
		if (d > 0) {
			if (max == r) h = ((gg - b) / d) % 6;
			else if (max == gg) h = (b - r) / d + 2;
			else h = (r - gg) / d + 4;
			h /= 6;
			if (h < 0) h += 1;
		}
		return new float[] {h, max == 0 ? 0 : d / max, max};
	}

	private static final float PICK_W = 216, PICK_H = 136;

	// saturation / brightness field on top, the hue strip under it, alpha under that for see through colors.
	// sits under the swatch, right aligned to it
	private void colorPopup(GuiGraphicsExtractor g) {
		Setting.Color col = colorEdit;
		float w = PICK_W + 24, h = PICK_H + 24 + 22 + (col.alpha ? 22 : 0) + 26;
		float x = Mth.clamp(cpx - w, sx + 8, sx + sw - 8 - w), y = cpy + h > sy + sh - 8 ? cpy - 34 - h : cpy;
		float keep = Ui.alpha;
		Ui.alpha = keep * smooth(ease("cp", 1, 14));
		hit(0, 0, vw, vh, b -> colorEdit = null);
		hit(x, y, w, h, b -> {
			if (b == 1) colorEdit = null;
		});
		Shape.shadow(g, x, y + 4, w, h, 14, 12, 0, Ui.c(0x50000000));
		rect(g, x, y, w, h, 14, POPUP);
		line(g, x, y, w, h, 14, POPUP_LINE, 1);
		int c = col.get();
		float[] hs = hsv(c);
		int pure = 0xFF000000 | Mth.hsvToRgb(hs[0], 1, 1);
		float qx = x + 12, qy = y + 12;
		Shape.gradient(g, qx, qy, PICK_W, PICK_H, 8, Ui.c(0xFFFFFFFF), Ui.c(pure), Ui.c(0xFF000000), Ui.c(0xFF000000));
		hit(qx, qy, PICK_W, PICK_H, b -> pick(b, 1));

		float hy = qy + PICK_H + 12;
		for (int i = 0; i < 6; i++) {
			int a = 0xFF000000 | Mth.hsvToRgb(i / 6f, 1, 1), b = 0xFF000000 | Mth.hsvToRgb((i + 1) % 6 / 6f, 1, 1);
			// the ends get the strip's rounding, the middle pieces are square
			Shape.gradient(g, qx + i * PICK_W / 6, hy, PICK_W / 6 + 0.5f, 10, 0, Ui.c(a), Ui.c(b), Ui.c(b), Ui.c(a));
		}
		knob(g, qx + hs[0] * PICK_W, hy + 5);
		hit(qx, hy - 4, PICK_W, 18, b -> pick(b, 2));
		float ny = hy + 22;
		if (col.alpha) {
			checker(g, qx, ny, PICK_W, 10);
			int full = 0xFF000000 | c & 0xFFFFFF, none = c & 0xFFFFFF;
			Shape.gradient(g, qx, ny, PICK_W, 10, 0, Ui.c(none), Ui.c(full), Ui.c(full), Ui.c(none));
			knob(g, qx + ARGB.alpha(c) / 255f * PICK_W, ny + 5);
			hit(qx, ny - 4, PICK_W, 18, b -> pick(b, 3));
			ny += 22;
		}
		rect(g, qx, ny, 20, 16, 5, c | 0xFF000000);
		String hex = String.format(Locale.ROOT, col.alpha ? "#%08X" : "#%06X", col.alpha ? c : c & 0xFFFFFF);
		Ui.label(g, hex, true, 13.5f, qx + 28, ny + 12.5f, SECONDARY, 'l');
		float cx = qx + hs[1] * PICK_W, cy = qy + (1 - hs[2]) * PICK_H;
		Shape.shadow(g, cx - 6, cy - 6, 12, 12, 6, 3, 0, Ui.c(0x6D000000));
		line(g, cx - 6, cy - 6, 12, 12, 6, 0xFFFFFFFF, 2);
		cqx = qx;
		cqy = qy;
		if (colorDrag != 0) dragColor();
		Ui.alpha = keep;
	}

	private void knob(GuiGraphicsExtractor g, float cx, float cy) {
		Shape.shadow(g, cx - 3, cy - 8, 6, 16, 3, 2, 0, Ui.c(0x60000000));
		rect(g, cx - 3, cy - 8, 6, 16, 3, 0xFFFFFFFF);
	}

	// a plain click picks right away, holding keeps picking every frame until release
	private void pick(int button, int what) {
		if (button != 0) return;
		colorDrag = what;
		dragColor();
	}

	private void dragColor() {
		float qx = cqx, qy = cqy;
		Setting.Color col = colorEdit;
		int c = col.get(), a = col.alpha ? c & 0xFF000000 : 0xFF000000;
		float[] hs = hsv(c);
		float fx = Mth.clamp((mx - qx) / PICK_W, 0, 1);
		switch (colorDrag) {
			case 1 -> col.set(a | Mth.hsvToRgb(hs[0], fx, 1 - Mth.clamp((my - qy) / PICK_H, 0, 1)) & 0xFFFFFF);
			// the hue lives in the color itself, so a grey can't remember one. keep a little saturation so it does
			case 2 -> col.set(a | Mth.hsvToRgb(Math.min(fx, 0.9999f), Math.max(hs[1], 0.01f), Math.max(hs[2], 0.01f)) & 0xFFFFFF);
			case 3 -> col.set(Math.round(fx * 255) << 24 | c & 0xFFFFFF);
			default -> {}
		}
	}

	// ---------- waypoints ----------

	// takes the list and settings columns. list on the left, the editor on the right
	private static String blockedBy() {
		String s = Blacklist.server();
		return "Blacklisted on " + (s == null ? "this server" : s);
	}

	private static boolean anyBlocked(Module m) {
		for (Setting<?> s : m.settings()) if (s.blocked()) return true;
		return false;
	}

	// ---------- profiles ----------

	private void profiles(GuiGraphicsExtractor g, boolean live) {
		float wx = lx + 28, ww = sx + sw - 28 - wx;
		Ui.label(g, "Profiles", true, 25, wx, sy + 48, PRIMARY, 'l');
		Ui.label(g, proList.size() == 1 ? "Only Default so far" : proList.size() + " saved", false, 13.5f,
			wx + Ui.tw("Profiles", true, 25) + 12, sy + 47, FAINT, 'l');
		float top = sy + 70, lw2 = (ww - 24) * 0.58f, ex = wx + lw2 + 24, ew = ww - lw2 - 24, bh = sy + sh - 18 - top;

		if (live) maxPro = Math.max(0, proList.size() * 58 - bh);
		scissor(g, wx - 4, top, lw2 + 8, bh);
		boolean inList = in(wx, top, lw2, bh);
		for (int i = 0; i < proList.size(); i++) {
			String n = proList.get(i);
			float y = top + i * 58 - proScroll;
			if (y + 54 < top || y > top + bh) continue;
			boolean cur = n.equals(Profiles.active());
			boolean over = live && inList && in(wx, y, lw2, 54);
			float hv = hover("pro" + n, over);
			if (hv > 0.01f || cur) rect(g, wx, y, lw2, 54, 11, mix(ARGB.multiplyAlpha(S_HOVER, hv), S_ACTIVE, cur ? 1 : 0));
			if (cur) rect(g, wx + 14, y + 21, 12, 12, 6, Ui.accent());
			float tx = cur ? wx + 38 : wx + 20;
			Ui.label(g, n, true, 15.5f, tx, y + 25, mix(SECONDARY, PRIMARY, Math.max(hv, cur ? 1 : 0)), 'l');
			Ui.label(g, cur ? "In use" : n.equals(Profiles.DEFAULT) ? "Always here" : "Saved settings", false, 12.5f, tx, y + 42, MUTED, 'l');
			if (over) {
				float bx = wx + lw2 - 12 - 54;
				if (!n.equals(Profiles.DEFAULT)) {
					button(g, "pdel" + i, bx, y + 13, 54, 28, "Del", true, () -> {
						Profiles.delete(n);
						proList = Profiles.all();
						if (n.equals(proRenaming)) stopRename();
						status("Deleted " + n);
					});
					bx -= 60;
					button(g, "pren" + i, bx, y + 13, 54, 28, "Ren", true, () -> {
						proRenaming = n;
						proName.text = n;
						focus = proName;
					});
					bx -= 60;
				}
				button(g, "pdup" + i, bx, y + 13, 54, 28, "Copy", true, () -> {
					String made = free(n + " copy");
					Profiles.duplicate(n, made);
					proList = Profiles.all();
					status("Copied to " + made);
				});
				if (!cur) {
					bx -= 60;
					button(g, "puse" + i, bx, y + 13, 54, 28, "Use", true, () -> {
						Profiles.switchTo(n);
						proList = Profiles.all();
						status("Switched to " + n);
					});
				}
			}
		}
		g.disableScissor();

		rect(g, ex, top, ew, bh, 14, 0x12000000);
		line(g, ex, top, ew, bh, 14, OUTLINE, 1);
		float fx = ex + 18, fw = ew - 36, y = top + 32;
		boolean ren = proRenaming != null;
		Ui.label(g, ren ? "Rename " + fit(proRenaming, true, 16, fw - 70) : "New profile", true, 16, fx, y, PRIMARY, 'l');
		y += 20;
		Ui.label(g, "Name", true, 12.5f, fx, y + 10, MUTED, 'l');
		field(g, proName, fx, y + 16, fw, 34, ren ? proRenaming : "Profile name", live);
		y += 62;
		Ui.label(g, ren ? "The file is renamed with it." : "Starts as a copy of the settings you have loaded right now.",
			false, 12.5f, fx, y, FAINT, 'l');
		y += 26;
		if (ren) {
			button(g, "psave", fx, y, (fw - 8) / 2, 34, "Save", live, this::applyRename);
			button(g, "pcancel", fx + (fw - 8) / 2 + 8, y, (fw - 8) / 2, 34, "Cancel", live, this::stopRename);
		} else {
			button(g, "pmake", fx, y, fw, 34, "Create", live, () -> {
				String n = free(proName.text.isBlank() ? "Profile" : proName.text.trim());
				Profiles.create(n);
				proList = Profiles.all();
				proName.text = "";
				status("Created " + n);
			});
		}
	}

	private void applyRename() {
		String to = proName.text.trim();
		if (proRenaming == null || to.isBlank()) return;
		Profiles.rename(proRenaming, free(to));
		proList = Profiles.all();
		status("Renamed to " + to);
		stopRename();
	}

	private void stopRename() {
		proRenaming = null;
		proName.text = "";
		if (focus == proName) focus = null;
	}

	// "Default copy", then "Default copy 2" and up, so copying twice doesn't quietly do nothing
	private String free(String want) {
		if (!Profiles.exists(want)) return want;
		for (int i = 2; i < 100; i++) if (!Profiles.exists(want + " " + i)) return want + " " + i;
		return want;
	}

	private void waypoints(GuiGraphicsExtractor g, boolean live) {
		float wx = lx + 28, ww = sx + sw - 28 - wx;
		Ui.label(g, "Waypoints", true, 25, wx, sy + 48, PRIMARY, 'l');
		boolean inWorld = minecraft.level != null;
		Ui.label(g, inWorld ? Nav.Waypoints.inWorld().size() + " in this world" : "Saved per world and server", false, 13.5f, wx + Ui.tw("Waypoints", true, 25) + 12, sy + 47, FAINT, 'l');
		float top = sy + 70, lw2 = (ww - 24) * 0.58f, ex = wx + lw2 + 24, ew = ww - lw2 - 24, bh = sy + sh - 18 - top;

		field(g, wpSearch, wx, top, lw2, 36, "Search name, coordinates or dimension", live);
		float ry0 = top + 46, rh = bh - 46;
		String q = wpSearch.text.trim().toLowerCase(Locale.ROOT);
		List<Nav.Waypoint> all = new ArrayList<>();
		for (Nav.Waypoint p : Nav.Waypoints.inWorld()) {
			String hay = (p.name() + " " + p.x() + " " + p.y() + " " + p.z() + " " + dimName(p.dim())).toLowerCase(Locale.ROOT);
			if (q.isEmpty() || hay.contains(q)) all.add(p);
		}
		if (live) maxWp = Math.max(0, all.size() * 58 - rh);
		scissor(g, wx - 4, ry0, lw2 + 8, rh);
		if (all.isEmpty()) {
			Ui.label(g, inWorld ? "No waypoints yet" : "Join a world to see its waypoints", true, 15, wx + lw2 / 2, ry0 + rh / 2 - 6, SECONDARY, 'c');
			Ui.label(g, inWorld ? "Make one on the right, or press your Add Waypoint key" : "They're saved per world and server", false, 13.5f, wx + lw2 / 2, ry0 + rh / 2 + 16, FAINT, 'c');
		}
		boolean inList = in(wx, ry0, lw2, rh);
		for (int i = 0; i < all.size(); i++) {
			Nav.Waypoint p = all.get(i);
			float y = ry0 + i * 58 - wpScroll;
			if (y + 54 < ry0 || y > ry0 + rh) continue;
			boolean over = live && inList && in(wx, y, lw2, 54);
			float hv = hover(p, over), sel = p == wpEditing ? 1 : 0;
			if (hv > 0.01f || sel > 0) rect(g, wx, y, lw2, 54, 11, mix(ARGB.multiplyAlpha(S_HOVER, hv), S_ACTIVE, sel));
			rect(g, wx + 14, y + 21, 12, 12, 6, p.color());
			Ui.label(g, p.name(), true, 15.5f, wx + 38, y + 25, mix(SECONDARY, PRIMARY, Math.max(hv, sel)), 'l');
			Ui.label(g, p.x() + ", " + p.y() + ", " + p.z() + "   " + dimName(p.dim()) + (p.until() != 0 ? ", temporary" : ""), false, 12.5f, wx + 38, y + 42, MUTED, 'l');
			if (over) {
				button(g, "wde" + i, wx + lw2 - 12 - 50, y + 13, 50, 28, "Del", true, () -> {
					Nav.Waypoints.remove(p);
					if (wpEditing == p) clearEditor();
					status("Deleted " + p.name());
				});
				button(g, "wed" + i, wx + lw2 - 12 - 50 - 6 - 50, y + 13, 50, 28, "Edit", true, () -> edit(p));
			}
		}
		g.disableScissor();

		rect(g, ex, top, ew, bh, 14, 0x12000000);
		line(g, ex, top, ew, bh, 14, OUTLINE, 1);
		float fx = ex + 18, fw = ew - 36, y = top + 32;
		Ui.label(g, wpEditing == null ? "New waypoint" : "Editing " + fit(wpEditing.name(), true, 16, fw - 70), true, 16, fx, y, PRIMARY, 'l');
		y += 20;
		Ui.label(g, "Name", true, 12.5f, fx, y + 10, MUTED, 'l');
		field(g, wpName, fx, y + 16, fw, 34, "Waypoint name", live);
		y += 62;
		float cw = (fw - 16) / 3;
		Field[] xyz = {wpX, wpY, wpZ};
		String[] ln = {"X", "Y", "Z"};
		for (int i = 0; i < 3; i++) {
			Ui.label(g, ln[i], true, 12.5f, fx + i * (cw + 8), y + 10, MUTED, 'l');
			field(g, xyz[i], fx + i * (cw + 8), y + 16, cw, 34, "0", live);
		}
		y += 62;
		Ui.label(g, "Dimension", true, 12.5f, fx, y + 10, MUTED, 'l');
		String[] dims = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
		rect(g, fx, y + 16, fw, 34, 9, 0x14000000);
		float dw = (fw - 6) / 3;
		int di = 0;
		for (int i = 0; i < 3; i++) if (wpDim.equals(dims[i])) di = i;
		float ds = ease("wpdim", di, 14);
		rect(g, fx + 3 + ds * dw, y + 19, dw, 28, 7, S_ACTIVE);
		for (int i = 0; i < 3; i++) {
			String d = dims[i];
			float bx = fx + 3 + i * dw;
			Ui.label(g, dimName(d), true, 13.5f, bx + dw / 2, y + 37.5f, wpDim.equals(d) ? PRIMARY : FAINT, 'c');
			click(bx, y + 16, dw, 34, live, () -> wpDim = d);
		}
		y += 66;
		button(g, "here", fx, y, fw, 32, "Use My Position", live && inWorld, this::fillHere);
		y += 40;
		float hw = (fw - 8) / 2;
		// the one accent button on the page, it's the thing you came here to press
		float sh2 = hover("wsave", live && inWorld && in(fx, y, hw, 32));
		rect(g, fx, y, hw, 32, 8, tint(inWorld ? 0xCC + Math.round(0x33 * sh2) : 0x44));
		Ui.label(g, wpEditing == null ? "Create" : "Save", true, 13.5f, fx + hw / 2, y + 20.5f, 0xFFFFFFFF, 'c');
		click(fx, y, hw, 32, live && inWorld, this::saveWaypoint);
		button(g, "clear", fx + hw + 8, y, hw, 32, wpEditing == null ? "Clear" : "New", live, this::clearEditor);
		if (!wpStatus.isEmpty() && Util.getMillis() - wpStatusAt < 2500) Ui.label(g, wpStatus, false, 13, fx + fw / 2, y + 56, MUTED, 'c');
	}

	private static String dimName(String d) {
		return switch (d) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> d.replace("minecraft:", "");
		};
	}

	private void field(GuiGraphicsExtractor g, Field f, float x, float y, float w, float h, String hint, boolean live) {
		boolean focused = focus == f;
		float hp = focused ? 1 : hover(f, live && in(x, y, w, h)) * 0.6f;
		rect(g, x, y, w, h, 9, mix(0x14000000, SOFT, hp));
		line(g, x, y, w, h, 9, focused ? tint(0x99) : mix(OUTLINE, O_HOVER, hp), 1);
		scissor(g, x + 10, y, w - 20, h);
		if (f.text.isEmpty() && !focused) Ui.label(g, hint, false, 13.5f, x + 12, y + h / 2 + 4.5f, FAINT, 'l');
		else Ui.label(g, f.text + (focused ? "_" : ""), true, 13.5f, x + 12, y + h / 2 + 4.5f, PRIMARY, 'l');
		g.disableScissor();
		if (live) hit(x, y, w, h, b -> {
			if (b == 0) focus = f;
		});
	}

	private void edit(Nav.Waypoint p) {
		wpEditing = p;
		wpName.text = p.name();
		wpX.text = Integer.toString(p.x());
		wpY.text = Integer.toString(p.y());
		wpZ.text = Integer.toString(p.z());
		wpDim = p.dim();
	}

	private void clearEditor() {
		wpEditing = null;
		wpName.text = wpX.text = wpY.text = wpZ.text = "";
	}

	private void fillHere() {
		if (minecraft.player == null) return;
		BlockPos b = minecraft.player.blockPosition();
		wpX.text = Integer.toString(b.getX());
		wpY.text = Integer.toString(b.getY());
		wpZ.text = Integer.toString(b.getZ());
		wpDim = minecraft.player.level().dimension().identifier().toString();
		if (wpName.text.isBlank()) wpName.text = "Waypoint " + (Nav.Waypoints.inWorld().size() + 1);
	}

	private void status(String s) {
		wpStatus = s;
		wpStatusAt = Util.getMillis();
	}

	private void saveWaypoint() {
		int x, y, z;
		try {
			x = Integer.parseInt(wpX.text.trim());
			y = Integer.parseInt(wpY.text.trim());
			z = Integer.parseInt(wpZ.text.trim());
		} catch (NumberFormatException e) {
			status("X, Y and Z have to be whole numbers");
			return;
		}
		String name = wpName.text.isBlank() ? "Waypoint " + (Nav.Waypoints.inWorld().size() + 1) : wpName.text.trim();
		int color = wpEditing != null ? wpEditing.color() : 0xFF5B8DEF;
		boolean was = wpEditing != null;
		if (was) Nav.Waypoints.remove(wpEditing);
		Nav.Waypoint p = new Nav.Waypoint(name, x, y, z, wpDim, color, 0);
		Nav.Waypoints.add(p);
		status((was ? "Saved " : "Created ") + name);
		wpEditing = p;
	}

	// ---------- input ----------

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (closing) return true;
		if (listening != null) {
			// clicking away cancels the bind and keeps whatever key it had
			listening = null;
			return true;
		}
		// a click drops the focus unless it lands on a field, which takes it again
		focus = null;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x && my >= h.y && mx < h.x + h.w && my < h.y + h.h) {
				h.c.click(e.button());
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragSlider = null;
		colorDrag = 0;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sxx, double syy) {
		if (dropdown != null) {
			int n = options(dropdown).size();
			ddScroll = Mth.clamp(ddScroll - (float) syy * 30, 0, Math.max(0, n - 7) * 30);
			return true;
		}
		if (colorEdit != null) return true;
		if (tab == 1) wpScrollGoal = Mth.clamp(wpScrollGoal - (float) syy * 58, 0, maxWp);
		if (tab == 2) proScrollGoal = Mth.clamp(proScrollGoal - (float) syy * 58, 0, maxPro);
		// wide: whichever column the mouse is over. narrow: the page when it's open
		else if (page != null && (!wide || mx >= px)) pageGoal = Mth.clamp(pageGoal - (float) syy * SROW * 1.5f, 0, maxPage);
		else scrollGoal = Mth.clamp(scrollGoal - (float) syy * (ROW + 4) * 1.5f, 0, maxScroll);
		return true;
	}

	private static boolean clears(KeyEvent e) {
		return e.isEscape() || e.key() == 259 || e.key() == 261;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (closing) return true;
		if (listening != null) {
			// esc, backspace or delete leave it unbound, anything else becomes the key
			listening.set(clears(e) ? -1 : e.key());
			listening = null;
			Ui.sound("click");
			return true;
		}
		if (e.isEscape()) {
			// one level at a time: a popup, the search, the page, then the menu
			if (dropdown != null || colorEdit != null) closePopups();
			else if (focus != null) focus = null;
			else if (page != null && tab == 0) back();
			else close();
			return true;
		}
		if (focus == null && KeyMappingHelper.getBoundKeyOf(Shrike.menuKey).getValue() == e.key()) {
			close();
			return true;
		}
		if (focus != null) {
			if (e.key() == 259) {
				type(s -> s.isEmpty() ? s : e.hasControlDown() ? "" : s.substring(0, s.length() - 1));
			} else if (e.key() == 257 || e.key() == 335) {
				focus = null;
			} else if (e.isPaste()) {
				String clip = minecraft.keyboardHandler.getClipboard().lines().findFirst().orElse("");
				type(s -> (s + clip).length() > 48 ? (s + clip).substring(0, 48) : s + clip);
			}
			return true;
		}
		// typing anywhere starts a search, no need to click the box first
		if (tab == 0 && e.key() >= 65 && e.key() <= 90 && !e.hasControlDown()) {
			focus = "search";
			return false;
		}
		if (page != null && e.key() == 259) {
			back();
			return true;
		}
		return super.keyPressed(e);
	}

	private void type(UnaryOperator<String> f) {
		if ("search".equals(focus)) {
			query = f.apply(query);
			scroll = scrollGoal = 0;
		} else if (focus instanceof Field fl) {
			fl.text = f.apply(fl.text);
		}
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (closing || focus == null || !e.isAllowedChatCharacter()) return false;
		type(s -> s.length() >= 48 ? s : s + e.codepointAsString());
		return true;
	}
}
