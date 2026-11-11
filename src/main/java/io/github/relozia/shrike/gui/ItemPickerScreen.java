package io.github.relozia.shrike.gui;

import io.github.relozia.shrike.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// every item in the game, click to add/remove it from an item list setting (Item Tracker)
// issues:
//   - searches the english name and the id, not other languages yet
//   - picked items show first, then the rest in registry order, which is a bit random
//   - the order is fixed when the picker opens. re-sorting on every click made items jump out from under the cursor
public class ItemPickerScreen extends Screen {
	private static final int SLOT = 20;

	private final Screen parent;
	private final Setting.Items setting;
	private final SearchBox search = new SearchBox();
	private final List<Item> all = new ArrayList<>();
	private String cachedQ;
	private List<Item> cached;
	private float scroll, scrollGoal;

	private int px, py, pw, ph, gx, gy, gw, gh, cols;

	public ItemPickerScreen(Screen parent, Setting.Items setting) {
		super(Component.literal("Pick items"));
		this.parent = parent;
		this.setting = setting;
		List<Item> rest = new ArrayList<>();
		for (Item i : BuiltInRegistries.ITEM) {
			if (i != Items.AIR) (setting.has(i) ? all : rest).add(i);
		}
		all.addAll(rest);
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 430);
		ph = Math.min(height - 24, 290);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		gx = px + 12;
		gy = py + 34;
		gw = pw - 24;
		gh = ph - 34 - 10;
		cols = gw / SLOT;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// names of ~1500 items every frame adds up, so the filtered list only rebuilds when the search changes
	private List<Item> shown() {
		String q = search.lower();
		if (q.equals(cachedQ)) return cached;
		List<Item> out = new ArrayList<>();
		for (Item i : all) {
			if (!q.isEmpty()) {
				String name = i.getName(new ItemStack(i)).getString().toLowerCase(Locale.ROOT);
				String id = BuiltInRegistries.ITEM.getKey(i).getPath();
				if (!name.contains(q) && !id.contains(q)) continue;
			}
			out.add(i);
		}
		cachedQ = q;
		cached = out;
		return out;
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
		Component count = Ui.t(setting.get().size() + " picked");
		Ui.text(g, count, px + 20 + Ui.width(Ui.bold(setting.name)), py + 13, Ui.MUTED);
		search.draw(g, px + pw - 14 - 150, py + 8, 150, "Search items");

		List<Item> items = shown();
		int rows = (items.size() + cols - 1) / cols;
		scrollGoal = Mth.clamp(scrollGoal, 0, Math.max(0, rows * SLOT - gh));
		scroll += (scrollGoal - scroll) * 0.35f;

		Item hov = null;
		g.enableScissor(gx, gy, gx + gw, gy + gh);
		int first = Math.max(0, (int) (scroll / SLOT) * cols);
		for (int i = first; i < items.size(); i++) {
			int x = gx + (i % cols) * SLOT, y = gy + (i / cols) * SLOT - Math.round(scroll);
			if (y > gy + gh) break;
			Item it = items.get(i);
			boolean picked = setting.has(it), h = Ui.in(mx, my, x, y, SLOT, SLOT) && Ui.in(mx, my, gx, gy, gw, gh);
			if (h) hov = it;
			if (picked || h) Ui.round(g, x, y, SLOT - 2, SLOT - 2, h ? Ui.CARD_HOVER : Ui.CARD);
			if (picked) Ui.outline(g, x, y, SLOT - 2, SLOT - 2, Ui.accent());
			g.item(new ItemStack(it), x + 1, y + 1);
		}
		g.disableScissor();

		if (items.isEmpty()) {
			Component c = Ui.t("No items match \"" + search.q.trim() + "\"");
			Ui.text(g, c, gx + (gw - Ui.width(c)) / 2, gy + gh / 2 - 4, Ui.MUTED);
		}
		if (hov != null) g.setTooltipForNextFrame(font, hov.getName(new ItemStack(hov)), mx, my);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (!Ui.in(e.x(), e.y(), gx, gy, gw, gh)) return true;
		List<Item> items = shown();
		int col = (int) ((e.x() - gx) / SLOT), row = (int) ((e.y() - gy + scroll) / SLOT);
		int i = row * cols + col;
		if (col < cols && i >= 0 && i < items.size()) {
			setting.toggle(items.get(i));
			Ui.sound("click");
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		scrollGoal -= (float) sy * SLOT * 2;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.isEscape()) {
			if (!search.q.isEmpty()) search.q = "";
			else minecraft.gui.setScreen(parent);
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
}
