package io.github.relozia.kestrel.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// Kestrel's toast: the hud box look in the top right, the accent edge, a bold title and the message under it.
// goes through vanilla's toast manager so it stacks and slides with everything else
// issues:
//   - vanilla's "notification time" accessibility option still stretches how long these stay, on purpose
public final class Notice implements Toast {
	private final Object token;
	private String title;
	private List<FormattedCharSequence> lines;
	private final long ms;
	private long shownAt;
	private boolean changed = true;
	private int w;
	private Visibility vis = Visibility.HIDE;

	private Notice(Object token, String title, Component msg, long ms) {
		this.token = token;
		this.ms = ms;
		set(title, msg);
	}

	// one per token, a second call while it's still up swaps the text and restarts its timer
	public static void show(Object token, String title, Component msg, long ms) {
		ToastManager tm = Minecraft.getInstance().gui.toastManager();
		Notice n = tm.getToast(Notice.class, token);
		if (n == null) tm.addToast(new Notice(token, title, msg, ms));
		else {
			n.set(title, msg);
			n.changed = true;
		}
	}

	public static void show(String title, String msg, long ms) {
		show(title, title, Component.literal(msg), ms);
	}

	private void set(String title, Component msg) {
		Font f = Minecraft.getInstance().font;
		this.title = title;
		lines = f.split(msg.copy().withStyle(s -> s.withFont(Ui.INTER.getFont())), 200);
		int tw = f.width(Ui.bold(title));
		for (FormattedCharSequence l : lines) tw = Math.max(tw, f.width(l));
		w = Math.max(150, tw + 26);
	}

	@Override public Object getToken() { return token; }
	@Override public int width() { return w; }
	@Override public int height() { return 21 + lines.size() * 10 + 4; }
	@Override public Visibility getWantedVisibility() { return vis; }

	@Override
	public void update(ToastManager tm, long visibleMs) {
		if (changed) {
			shownAt = visibleMs;
			changed = false;
		}
		vis = visibleMs - shownAt < ms * tm.getNotificationDisplayTimeMultiplier() ? Visibility.SHOW : Visibility.HIDE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long visibleMs) {
		// a few px in from the screen edge and the slot above, vanilla's toasts sit flush
		float x = 4, y = 4, bw = w - 8, bh = height() - 6;
		Shape.shadow(g, x, y + 1.5f, bw, bh, 6, 4, 0, 0x40000000);
		Shape.glass(g, x, y, bw, bh, 6, 1, 6);
		Shape.rect(g, x, y, bw, bh, 6, 0xC40B0F15);
		Shape.outline(g, x, y, bw, bh, 6, 0x2EDCE6F5, 1);
		Shape.rect(g, x + 1.5f, y + 6, 2.5f, bh - 12, 1.25f, Ui.accent());
		g.text(font, Ui.bold(title), 13, 10, Ui.TEXT, false);
		for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), 13, 21 + i * 10, Ui.MUTED, false);
	}
}
