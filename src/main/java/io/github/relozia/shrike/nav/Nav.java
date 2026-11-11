package io.github.relozia.shrike.nav;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.shrike.config.Config;
import io.github.relozia.shrike.gui.Notice;
import io.github.relozia.shrike.gui.Shape;
import io.github.relozia.shrike.gui.Ui;
import io.github.relozia.shrike.gui.WaypointScreen;
import io.github.relozia.shrike.hud.HudWidget;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import io.github.relozia.shrike.visual.WorldDraw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// waypoints and the things that make them: your key, your death, funtime events. plus the compass and trap timer
public final class Nav {
	private Nav() {}

	public record Waypoint(String name, int x, int y, int z, String dim, int color, long until) {
		boolean temporary() {
			return until != 0;
		}
	}

	// "the world we're in": a server address or the singleplayer save name. waypoints are kept per world
	static @Nullable String world() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		if (mc.getCurrentServer() != null) return "server:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		if (mc.getSingleplayerServer() != null) return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		return null;
	}

	static String dim() {
		return Minecraft.getInstance().level.dimension().identifier().toString();
	}

	// funtime only features check this, they're about that server's messages and items
	static boolean funtime() {
		var sd = Minecraft.getInstance().getCurrentServer();
		return sd != null && sd.ip.toLowerCase(Locale.ROOT).contains("funtime");
	}

	// ---------- waypoints ----------

	public static class Waypoints extends Module {
		static Waypoints INSTANCE;

		private final Setting.Bind add = add(new Setting.Bind("Add Waypoint Key", 66));
		private final Setting.Bind list = add(new Setting.Bind("Waypoint List Key", 78));
		private final Setting.Toggle deaths = add(new Setting.Toggle("Death Waypoints", true));
		private final Setting.Toggle onlyLatest = add(new Setting.Toggle("Keep Only Latest Death", true)).when(deaths::on);
		private final Setting.Toggle deathChat = add(new Setting.Toggle("Death Chat Message", true)).when(deaths::on);
		private final Setting.Toggle beams = add(new Setting.Toggle("Beams", true));
		private final Setting.Slider size = add(new Setting.Slider("Label Size", 100, 50, 200, 10, "%"));

		private final Map<String, List<Waypoint>> all = new HashMap<>();
		private boolean wasDead;

		public Waypoints() {
			super("waypoints", "Waypoints", Category.UTILITY, '\ue111', "B saves a waypoint where you stand, N lists them. Your death spot gets one too.");
			INSTANCE = this;
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
				if (on() && Minecraft.getInstance().level != null) draw(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState);
			});
		}

		public static List<Waypoint> here() {
			Waypoints w = INSTANCE;
			String k = world();
			if (w == null || !w.on() || k == null) return List.of();
			List<Waypoint> out = new ArrayList<>();
			for (Waypoint p : w.all.getOrDefault(k, List.of())) if (p.dim().equals(dim())) out.add(p);
			return out;
		}

		public static List<Waypoint> inWorld() {
			Waypoints w = INSTANCE;
			String k = world();
			return w == null || k == null ? List.of() : w.all.getOrDefault(k, List.of());
		}

		public static void add(Waypoint p) {
			Waypoints w = INSTANCE;
			String k = world();
			if (w == null || k == null) return;
			List<Waypoint> l = w.all.computeIfAbsent(k, x -> new ArrayList<>());
			// a temporary one with the same name (an event that moved) replaces the old spot
			if (p.temporary()) l.removeIf(o -> o.name().equals(p.name()));
			l.add(p);
			Config.dirty();
		}

		public static void remove(Waypoint p) {
			Waypoints w = INSTANCE;
			String k = world();
			if (w == null || k == null || !w.all.containsKey(k)) return;
			w.all.get(k).remove(p);
			Config.dirty();
		}

		@Override
		public void tick(Minecraft mc) {
			if (mc.player == null || world() == null) return;
			if (add.pressed(mc)) {
				BlockPos b = mc.player.blockPosition();
				String name = "Waypoint " + (inWorld().size() + 1);
				add(new Waypoint(name, b.getX(), b.getY(), b.getZ(), dim(), 0xFF5B8DEF, 0));
				mc.gui.hud.setOverlayMessage(Component.literal("Added " + name), false);
			}
			if (list.pressed(mc)) mc.gui.setScreen(new WaypointScreen(null));
			boolean dead = mc.player.isDeadOrDying();
			if (dead && !wasDead && deaths.on()) {
				BlockPos b = mc.player.blockPosition();
				// death spots are the ones that never time out, see Waypoint.until
				if (onlyLatest.on() && all.containsKey(world())) all.get(world()).removeIf(p -> p.until() == Long.MAX_VALUE);
				String at = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
				add(new Waypoint("Death (" + at + ")", b.getX(), b.getY(), b.getZ(), dim(), 0xFFE5484D, Long.MAX_VALUE));
				if (deathChat.on()) {
					mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Death waypoint saved at " + b.getX() + " " + b.getY() + " " + b.getZ()
						+ " (" + mc.level.dimension().identifier().getPath().replace('_', ' ') + ")").withStyle(ChatFormatting.GRAY));
				}
			}
			wasDead = dead;
			// temporary ones go when they time out or when you walk up to them (that's what a death spot is for)
			String k = world();
			if (!all.containsKey(k)) return;
			long now = System.currentTimeMillis();
			boolean changed = all.get(k).removeIf(p -> p.temporary() && (now > p.until()
				|| p.dim().equals(dim()) && mc.player.distanceToSqr(p.x() + 0.5, p.y(), p.z() + 0.5) < 9 && !dead));
			if (changed) Config.dirty();
		}

		// far ones get pulled in to 48 blocks and scaled up so they keep the same size on screen, the
		// game doesn't draw anything past render distance otherwise
		private void draw(PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
			for (Waypoint p : here()) {
				Vec3 at = new Vec3(p.x() + 0.5, p.y() + 1.2, p.z() + 0.5).subtract(cam.pos);
				double d = at.length();
				float k = size.get().floatValue() / 100;
				Vec3 shown = d > 48 ? at.scale(48 / d) : at;
				float scale = (float) (Math.max(1, d / 8) * (d > 48 ? 48 / d : 1)) * k;
				String label = p.name() + "  " + Math.round(d) + "m";
				WorldDraw.text(out, ps, cam, shown, Component.literal(label), p.color(), scale);
				if (beams.on() && d < 160) {
					float bx = (float) (p.x() + 0.5 - cam.pos.x), bz = (float) (p.z() + 0.5 - cam.pos.z), by = (float) (p.y() - cam.pos.y);
					int c = ARGB.color(170, p.color() & 0xFFFFFF);
					out.submitCustomGeometry(ps, RenderTypes.lines(), (pose, vc) -> WorldDraw.line(pose, vc, c, 3, bx, by, bz, bx, by + 256, bz));
				}
			}
		}

		@Override
		public JsonObject save() {
			JsonObject o = super.save();
			JsonObject w = new JsonObject();
			all.forEach((k, l) -> {
				JsonArray a = new JsonArray();
				for (Waypoint p : l) {
					if (p.until() == Long.MAX_VALUE || p.until() == 0) {
						JsonObject j = new JsonObject();
						j.addProperty("name", p.name());
						j.addProperty("x", p.x());
						j.addProperty("y", p.y());
						j.addProperty("z", p.z());
						j.addProperty("dim", p.dim());
						j.addProperty("color", p.color());
						j.addProperty("death", p.until() == Long.MAX_VALUE);
						a.add(j);
					}
				}
				if (!a.isEmpty()) w.add(k, a);
			});
			o.add("waypoints", w);
			return o;
		}

		// event waypoints with a real expiry aren't saved, they'd be stale by the next launch anyway
		@Override
		public void load(JsonObject o) {
			super.load(o);
			all.clear();
			if (!o.has("waypoints")) return;
			for (var e : o.getAsJsonObject("waypoints").entrySet()) {
				List<Waypoint> l = new ArrayList<>();
				for (JsonElement el : e.getValue().getAsJsonArray()) {
					JsonObject j = el.getAsJsonObject();
					l.add(new Waypoint(j.get("name").getAsString(), j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt(),
						j.get("dim").getAsString(), j.get("color").getAsInt(), j.has("death") && j.get("death").getAsBoolean() ? Long.MAX_VALUE : 0));
				}
				all.put(e.getKey(), l);
			}
		}
	}

	// ---------- compass ----------

	// a 26 tall pill with letters every 45 degrees, ticks every 15, waypoint dots, and the name of a waypoint
	// you're looking almost straight at under it
	public static class Compass extends HudWidget {
		private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		private static final int TEXT = 0xF2F7FAFC, TICK = 0x8CF7FAFC;

		private final Setting.Slider width = add(new Setting.Slider("Width", 240, 120, 400, 10, ""));
		private final Setting.Slider fov = add(new Setting.Slider("Field of View", 180, 90, 360, 15, "\u00b0"));
		private final Setting.Toggle degrees = add(new Setting.Toggle("Show Degrees", true));
		private final Setting.Toggle points = add(new Setting.Toggle("Show Waypoints", true));

		public Compass() {
			super("compass", "Compass", '\ue09b', "A heading strip with your waypoints marked on it.", 0.5f, 0.0f);
		}

		@Override
		protected Look look() {
			return Look.NONE;
		}

		@Override protected int contentW() { return width.get().intValue(); }
		@Override protected int contentH() { return 26; }

		@Override
		protected void content(GuiGraphicsExtractor g) {
			Minecraft mc = Minecraft.getInstance();
			float w = contentW(), mid = w / 2, f = fov.get().floatValue();
			if (bg.on()) box(g, w, 26, 6);
			// a real bearing: 0 north, 90 east. minecraft yaw 180 is north
			float heading = Mth.positiveModulo((mc.player == null ? 0 : mc.player.getYRot()) + 180, 360);
			for (int deg = 0; deg < 360; deg += 15) {
				float d = Mth.wrapDegrees(deg - heading);
				if (Math.abs(d) > f / 2) continue;
				float px = mid + d / f * (w - 16);
				// letters near the ends fade out instead of popping off the strip
				float fade = 1 - Math.max(0, (Math.abs(d) / (f / 2) - 0.75f) * 4);
				if (deg % 45 == 0) {
					String l = DIRS[deg / 45];
					float size = l.length() == 1 ? 11 : 9;
					draw(g, l, HUD, px - width(l, HUD, size) / 2, 13, ARGB.multiplyAlpha(l.length() == 1 ? TEXT : TICK, fade), size);
				} else {
					Shape.rect(g, px - 0.5f, 6, 1, 4, 0, ARGB.multiplyAlpha(TICK, fade));
				}
			}
			if (mc.player != null && points.on()) {
				for (Waypoint p : Waypoints.here()) {
					double dx = p.x() + 0.5 - mc.player.getX(), dz = p.z() + 0.5 - mc.player.getZ();
					float bearing = (float) Math.toDegrees(Math.atan2(dx, -dz));
					float d = Mth.wrapDegrees(bearing - heading);
					if (Math.abs(d) > f / 2) continue;
					float px = mid + d / f * (w - 16);
					Shape.rect(g, px - 2.5f, 16, 5, 5, 2.5f, p.color());
					if (Math.abs(d) < 8) {
						String n = p.name() + " (" + Math.round(Math.hypot(dx, dz)) + "m)";
						draw(g, n, LIGHT, px - width(n, LIGHT, 8.5f) / 2, 36, TEXT, 8.5f);
					}
				}
			}
			Shape.rect(g, mid - 0.75f, 1, 1.5f, 5, 0.5f, Ui.accent());
			if (degrees.on()) {
				String s2 = Math.round(heading) % 360 + "\u00b0";
				draw(g, s2, LIGHT, mid - width(s2, LIGHT, 8) / 2, 23, TEXT, 8);
			}
		}
	}

	// ---------- funtime ----------

	// funtime announces events with coordinates in chat. the phrases are the server's own (russian), the
	// waypoint lasts 10 minutes
	public static class AutoWaypoint extends Module {
		private static final List<Object[]> EVENTS = List.of(
			new Object[] {Pattern.compile("Метеоритный дождь.*?\\[(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]"), "Meteor Shower", 0xFFE0703A},
			new Object[] {Pattern.compile("Вулкан.*?(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)"), "Volcano", 0xFFE5484D},
			new Object[] {Pattern.compile("Маяк убийца.*?(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)"), "Killer Beacon", 0xFFC879E8});
		// the event is over or the loot is gone, the marker has nothing left to point at
		private static final List<String[]> ENDS = List.of(new String[] {"Портал на резню активирован", "the portal opened"},
			new String[] {"Идёт страшный бой", "the fight started"});
		private static final Pattern DEATH_CHEST = Pattern.compile("Сундук Смерти.*?Появится уже через 3 минут");
		static AutoWaypoint INSTANCE;

		private final Setting.Multi events = add(new Setting.Multi("Events", List.of("Meteor Shower", "Volcano", "Killer Beacon"), "Meteor Shower", "Volcano", "Killer Beacon"));
		private final Setting.Toggle notify = add(new Setting.Toggle("Notifications", true));

		public AutoWaypoint() {
			super("autowaypoint", "AutoWaypoint", Category.UTILITY, '\ue53d', "On Funtime, event messages with coordinates become a waypoint for 10 minutes.");
			INSTANCE = this;
		}

		private void toast(String text) {
			if (notify.on()) Notice.show("AutoWaypoint", text, 3400);
		}

		public static void message(String s) {
			AutoWaypoint a = INSTANCE;
			if (a == null || !a.on() || !funtime()) return;
			for (Object[] e : EVENTS) {
				Matcher m = ((Pattern) e[0]).matcher(s);
				if (!m.find() || !a.events.has((String) e[1])) continue;
				int x = Integer.parseInt(m.group(1)), y = Integer.parseInt(m.group(2)), z = Integer.parseInt(m.group(3));
				Waypoints.add(new Waypoint((String) e[1], x, y, z, dim(), (int) e[2], System.currentTimeMillis() + 10 * 60_000));
				a.toast("Waypoint set: " + e[1] + " [" + x + " " + y + " " + z + "]");
				return;
			}
			String why = DEATH_CHEST.matcher(s).find() ? "the death chest is coming" : null;
			for (String[] end : ENDS) if (s.contains(end[0])) why = end[1];
			if (why == null) return;
			boolean any = false;
			for (Waypoint p : List.copyOf(Waypoints.inWorld())) {
				if (p.temporary() && p.until() != Long.MAX_VALUE && EVENTS.stream().anyMatch(e -> e[1].equals(p.name()))) {
					Waypoints.remove(p);
					any = true;
				}
			}
			if (any) a.toast("Marker cleared: " + why);
		}
	}

	// funtime's traps: using netherite scrap builds a cage for 15s (pistons sound), the dragon one 30s (evoker
	// fangs). only your own use within 7 blocks and 0.7s of pressing use counts
	public static class TrapTimer extends Module {
		static TrapTimer INSTANCE;

		private record Trap(String name, Vec3 at, long until) {}

		private final List<Trap> traps = new ArrayList<>();
		private long usedAt;

		public TrapTimer() {
			super("trap_timer", "Trap Timer", Category.UTILITY, '\ue1e0', "On Funtime, how long your trap has left, floating where you used it.");
			INSTANCE = this;
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
				if (!on() || traps.isEmpty()) return;
				long now = Util.getMillis();
				CameraRenderState cam = ctx.levelState().cameraRenderState;
				for (Trap t : traps) {
					float left = Math.max(0, (t.until() - now) / 1000f);
					WorldDraw.text(ctx.submitNodeCollector(), ctx.poseStack(), cam, t.at().add(0, 2.5, 0).subtract(cam.pos),
						Component.literal(t.name() + " " + String.format(Locale.ROOT, "%.1f", left)), left < 3 ? 0xFFE5484D : 0xFFF5A524, 1.3f);
				}
			});
		}

		@Override
		public void tick(Minecraft mc) {
			traps.removeIf(t -> Util.getMillis() > t.until());
			if (mc.player != null && mc.options.keyUse.isDown() && (mc.player.getMainHandItem().is(Items.NETHERITE_SCRAP) || mc.player.getOffhandItem().is(Items.NETHERITE_SCRAP))) {
				usedAt = Util.getMillis();
			}
		}

		public static void sound(ClientboundSoundPacket p) {
			TrapTimer t = INSTANCE;
			Minecraft mc = Minecraft.getInstance();
			if (t == null || !t.on() || mc.player == null || !funtime() || Util.getMillis() - t.usedAt > 700) return;
			Vec3 at = new Vec3(p.getX(), p.getY(), p.getZ());
			if (mc.player.position().distanceToSqr(at) > 49) return;
			String id = p.getSound().value().location().getPath();
			if (id.equals("block.piston.extend") || id.equals("block.piston.contract")) t.traps.add(new Trap("Trap", at, Util.getMillis() + 15_000));
			else if (id.equals("entity.evoker_fangs.attack")) t.traps.add(new Trap("Dragon Trap", at, Util.getMillis() + 30_000));
			else return;
			t.usedAt = 0;
		}
	}
}
