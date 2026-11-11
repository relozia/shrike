package io.github.relozia.kestrel.hud;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.gui.Shape;
import io.github.relozia.kestrel.gui.Ui;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.KestrelSettings;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.module.Modules;
import io.github.relozia.kestrel.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

// anything drawn on the hud and movable in the editor. most sit on Kestrel's box (see box()), a few draw their own
// sizes are in hud pixels: a widget lays out in those and render() scales them by unit(), so they come out the same
// physical size at any gui scale
// position is a fraction of the free space (screen size minus widget size) instead of pixels, so a widget pushed
// against the right edge stays on the edge after a resolution or gui scale change
// issues:
//   - a widget whose width changes a lot (clock going 9:59 > 10:00) shifts slightly if it sits mid screen
//   - no way to glue neighbouring widgets into one shared background, every widget draws its own box
public abstract class HudWidget extends Module {
	// set by the hud editor while it draws, widgets with nothing to show (no server, no effects) show
	// a sample instead so you can still grab and place them
	public static boolean editing;

	public float fx, fy;
	public float scale = 1;

	public final Setting.Toggle bg = new Setting.Toggle("Background", true);

	protected HudWidget(String name, char icon, String desc, float fx, float fy) {
		this(name, Category.HUD, icon, desc, fx, fy);
	}

	// the pvp coaches are hud boxes you can move, but they belong with the combat modules in the menu
	protected HudWidget(String name, Category cat, char icon, String desc, float fx, float fy) {
		super(name, cat, icon, desc);
		this.fx = fx;
		this.fy = fy;
	}

	@Override
	public List<Setting<?>> settings() {
		List<Setting<?>> all = new ArrayList<>(super.settings());
		if (look() != Look.NONE) all.add(bg);
		return all;
	}

	// size of the whole box (padding included), in layout pixels unless units() is off
	protected abstract int contentW();

	protected abstract int contentH();

	// draws at 0,0 over the background
	protected abstract void content(GuiGraphicsExtractor g);

	public boolean visible() {
		return true;
	}

	// boxes the editor and the hud treat on their own. the armor hud's held item, offhand and loose pieces are
	// parts of it that move separately but share its settings
	public List<HudWidget> boxes() {
		return List.of(this);
	}

	// ---------- looks ----------

	// BOX: Kestrel's box under the content. NONE: the widget draws its own (keystrokes) or none at all
	protected enum Look { BOX, NONE }

	protected Look look() {
		return Look.BOX;
	}

	protected float radius() {
		return 6;
	}

	// the thin accent line down the left side, for the few widgets you glance at mid fight
	protected boolean edge() {
		return false;
	}

	// vanilla's parts and the armor hud keep gui pixels, they look like vanilla's hud on purpose
	protected boolean units() {
		return true;
	}

	// layout pixels to gui pixels. widgets should keep the same physical size on screen whatever the gui scale is
	// set to, so this undoes the scale, then adds a small boost on low density screens or they come out tiny
	public static float unit() {
		Window w = Minecraft.getInstance().getWindow();
		float density = Math.min((float) w.getWidth() / Math.max(1, w.getScreenWidth()), (float) w.getHeight() / Math.max(1, w.getScreenHeight()));
		float boost = Mth.clamp((1.75f - density) / 0.5f, 0, 1);
		float auto = 1;
		if (boost > 0) {
			auto += 0.18f * boost;
			if (Math.min(w.getWidth(), w.getHeight()) <= 1080) auto += 0.04f * boost;
			auto = Math.min(auto, 1.22f);
		}
		return auto / w.getGuiScale();
	}

	private float u() {
		return units() ? unit() : 1;
	}

	public int w() {
		return Math.round(contentW() * u());
	}

	public int h() {
		return Math.round(contentH() * u());
	}

	// Kestrel Rust, offered next to white wherever a widget has a color
	protected static final int RUST = 0xFFE0703A;
	protected static final int WHITE = 0xF5F7F9FC, SUFFIX = 0xD6B9C2CE, LABEL = 0xEBE2E8F0, ICON_GREY = 0xFFC2C9D3, WARN = 0xFFEB5757;
	// all Inter: HUD semibold for values, LIGHT medium for units and quieter numbers
	protected static final Style HUD = font("hud"), LABEL_FONT = font("hud_label"), VALUE_FONT = font("hud_value"), LIGHT = font("hud_light"),
		ICONS = font("material");

	private static Style font(String id) {
		return Style.EMPTY.withFont(new FontDescription.Resource(Kestrel.id(id)));
	}

	// line height for each font, layouts hang off these ("Ag" measured)
	protected static float lineH(Style s) {
		return s == ICONS ? 21.6f : s == HUD || s == LIGHT ? 16.94f : 15.73f;
	}

	protected static int width(String s, Style st) {
		return font().width(Component.literal(s).withStyle(st));
	}

	// the size each font file is set to, anything else gets scaled from that
	private static float size(Style st) {
		return st == ICONS ? 18 : st == HUD || st == LIGHT ? 14 : 13;
	}

	protected static float width(String s, Style st, float size) {
		return width(s, st) * size / size(st);
	}

	protected static void draw(GuiGraphicsExtractor g, String s, Style st, float x, float baseline, int color, float size) {
		float k = size / size(st);
		g.pose().pushMatrix();
		g.pose().translate(x, baseline - 7 * k);
		g.pose().scale(k, k);
		g.text(font(), Component.literal(s).withStyle(st), 0, 0, color, false);
		g.pose().popMatrix();
	}

