package io.github.relozia.kestrel.gui;

import io.github.relozia.kestrel.setting.Setting;
import io.github.relozia.kestrel.visual.Crosshair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// paint the crosshair: left click/drag draws, right click/drag erases. presets on the right
public class CrosshairEditorScreen extends Screen {
	private static final int CELL = 11, N = Setting.Grid.N;

	private final Screen parent;
	private final Setting.Grid grid;
	private int paint = -1; // -1 not dragging, 1 drawing, 0 erasing
	private int px, py, pw, ph, gx, gy;

	public CrosshairEditorScreen(Screen parent, Setting.Grid grid) {
		super(Component.literal("Crosshair"));
		this.parent = parent;
		this.grid = grid;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 330);
		ph = Math.min(height - 24, 220);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		gx = px + 14;
		gy = py + 34;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<String> buttons() {
		List<String> b = new ArrayList<>(Crosshair.PRESETS.keySet());
		b.add("Clear");
		return b;
	}

	private int bx() {
		return gx + N * CELL + 16;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		if (minecraft.level == null) {
			super.extractBackground(g, mx, my, a);
			return;
		}
		// the world stays sharp behind the frosted panel, like the menu
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		Ui.bigRound(g, px, py, pw, ph, Ui.PANEL);
		Ui.text(g, Ui.bold("Crosshair"), px + 14, py + 13, Ui.TEXT);
		Ui.text(g, Ui.t("Left click draws, right click erases"), px + 82, py + 13, Ui.MUTED);

		for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
			int cx = gx + x * CELL, cy = gy + y * CELL;
			boolean center = x == N / 2 && y == N / 2;
			int col = grid.at(x, y) ? Ui.accent() : Ui.in(mx, my, cx, cy, CELL, CELL) ? 0xFF353A46 : center ? 0xFF2A2E38 : Ui.CARD;
			g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, col);
		}

		int bx = bx(), by = gy;
		for (String b : buttons()) {
			boolean hov = Ui.in(mx, my, bx, by, 70, 16);
			Ui.round(g, bx, by, 70, 16, hov ? Ui.CARD_HOVER : Ui.CARD);
			Ui.text(g, Ui.t(b), bx + 7, by + 4, b.equals("Clear") ? 0xFFE5484D : Ui.TEXT);
			by += 20;
		}

		// actual size, the grid is zoomed way in. sits next to the buttons, under them it ran off the
		// panel on short screens
		int vx = bx() + 82;
		Ui.text(g, Ui.small("PREVIEW"), vx, gy, Ui.MUTED);
		Ui.round(g, vx, gy + 10, 40, 40, 0xFF5E7FA8);
		Crosshair.paint(g, grid, vx + 20 - N / 2, gy + 30 - N / 2, 1, 0xFFFFFFFF, true);
	}

	private boolean cell(double mx, double my) {
		int x = (int) ((mx - gx) / CELL), y = (int) ((my - gy) / CELL);
		if (mx < gx || my < gy || x >= N || y >= N || paint < 0) return false;
		grid.put(x, y, paint == 1);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		int bx = bx(), by = gy;
		for (String b : buttons()) {
			if (Ui.in(e.x(), e.y(), bx, by, 70, 16)) {
				grid.set(b.equals("Clear") ? ".".repeat(N * N) : Crosshair.PRESETS.get(b));
				Ui.sound("click");
				return true;
			}
			by += 20;
		}
		paint = e.button() == 0 ? 1 : e.button() == 1 ? 0 : -1;
		cell(e.x(), e.y());
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		cell(e.x(), e.y());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		paint = -1;
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
