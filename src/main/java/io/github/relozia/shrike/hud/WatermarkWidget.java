package io.github.relozia.shrike.hud;

import io.github.relozia.shrike.gui.Ui;
import io.github.relozia.shrike.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// a square logo chip, then a chip with the name and version, both on Shrike's box
// the logo's source is tools/logo.svg, textures/gui/logo.png is it rendered white at 256 so it can take any color
public class WatermarkWidget extends HudWidget {
	private static final String VERSION = "v" + FabricLoader.getInstance().getModContainer("shrike")
		.map(c -> c.getMetadata().getVersion().getFriendlyString().split("\\+")[0]).orElse("");
	private static final float TEXT = 14, GAP = 5;

	private final Setting.Color color = add(new Setting.Color("Color", 0xF5F7FAFC, 0xF5F7FAFC, RUST, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
	private final Setting.Toggle version = add(new Setting.Toggle("Show Version", true));
	private final Setting.Toggle logo = add(new Setting.Toggle("Show Logo", true));
	private final Setting.Color logoColor = add(new Setting.Color("Logo Color", 0xF5F7FAFC, 0xF5F7FAFC, RUST, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
	private final Setting.Slider logoSize = add(new Setting.Slider("Logo Size", 1, 0.7, 1.8, 0.05, "x"));

	public WatermarkWidget() {
		super("watermark", "Watermark", '\ue3c5', "Shrike's name in the corner.", 0.01f, 0.01f);
	}

	@Override
	protected Look look() {
		return Look.NONE;
	}

	private float textH() {
		return 18 + lineH(LIGHT);
	}

	private float logoChip() {
		return logo.on() ? 18 + TEXT * logoSize.get().floatValue() : 0;
	}

	private float textW() {
		return 28 + width("Shrike", LIGHT, TEXT) + (version.on() ? GAP + width(VERSION, LIGHT, TEXT) : 0);
	}

	@Override
	protected int contentW() {
		return Math.round(textW() + (logo.on() ? logoChip() + GAP : 0));
	}

	@Override
	protected int contentH() {
		return Math.round(Math.max(textH(), logoChip()));
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		float h = contentH(), x = 0;
		if (logo.on()) {
			float c = logoChip(), y = (h - c) / 2, ls = TEXT * logoSize.get().floatValue();
			g.pose().pushMatrix();
			g.pose().translate(0, y);
			if (bg.on()) box(g, c, c, 6);
			Ui.logo(g, (c - ls) / 2, (c - ls) / 2, ls, logoColor.get());
			g.pose().popMatrix();
			x = c + GAP;
		}
		float th = textH(), y = (h - th) / 2;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		if (bg.on()) box(g, textW(), th, 6);
		float base = 9 + lineH(LIGHT) * 0.8f;
		draw(g, "Shrike", LIGHT, 14, base, color.get(), TEXT);
		if (version.on()) draw(g, VERSION, LIGHT, 14 + width("Shrike", LIGHT, TEXT) + GAP, base, 0xB2CBD5E1, TEXT);
		g.pose().popMatrix();
	}
}
