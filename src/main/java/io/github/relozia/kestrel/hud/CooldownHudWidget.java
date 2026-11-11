package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.gui.Ui;
import io.github.relozia.kestrel.mixin.CooldownAccessor;
import io.github.relozia.kestrel.pvp.CooldownEnd;
import io.github.relozia.kestrel.setting.Setting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// everything in your inventory that's cooling down, icon + name + time left. its own module, not part of Cooldowns:
// that one writes the seconds onto the slot, this is a list off to the side, and they get used differently
// rows slide in from below and out upward, the box grows and shrinks to fit
// issues:
//   - the frame is still the plain widget box, it never got a panel of its own
//   - two items sharing a cooldown group (all the spears) show as one row, named after the first one found
public class CooldownHudWidget extends HudWidget {
	// 12 padding, 16 icon, 6 to the name, 8 before the time, Inter 13 on a 15.6 line, 4 between rows. tight enough
	// that a full set of cooling spears still fits above the hotbar without the name crowding the icon
	private static final float PAD = 12, ICON = 16, GAP = 6, SPACE = 8, TEXT = 13, LINE = 15.6f, ROW_GAP = 4, MIN_W = 160;
	// the editor shows two sample rows at a time, swapping every 2.25s with a short crossfade
	private static final long HOLD = 1800, FADE = 450, STEP = HOLD + FADE;
	private static List<ItemStack> samples;

	private final Setting.Slider size = add(new Setting.Slider("Size", 1, 0.7, 1.5, 0.05, "x"));

	private static final class Row {
		ItemStack stack;
		String name;
		int end;
		boolean active = true;
		float alpha, off = 6;
	}

	private final Map<Identifier, Row> rows = new LinkedHashMap<>();
	private float curW = -1, curH;
	private long lastFrame = Util.getMillis(), previewFrom;

	public CooldownHudWidget() {
		super("Cooldown HUD", '\ue1e0', "A list of every item that's on cooldown, with how long is left.", 0.02f, 0.4f);
	}

	// ---------- tracking ----------

