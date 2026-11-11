package io.github.relozia.kestrel.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.relozia.kestrel.config.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

// all the setting types live in here, they're tiny and kept changing together
// issues:
//   - Slider stores doubles, int-ish settings just round when shown
//   - Bind only does keyboard keys, mouse side buttons would need their own path
public abstract class Setting<T> {
	public final String name;
	public final T def;
	protected T value;
	// a color under a switch only shows up in the menu once the switch is on
	private BooleanSupplier shown = () -> true;

	protected Setting(String name, T def) {
		this.name = name;
		this.def = def;
		this.value = def;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S when(BooleanSupplier b) {
		shown = b;
		return (S) this;
	}

	public boolean visible() {
		return shown.getAsBoolean();
	}

	public T get() {
		return value;
	}

	public void set(T v) {
		if (v.equals(value)) return;
		value = v;
		Config.dirty();
	}

	public boolean changed() {
		return !value.equals(def);
	}

	public void reset() {
		set(def);
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement e);

	// ---------- types ----------

	public static class Toggle extends Setting<Boolean> {
		public Toggle(String name, boolean def) {
			super(name, def);
		}

		public boolean on() {
			return value;
		}

		public void flip() {
			set(!value);
		}

		@Override public JsonElement save() { return new JsonPrimitive(value); }
		@Override public void load(JsonElement e) { value = e.getAsBoolean(); }
	}

	public static class Slider extends Setting<Double> {
		public final double min, max, step;
		private final String unit;

		public Slider(String name, double def, double min, double max, double step, String unit) {
			super(name, def);
			this.min = min;
			this.max = max;
			this.step = step;
			this.unit = unit;
		}

		@Override
		public void set(Double v) {
			super.set(snap(v));
		}

		// rounding to the step alone leaves stuff like 0.30000000000000004 in the config, hence the second round
		private double snap(double v) {
			double s = Math.round(Mth.clamp(v, min, max) / step) * step;
			return Math.round(s * 1000.0) / 1000.0;
		}

		public double pct() {
			return (value - min) / (max - min);
		}

		public String shown() {
			// as many decimals as the step has, a 0.05 step showing one decimal turned 0.75 into 0.8
			int d = step >= 1 ? 0 : step >= 0.1 ? 1 : step >= 0.01 ? 2 : 3;
			String n = d == 0 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%." + d + "f", value);
			return n + unit;
		}

		@Override public JsonElement save() { return new JsonPrimitive(value); }
		@Override public void load(JsonElement e) { value = snap(e.getAsDouble()); }
	}

	public static class Choice extends Setting<String> {
		public final List<String> options;

		public Choice(String name, String def, String... options) {
			super(name, def);
			this.options = List.of(options);
		}

		public void cycle(int dir) {
			int i = options.indexOf(value);
			set(options.get(Math.floorMod(i + dir, options.size())));
		}

		@Override public JsonElement save() { return new JsonPrimitive(value); }

		@Override
		public void load(JsonElement e) {
			// an option renamed in an update shouldn't wipe the whole module, just fall back to default
			String s = e.getAsString();
			if (options.contains(s)) value = s;
		}
	}

	public static class Color extends Setting<Integer> {
		public final int[] presets;
		// see through colors (armor hud tiles) get an opacity strip next to the hue one
		public boolean alpha;

		public Color(String name, int def, int... presets) {
			super(name, def);
			this.presets = presets;
		}

		public Color withAlpha() {
			alpha = true;
			return this;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(alpha ? String.format(Locale.ROOT, "#%08X", value) : String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF));
		}

		@Override
		public void load(JsonElement e) {
			String h = e.getAsString().replace("#", "");
			// parseUnsignedInt, an alpha above 7F doesn't fit a signed int
			int v = Integer.parseUnsignedInt(h, 16);
			value = h.length() > 6 && alpha ? v : 0xFF000000 | v;
		}
	}

	// a button in the settings page, nothing to save
	public static class Action extends Setting<String> {
		public final Runnable run;

