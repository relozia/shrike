package io.github.relozia.kestrel.gui;

import io.github.relozia.kestrel.setting.Setting;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// edit a list of words: type + enter adds, the x on a row removes it
public class WordsScreen extends Screen {
	private static final int ROW = 18;
	private static final char I_X = '\ue1b2';

	private final Screen parent;
	private final Setting.Words setting;
	private final SearchBox input = new SearchBox();
	private float scroll, scrollGoal;
	private int px, py, pw, ph, lx, ly, lw, lh;

	public WordsScreen(Screen parent, Setting.Words setting) {
		super(Component.literal(setting.name));
		this.parent = parent;
		this.setting = setting;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 300);
		ph = Math.min(height - 24, 240);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		lx = px + 12;
		ly = py + 56;
		lw = pw - 24;
		lh = ph - 56 - 10;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
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
		Ui.text(g, Ui.bold(setting.name), px + 14, py + 13, Ui.TEXT);
		Component count = Ui.t(String.valueOf(setting.get().size()));
		Ui.text(g, count, px + 20 + Ui.width(Ui.bold(setting.name)), py + 13, Ui.MUTED);
		input.draw(g, px + 12, py + 32, pw - 24, setting.hint);

		List<String> words = setting.get();
		scrollGoal = Mth.clamp(scrollGoal, 0, Math.max(0, words.size() * ROW - lh));
		scroll += (scrollGoal - scroll) * 0.35f;
		g.enableScissor(lx, ly, lx + lw, ly + lh);
		for (int i = 0; i < words.size(); i++) {
			int y = ly + i * ROW - Math.round(scroll);
			if (y + ROW < ly || y > ly + lh) continue;
			boolean hov = Ui.in(mx, my, lx, y, lw, ROW - 2) && Ui.in(mx, my, lx, ly, lw, lh);
			Ui.round(g, lx, y, lw, ROW - 2, hov ? Ui.CARD_HOVER : Ui.CARD);
			Ui.text(g, Ui.t(words.get(i)), lx + 7, y + 4, Ui.TEXT);
			boolean onX = hov && mx >= lx + lw - 18;
			Ui.text(g, Ui.icon(I_X), lx + lw - 14, y + 4, onX ? 0xFFE5484D : Ui.MUTED);
		}
		g.disableScissor();
		if (words.isEmpty()) {
			Component c = Ui.t("Nothing yet");
			Ui.text(g, c, lx + (lw - Ui.width(c)) / 2, ly + 20, Ui.MUTED);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (!Ui.in(e.x(), e.y(), lx, ly, lw, lh) || e.x() < lx + lw - 18) return true;
		int i = (int) ((e.y() - ly + scroll) / ROW);
		List<String> words = setting.get();
		if (i >= 0 && i < words.size()) {
			setting.remove(words.get(i));
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
			if (!input.q.isEmpty()) input.q = "";
			else minecraft.gui.setScreen(parent);
			return true;
		}
		if (e.isConfirmation()) {
			if (!input.empty()) {
				setting.add(input.q);
				input.q = "";
				Ui.sound("click");
			}
			return true;
		}
		return input.key(e, minecraft) || super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		return input.type(e);
	}
}
