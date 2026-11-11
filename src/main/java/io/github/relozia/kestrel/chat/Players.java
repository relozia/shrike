package io.github.relozia.kestrel.chat;

import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

// other people: friends, nametags, the tab list, chat bubbles
public final class Players {
	private Players() {}

	// ---------- friends ----------

	public static class Friends extends Module {
		static Friends INSTANCE;

		private final Setting.Words names = add(new Setting.Words("Friends", "Player name"));
		private final Setting.Color color = add(new Setting.Color("Friend Color", 0xFF57D163,
			0xFF57D163, 0xFF5B8DEF, 0xFF2EC4B6, 0xFFF5A524, 0xFFE0703A, 0xFFC879E8));
		private final Setting.Toggle ignoreVisuals = add(new Setting.Toggle("Ignore Friends For Visuals", false));

		public Friends() {
			super("Friends", Category.UTILITY, '\ue1a4', "Your friends show up in their own color in tab and on nametags.");
			INSTANCE = this;
			startOn();
		}

		public static boolean is(String name) {
			return INSTANCE != null && INSTANCE.on() && INSTANCE.names.has(name);
		}

		// hitboxes leave friends alone when you ask for it
		public static boolean ignored(net.minecraft.world.entity.player.Player p) {
			return is(p.getGameProfile().name()) && INSTANCE.ignoreVisuals.on();
		}

		public static int color() {
			return INSTANCE.color.get();
		}

		// just the name in the friend color, team prefixes and all dropped so it's obvious at a glance
		public static Component paint(String name) {
			return Component.literal(name).withColor(INSTANCE.color.get() & 0xFFFFFF);
		}
	}

	// ---------- nametags ----------

	public static class NameTags extends Module {
		static NameTags INSTANCE;

		private final Setting.Toggle own = add(new Setting.Toggle("Show Your Own", true));
		private final Setting.Slider scale = add(new Setting.Slider("Size", 100, 50, 200, 5, "%"));
		private final Setting.Slider bg = add(new Setting.Slider("Background Opacity", 25, 0, 100, 5, "%"));
		private final Setting.Toggle shadow = add(new Setting.Toggle("Text Shadow", false));

		public NameTags() {
			super("NameTags", Category.VISUAL, '\ue17f', "Your own nametag in third person, and nametag size and background.");
			INSTANCE = this;
		}

		public static boolean showOwn(Entity e) {
			Minecraft mc = Minecraft.getInstance();
			return INSTANCE != null && INSTANCE.on() && INSTANCE.own.on() && e == mc.player && !mc.options.getCameraType().isFirstPerson();
		}

		// servers that show you your own name do it with a text display or a named armor stand riding you or floating
		// just over your head. ours drawn on top of theirs was two names in one spot
		public static boolean serverShows(Entity e) {
			for (Entity p : e.getIndirectPassengers()) if (p instanceof Display.TextDisplay || p.isCustomNameVisible()) return true;
			return !e.level().getEntities(e, over(e), NameTags::tagLike).isEmpty();
		}

		// one of those, for you. the badge goes on it (Cosmetics.badgeLines, the armor stand name in EntityRendererMixin)
		public static boolean serverTag(Entity o) {
			Entity me = Minecraft.getInstance().player;
			return me != null && o != me && tagLike(o) && (me.hasIndirectPassenger(o) || over(me).contains(o.position()));
		}

		private static boolean tagLike(Entity o) {
			return o instanceof Display.TextDisplay || o instanceof ArmorStand a && a.isCustomNameVisible();
		}

		private static AABB over(Entity e) {
			return new AABB(e.getX() - 0.6, e.getY() + e.getBbHeight() - 0.3, e.getZ() - 0.6, e.getX() + 0.6, e.getY() + e.getBbHeight() + 1.4, e.getZ() + 0.6);
		}

		public static float scale() {
			return INSTANCE != null && INSTANCE.on() ? INSTANCE.scale.get().floatValue() / 100 : 1;
		}

		public static float bg(float vanilla) {
			return INSTANCE != null && INSTANCE.on() ? INSTANCE.bg.get().floatValue() / 100 : vanilla;
		}

		public static boolean shadow() {
			return INSTANCE != null && INSTANCE.on() && INSTANCE.shadow.on();
		}
	}

	// ---------- tab ----------

	public static class BetterTab extends Module {
		static BetterTab INSTANCE;

		private final Setting.Toggle numbers = add(new Setting.Toggle("Ping As Numbers", true));
		private final Setting.Slider max = add(new Setting.Slider("Max Players", 160, 80, 300, 10, ""));
		private final Setting.Toggle heads = add(new Setting.Toggle("Always Show Heads", true));

		public BetterTab() {
			super("Better Tab", Category.UTILITY, '\ue106', "Ping in milliseconds and more than 80 players in the tab list.");
			INSTANCE = this;
		}

		public static long max(long vanilla) {
			return INSTANCE != null && INSTANCE.on() ? INSTANCE.max.get().longValue() : vanilla;
		}

		public static boolean heads() {
			return INSTANCE != null && INSTANCE.on() && INSTANCE.heads.on();
		}

		public static boolean numbers() {
			return INSTANCE != null && INSTANCE.on() && INSTANCE.numbers.on();
		}

		// a number is wider than vanilla's 10px bars, the slot grows to fit "999ms"
		public static int extra() {
			return numbers() ? Minecraft.getInstance().font.width("999ms") - 10 : 0;
		}

		public static void ping(GuiGraphicsExtractor g, int slotWidth, int xo, int yo, int ms) {
			// negative is "no reply yet" (vanilla's crossed out bars), 0 is real, singleplayer reports it
			String s = ms < 0 ? "?" : ms + "ms";
			int c = ms < 0 ? 0xFFAAAAAA : ms < 100 ? 0xFF57D163 : ms < 200 ? 0xFFF5A524 : ms < 350 ? 0xFFE0703A : 0xFFE5484D;
			var f = Minecraft.getInstance().font;
			g.text(f, s, xo + slotWidth - f.width(s) - 1, yo, c);
		}
	}
}
