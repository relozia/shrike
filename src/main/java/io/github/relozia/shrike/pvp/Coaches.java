package io.github.relozia.shrike.pvp;

import io.github.relozia.shrike.hud.HudWidget;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.setting.Setting;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

// the jump reset and swap coaches, each a small panel you can move in the hud editor. results only, they show up
// after the fact so they can't be used as a cue, which is the line between practice tool and cheat
// issues:
//   - the swap coach can't see a spear click the client refused, so a swing that never charged reads as no click
//   - jump timing comes from the jump key going down, a jump the game ignores (mid air) still counts as the press
public final class Coaches {
	private Coaches() {}

	static final int GREEN = 0xFF5BE37D, YELLOW = 0xFFF2C94C, RED = 0xFFEB5757, DIM = 0xB3F7FAFC;

	// the shared panel: a dark 6px rounded box, the result on top and a dimmer line or two under it
	abstract static class Panel extends HudWidget {
		final Setting.Slider shown = add(new Setting.Slider("Result Display Time", 2.5, 1, 6, 0.5, "s"));
		final Setting.Toggle stats = add(new Setting.Toggle("Show Session Stats", true));
		final Setting.Slider size = add(new Setting.Slider("Size", 1, 0.6, 2, 0.05, "x"));
		String title = "", detail = "";
		int color;
		long at = -100_000;

		Panel(String id, String name, char icon, String desc, float fy) {
			super(id, name, Category.COMBAT, icon, desc, 0.5f, fy);
		}

		@Override
		protected Look look() {
			return Look.NONE;
		}

		void result(String t, String d, int c) {
			title = t;
			detail = d;
			color = c;
			at = Util.getMillis();
		}

		// 1 while a result is up, fading over its last 300ms
		float fade() {
			long age = Util.getMillis() - at, life = Math.round(shown.get() * 1000);
			return age > life ? 0 : age < life - 300 ? 1 : (life - age) / 300f;
		}

		abstract String statsLine();

		float rawW() {
			return 150;
		}

		float rawH() {
			return stats.on() ? 34 : 20;
		}

		@Override protected int contentW() { return Math.round(rawW() * size.get().floatValue()); }
		@Override protected int contentH() { return Math.round(rawH() * size.get().floatValue()); }

		void box(GuiGraphicsExtractor g, float a) {
			g.pose().scale(size.get().floatValue(), size.get().floatValue());
			box(g, rawW(), rawH(), 6, a, false);
		}

		void centered(GuiGraphicsExtractor g, String s, boolean big, float base, int c) {
			if (big) draw(g, s, HUD, (rawW() - width(s, HUD, 11)) / 2, base, c, 11);
			else draw(g, s, LIGHT, (rawW() - width(s, LIGHT, 8.5f)) / 2, base, c, 8.5f);
		}
	}

	// ---------- jump reset ----------

	public static class JumpReset extends Panel {
		private final Setting.Toggle keep = add(new Setting.Toggle("Keep Stats On Screen", false));
		private int tick, hitAt = -100, jumpAt = -100, lastHurt;
		private boolean jumpWas, graded = true, groundBefore, groundAtHit;
		private int attempts, perfect, streak, best;

		public JumpReset() {
			super("jump_reset_coach", "Jump Reset Coach", '\ue1e4', "After you take knockback, shows how well timed your jump was.", 0.62f);
		}

		@Override
		protected void enable() {
			attempts = perfect = streak = best = 0;
		}

		private void grade(String text, int c, boolean hit) {
			attempts++;
			if (hit) {
				perfect++;
				best = Math.max(best, ++streak);
			} else {
				streak = 0;
			}
			result(text, "", c);
		}

