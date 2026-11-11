package io.github.relozia.kestrel.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.chat.Players;
import io.github.relozia.kestrel.config.Config;
import io.github.relozia.kestrel.gui.Notice;
import io.github.relozia.kestrel.gui.Ui;
import io.github.relozia.kestrel.hud.HudWidget;
import io.github.relozia.kestrel.hud.SimpleWidgets;
import io.github.relozia.kestrel.mixin.ButtonAccessor;
import io.github.relozia.kestrel.mixin.CooldownAccessor;
import io.github.relozia.kestrel.mixin.PauseScreenAccessor;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import io.github.relozia.kestrel.track.Combat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.food.FoodProperties;

// pvp tools that aren't drawn in the world: coaches, trackers, inventory helpers
public final class PvpTools {
	private PvpTools() {}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, Kestrel.id("low_health"), (g, dt) -> LowHealth.INSTANCE.edges(g));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Kestrel.id("hotbar_locks"), (g, dt) -> LockSlot.INSTANCE.hotbar(g));
	}

	// ---------- low health ----------

	public static class LowHealth extends Module {
		static LowHealth INSTANCE;

		private final Setting.Slider below = add(new Setting.Slider("Threshold", 4, 1, 10, 0.5, " hearts"));
		private final Setting.Toggle absorption = add(new Setting.Toggle("Include Absorption", true));
		private final Setting.Toggle edges = add(new Setting.Toggle("Red Screen Edges", true));
		private final Setting.Toggle chat = add(new Setting.Toggle("Chat Notification", true));
		private final Setting.Toggle sound = add(new Setting.Toggle("Sound Notification", true));
		private final Setting.Slider cooldown = add(new Setting.Slider("Cooldown", 10, 1, 30, 1, "s"));
		private boolean armed = true;
		private long firedAt = -100_000;

		public LowHealth() {
			super("Low Health", Category.COMBAT, '\ue36e', "Red screen edges and a warning sound when your health gets low.");
			INSTANCE = this;
		}

		private float hp(Player p) {
			return p.getHealth() + (absorption.on() ? p.getAbsorptionAmount() : 0);
		}

		private boolean isLow(Player p) {
			return p.getHealth() > 0 && hp(p) <= below.get() * 2;
		}

		// once per dip: it fires going under the line, and only fires again after you've been a heart above it
		// and the cooldown is over, so bouncing around the line doesn't spam
		@Override
		public void tick(Minecraft mc) {
			if (mc.player == null) return;
			float hp = hp(mc.player);
			if (hp >= below.get() * 2 + 2) armed = true;
			if (!armed || !isLow(mc.player) || Util.getMillis() - firedAt < cooldown.get() * 1000) return;
			armed = false;
			firedAt = Util.getMillis();
			if (sound.on()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f, 1));
			if (chat.on()) {
				mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[Low Health] ").withStyle(ChatFormatting.RED)
					.append(Component.literal(String.format(Locale.ROOT, "Health low: %.1f hearts", hp / 2)).withStyle(ChatFormatting.GRAY)));
			}
		}

		// pulses about once a second, stronger the lower you are. sides are drawn as columns, the gui only
		// has vertical gradients
		void edges(GuiGraphicsExtractor g) {
			Minecraft mc = Minecraft.getInstance();
			if (!on() || !edges.on() || mc.player == null || !isLow(mc.player) || mc.gui.hud.isHidden()) return;
			float depth = 1 - hp(mc.player) / (float) (below.get() * 2);
			float pulse = (Mth.sin(Util.getMillis() / 1000f * Mth.TWO_PI) + 1) / 2;
			int a = Math.round((0.25f + 0.35f * depth) * (0.6f + 0.4f * pulse) * 255);
			int w = g.guiWidth(), h = g.guiHeight(), e = 40;
			g.fillGradient(0, 0, w, e, ARGB.color(a, 0xC81E1E), 0);
			g.fillGradient(0, h - e, w, h, 0, ARGB.color(a, 0xC81E1E));
			for (int i = 0; i < e; i += 2) {
				int c = ARGB.color(Math.round(a * (1 - i / (float) e)), 0xC81E1E);
				g.fill(i, 0, i + 2, h, c);
				g.fill(w - i - 2, 0, w - i, h, c);
			}
		}
	}

	// ---------- totems ----------

	public static class Totems extends Module {
		static Totems INSTANCE;
		private final Map<String, Integer> pops = new HashMap<>();
		private final Setting.Toggle notify = add(new Setting.Toggle("Notifications", true));

		public Totems() {
			super("Totem Tracker", Category.COMBAT, '\ue0f2', "Says in chat when someone pops a totem, and how many so far.");
			INSTANCE = this;
			ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> pops.clear());
		}

		// entity event 35 is the totem animation, sent to everyone nearby
		public static void popped(Player p) {
			Totems t = INSTANCE;
			if (t == null || !t.on()) return;
			String name = p.getGameProfile().name();
			int n = t.pops.merge(name, 1, Integer::sum);
			boolean me = p == Minecraft.getInstance().player;
			Component who = me ? Component.literal("You") : Players.Friends.is(name) ? Players.Friends.paint(name) : Component.literal(name).withStyle(ChatFormatting.YELLOW);
			Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.empty().append(who)
				.append(Component.literal(" popped a totem ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("(" + n + ")").withStyle(ChatFormatting.GOLD)));
			// a toast for everyone else's pops, yours you already know about
			if (t.notify.on() && !me) {
				Notice.show("totems", p.isInvisible() ? "Someone" : name, Component.literal("popped " + n + " totem" + (n == 1 ? "" : "s")), 3200);
			}
		}

		// a death resets their count, totems in a new life are a new story
		@Override
		public void tick(Minecraft mc) {
			if (mc.level == null) return;
			for (Player p : mc.level.players()) if (p.isDeadOrDying()) pops.remove(p.getGameProfile().name());
		}
	}

	// ---------- pvp info ----------

	// this session's hits, accuracy, combo, hits taken, arrows and deaths, and right after a fight a
	// "Fight Summary" card for a few seconds instead
	public static class PvpInfo extends HudWidget {
		static PvpInfo INSTANCE;

		private final Setting.Color color = add(new Setting.Color("Color", 0xF5F7FAFC, 0xF5F7FAFC, 0xFFE0703A, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
		private final Setting.Toggle playersOnly = add(new Setting.Toggle("Players Only", true));
		private final Setting.Toggle session = add(new Setting.Toggle("Show Session Stats", true));
		private final Setting.Toggle fightCard = add(new Setting.Toggle("Fight Summary", true));
		private final Setting.Slider summaryTime = add(new Setting.Slider("Summary Time", 8, 3, 20, 1, "s"));
		private final Setting.Toggle chatLine = add(new Setting.Toggle("Summary In Chat", false));

		private int landed0, attempts0, bestCombo, arrowsShot, arrowsHit, hitsTaken, deaths;
		private float taken, lastHealth = -1;
		private long lastAction, fightStart, fightEnd = -1;
		private boolean fighting, drawing, dead, died;
		// what the session counters were when the fight started, the summary is the difference
		private int fHits0, fAttempts0, fArrows0, fShots0, fTaken0, fCombo;
		private float fHurt0;
		private List<String[]> summary = List.of();

		public PvpInfo() {
			super("PvP Info", '\ue2a2', "Your hits, accuracy, best combo, arrows and damage taken, and a summary after each fight.", 0.01f, 0.3f);
			INSTANCE = this;
			ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> reset());
		}

		@Override
		protected boolean edge() {
			return true;
		}

		private void reset() {
			landed0 = Combat.landed();
			attempts0 = Combat.attempts();
			bestCombo = arrowsShot = arrowsHit = hitsTaken = deaths = 0;
			taken = 0;
			lastHealth = -1;
			fighting = false;
			fightEnd = -1;
		}

		public static void arrowHit() {
			if (INSTANCE != null) {
				INSTANCE.arrowsHit++;
				INSTANCE.lastAction = Util.getMillis();
			}
		}

		@Override
		public void tick(Minecraft mc) {
			Player p = mc.player;
			if (p == null) return;
			long now = Util.getMillis();
			bestCombo = Math.max(bestCombo, Combat.combo());
			if (fighting) fCombo = Math.max(fCombo, Combat.combo());
			if (lastHealth >= 0 && p.getHealth() < lastHealth && p.getHealth() > 0) {
				taken += lastHealth - p.getHealth();
				hitsTaken++;
				if (fighting) lastAction = now;
			}
			lastHealth = p.getHealth();
			boolean nowDead = p.isDeadOrDying();
			if (nowDead && !dead) {
				deaths++;
				died = true;
			}
			dead = nowDead;
			// an arrow counts when you let go of a drawn bow, the release is the shot
			boolean bow = p.isUsingItem() && p.getUseItem().is(Items.BOW);
			if (drawing && !bow) arrowsShot++;
			drawing = bow && p.getTicksUsingItem() >= 3;
			var t = Combat.target(500);
			if (t != null && (!playersOnly.on() || t instanceof Player)) lastAction = now;
			if (!fighting && now - lastAction < 500) {
				fighting = true;
				died = false;
				fightStart = now;
				fHits0 = Combat.landed();
				fAttempts0 = Combat.attempts();
				fArrows0 = arrowsHit;
				fShots0 = arrowsShot;
				fTaken0 = hitsTaken;
				fHurt0 = taken;
				fCombo = 0;
			} else if (fighting && (now - lastAction > 8000 || died)) {
				// eight quiet seconds, or dying, ends a fight
				fighting = false;
				end(mc, now);
			}
		}

		private static String acc(int hits, int swings) {
			return swings == 0 ? "-" : Math.round(hits * 100f / Math.max(swings, hits)) + "%";
		}

		private void end(Minecraft mc, long now) {
			int hits = Combat.landed() - fHits0, swings = Combat.attempts() - fAttempts0, arrows = arrowsHit - fArrows0, shots = arrowsShot - fShots0;
			float hurt = (taken - fHurt0) / 2;
			if (hits == 0 && arrows == 0 && hurt == 0) return;
			List<String[]> l = new ArrayList<>();
			l.add(new String[] {died ? "Fight (you died)" : "Fight Summary", SimpleWidgets.clock(now - fightStart)});
			l.add(new String[] {"Hits", hits + " (" + acc(hits, swings) + ")"});
			l.add(new String[] {"Best Combo", Integer.toString(fCombo)});
			l.add(new String[] {"Hits Taken", Integer.toString(hitsTaken - fTaken0)});
			l.add(new String[] {"Damage Taken", String.format(Locale.ROOT, "%.1f hearts", hurt)});
			if (arrows > 0 || shots > 0) l.add(new String[] {"Arrows", arrows + "/" + shots});
			summary = l;
			fightEnd = now;
			if (chatLine.on()) {
				String s = String.format(Locale.ROOT, "Fight over: %d hit%s, took %.1f hearts", hits, hits == 1 ? "" : "s", hurt);
				mc.gui.hud.getChat().addClientSystemMessage(Component.literal(s).withStyle(ChatFormatting.GRAY));
			}
		}

		private List<String[]> rows() {
			if (fightCard.on() && fightEnd >= 0 && Util.getMillis() - fightEnd < summaryTime.get() * 1000 && !editing) return summary;
			if (!session.on() && !editing) return List.of();
			int l = Combat.landed() - landed0, a = Combat.attempts() - attempts0;
			List<String[]> out = new ArrayList<>();
			out.add(new String[] {"Hits", l + " (" + acc(l, a) + ")"});
			out.add(new String[] {"Best Combo", Integer.toString(bestCombo)});
			out.add(new String[] {"Hits Taken", Integer.toString(hitsTaken)});
			out.add(new String[] {"Arrows", arrowsHit + "/" + arrowsShot});
			out.add(new String[] {"Deaths", Integer.toString(deaths)});
			return out;
		}

		@Override
		public boolean visible() {
			return !rows().isEmpty();
		}

		@Override protected int contentW() { return Math.round(cardW(rows())); }
		@Override protected int contentH() { return Math.round(cardH(rows().size())); }

		@Override
		protected void content(GuiGraphicsExtractor g) {
			cardRows(g, rows(), contentW(), color.get());
		}
	}

	// ---------- cooldowns ----------

	// seconds left right on the slot, small white text in the top left corner. the list of everything cooling
	// down is its own widget, hud/CooldownHudWidget
	public static class Cooldowns extends Module {
		static Cooldowns INSTANCE;

		private final Setting.Choice place = add(new Setting.Choice("Position", "Top Left", "Top Left", "Center"));
		private final Setting.Color color = add(new Setting.Color("Text Color", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE0703A, 0xFFF5A524, 0xFF5B8DEF, 0xFFE5484D));

		public Cooldowns() {
			super("Cooldowns", Category.COMBAT, '\ue296', "Seconds left on an item's cooldown, right on its slot.");
			INSTANCE = this;
		}

		// ticks left for this item with the partial tick, so the tenths count down smoothly instead of in steps
		public static float left(Player p, ItemStack s) {
			var cd = (CooldownAccessor) p.getCooldowns();
			Object inst = cd.kestrel$cooldowns().get(p.getCooldowns().getCooldownGroup(s));
			if (inst == null) return 0;
			float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			return Math.max(0, ((CooldownEnd) inst).kestrel$end() - (cd.kestrel$tick() + pt));
		}

		public static void slot(GuiGraphicsExtractor g, ItemStack s, int x, int y) {
			Cooldowns c = INSTANCE;
			Player p = Minecraft.getInstance().player;
			if (c == null || !c.on() || p == null || s.isEmpty()) return;
			float t = left(p, s) / 20;
			if (t <= 0) return;
			// whole seconds from 10 up, "9.9s" is as wide as a slot gets
			String txt = t >= 9.95f ? (int) Math.ceil(t) + "s" : String.format(Locale.ROOT, "%.1fs", t);
			var f = Minecraft.getInstance().font;
			float k = Mth.clamp(14f / f.width(txt), 0.62f, 0.87f);
			g.pose().pushMatrix();
			// centered is bigger, it's the only thing on the slot then
			if (c.place.get().equals("Center")) {
				k = Mth.clamp(16f / f.width(txt), 0.7f, 1);
				g.pose().translate(x + 8 - f.width(txt) * k / 2, y + 8 - 4 * k);
			} else {
				g.pose().translate(x + 1, y + 1);
			}
			g.pose().scale(k, k);
			g.text(f, txt, 0, 0, c.color.get(), true);
			g.pose().popMatrix();
		}
	}

	// ---------- heal highlight ----------

	// heal helper and potion highlight in one module: a pulsing highlight on what to eat or drink right now, the
	// best plain food, and every splash potion tinted by whether it helps or hurts
	public static class HealHighlight extends Module {
		static HealHighlight INSTANCE;

		private final Setting.Toggle suggest = add(new Setting.Toggle("Heal Suggestion", true));
		private final Setting.Color suggestColor = add(new Setting.Color("Suggestion Color", 0xFF8F5BFF, 0xFF8F5BFF, 0xFFE5484D, 0xFF57D163, 0xFFF5A524));
		private final Setting.Toggle autoScan = add(new Setting.Toggle("Auto Scan Inventory", true));
		private final Setting.Toggle food = add(new Setting.Toggle("Best Food", true));
		private final Setting.Color color = add(new Setting.Color("Food Color", 0xFFE5484D, 0xFFE5484D, 0xFF57D163, 0xFFF5A524, 0xFF5B8DEF));
		private final Setting.Toggle potions = add(new Setting.Toggle("Potion Highlight", true));
		private final Setting.Color good = add(new Setting.Color("Beneficial Color", 0x6637C871, 0xFF37C871, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
		private final Setting.Color bad = add(new Setting.Color("Harmful Color", 0x66D35A5A, 0xFFD35A5A, 0xFFF5A524, 0xFF8B5CF6).withAlpha());

		public HealHighlight() {
			super("Heal Highlight", Category.COMBAT, '\ue0d6', "Highlights what to eat or drink to heal right now, the best food, and good or bad splash potions.");
			INSTANCE = this;
		}

		private static boolean has(ItemStack s, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e) {
			PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
			if (pc == null) return false;
			for (var x : pc.getAllEffects()) if (x.getEffect().is(e)) return true;
			return false;
		}

		private int slots() {
			return autoScan.on() ? 36 : 9;
		}

		// worst case first: an enchanted apple when health is bad, then an instant health potion, a golden carrot
		// when it's hunger not health, a golden apple, and the enchanted one only when there's no plain apple
		// left. skips anything still on cooldown, suggesting something you can't eat yet is worse than nothing
		private ItemStack suggestion(Player p) {
			float hp = p.getHealth() + p.getAbsorptionAmount();
			Inventory inv = p.getInventory();
			ItemStack egap = ItemStack.EMPTY, pot = ItemStack.EMPTY, carrot = ItemStack.EMPTY, gap = ItemStack.EMPTY;
			for (int i = 0; i < slots(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.isEmpty() || p.getCooldowns().isOnCooldown(s)) continue;
				if (s.is(Items.ENCHANTED_GOLDEN_APPLE) && egap.isEmpty()) egap = s;
				else if (s.is(Items.GOLDEN_APPLE) && gap.isEmpty()) gap = s;
				else if (s.is(Items.GOLDEN_CARROT) && carrot.isEmpty()) carrot = s;
				else if (pot.isEmpty() && (s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) && has(s, MobEffects.INSTANT_HEALTH)) pot = s;
			}
			if (hp <= 6 && !egap.isEmpty()) return egap;
			if (hp <= 10 && !pot.isEmpty()) return pot;
			if (p.getFoodData().getFoodLevel() <= 16 && !carrot.isEmpty()) return carrot;
			if (hp <= 15 && !gap.isEmpty()) return gap;
			if (hp <= 15 && gap.isEmpty() && !egap.isEmpty()) return egap;
			return ItemStack.EMPTY;
		}

		// golden apples when hurt, otherwise whatever fills the most hunger without wasting much
		private static ItemStack bestFood(Player p) {
			ItemStack best = ItemStack.EMPTY;
			float score = 0;
			int missing = 20 - p.getFoodData().getFoodLevel();
			boolean hurt = p.getHealth() < p.getMaxHealth() * 0.6f;
			for (int i = 0; i < 9; i++) {
				ItemStack s = p.getInventory().getItem(i);
				FoodProperties f = s.get(DataComponents.FOOD);
				if (f == null) continue;
				boolean gap = s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE);
				if (gap && !hurt) continue;
				if (!gap && missing <= 0) continue;
				float sc = gap ? 100 + (s.is(Items.ENCHANTED_GOLDEN_APPLE) ? 1 : 0) : Math.min(f.nutrition(), missing) + f.saturation() - Math.max(0, f.nutrition() - missing) * 0.5f;
				if (sc > score) {
					score = sc;
					best = s;
				}
			}
			return best;
		}

		// more good effects than bad is beneficial, a potion with neither gets nothing
		private int potionTint(ItemStack s) {
			if (!s.is(Items.SPLASH_POTION)) return 0;
			PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
			if (pc == null) return 0;
			int up = 0, down = 0;
			for (var e : pc.getAllEffects()) {
				var cat = e.getEffect().value().getCategory();
				if (cat == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) up++;
				else if (cat == net.minecraft.world.effect.MobEffectCategory.HARMFUL) down++;
			}
			return up + down == 0 ? 0 : up >= down ? good.get() : bad.get();
		}

		// stacks shown in the hotbar and inventory are the same objects as the ones in your inventory, that's how
		// a slot is told apart from an item drawn anywhere else (creative tabs, tooltips)
		public static void back(GuiGraphicsExtractor g, ItemStack s, int x, int y) {
			HealHighlight h = INSTANCE;
			Minecraft mc = Minecraft.getInstance();
			Player p = mc.player;
			if (h == null || !h.on() || p == null || s.isEmpty() || !inInventory(p, s)) return;
			// without auto scan it's the hotbar only, inventories stay as they are
			if (!h.autoScan.on() && mc.gui.screen() != null) return;
			if (h.suggest.on() && s == h.suggestion(p)) {
				float phase = Util.getMillis() % 1400 / 1400f;
				int a = Math.max(28, Math.round(255 * (0.4f + 0.6f * (0.5f + 0.5f * Mth.sin(phase * Mth.TWO_PI)))));
				g.fill(x, y, x + 16, y + 16, ARGB.color(a, h.suggestColor.get() & 0xFFFFFF));
				return;
			}
			int tint = h.potions.on() ? h.potionTint(s) : 0;
			if (tint != 0) g.fill(x, y, x + 16, y + 16, tint);
			else if (h.food.on() && s == bestFood(p)) g.fill(x, y, x + 16, y + 16, ARGB.color(110, h.color.get() & 0xFFFFFF));
		}

		static boolean inInventory(Player p, ItemStack s) {
			Inventory inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i) == s) return true;
			return false;
		}
	}

	// ---------- lock slot ----------

	public static class LockSlot extends Module {
		static LockSlot INSTANCE;

		private final Setting.Bind key = add(new Setting.Bind("Lock Key", InputConstants.KEY_LALT));
		private final Setting.Color overlay = add(new Setting.Color("Overlay Color", 0x6AFFC857, 0xFFFFC857, 0xFFE5484D, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
		private final TreeSet<Integer> locked = new TreeSet<>();

		public LockSlot() {
			super("Lock Slot", Category.UTILITY, '\ue10b', "Hold Left Alt and click a slot to lock it. Locked items can't be moved or dropped.");
			INSTANCE = this;
		}

		public static boolean locked(int invSlot) {
			return INSTANCE != null && INSTANCE.on() && INSTANCE.locked.contains(invSlot);
		}

		// Bind.down() ignores keys while a screen is open, here the screen is the whole point
		private boolean held() {
			Minecraft mc = Minecraft.getInstance();
			return key.bound() && InputConstants.isKeyDown(mc.getWindow(), key.get());
		}

		// true = the click is eaten. alt+click toggles, anything touching a locked slot is blocked, including
		// number key swaps aimed at a locked hotbar slot
		public static boolean click(Slot slot, int button, ContainerInput input) {
			LockSlot l = INSTANCE;
			if (l == null || !l.on()) return false;
			boolean mine = slot != null && slot.container instanceof Inventory;
			if (mine && l.held()) {
				int i = slot.getContainerSlot();
				if (!l.locked.remove(i)) l.locked.add(i);
				Config.dirty();
				Ui.sound("click");
				return true;
			}
			if (mine && l.locked.contains(slot.getContainerSlot())) return true;
			return input == ContainerInput.SWAP && l.locked.contains(button == 40 ? 40 : button);
		}

		public static void icon(GuiGraphicsExtractor g, Slot slot) {
			if (slot.container instanceof Inventory && locked(slot.getContainerSlot())) lockAt(g, slot.x, slot.y);
		}

		// a faint fill (about a quarter of the color's alpha) and a 1px frame in the full color, enough to read
		// as locked without hiding the item underneath
		private static void lockAt(GuiGraphicsExtractor g, int x, int y) {
			int c = INSTANCE.overlay.get(), a = Mth.clamp(Math.round(ARGB.alpha(c) * 0.28f), 16, 96);
			g.fill(x + 2, y + 2, x + 14, y + 14, ARGB.color(a, c & 0xFFFFFF));
			g.fill(x + 2, y + 2, x + 14, y + 3, c);
			g.fill(x + 2, y + 13, x + 14, y + 14, c);
			g.fill(x + 2, y + 3, x + 3, y + 13, c);
			g.fill(x + 13, y + 3, x + 14, y + 13, c);
			g.pose().pushMatrix();
			g.pose().translate(x + 10, y - 1);
			g.pose().scale(0.75f, 0.75f);
			Ui.text(g, Ui.icon('\ue10b'), 0, 0, 0xFFF5A524);
			g.pose().popMatrix();
		}

		void hotbar(GuiGraphicsExtractor g) {
			Minecraft mc = Minecraft.getInstance();
			if (!on() || mc.player == null || mc.gui.hud.isHidden() || locked.isEmpty()) return;
			for (int i = 0; i < 9; i++) if (locked.contains(i)) lockAt(g, g.guiWidth() / 2 - 90 + i * 20 + 2, g.guiHeight() - 19);
		}

		@Override
		public JsonObject save() {
			JsonObject o = super.save();
			JsonArray a = new JsonArray();
			locked.forEach(a::add);
			o.add("locked", a);
			return o;
		}

		@Override
		public void load(JsonObject o) {
			super.load(o);
			locked.clear();
			if (o.has("locked")) for (JsonElement e : o.getAsJsonArray("locked")) locked.add(e.getAsInt());
		}
	}

	// ---------- disconnect guard ----------

	public static class DisconnectGuard extends Module {
		private final Setting.Choice when = add(new Setting.Choice("Ask", "In Combat", "In Combat", "Always"));
		private final Setting.Toggle single = add(new Setting.Toggle("Also In Singleplayer", false));
		// the server keeps "last hurt by" to itself, so our own red flash is the client's way to know
		private long hurtAt;
		private int lastHurt;

		public DisconnectGuard() {
			super("Disconnect Guard", Category.UTILITY, '\ue10e', "Asks before Disconnect in the pause menu, so a misclick doesn't end a fight.");
			// the pause menu's button keeps its look, only what pressing it does gets wrapped
			ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (!(screen instanceof PauseScreen ps) || !on()) return;
				Button b = ((PauseScreenAccessor) ps).kestrel$disconnect();
				if (b == null) return;
				Button.OnPress real = ((ButtonAccessor) b).kestrel$onPress();
				((ButtonAccessor) b).kestrel$setOnPress(btn -> {
					if (!ask()) {
						real.onPress(btn);
						return;
					}
					mc.gui.setScreen(new ConfirmScreen(yes -> {
						if (yes) real.onPress(btn);
						else mc.gui.setScreen(ps);
					}, Component.literal("Leave the server?"), Component.literal(combat() ? "You were in a fight a moment ago." : "You'll be disconnected.")));
				});
			});
		}

		@Override
		public void tick(Minecraft mc) {
			if (mc.player == null) return;
			if (mc.player.hurtTime > lastHurt) hurtAt = Util.getMillis();
			lastHurt = mc.player.hurtTime;
		}

		private boolean ask() {
			if (Minecraft.getInstance().isLocalServer() && !single.on()) return false;
			return when.get().equals("Always") || combat();
		}

		// hit something or got hit in the last 15 seconds
		private boolean combat() {
			return Combat.target(15_000) != null || Util.getMillis() - hurtAt < 15_000;
		}
	}
}