		public Action(String name, String label, Runnable run) {
			super(name, label);
			this.run = run;
		}

		// Module.save skips these
		@Override public JsonElement save() { return JsonNull.INSTANCE; }
		@Override public void load(JsonElement e) {}
	}

	// a keyboard key, -1 = unbound
	public static class Bind extends Setting<Integer> {
		// presses per key, counted by KeyboardHandlerMixin as they happen. polling "is it down" once a tick
		// missed taps shorter than a tick
		private static final Map<Integer, Integer> PRESSES = new HashMap<>();
		private int seen = -1;

		public Bind(String name, int def) {
			super(name, def);
		}

		public static void press(int key) {
			PRESSES.merge(key, 1, Integer::sum);
		}

		public boolean bound() {
			return value >= 0;
		}

		// true once per press. presses made with a screen open aren't counted, so typing in chat doesn't trigger it
		public boolean pressed(Minecraft mc) {
			int c = bound() ? PRESSES.getOrDefault(value, 0) : 0;
			// the first look after the module turns on just catches up, an old press shouldn't fire
			boolean hit = seen >= 0 && c != seen;
			seen = c;
			return hit;
		}

		// held right now, for hold-to-use keys like zoom. same "no screen open" rule as pressed()
		public boolean down(Minecraft mc) {
			return bound() && mc.gui.screen() == null && InputConstants.isKeyDown(mc.getWindow(), value);
		}

		public String keyName() {
			return bound() ? label(InputConstants.Type.KEYSYM.getOrCreate(value)) : "None";
		}

		// short names for hotbar slots, keystrokes and bind buttons. vanilla's names are made for the controls
		// screen, side buttons are "Button 4" there and cutting that to fit a slot left "But"
		public static String label(InputConstants.Key k) {
			int v = k.getValue();
			if (v < 0) return "";
			if (k.getType() == InputConstants.Type.MOUSE) return switch (v) {
				case 0 -> "LMB";
				case 1 -> "RMB";
				case 2 -> "MMB";
				default -> "M" + (v + 1);
			};
			if (v >= InputConstants.KEY_NUMPAD0 && v <= InputConstants.KEY_NUMPAD9) return "Num" + (v - InputConstants.KEY_NUMPAD0);
			return switch (v) {
				case InputConstants.KEY_LSHIFT -> "LShift";
				case InputConstants.KEY_RSHIFT -> "RShift";
				case InputConstants.KEY_LCONTROL -> "LCtrl";
				case InputConstants.KEY_RCONTROL -> "RCtrl";
				case InputConstants.KEY_LALT -> "LAlt";
				case InputConstants.KEY_RALT -> "RAlt";
				case InputConstants.KEY_CAPSLOCK -> "Caps";
				case InputConstants.KEY_BACKSPACE -> "Bksp";
				case InputConstants.KEY_ESCAPE -> "Esc";
				case InputConstants.KEY_PAGEUP -> "PgUp";
				case InputConstants.KEY_PAGEDOWN -> "PgDn";
				case InputConstants.KEY_INSERT -> "Ins";
				case InputConstants.KEY_DELETE -> "Del";
				case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> "Win";
				default -> k.getDisplayName().getString();
			};
		}

		@Override public JsonElement save() { return new JsonPrimitive(value); }
		@Override public void load(JsonElement e) { value = e.getAsInt(); }
	}

	// 15x15 pixel grid (custom crosshair). saved as rows of '#' and '.' so the config stays readable by hand
	public static class Grid extends Setting<String> {
		public static final int N = 15;

		public Grid(String name, String def) {
			super(name, def);
		}

		public boolean at(int x, int y) {
			return value.charAt(y * N + x) == '#';
		}

		public void put(int x, int y, boolean on) {
			if (at(x, y) == on) return;
			char[] c = value.toCharArray();
			c[y * N + x] = on ? '#' : '.';
			set(new String(c));
		}

		@Override
		public JsonElement save() {
			JsonArray a = new JsonArray();
			for (int y = 0; y < N; y++) a.add(value.substring(y * N, y * N + N));
			return a;
		}