		// perfect is the jump on the tick the hit lands, 1 to 3 ticks before is early, 1 or 2 after is late. no jump
		// at all while you were standing is a miss, a hit in the air isn't a jump reset chance and isn't graded
		@Override
		public void tick(Minecraft mc) {
			Player p = mc.player;
			if (p == null) return;
			tick++;
			boolean jump = mc.options.keyJump.isDown();
			if (jump && !jumpWas) jumpAt = tick;
			jumpWas = jump;
			if (p.hurtTime > lastHurt) {
				int early = tick - jumpAt;
				if (early >= 1 && early <= 3) {
					grade(early + " tick" + (early > 1 ? "s" : "") + " early", YELLOW, false);
					graded = true;
				} else {
					hitAt = tick;
					groundAtHit = groundBefore;
					graded = false;
				}
			}
			lastHurt = p.hurtTime;
			// the knockback already lifted us by the end of the tick the hit landed in, standing on the
			// ground is a question about the tick before
			groundBefore = p.onGround();
			if (graded || tick - hitAt < 2 && jumpAt < hitAt) return;
			int d = jumpAt - hitAt;
			if (d == 0) grade("Perfect", GREEN, true);
			else if (d >= 1 && d <= 2) grade(d + " tick" + (d > 1 ? "s" : "") + " late", YELLOW, false);
			else if (groundAtHit) grade("No jump", RED, false);
			graded = true;
		}

		@Override
		String statsLine() {
			if (attempts == 0) return "No attempts yet";
			return perfect + "/" + attempts + " (" + Math.round(perfect * 100f / attempts) + "%)  streak " + streak + "  best " + best;
		}

		@Override
		public boolean visible() {
			return editing || fade() > 0 || keep.on() && stats.on();
		}

		@Override
		protected void content(GuiGraphicsExtractor g) {
			float f = fade(), a = editing || keep.on() && stats.on() ? Math.max(f, 1) : f;
			box(g, a);
			boolean none = f <= 0;
			centered(g, none ? "Jump Reset" : title, true, 13, ARGB.multiplyAlpha(none ? DIM : color, a));
			if (stats.on()) centered(g, statsLine(), false, 27, ARGB.multiplyAlpha(DIM, a));
		}
	}

	// ---------- swap helper ----------

	public static class SwapHelper extends Panel {
		static SwapHelper INSTANCE;

		private final Setting.Toggle mace = add(new Setting.Toggle("Mace / Breach Swaps", true));
		private final Setting.Toggle shield = add(new Setting.Toggle("Shield Break Swaps", true));
		private final Setting.Toggle slams = add(new Setting.Toggle("Stun Slams", true));
		private final Setting.Toggle lunge = add(new Setting.Toggle("Lunge Swaps", true));
		private final Setting.Toggle swapBack = add(new Setting.Toggle("Grade Lunge Swap Back", false));
		private final Setting.Toggle server = add(new Setting.Toggle("Check Server Result", true));
		private final Setting.Toggle gap = add(new Setting.Toggle("Show Input Gap (ms)", true));
		private final Setting.Toggle players = add(new Setting.Toggle("Players Only", false));

		private enum Kind { MACE, SPEAR, AXE, OTHER }

		private int tick;
		private Kind held = Kind.OTHER;
		private int changedAt = -100, axeBlockAt = -100, lungeAt = -100;
		private long swapMs;
		// a click with the old item, waiting to see whether the new one got a hit in after it
		private int lateClickAt = -100;
		private Kind lateClickKind;
		private boolean lateBlocking;
		// a graded hit waiting for the server's sound to say what really happened
		private String pending;
		private long pendingAt;
		private final Map<String, int[]> tally = new LinkedHashMap<>();

		public SwapHelper() {
			super("swap_helper", "Swap Helper", '\ue146', "Grades swaps to a mace, axe or spear around a hit as on time, early or late.", 0.7f);
			INSTANCE = this;
			for (String k : new String[] {"Mace", "Axe", "Slam", "Lunge", "Back"}) tally.put(k, new int[2]);
			AttackEntityCallback.EVENT.register((pl, level, hand, e, hit) -> {
				if (on() && level.isClientSide() && e instanceof LivingEntity l) attack(l);
				return InteractionResult.PASS;
			});
		}

