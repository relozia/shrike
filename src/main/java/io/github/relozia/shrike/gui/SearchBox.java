package io.github.relozia.shrike.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;

// the search field from the menu, pulled out once the item picker needed the exact same thing
// typing, backspace (ctrl for a whole word), paste. no cursor or selection, nobody edits the middle of a search
final class SearchBox {
	private static final int MAX = 40;
	private static final char I_SEARCH = '\ue151';

	String q = "";

	boolean empty() {
		return q.isBlank();
	}

	String lower() {
		return q.trim().toLowerCase(java.util.Locale.ROOT);
	}

	boolean type(CharacterEvent e) {
		if (!e.isAllowedChatCharacter() || q.length() >= MAX) return false;
		q += e.codepointAsString();
		return true;
	}

	// only backspace and paste, everything else falls through to the screen
	boolean key(KeyEvent e, Minecraft mc) {
		if (e.key() == 259 && !q.isEmpty()) {
			if (e.hasControlDown()) {
				String t = q.stripTrailing();
				int sp = t.lastIndexOf(' ');
				q = sp < 0 ? "" : t.substring(0, sp + 1);
			} else {
				q = q.substring(0, q.length() - 1);
			}
			return true;
		}
		if (e.isPaste()) {
			String clip = mc.keyboardHandler.getClipboard().lines().findFirst().orElse("");
			q = (q + clip).substring(0, Math.min(MAX, q.length() + clip.length()));
			return true;
		}
		return false;
	}

	void draw(GuiGraphicsExtractor g, int x, int y, int w, String placeholder) {
		Ui.round(g, x, y, w, 16, Ui.CARD);
		Ui.text(g, Ui.icon(I_SEARCH), x + 5, y + 4, Ui.MUTED);
		if (q.isEmpty()) {
			Ui.text(g, Ui.t(placeholder), x + 18, y + 4, Ui.MUTED);
		} else {
			String shown = q;
			// keep the end of what you're typing visible, drop characters off the front
			while (Ui.width(Ui.t(shown)) > w - 26 && shown.length() > 1) shown = shown.substring(1);
			Ui.text(g, Ui.t(shown), x + 18, y + 4, Ui.TEXT);
		}
		if ((Util.getMillis() / 500) % 2 == 0) {
			// empty: caret sits just before the placeholder instead of on top of its first letter
			int cx = q.isEmpty() ? x + 16 : x + 18 + Math.min(Ui.width(Ui.t(q)), w - 26) + 1;
			g.fill(cx, y + 3, cx + 1, y + 13, Ui.c(Ui.accent()));
		}
	}
}