		@Override
		public void load(JsonElement e) {
			StringBuilder sb = new StringBuilder();
			for (JsonElement row : e.getAsJsonArray()) sb.append(row.getAsString());
			String s = sb.toString();
			if (s.length() == N * N && s.chars().allMatch(ch -> ch == '#' || ch == '.')) value = s;
		}
	}

	// pick any number of options, used for things like "which effects to warn about"
	public static class Multi extends Setting<Set<String>> {
		public final List<String> options;

		public Multi(String name, List<String> options, String... def) {
			super(name, Set.of(def));
			this.options = options;
			this.value = new LinkedHashSet<>(this.def);
		}

		public boolean has(String o) {
			return value.contains(o);
		}

		public void toggle(String o) {
			Set<String> next = new LinkedHashSet<>(value);
			if (!next.remove(o)) next.add(o);
			set(next);
		}

		@Override public void reset() { set(new LinkedHashSet<>(def)); }

		@Override
		public JsonElement save() {
			JsonArray a = new JsonArray();
			value.forEach(a::add);
			return a;
		}

		@Override
		public void load(JsonElement e) {
			Set<String> next = new LinkedHashSet<>();
			for (JsonElement x : e.getAsJsonArray()) if (options.contains(x.getAsString())) next.add(x.getAsString());
			value = next;
		}
	}

	// free text entries: friend names, words to watch chat for, gg lines. edited in WordsScreen.
	// matching ignores case, people never type names the way the server has them
	public static class Words extends Setting<List<String>> {
		public final String hint;

		public Words(String name, String hint, String... def) {
			super(name, List.of(def));
			this.hint = hint;
			this.value = new ArrayList<>(this.def);
		}

		public boolean has(String s) {
			return value.stream().anyMatch(v -> v.equalsIgnoreCase(s));
		}

		public void add(String s) {
			s = s.trim();
			if (s.isEmpty() || has(s)) return;
			List<String> next = new ArrayList<>(value);
			next.add(s);
			set(next);
		}

		public void remove(String s) {
			List<String> next = new ArrayList<>(value);
			next.removeIf(v -> v.equalsIgnoreCase(s));
			set(next);
		}

		@Override public void reset() { set(new ArrayList<>(def)); }

		@Override
		public JsonElement save() {
			JsonArray a = new JsonArray();
			value.forEach(a::add);
			return a;
		}

		@Override
		public void load(JsonElement e) {
			List<String> next = new ArrayList<>();
			for (JsonElement x : e.getAsJsonArray()) next.add(x.getAsString());
			value = next;
		}
	}

	// item ids like "minecraft:ender_pearl". edited through the item picker screen
	public static class Items extends Setting<List<String>> {
		public Items(String name, String... def) {
			super(name, List.of(def));
			this.value = new ArrayList<>(this.def);
		}

		public boolean has(Item i) {
			return value.contains(BuiltInRegistries.ITEM.getKey(i).toString());
		}

		public void toggle(Item i) {
			String id = BuiltInRegistries.ITEM.getKey(i).toString();
			List<String> next = new ArrayList<>(value);
			if (!next.remove(id)) next.add(id);
			set(next);
		}

		// unknown ids (an item from a mod that got removed) just get skipped
		public List<Item> items() {
			List<Item> out = new ArrayList<>();
			for (String id : value) {
				Identifier rl = Identifier.tryParse(id);
				if (rl != null) BuiltInRegistries.ITEM.getOptional(rl).ifPresent(out::add);
			}
			return out;
		}

		@Override public void reset() { set(new ArrayList<>(def)); }

		@Override
		public JsonElement save() {
			JsonArray a = new JsonArray();
			value.forEach(a::add);
			return a;
		}

		@Override
		public void load(JsonElement e) {
			List<String> next = new ArrayList<>();
			for (JsonElement x : e.getAsJsonArray()) next.add(x.getAsString());
			value = next;
		}
	}
}
