package io.github.relozia.shrike.hud;

import io.github.relozia.shrike.setting.Setting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

// coordinates panel: "x, y, z" with the pin icon on top, then label / value rows. with only the xyz row
// it shrinks to a compact chip
public class CoordsWidget extends HudWidget {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final float PAD = 10, TEXT = 13, LINE = 15.6f, ICON = 15, GAP = 12, ICON_GAP = 8, SPACING = 3;

	private final Setting.Color color = add(new Setting.Color("Color", 0xF5F7FAFC, 0xF5F7FAFC, RUST, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
	private final Setting.Toggle time = add(new Setting.Toggle("Show Real Time", true));
	private final Setting.Toggle facing = add(new Setting.Toggle("Show Direction", true));
	private final Setting.Toggle dim = add(new Setting.Toggle("Show Dimension", true));
	private final Setting.Toggle biome = add(new Setting.Toggle("Show Biome", false));
	private final Setting.Slider size = add(new Setting.Slider("Size", 1, 0.7, 1.5, 0.05, "x"));

	public CoordsWidget() {
		super("coordinates", "Coordinates", '\ue111', "Your position, facing, dimension and the time.", 0.01f, 0.03f);
	}

	@Override
	public boolean visible() {
		return Minecraft.getInstance().player != null || editing;
	}

	private float s() {
		return size.get().floatValue();
	}

	private List<String[]> rows() {
		Player p = Minecraft.getInstance().player;
		List<String[]> out = new ArrayList<>();
		if (p == null) {
			out.add(new String[] {"XYZ", "120.5, 64.0, -45.5"});
			return out;
		}
		out.add(new String[] {"XYZ", String.format(Locale.ROOT, "%.1f, %.1f, %.1f", p.getX(), p.getY(), p.getZ())});
		if (facing.on()) out.add(new String[] {"Facing", direction(p.getYRot())});
		if (dim.on()) out.add(new String[] {"Dimension", dimension(p)});
		if (biome.on()) out.add(new String[] {"Biome", p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> title(k.identifier().getPath())).orElse("Unknown")});
		if (time.on()) out.add(new String[] {"Time", LocalTime.now().format(TIME)});
		return out;
	}

	// eight ways, yaw 0 is south in minecraft
	private static String direction(float yaw) {
		float y = Mth.positiveModulo(yaw, 360);
		String[] n = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
		return n[(int) ((y + 22.5f) / 45) % 8];
	}

	private static String dimension(Player p) {
		String id = p.level().dimension().identifier().toString();
		return switch (id) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> id.replace("minecraft:", "").replace('_', ' ');
		};
	}

	private static String title(String path) {
		StringBuilder sb = new StringBuilder();
		for (String w : path.split("_")) {
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	private float rawW(List<String[]> r) {
		float xyz = ICON + ICON_GAP + width(r.getFirst()[1], LIGHT, TEXT);
		if (r.size() == 1) return xyz + 16;
		float w = Math.max(156, PAD * 2 + xyz);
		for (int i = 1; i < r.size(); i++) w = Math.max(w, PAD * 2 + width(r.get(i)[0], LIGHT, TEXT) + GAP + width(r.get(i)[1], LIGHT, TEXT));
		return w;
	}

	@Override
	protected int contentW() {
		return Math.round(rawW(rows()) * s());
	}

	@Override
	protected int contentH() {
		int n = rows().size();
		return Math.round((PAD * 2 + LINE * n + SPACING * (n - 1)) * s());
	}

	@Override
	protected float radius() {
		return 6 * s();
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		List<String[]> r = rows();
		float s = s(), w = rawW(r);
		g.pose().pushMatrix();
		g.pose().scale(s, s);
		float base = PAD + LINE * 0.8f;
		float xyzW = ICON + ICON_GAP + width(r.getFirst()[1], LIGHT, TEXT);
		float x0 = r.size() == 1 ? 8 : (w - xyzW) / 2;
		icon(g, (char) 0xE0C8, x0, PAD + LINE * 0.5f, ICON, color.get());
		draw(g, r.getFirst()[1], LIGHT, x0 + ICON + ICON_GAP, base, color.get(), TEXT);
		for (int i = 1; i < r.size(); i++) {
			base += LINE + SPACING;
			draw(g, r.get(i)[0], LIGHT, PAD, base, LABEL, TEXT);
			draw(g, r.get(i)[1], LIGHT, w - PAD - width(r.get(i)[1], LIGHT, TEXT), base, 0xB8CBD5E1, TEXT);
		}
		g.pose().popMatrix();
	}
}
