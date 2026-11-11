package io.github.relozia.shrike.gui;

import io.github.relozia.shrike.sound.Sounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

// Better Sounds' list: every sound in the game with its own volume (0 to 200%) and pitch (50 to 200%)
// issues:
//   - only sounds in the registry, a resource pack's extra sounds.json events don't show up
//   - the play button uses your new volume and pitch only after you let go of the slider
public class SoundsScreen extends Screen {
	private static final int ROW = 22;
	private static final char I_PLAY = '\ue13c';

	private final @Nullable Screen parent;
	private final Sounds.PerSound tweaks = Sounds.BetterSounds.tweaks();
	private final List<String> all = new ArrayList<>();
	private final SearchBox search = new SearchBox();
	private boolean onlyChanged;
	private String cachedQ;
	private boolean cachedOnly;
	private List<String> cached;
	private float scroll, scrollGoal;
	// the slider being dragged: its sound and whether it's the pitch one
	private @Nullable String drag;
	private boolean dragPitch;

	private int px, py, pw, ph, lx, ly, lw, lh;

	public SoundsScreen(@Nullable Screen parent) {
		super(Component.literal("Every Sound"));
		this.parent = parent;
		for (var id : BuiltInRegistries.SOUND_EVENT.keySet()) all.add(id.toString());
		all.sort(null);
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 470);
		ph = Math.min(height - 24, 330);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		lx = px + 12;
		ly = py + 34;
		lw = pw - 24;
		lh = ph - 34 - 10;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		if (minecraft.level == null) super.extractBackground(g, mx, my, a);
	}

	// spaces match the dots in ids, so "rain above" finds weather.rain.above
	private List<String> shown() {
		String q = search.lower().replace(' ', '.');
		if (q.equals(cachedQ) && onlyChanged == cachedOnly) return cached;
		List<String> out = new ArrayList<>();
		for (String id : all) if (id.contains(q) && (!onlyChanged || tweaks.touched(id))) out.add(id);
		cachedQ = q;
		cachedOnly = onlyChanged;
		cached = out;
		return out;
	}

	// column layout from the right: play button, pitch, volume, then the name gets the rest
	private int playX() { return lx + lw - 20; }
	private int pitchX() { return playX() - 6 - 90; }
	private int volX() { return pitchX() - 6 - 120; }

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		Ui.bigRound(g, px, py, pw, ph, Ui.PANEL);
		Component title = Ui.bold("Every Sound");
		Ui.text(g, title, px + 14, py + 13, Ui.TEXT);
		Ui.text(g, Ui.t(all.size() + " sounds"), px + 20 + Ui.width(title), py + 13, Ui.MUTED);
		search.draw(g, px + pw - 14 - 150, py + 8, 150, "Search sounds");
		// the filter, accent only while it's narrowing the list
		Component f = Ui.t(onlyChanged ? "Changed" : "All");
		int fw = Ui.width(f) + 14, fx = px + pw - 14 - 150 - 6 - fw;
		boolean fh = Ui.in(mx, my, fx, py + 8, fw, 16);
		Ui.round(g, fx, py + 8, fw, 16, onlyChanged ? Ui.accent() & 0x55FFFFFF : fh ? Ui.CARD_HOVER : Ui.CARD);
		Ui.text(g, f, fx + 7, py + 12, onlyChanged || fh ? Ui.TEXT : Ui.MUTED);

		List<String> rows = shown();
		scrollGoal = Mth.clamp(scrollGoal, 0, Math.max(0, rows.size() * ROW - lh));
		scroll += (scrollGoal - scroll) * 0.35f;

		g.enableScissor(lx, ly, lx + lw, ly + lh);
		int first = Math.max(0, (int) (scroll / ROW));
		for (int i = first; i < rows.size(); i++) {
			int y = ly + i * ROW - Math.round(scroll);
			if (y > ly + lh) break;
			String id = rows.get(i);
			boolean hov = Ui.in(mx, my, lx, y, lw, ROW - 2) && Ui.in(mx, my, lx, ly, lw, lh);
			if (hov) Ui.round(g, lx, y, lw, ROW - 2, Ui.CARD);
			String name = id.startsWith("minecraft:") ? id.substring(10) : id;
			Ui.text(g, Ui.t(Ui.fit(name, Ui.INTER, volX() - lx - 14)), lx + 6, y + 6, tweaks.touched(id) ? Ui.accent() : Ui.TEXT);
			float v = tweaks.volume(id), p = tweaks.pitch(id);
			slider(g, volX(), y + 2, 120, v / 2, v != 1, v <= 0 ? "Muted" : "Volume " + Math.round(v * 100) + "%", mx, my);
			slider(g, pitchX(), y + 2, 90, (p - 0.5f) / 1.5f, p != 1, "Pitch " + Math.round(p * 100) + "%", mx, my);
			boolean ph2 = Ui.in(mx, my, playX(), y + 2, 20, 16);
			Ui.round(g, playX(), y + 2, 20, 16, ph2 ? Ui.CARD_HOVER : Ui.CARD);
			Ui.glyph(g, I_PLAY, playX() + 10.5f, y + 10, 8, ph2 ? Ui.TEXT : Ui.MUTED);
		}
		g.disableScissor();

		if (rows.isEmpty()) {
			Component c = Ui.t(onlyChanged && search.empty() ? "Nothing changed yet" : "No sounds match \"" + search.q.trim() + "\"");
			Ui.text(g, c, lx + (lw - Ui.width(c)) / 2, ly + lh / 2 - 4, Ui.MUTED);
		}
	}

	// the accent only shows on sliders you moved, a list of 2000 identical orange bars says nothing
	private void slider(GuiGraphicsExtractor g, int x, int y, int w, float t, boolean moved, String label, int mx, int my) {
		boolean h = Ui.in(mx, my, x, y, w, 16);
		Ui.round(g, x, y, w, 16, h ? Ui.CARD_HOVER : Ui.CARD);
		int fill = Math.round(w * Mth.clamp(t, 0, 1));
		if (fill > 0) Ui.round(g, x, y, fill, 16, moved ? Ui.accent() & 0x66FFFFFF : 0x12FFFFFF);
		Component c = Ui.small(label);
		Ui.text(g, c, x + (w - Ui.width(c)) / 2, y + 4, h || moved ? Ui.TEXT : Ui.MUTED);
	}

	private @Nullable String rowAt(double my) {
		List<String> rows = shown();
		int i = (int) ((my - ly + scroll) / ROW);
		return i >= 0 && i < rows.size() ? rows.get(i) : null;
	}

	// volume in 5% steps up to 200, pitch 50 to 200 in 5% steps
	private void set(String id, boolean pitch, double mx) {
		int x = pitch ? pitchX() : volX(), w = pitch ? 90 : 120;
		float t = Mth.clamp((float) (mx - x) / w, 0, 1);
		if (pitch) tweaks.pitch(id, Math.round((0.5f + t * 1.5f) * 20) / 20f);
		else tweaks.volume(id, Math.round(t * 2 * 20) / 20f);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		int fw = Ui.width(Ui.t(onlyChanged ? "Changed" : "All")) + 14, fx = px + pw - 14 - 150 - 6 - fw;
		if (Ui.in(e.x(), e.y(), fx, py + 8, fw, 16)) {
			onlyChanged = !onlyChanged;
			scroll = scrollGoal = 0;
			Ui.sound("click");
			return true;
		}
		if (!Ui.in(e.x(), e.y(), lx, ly, lw, lh)) return true;
		String id = rowAt(e.y());
		if (id == null) return true;
		boolean vol = e.x() >= volX() && e.x() < volX() + 120, pitch = e.x() >= pitchX() && e.x() < pitchX() + 90;
		// right click puts a slider back to 100%
		if (e.button() == 1) {
			if (vol) tweaks.volume(id, 1);
			else if (pitch) tweaks.pitch(id, 1);
			return true;
		}
		if (vol || pitch) {
			drag = id;
			dragPitch = pitch;
			set(id, pitch, e.x());
		} else if (e.x() >= playX()) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.parse(id)), 1, 1));
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (drag != null) set(drag, dragPitch, e.x());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		drag = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		scrollGoal -= (float) sy * ROW * 3;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.isEscape()) {
			if (!search.q.isEmpty()) search.q = "";
			else onClose();
			return true;
		}
		if (search.key(e, minecraft)) {
			scroll = scrollGoal = 0;
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (!search.type(e)) return false;
		scroll = scrollGoal = 0;
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
