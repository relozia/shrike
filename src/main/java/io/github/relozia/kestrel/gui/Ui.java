package io.github.relozia.kestrel.gui;

import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.module.KestrelSettings;
import io.github.relozia.kestrel.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;

// shared look for the menu + hud editor. everything is drawn with vanilla gui sprites and fonts,
// no raw gl, so it keeps working whatever backend minecraft ends up on
public final class Ui {
	// ---------- colors ----------
	// cool glass, the same family as KestrelScreen's. the accent is the only warm color anywhere
	public static final int PANEL = 0xA810141A;
	public static final int CARD = 0x0FFFFFFF;
	public static final int CARD_HOVER = 0x1AFFFFFF;
	public static final int LINE = 0x1CDCE6F5;
	public static final int TEXT = 0xFFF1F4F8;
	public static final int MUTED = 0xFF959FAD;
	public static final int OFF = 0x1BFFFFFF;

	// ---------- fonts ----------
	static final Style INTER = font("inter");
	static final Style SMALL = font("inter_small");
	static final Style BOLD = font("inter_bold");
	static final Style ICONS = font("icons");
	private static final Identifier LOGO = Kestrel.id("textures/gui/logo.png");
	static final Style MENU = font("menu"), MENU_MEDIUM = font("menu_medium");

	// set by the menu while it fades in/out, everything drawn through here picks it up
	static float alpha = 1;

	private Ui() {}

	private static Style font(String id) {
		return Style.EMPTY.withFont(new FontDescription.Resource(Kestrel.id(id)));
	}

	public static int accent() {
		return Modules.SETTINGS.accent.get();
	}

	static int c(int argb) {
		return alpha >= 1 ? argb : ARGB.multiplyAlpha(argb, alpha);
	}

	public static Component t(String s) { return Component.literal(s).withStyle(INTER); }
	public static Component small(String s) { return Component.literal(s).withStyle(SMALL); }
	public static Component bold(String s) { return Component.literal(s).withStyle(BOLD); }
	public static Component icon(char ch) { return Component.literal(String.valueOf(ch)).withStyle(ICONS); }

	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		Shape.rect(g, x, y, w, h, Math.min(5, h / 2f), c(color));
	}

	// the frosted panel the sub screens sit on, the menu shell's look at gui size
	static void bigRound(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (!Modules.SETTINGS.menuStyle.get().equals("Solid")) Shape.glassPx(g, x, y, w, h, 12, alpha, 14);
		Shape.shadow(g, x, y, w, h, 12, 4, 0, c(0x24000000));
		Shape.rect(g, x, y, w, h, 12, c(color));
		Shape.outline(g, x, y, w, h, 12, c(0x26DCE6F5), 1);
	}

	static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		Shape.outline(g, x, y, w, h, Math.min(5, h / 2f), c(color), 1);
	}

	static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		Shape.rect(g, x, y, w, h, h / 2f, c(color));
	}

	static void dot(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		Shape.rect(g, x, y, size, size, size / 2f, c(color));
	}

	// ---------- layout pixel text, the menu and the editor toolbar ----------

	// menu fonts are made at 20, size is what you want it drawn at
	static float tw(String s, boolean bold, float size) {
		return width(Component.literal(s).withStyle(bold ? MENU : MENU_MEDIUM)) * size / 20;
	}

	// x, baseline and align ('l', 'c', 'r')
	static void label(GuiGraphicsExtractor g, String s, boolean bold, float size, float x, float base, int color, char align) {
		float k = size / 20, w = tw(s, bold, size);
		float x0 = align == 'c' ? x - w / 2 : align == 'r' ? x - w : x;
		g.pose().pushMatrix();
		g.pose().translate(x0, base - 7 * k);
		g.pose().scale(k, k);
		g.text(Minecraft.getInstance().font, Component.literal(s).withStyle(bold ? MENU : MENU_MEDIUM), 0, 0, c(color), false);
		g.pose().popMatrix();
	}

	// a lucide icon centered on cx, cy. the font is made at 9
	static void glyph(GuiGraphicsExtractor g, char ch, float cx, float cy, float size, int color) {
		Component ic = icon(ch);
		float k = size / 9;
		g.pose().pushMatrix();
		g.pose().translate(cx - width(ic) * k / 2, cy - 4.5f * k);
		g.pose().scale(k, k);
		g.text(Minecraft.getInstance().font, ic, 0, 0, c(color), false);
		g.pose().popMatrix();
	}

	public static void text(GuiGraphicsExtractor g, Component s, int x, int y, int color) {
		g.text(Minecraft.getInstance().font, s, x, y, c(color), false);
	}

	public static int width(Component s) {
		return Minecraft.getInstance().font.width(s);
	}

	// cuts with "..." instead of wrapping, cards only have room for one line
	static String fit(String s, Style st, int max) {
		var f = Minecraft.getInstance().font;
		if (f.width(Component.literal(s).withStyle(st)) <= max) return s;
		while (s.length() > 1 && f.width(Component.literal(s + "...").withStyle(st)) > max) s = s.substring(0, s.length() - 1);
		return s + "...";
	}

	// the kestrel, white in the file so it takes any color. 256 in the texture, drawn at whatever size
	public static void logo(GuiGraphicsExtractor g, float x, float y, float size, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 256, size / 256);
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, 0, 0, 0, 0, 256, 256, 256, 256, c(color));
		g.pose().popMatrix();
	}

	// ---------- sound ----------

	public static void sound(String name) {
		KestrelSettings s = Modules.SETTINGS;
		if (!s.sounds.on() || s.volume.get() <= 0) return;
		float vol = (float) (s.volume.get() / 100.0);
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(Kestrel.id("ui." + name), SoundSource.UI, vol, 1.0F,
			SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
	}

	static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