	@Override
	public void tick(Minecraft mc) {
		Player p = mc.player;
		if (p == null) {
			rows.clear();
			return;
		}
		CooldownAccessor cd = (CooldownAccessor) p.getCooldowns();
		Map<Identifier, Row> seen = new LinkedHashMap<>();
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			Identifier grp = p.getCooldowns().getCooldownGroup(s);
			if (grp == null || seen.containsKey(grp)) continue;
			Object inst = cd.kestrel$cooldowns().get(grp);
			if (inst == null || ((CooldownEnd) inst).kestrel$end() <= cd.kestrel$tick()) continue;
			Row r = rows.computeIfAbsent(grp, k -> new Row());
			r.stack = s.copy();
			r.name = name(s);
			r.end = ((CooldownEnd) inst).kestrel$end();
			r.active = true;
			seen.put(grp, r);
		}
		for (Map.Entry<Identifier, Row> e : rows.entrySet()) if (!seen.containsKey(e.getKey())) e.getValue().active = false;
		// gone once it has faded all the way out
		rows.values().removeIf(r -> !r.active && r.alpha < 0.01f);
		scrollFrom.keySet().removeIf(k -> k instanceof Row r && !rows.containsValue(r));
	}

	// server items like to have decorations around the name ("» Pearl «"), the row only wants the name
	private static String name(ItemStack s) {
		String n = ChatFormatting.stripFormatting(s.getHoverName().getString());
		n = n.replaceAll("\\s+", " ").replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "").trim();
		return n.isEmpty() ? s.getItem().getName(s.getItem().getDefaultInstance()).getString() : n;
	}

	private float secondsLeft(int end) {
		Minecraft mc = Minecraft.getInstance();
		CooldownAccessor cd = (CooldownAccessor) mc.player.getCooldowns();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return Math.max(0, end - (cd.kestrel$tick() + pt)) / 20;
	}

	private static String time(float s) {
		return String.format(Locale.ROOT, "%.1fs", s);
	}

	// red in the last half second, orange up to a second and a half, the accent before that
	private static int timeColor(float s) {
		return s <= 0.5f ? 0xFFFF5A5A : s <= 1.5f ? 0xFFFFB347 : Ui.accent();
	}

	// ---------- what to draw ----------

	private record Line(ItemStack stack, String name, float secs, float alpha, float off, Object key) {}

	private boolean previewing() {
		return editing && rows.isEmpty();
	}

	// rows as they should look this frame. in the editor with nothing cooling down, the sample rows instead
	private List<Line> lines() {
		List<Line> out = new ArrayList<>();
		if (previewing()) {
			long t = Util.getMillis() - previewFrom, step = t / STEP, local = t % STEP;
			float blend = local <= HOLD ? 0 : Mth.clamp((local - HOLD) / (float) FADE, 0, 1);
			blend = blend * blend * (3 - 2 * blend);
			List<ItemStack> all = samples();
			for (int i = 0; i < 2; i++) {
				int a = (int) ((step + i) % all.size()), b = (a + 1) % all.size();
				out.add(new Line(all.get(a), name(all.get(a)), 0.8f + a % 5 * 0.4f, 1 - blend, 0, i));
				if (blend > 0.001f) out.add(new Line(all.get(b), name(all.get(b)), 0.8f + b % 5 * 0.4f, blend, 0, i));
			}
			return out;
		}
		for (Row r : rows.values()) out.add(new Line(r.stack, r.name, secondsLeft(r.end), r.alpha, r.off, r));
		return out;
	}

	private static List<ItemStack> samples() {
		if (samples == null) {
			samples = List.of(new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.DIAMOND_SWORD),
				new ItemStack(Items.BOW), new ItemStack(Items.SHIELD), new ItemStack(Items.COBWEB));
		}
		return samples;
	}

	private float targetW(List<Line> ls) {
		float w = MIN_W;
		for (Line l : ls) w = Math.max(w, PAD * 2 + ICON + GAP + width(l.name, LIGHT, TEXT) + SPACE + width(time(l.secs), LIGHT, TEXT));
		return w;
	}

	private int rowCount() {
		return previewing() ? 2 : rows.size();
	}

	// eases toward the size the rows want, once a frame (render), so the editor's measuring doesn't speed it up
	private void animate(List<Line> ls) {
		long now = Util.getMillis();
		float k = Mth.clamp((now - lastFrame) / 1000f * 12, 0, 1);
		lastFrame = now;
		float s = size.get().floatValue();
		int n = rowCount();
		float tw = targetW(ls) * s, th = n == 0 ? 0 : (PAD * 2 + n * LINE + (n - 1) * ROW_GAP) * s;
		if (curW < 0) {
			curW = tw;
			curH = th;
		}
		curW += (tw - curW) * k;
		curH += (th - curH) * k;
		for (Row r : rows.values()) {
			r.alpha += ((r.active ? 1 : 0) - r.alpha) * k;
			r.off += ((r.active ? 0 : -6) - r.off) * k;
		}
	}

	@Override
	public boolean visible() {
		return previewing() || !rows.isEmpty() || curH > 0.5f;
	}

	@Override
	protected int contentW() {
		return Math.round(Math.max(curW, MIN_W * size.get().floatValue()));
	}

	@Override
	protected int contentH() {
		return Math.max(1, Math.round(curH));
	}

	@Override
	protected float radius() {
		return 6 * size.get().floatValue();
	}

	@Override
	protected boolean edge() {
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int sw, int sh) {
		if (editing && previewFrom == 0) previewFrom = Util.getMillis();
		if (!editing) previewFrom = 0;
		animate(lines());
		super.render(g, sw, sh);
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		float s = size.get().floatValue();
		float w = contentW() / s;
		g.enableScissor(0, 0, contentW(), contentH());
		g.pose().pushMatrix();
		g.pose().scale(s, s);
		int i = 0;
		for (Line l : lines()) {
			// in the preview two lines share a row while they crossfade
			int row = l.key instanceof Integer k ? k : i++;
			if (l.alpha <= 0.001f) continue;
			float base = PAD + LINE * 0.8f + row * (LINE + ROW_GAP) + l.off;
			if (l.alpha > 0.35f) g.item(l.stack, Math.round(PAD), Math.round(base - ICON / 2 - 5));
			String t = time(l.secs);
			float tx = w - PAD - width(t, LIGHT, TEXT), nx = PAD + ICON + GAP;
			String n = scrolled(l.name, tx - nx - SPACE, l.key);
			draw(g, n, LIGHT, nx, base, ARGB.multiplyAlpha(WHITE, l.alpha), TEXT);
			draw(g, t, LIGHT, tx, base, ARGB.multiplyAlpha(timeColor(l.secs), l.alpha), TEXT);
		}
		g.pose().popMatrix();
		g.disableScissor();
	}

	// long names scroll: a second still, then drop letters off the front, then start over
	private final Map<Object, Long> scrollFrom = new LinkedHashMap<>();

	private String scrolled(String n, float max, Object key) {
		float full = width(n, LIGHT, TEXT);
		if (full <= max) {
			scrollFrom.remove(key);
			return n;
		}
		long now = Util.getMillis(), from = scrollFrom.computeIfAbsent(key, k -> now);
		long cycle = (long) (1000 + full * 30 + 500), pos = (now - from) % cycle;
		float off = pos > 1000 ? (pos - 1000) / 30f : 0;
		int start = 0;
		while (start < n.length() - 1 && width(n.substring(start), LIGHT, TEXT) + off >= max) start++;
		return n.substring(start);
	}
}