		private static Kind kind(ItemStack s) {
			if (s.is(Items.MACE)) return Kind.MACE;
			if (s.has(DataComponents.PIERCING_WEAPON) || s.is(ItemTags.SPEARS)) return Kind.SPEAR;
			if (s.is(ItemTags.AXES)) return Kind.AXE;
			return Kind.OTHER;
		}

		private void grade(String what, String res, String d, int c) {
			tally.get(what)[1]++;
			if (c == GREEN) tally.get(what)[0]++;
			String det = d;
			if (gap.on() && c == GREEN && !what.equals("Back")) {
				long ms = Math.abs(Util.getMillis() - swapMs);
				if (ms <= 1000) det += " - " + ms + " ms between swap and click";
			}
			String label = what.equals("Back") ? "Swap back" : what;
			result(label + ": " + res, det, c);
			if (server.on() && (what.equals("Mace") || what.equals("Axe") || what.equals("Slam"))) {
				pending = what;
				pendingAt = Util.getMillis();
			}
		}

		private void attack(LivingEntity t) {
			Minecraft mc = Minecraft.getInstance();
			if (players.on() && !(t instanceof Player)) return;
			Kind now = kind(mc.player.getMainHandItem());
			boolean blocking = t.isBlocking();
			// clicks run mid tick, before this module's tick: held is still last tick's item, so a different item in
			// hand now means the swap happened this very tick
			boolean swappedNow = now != held;
			int since = tick - changedAt + 1;
			if (now == Kind.MACE) {
				// a mace hit a tick or so after an axe broke the shield: a stun slam
				int sinceAxe = tick - axeBlockAt;
				if (slams.on() && sinceAxe >= 1 && sinceAxe <= 20) {
					if (mc.player.fallDistance <= 1.5) grade("Slam", "No smash", "Not falling far enough for a smash", RED);
					else if (sinceAxe == 1) grade("Slam", "Perfect", "Mace 1 tick after the axe (fastest possible)", GREEN);
					else grade("Slam", "Landed", "Mace " + sinceAxe + " ticks after the axe (fastest is 1)", GREEN);
					lateClickAt = -100;
					return;
				}
				if (!mace.on()) return;
				String ctx = (mc.player.fallDistance > 1.5 ? "Smash" : "Breach") + (held == Kind.SPEAR ? " from spear (spear reach)" : "");
				if (swappedNow) grade("Mace", "On time", ctx, GREEN);
				else if (since >= 1 && since <= 3) grade("Mace", since + " ticks early", "Swapped before the click - charge reset", YELLOW);
				lateClickAt = -100;
			} else if (now == Kind.AXE && blocking) {
				axeBlockAt = tick;
				if (!shield.on()) return;
				if (swappedNow) grade("Axe", "On time", "Target was blocking", GREEN);
				else if (since >= 1 && since <= 3) grade("Axe", since + " ticks early", "Swapped before the click - charge reset", YELLOW);
				lateClickAt = -100;
			} else {
				// a click with something else, if a mace or axe shows up in the next few ticks it was the wrong order
				lateClickAt = tick;
				lateClickKind = now;
				lateBlocking = blocking;
			}
		}

		// MultiPlayerGameMode.piercingAttack, a spear jab
		public static void jab() {
			SwapHelper s = INSTANCE;
			if (s == null || !s.on() || !s.lunge.on()) return;
			Minecraft mc = Minecraft.getInstance();
			ItemStack st = mc.player.getMainHandItem();
			boolean lunging = st.getEnchantments().keySet().stream().anyMatch(h -> h.is(Enchantments.LUNGE));
			if (!lunging) return;
			if (s.held != Kind.SPEAR) s.grade("Lunge", "On time", "Jab used the carrier item's charge", GREEN);
			s.lungeAt = s.tick;
		}

