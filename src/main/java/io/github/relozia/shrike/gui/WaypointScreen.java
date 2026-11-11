package io.github.relozia.shrike.gui;

import io.github.relozia.shrike.nav.Nav;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

// the waypoints in this world, nearest first. the x removes one
public class WaypointScreen extends Screen {
	private static final int ROW = 20;
	private static final char I_X = '\ue1b2';

	private final @Nullable Screen parent;
	private float scroll, scrollGoal;
	private int px, py, pw, ph, lx, ly, lw, lh;

	public WaypointScreen(@Nullable Screen parent) {
		super(Component.literal("Waypoints"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 320);
		ph = Math.min(height - 24, 240);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		lx = px + 12;
		ly = py + 32;
		lw = pw - 24;
		lh = ph - 32 - 10;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<Nav.Waypoint> sorted() {
		return Nav.Waypoints.inWorld().stream().sorted((a, b) -> Double.compare(dist(a), dist(b))).toList();
	}

	private double dist(Nav.Waypoint p) {
		return minecraft.player == null ? 0 : Math.sqrt(minecraft.player.distanceToSqr(p.x() + 0.5, p.y(), p.z() + 0.5));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		// the world stays sharp behind the frosted panel, like the menu
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		Ui.bigRound(g, px, py, pw, ph, Ui.PANEL);
		Ui.text(g, Ui.bold("Waypoints"), px + 14, py + 13, Ui.TEXT);
		List<Nav.Waypoint> l = sorted();
		scrollGoal = Mth.clamp(scrollGoal, 0, Math.max(0, l.size() * ROW - lh));
		scroll += (scrollGoal - scroll) * 0.35f;
		g.enableScissor(lx, ly, lx + lw, ly + lh);
		for (int i = 0; i < l.size(); i++) {
			Nav.Waypoint p = l.get(i);
			int y = ly + i * ROW - Math.round(scroll);
			if (y + ROW < ly || y > ly + lh) continue;
			boolean hov = Ui.in(mx, my, lx, y, lw, ROW - 2) && Ui.in(mx, my, lx, ly, lw, lh);
			Ui.round(g, lx, y, lw, ROW - 2, hov ? Ui.CARD_HOVER : Ui.CARD);
			g.fill(lx + 6, y + 6, lx + 12, y + 12, p.color());
			Ui.text(g, Ui.t(p.name()), lx + 18, y + 5, Ui.TEXT);
			boolean other = minecraft.level == null || !p.dim().equals(minecraft.level.dimension().identifier().toString());
			String where = p.x() + " " + p.y() + " " + p.z() + (other ? "  other dimension" : "  " + Math.round(dist(p)) + "m");
			Component w = Ui.small(where);
			Ui.text(g, w, lx + lw - 24 - Ui.width(w), y + 6, Ui.MUTED);
			Ui.text(g, Ui.icon(I_X), lx + lw - 14, y + 5, hov && mx >= lx + lw - 18 ? 0xFFE5484D : Ui.MUTED);
		}
		g.disableScissor();
		if (l.isEmpty()) {
			Component c = Ui.t("No waypoints here yet, press B to add one");
			Ui.text(g, c, lx + (lw - Ui.width(c)) / 2, ly + 20, Ui.MUTED);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (!Ui.in(e.x(), e.y(), lx, ly, lw, lh) || e.x() < lx + lw - 18) return true;
		int i = (int) ((e.y() - ly + scroll) / ROW);
		List<Nav.Waypoint> l = sorted();
		if (i >= 0 && i < l.size()) {
			Nav.Waypoints.remove(l.get(i));
			Ui.sound("click");
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		scrollGoal -= (float) sy * ROW * 2;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.isEscape()) {
			minecraft.gui.setScreen(parent);
			return true;
		}
		return super.keyPressed(e);
	}
}