	// vanilla puts every font's baseline 7 below where it's drawn, so baseline here is the real baseline, not the top
	protected static void draw(GuiGraphicsExtractor g, String s, Style st, float x, float baseline, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, baseline - 7);
		g.text(font(), Component.literal(s).withStyle(st), 0, 0, color, false);
		g.pose().popMatrix();
	}

	// a material symbol centered on cy: the font's em box is the icon box, so its middle is half the size up
	protected static void icon(GuiGraphicsExtractor g, char c, float x, float cy, float size, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, cy + size * 0.5f - 7 * size / 18);
		g.pose().scale(size / 18, size / 18);
		g.text(font(), Component.literal(String.valueOf(c)).withStyle(ICONS), 0, 0, color, false);
		g.pose().popMatrix();
	}

	protected static int iconW(float size) {
		return Math.round(size);
	}

	// ---------- label / value rows (mini debug, pvp info) ----------

	protected static float cardW(List<String[]> rows) {
		float l = 0, v = 0;
		for (String[] r : rows) {
			l = Math.max(l, width(r[0], LABEL_FONT, 13));
			v = Math.max(v, width(r[1], VALUE_FONT, 13));
		}
		return Math.max(160, 20 + l + 14 + v);
	}

	protected static float cardH(int n) {
		return 14 + lineH(LABEL_FONT) * n + 2 * (n - 1);
	}

	protected static void cardRows(GuiGraphicsExtractor g, List<String[]> rows, float w, int valueColor) {
		float base = 7 + lineH(LABEL_FONT) * 0.8f;
		for (String[] r : rows) {
			draw(g, r[0], LABEL_FONT, 10, base, 0xB8C9D0DA, 13);
			draw(g, r[1], VALUE_FONT, w - 10 - width(r[1], VALUE_FONT, 13), base, valueColor | 0xFF000000, 13);
			base += lineH(LABEL_FONT) + 2;
		}
	}

	// Kestrel's box: a light blur, a cool dark fill a little heavier than the menu's so values hold up over a noon sky,
	// a hairline edge that's brighter along the top like light catching it
	protected static void box(GuiGraphicsExtractor g, float w, float h, float r) {
		box(g, w, h, r, 1, false);
	}

	// with a fade, the music player eases its whole box in and out
	protected static void box(GuiGraphicsExtractor g, float w, float h, float r, float a, boolean edge) {
		KestrelSettings k = Modules.SETTINGS;
		if (k.hudShadow.on()) {
			float size = k.shadowSize.get().floatValue() / 100;
			float sa = Math.min(1, 0.18f * k.shadowOpacity.get().floatValue() / 100) * a;
			Shape.shadow(g, 0, 1.5f, w, h, r, 4 * size, k.shadowSpread.get().floatValue(), ARGB.color(sa, k.shadowColor.get()));
		}
		Shape.glass(g, 0, 0, w, h, r, a, 6);
		Shape.rect(g, 0, 0, w, h, r, ARGB.multiplyAlpha(0x9E0B0F15, a));
		Shape.gradient(g, 0, 0, w, h, r, ARGB.multiplyAlpha(0x14DCE6F5, a), ARGB.multiplyAlpha(0x14DCE6F5, a), 0, 0);
		Shape.outline(g, 0, 0, w, h, r, ARGB.multiplyAlpha(0x2EDCE6F5, a), 1);
		if (edge) Shape.rect(g, 1.5f, Math.min(r, h / 4), 2.5f, h - 2 * Math.min(r, h / 4), 1.25f, ARGB.multiplyAlpha(Ui.accent(), a));
	}

	// ---------- position ----------

	public int px(int sw) {
		return Math.round(fx * Math.max(0, sw - w() * scale));
	}

	public int py(int sh) {
		return Math.round(fy * Math.max(0, sh - h() * scale));
	}

	public void moveTo(float x, float y, int sw, int sh) {
		float freeX = sw - w() * scale, freeY = sh - h() * scale;
		fx = freeX <= 0 ? 0 : Mth.clamp(x / freeX, 0, 1);
		fy = freeY <= 0 ? 0 : Mth.clamp(y / freeY, 0, 1);
	}

	// ---------- drawing ----------

	public void render(GuiGraphicsExtractor g, int sw, int sh) {
		g.pose().pushMatrix();
		g.pose().translate(px(sw), py(sh));
		float s = scale * u();
		g.pose().scale(s, s);
		if (bg.on() && look() == Look.BOX) box(g, contentW(), contentH(), radius(), 1, edge());
		content(g);
		g.pose().popMatrix();
	}

	protected static Font font() {
		return Minecraft.getInstance().font;
	}

	// ---------- config ----------

	@Override
	public JsonObject save() {
		JsonObject o = super.save();
		JsonObject p = new JsonObject();
		p.addProperty("x", fx);
		p.addProperty("y", fy);
		p.addProperty("scale", scale);
		o.add("hud", p);
		return o;
	}

	@Override
	public void load(JsonObject o) {
		super.load(o);
		JsonObject p = o.getAsJsonObject("hud");
		if (p == null) return;
		fx = Mth.clamp(p.get("x").getAsFloat(), 0, 1);
		fy = Mth.clamp(p.get("y").getAsFloat(), 0, 1);
		scale = Mth.clamp(p.get("scale").getAsFloat(), 0.5f, 3f);
	}
}
