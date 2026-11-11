package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// one value widgets in three arrangements on Kestrel's box:
//   Icon    - icon, value, then a quieter unit ("fps", "ms")
//   Label   - "RAM:" on the left, the value pushed right
//   Compact - a small icon, then the value with its unit in it
// renaming these from the old Panel / Pill / Icon Pill puts saved widgets back on their default style once
public abstract class TextWidget extends HudWidget {
	private final char glyph;
	private final String label;
	private final Setting.Choice style;
	private final Setting.Color iconColor, valueColor, labelColor;

	protected TextWidget(String name, char icon, String desc, float fx, float fy, char glyph, String label, String style) {
		this(name, icon, desc, fx, fy, glyph, label, style, WHITE);
	}

	protected TextWidget(String name, char icon, String desc, float fx, float fy, char glyph, String label, String style, int iconDef) {
		super(name, icon, desc, fx, fy);
		this.glyph = glyph;
		this.label = label;
		this.style = add(new Setting.Choice("Style", style, "Icon", "Label", "Compact"));
		this.iconColor = add(new Setting.Color("Icon Color", iconDef, WHITE, RUST, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
		this.valueColor = add(new Setting.Color("Value Color", WHITE, WHITE, RUST, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
		this.labelColor = add(new Setting.Color("Label Color", style.equals("Icon") ? SUFFIX : LABEL, SUFFIX, LABEL, 0xFF8A8F99, RUST).withAlpha());
	}

	// null hides the widget (no server to show, nothing tracked yet)
	protected abstract String value();

	// the quieter unit after the value in the Icon style
	protected String suffix() {
		return "";
	}

	// the Label style's word ("RAM:", "Session:")
	protected String label() {
		return label;
	}

	// what the editor shows when value() is null, so the widget can still be placed
	protected String sample() {
		return "...";
	}

	protected int valueColor() {
		return valueColor.get();
	}

	// ping and tps: 0 good, 1 meh, 2 bad. "When Bad" leaves good values in the normal value color
	protected Setting.Choice colors() {
		return add(new Setting.Choice("Colors", "When Bad", "Off", "When Bad", "Always"));
	}

	protected int graded(Setting.Choice colors, int level) {
		if (colors.get().equals("Off") || level == 0 && colors.get().equals("When Bad")) return valueColor.get();
		return level == 0 ? 0xFF57D163 : level == 1 ? 0xFFF5C542 : 0xFFE5484D;
	}

	private String shown() {
		String v = value();
		return v == null && editing ? sample() : v;
	}

	@Override
	public boolean visible() {
		return shown() != null;
	}

	// Icon puts the unit apart from the number, so values like ping drop their own "ms"
	protected boolean split() {
		return style.get().equals("Icon");
	}

	private String v() {
		String v = shown();
		return v == null ? "" : v;
	}

	// ---------- layout ----------

	// 10 in from the sides, 6 above and below. tight on purpose, the box shouldn't be bigger than what it says
	@Override
	protected int contentW() {
		String v = v();
		return switch (style.get()) {
			case "Icon" -> 20 + 16 + 5 + width(v, HUD) + width(suffix(), LIGHT) + (suffix().isEmpty() ? 0 : 2);
			case "Compact" -> 20 + 14 + 5 + width(v, HUD);
			default -> 20 + width(v, HUD) + (label().isBlank() ? 0 : width(label(), LIGHT) + 8);
		};
	}

	@Override
	protected int contentH() {
		return Math.round(12 + lineH(HUD));
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		String v = v();
		int w = contentW(), h = contentH();
		float base = 6 + lineH(HUD) * 0.8f;
		switch (style.get()) {
			case "Icon" -> {
				icon(g, glyph, 10, h / 2f, 16, iconColor.get());
				float x = 10 + 16 + 5;
				draw(g, v, HUD, x, base, valueColor());
				draw(g, suffix(), LIGHT, x + width(v, HUD) + 2, base, labelColor.get());
			}
			case "Compact" -> {
				// a softer icon unless you pick a color, the value is the point here
				icon(g, glyph, 10, h / 2f, 14, iconColor.get() == WHITE ? ICON_GREY : iconColor.get());
				draw(g, v, HUD, 10 + 14 + 5, base, valueColor());
			}
			default -> {
				if (label().isBlank()) {
					draw(g, v, HUD, (w - width(v, HUD)) / 2f, base, valueColor());
				} else {
					draw(g, label(), LIGHT, 10, base, labelColor.get());
					draw(g, v, HUD, w - 10 - width(v, HUD), base, valueColor());
				}
			}
		}
	}
}
