package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.setting.Setting;
import io.github.relozia.kestrel.track.Clicks;
import io.github.relozia.kestrel.track.Combat;
import io.github.relozia.kestrel.track.ServerTps;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

// the small one line widgets. they were a dozen near identical 15 line files, so they live here as nested classes
// each one picks its own label, suffix and icon, the shared box and text styles come from TextWidget
public final class SimpleWidgets {
	private SimpleWidgets() {}

	// m:ss, or h:mm:ss past an hour
	public static String clock(long ms) {
		long s = ms / 1000;
		return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	public static class Cps extends TextWidget {
		private final Setting.Choice show = add(new Setting.Choice("Show", "Left", "Left", "Right", "Both"));

		public Cps() {
			super("CPS", '\ue120', "Clicks per second.", 0.01f, 0.10f, '\ue323', "CPS:", "Icon");
		}

		@Override
		protected String value() {
			return switch (show.get()) {
				case "Right" -> Integer.toString(Clicks.right());
				case "Both" -> Clicks.left() + " | " + Clicks.right();
				default -> Integer.toString(Clicks.left());
			};
		}

		@Override
		protected String suffix() {
			return "c/s";
		}
	}

	public static class Ping extends TextWidget {
		private final Setting.Choice colors = colors();

		public Ping() {
			super("Ping", '\ue1ae', "Your connection delay to the server.", 0.01f, 0.18f, '\uef10', "Ping:", "Icon");
		}

		private int ms() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.getConnection() == null) return -1;
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			return info == null ? -1 : info.getLatency();
		}

		@Override
		protected String value() {
			int ms = ms();
			if (ms < 0) return null;
			// the pill reads "ping: 45ms", the panel puts the ms in grey after
			return split() ? Integer.toString(ms) : ms + "ms";
		}

		@Override
		protected String suffix() {
			return "ms";
		}

		@Override
		protected String sample() {
			return split() ? "45" : "45ms";
		}