		// what the server played back after a graded hit, within a second
		public static void sound(Identifier id) {
			SwapHelper s = INSTANCE;
			if (s == null || s.pending == null || Util.getMillis() - s.pendingAt > 1000) return;
			String p = id.getPath(), extra = null;
			boolean strong = p.equals("entity.player.attack.strong") || p.equals("entity.player.attack.crit")
				|| p.equals("entity.player.attack.knockback") || p.equals("entity.player.attack.sweep");
			if (p.startsWith("item.mace.smash")) extra = s.pending.equals("Slam") ? "shield disabled + smash landed" : "smash landed";
			else if (p.equals("item.shield.break")) extra = "shield disabled";
			else if (strong) extra = s.pending.equals("Axe") ? "shield disabled, full-strength hit" : "full-strength hit";
			else if (p.equals("entity.player.attack.weak")) extra = s.color == GREEN ? "weak hit (server may block swaps)" : "weak hit";
			if (extra == null) return;
			s.detail += "  |  Server: " + extra;
			s.pending = null;
		}

		@Override
		public void tick(Minecraft mc) {
			if (mc.player == null) return;
			tick++;
			Kind now = kind(mc.player.getMainHandItem());
			if (now != held) {
				changedAt = tick;
				swapMs = Util.getMillis();
				// swapping off the spear after a lunge jab: the tick right after is perfect
				int sinceLunge = tick - lungeAt;
				if (swapBack.on() && held == Kind.SPEAR && sinceLunge >= 1 && sinceLunge <= 6) {
					if (sinceLunge == 1) grade("Back", "Perfect", "Swapped back the tick after the jab", GREEN);
					else grade("Back", (sinceLunge - 1) + " tick" + (sinceLunge > 2 ? "s" : "") + " slow", "Swap back right after the jab", YELLOW);
					lungeAt = -100;
				}
				held = now;
			}
			// clicked with the old item, then swapped: if no hit with the new one followed, the hit used the old one
			if (lateClickAt >= 0 && tick - lateClickAt >= 2) {
				int d = changedAt - lateClickAt;
				boolean toMace = held == Kind.MACE && mace.on(), toAxe = held == Kind.AXE && lateBlocking && shield.on();
				if (d >= 0 && d <= 3 && held != lateClickKind && (toMace || toAxe)) {
					grade(toMace ? "Mace" : "Axe", d + " tick" + (d == 1 ? "" : "s") + " late", "Clicked before swapping - hit used the old item", RED);
				}
				lateClickAt = -100;
			}
		}

		@Override
		String statsLine() {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, int[]> e : tally.entrySet()) {
				if (e.getValue()[1] == 0) continue;
				if (!sb.isEmpty()) sb.append("  ");
				sb.append(e.getKey()).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]);
			}
			return sb.isEmpty() ? "No swaps yet this session" : sb.toString();
		}

		@Override
		float rawW() {
			float m = Math.max(width(detail.isEmpty() ? "Results appear here after a swap" : detail, LIGHT, 8.5f), width(statsLine(), LIGHT, 8.5f));
			return Math.max(170, m + 16);
		}

		@Override
		float rawH() {
			return stats.on() ? 44 : 32;
		}

		@Override
		public boolean visible() {
			return editing || fade() > 0;
		}

		@Override
		protected void content(GuiGraphicsExtractor g) {
			float f = fade(), a = editing ? 1 : f;
			box(g, a);
			boolean none = f <= 0;
			centered(g, none ? "Swap Helper" : title, true, 13, ARGB.multiplyAlpha(none ? 0xBFF7FAFC : color, a));
			centered(g, none || detail.isEmpty() ? "Results appear here after a swap" : detail, false, 26, ARGB.multiplyAlpha(DIM, a));
			if (stats.on()) centered(g, statsLine(), false, 38, ARGB.multiplyAlpha(DIM, a));
		}

		@Override
		protected void enable() {
			for (int[] t : tally.values()) t[0] = t[1] = 0;
		}
	}
}
