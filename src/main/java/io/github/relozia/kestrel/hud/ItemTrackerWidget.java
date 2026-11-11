package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// how many of each picked item you're carrying. replaces the old Item Counter + UHC Overlay
// issues:
//   - one "warn below" for all items, a per item threshold would need a number field in the picker
//   - shulker box contents don't count, only what's directly in your inventory
public class ItemTrackerWidget extends HudWidget {
	private final Setting.Items items = add(new Setting.Items("Items",
		"minecraft:ender_pearl", "minecraft:golden_apple", "minecraft:totem_of_undying", "minecraft:arrow"));
	private final Setting.Choice layout = add(new Setting.Choice("Layout", "Vertical", "Vertical", "Horizontal"));
	private final Setting.Toggle hideEmpty = add(new Setting.Toggle("Hide Empty", true));
	private final Setting.Toggle lowWarn = add(new Setting.Toggle("Low Stock Warning", true));
	private final Setting.Slider lowAt = add(new Setting.Slider("Low Stock At", 2, 0, 16, 1, ""));

	public ItemTrackerWidget() {
		super("Item Tracker", '\ue129', "Counts the items you pick, like pearls, gapples and totems.", 0.99f, 0.40f);
	}

	private record Entry(Item item, int count) {}

	private List<Entry> entries() {
		Player p = Minecraft.getInstance().player;
		List<Entry> out = new ArrayList<>();
		for (Item it : items.items()) {
			int n = editing ? 12 : p == null ? 0 : count(p.getInventory(), it);
			if (n == 0 && hideEmpty.on() && !editing) continue;
			out.add(new Entry(it, n));
		}
		return out;
	}

	private static int count(Inventory inv, Item it) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(it)) n += s.getCount();
		}
		return n;
	}

	@Override
	public boolean visible() {
		return !entries().isEmpty();
	}

	private boolean low(Entry e) {
		return lowWarn.on() && !editing && e.count <= lowAt.get();
	}

	// gui pixels and vanilla's font, the counts sit next to real item icons and should look like the inventory's.
	// the box's corner is in gui pixels here too, so it's smaller to come out like the others
	@Override protected boolean units() { return false; }
	@Override protected float radius() { return 3; }

	private static String label(Entry e) {
		return "x" + e.count;
	}

	private int entryW(Entry e) {
		return 16 + 3 + font().width(label(e));
	}

	@Override
	protected int contentW() {
		List<Entry> es = entries();
		int w = 0;
		if (layout.get().equals("Horizontal")) {
			for (Entry e : es) w += entryW(e) + 6;
			w -= 6;
		} else {
			for (Entry e : es) w = Math.max(w, entryW(e));
		}
		return Math.max(0, w) + 8;
	}

	@Override
	protected int contentH() {
		int n = entries().size();
		return (layout.get().equals("Horizontal") ? 18 : 18 * n) + 6;
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		boolean horiz = layout.get().equals("Horizontal");
		// low stock flashes between red and a red tinted white, steady red would just look like the design after a while
		boolean flash = (Util.getMillis() / 400) % 2 == 0;
		int x = 4, y = 3;
		for (Entry e : entries()) {
			g.item(new ItemStack(e.item), x, y);
			int col = low(e) ? flash ? 0xFFFF5555 : ARGB.srgbLerp(0.5f, 0xFFF7F7F7, 0xFFFF5555) : 0xFFF7F7F7;
			g.text(font(), label(e), x + 19, y + 4, col, true);
			if (horiz) x += entryW(e) + 6;
			else y += 18;
		}
	}
}