		@Override
		protected int valueColor() {
			int ms = ms();
			return graded(colors, ms < 80 ? 0 : ms < 150 ? 1 : 2);
		}
	}

	public static class Tps extends TextWidget {
		private final Setting.Choice colors = colors();

		public Tps() {
			super("TPS", '\ue038', "How fast the server is running, 20 is normal.", 0.01f, 0.26f, '\ue855', "TPS:", "Icon");
		}

		@Override
		protected String value() {
			float t = ServerTps.tps();
			return t < 0 ? null : String.format(Locale.ROOT, "%.1f", t);
		}

		@Override
		protected String suffix() {
			return "t/s";
		}

		@Override
		protected String sample() {
			return "20.0";
		}

		@Override
		protected int valueColor() {
			float t = ServerTps.tps();
			return graded(colors, t >= 19 ? 0 : t >= 15 ? 1 : 2);
		}
	}

	public static class Bps extends TextWidget {
		private final float[] speeds = new float[10];
		private int i;
		private Vec3 last;

		public Bps() {
			super("BPS", '\ue3b9', "How fast you're moving, in blocks per second.", 0.01f, 0.34f, '\ue9e4', "BPS:", "Icon");
		}

		// horizontal only, falling would make it read huge numbers that mean nothing for movement
		@Override
		public void tick(Minecraft mc) {
			if (mc.player == null) {
				last = null;
				return;
			}
			Vec3 now = mc.player.position();
			if (last != null) speeds[i++ % speeds.length] = (float) Math.hypot(now.x - last.x, now.z - last.z) * 20;
			last = now;
		}

		@Override
		protected String value() {
			float sum = 0;
			for (float f : speeds) sum += f;
			return String.format(Locale.ROOT, "%.1f", sum / speeds.length);
		}

		@Override
		protected String suffix() {
			return "b/s";
		}
	}

	public static class Memory extends TextWidget {
		private final Setting.Choice show = add(new Setting.Choice("Show", "Both", "Percent", "Megabytes", "Both"));
		private final Setting.Slider warnAt = add(new Setting.Slider("Warn Above", 85, 50, 100, 5, "%"));

		public Memory() {
			super("Memory", '\ue445', "How much of Minecraft's memory is in use.", 0.01f, 0.42f, '\ue322', "RAM:", "Label");
		}

		private static long pct() {
			Runtime r = Runtime.getRuntime();
			return (r.totalMemory() - r.freeMemory()) * 100 / r.maxMemory();
		}

		@Override
		protected String value() {
			Runtime r = Runtime.getRuntime();
			long used = (r.totalMemory() - r.freeMemory()) / 1048576, max = r.maxMemory() / 1048576;
			return switch (show.get()) {
				case "Megabytes" -> used + "/" + max + "MB";
				case "Percent" -> pct() + "%";
				default -> String.format(Locale.ROOT, "%d%% %d/%dMB", pct(), used, max);
			};
		}

		// red once the game is close to running out, the threshold is the Warn Above setting
		@Override
		protected int valueColor() {
			return pct() >= warnAt.get() ? WARN : super.valueColor();
		}
	}

	public static class Server extends TextWidget {
		public Server() {
			super("Server", '\ue153', "The address of the server you're on.", 0.99f, 0.76f, '\ue80b', "Server:", "Icon");
		}

		// the stats bar said "singleplayer" instead of hiding
		@Override
		protected String value() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.isLocalServer()) return "singleplayer";
			ServerData s = mc.getCurrentServer();
			return s == null ? null : s.ip;
		}

		@Override
		protected String sample() {
			return "play.example.net";
		}
	}

	public static class Session extends TextWidget {
		private static final long LAUNCH = Util.getMillis();
		private final Setting.Choice count = add(new Setting.Choice("Count", "Server", "Server", "Since Launch"));
		private Object conn;
		private long start;

		public Session() {
			super("Session Timer", '\ue296', "How long you've been in this world or server.", 0.99f, 0.90f, '\ue425', "Session:", "Label");
		}

		@Override
		protected String label() {
			return count.get().equals("Server") ? "Session:" : "Playtime:";
		}

		// keyed on the connection, not the level: changing dimension swaps the level but it's the same session
		@Override
		public void tick(Minecraft mc) {
			Object c = mc.getConnection();
			if (c != conn) {
				conn = c;
				start = Util.getMillis();
			}
		}

		@Override
		protected String value() {
			if (count.get().equals("Since Launch")) return clock(Util.getMillis() - LAUNCH);
			return conn == null ? null : clock(Util.getMillis() - start);
		}

		@Override
		protected String sample() {
			return "1:23:45";
		}
	}

	public static class Stopwatch extends TextWidget {
		private final Setting.Bind toggleKey = add(new Setting.Bind("Start/Stop Key", -1));
		private final Setting.Bind resetKey = add(new Setting.Bind("Reset Key", -1));
		private long started = -1, banked;

		public Stopwatch() {
			super("Stopwatch", '\ue1e0', "A stopwatch you control with keys.", 0.99f, 0.83f, '\ue425', "Stopwatch:", "Label");
		}

		@Override
		protected String label() {
			return started < 0 ? "Stopped:" : "Stopwatch:";
		}

		@Override
		public void tick(Minecraft mc) {
			if (toggleKey.pressed(mc)) {
				if (started < 0) {
					started = Util.getMillis();
				} else {
					banked += Util.getMillis() - started;
					started = -1;
				}
			}
			if (resetKey.pressed(mc)) {
				banked = 0;
				started = -1;
			}
		}

		@Override
		protected String value() {
			long t = banked + (started < 0 ? 0 : Util.getMillis() - started);
			return clock(t) + String.format(Locale.ROOT, ".%02d", t / 10 % 100);
		}
	}

	public static class Combo extends TextWidget {
		private final Setting.Toggle hideZero = add(new Setting.Toggle("Hide At 0", false));

		public Combo() {
			super("Combo", '\ue1b4', "Hits you landed in a row without getting hit back.", 0.5f, 0.62f, '\ue3e4', "Combo:", "Icon");
		}

		@Override
		protected String value() {
			int c = Combat.combo();
			return c == 0 && hideZero.on() ? null : Integer.toString(c);
		}

		@Override
		protected String suffix() {
			return "x";
		}
	}

	public static class Reach extends TextWidget {
		private final Setting.Slider clear = add(new Setting.Slider("Clear After", 3, 1, 10, 1, "s"));
		private final Setting.Choice decimals = add(new Setting.Choice("Decimals", "1", "1", "2"));

		public Reach() {
			super("Reach", '\ue0ac', "How far away your last hit was.", 0.5f, 0.68f, '\ue41c', "Reach:", "Icon");
		}

		@Override
		protected String value() {
			float r = Combat.reach();
			if (r < 0 || Combat.reachAge() > clear.get() * 1000) return decimals.get().equals("1") ? "0.0" : "0.00";
			return String.format(Locale.ROOT, decimals.get().equals("1") ? "%.1f" : "%.2f", r);
		}

		@Override
		protected String suffix() {
			return "m";
		}
	}
}
