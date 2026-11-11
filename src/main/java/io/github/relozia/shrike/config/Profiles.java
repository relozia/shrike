package io.github.relozia.shrike.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.relozia.shrike.Shrike;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

// named setting profiles, one json each in config/shrike/profiles. which one is live is kept in profiles.json
// next to them, not inside a profile, or switching would also switch the thing that says what's switched
// Default always exists and can't be renamed or deleted, so there's always somewhere to land
// issues:
//   - the name is the file name, so two profiles differing only in case are the same file on windows
public final class Profiles {
	public static final String DEFAULT = "Default";

	private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("shrike");
	private static final Path DIR = ROOT.resolve("profiles");
	private static final Path POINTER = ROOT.resolve("profiles.json");

	private static String active = DEFAULT;

	private Profiles() {}

	// windows will take most of this but a slash or a colon would quietly write somewhere else
	private static String safe(String name) {
		String s = name.trim().replaceAll("[^A-Za-z0-9 _-]", "").trim();
		return s.isEmpty() ? DEFAULT : s;
	}

	public static Path file(String name) {
		return DIR.resolve(safe(name) + ".json");
	}

	public static String active() {
		return active;
	}

	public static List<String> all() {
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(DIR)) {
			s.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
				String n = p.getFileName().toString();
				out.add(n.substring(0, n.length() - 5));
			});
		} catch (IOException e) {
			Shrike.LOG.warn("couldn't list profiles: {}", e.getMessage());
		}
		if (!out.contains(DEFAULT)) out.add(DEFAULT);
		// Default first, the rest alphabetical, so the list doesn't reshuffle when you add one
		out.sort(Comparator.comparing((String n) -> !n.equals(DEFAULT)).thenComparing(n -> n.toLowerCase(Locale.ROOT)));
		return out;
	}

	public static boolean exists(String name) {
		return Files.exists(file(name));
	}

	// ---------- lifecycle ----------

	// called once at startup, before Config.load
	public static void init() {
		try {
			Files.createDirectories(DIR);
		} catch (IOException e) {
			Shrike.LOG.warn("couldn't make the profiles folder: {}", e.getMessage());
		}
		Path old = ROOT.resolve("config.json");
		Path def = file(DEFAULT);
		// settings used to live in config.json, move them across so nobody loses theirs on the update
		if (Files.exists(old) && !Files.exists(def)) {
			try {
				Files.move(old, def, StandardCopyOption.ATOMIC_MOVE);
				Shrike.LOG.info("moved config.json into profiles/{}.json", DEFAULT);
			} catch (IOException e) {
				Shrike.LOG.warn("couldn't move the old config into a profile: {}", e.getMessage());
			}
		}
		if (Files.exists(POINTER)) {
			try (Reader r = Files.newBufferedReader(POINTER)) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				if (o.has("active")) active = o.get("active").getAsString();
			} catch (IOException | RuntimeException e) {
				Shrike.LOG.warn("couldn't read which profile was in use, falling back to {}", DEFAULT);
			}
		}
		// the profile it points at may have been deleted by hand between sessions
		if (!exists(active)) active = DEFAULT;
	}

	private static void savePointer() {
		JsonObject o = new JsonObject();
		o.addProperty("active", active);
		try {
			Files.writeString(POINTER, o.toString());
		} catch (IOException e) {
			Shrike.LOG.warn("couldn't remember the active profile: {}", e.getMessage());
		}
	}

	// ---------- the operations behind the menu ----------

	public static void switchTo(String name) {
		if (!exists(name) || safe(name).equals(active)) return;
		Config.save();
		active = safe(name);
		savePointer();
		Config.load();
	}

	// a new profile starts from what's loaded right now, an empty one would mean every module back to default
	public static void create(String name) {
		String n = safe(name);
		if (n.isEmpty() || exists(n)) return;
		Config.save();
		copy(file(active), file(n));
		switchTo(n);
	}

	public static void duplicate(String from, String name) {
		String n = safe(name);
		if (!exists(from) || exists(n)) return;
		if (safe(from).equals(active)) Config.save();
		copy(file(from), file(n));
	}

	public static void rename(String from, String name) {
		String n = safe(name);
		if (DEFAULT.equals(safe(from)) || !exists(from) || exists(n)) return;
		boolean live = safe(from).equals(active);
		if (live) Config.save();
		try {
			Files.move(file(from), file(n), StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Shrike.LOG.warn("couldn't rename profile {}: {}", from, e.getMessage());
			return;
		}
		if (live) {
			active = n;
			savePointer();
		}
	}

	public static void delete(String name) {
		String n = safe(name);
		if (DEFAULT.equals(n) || !exists(n)) return;
		try {
			Files.delete(file(n));
		} catch (IOException e) {
			Shrike.LOG.warn("couldn't delete profile {}: {}", n, e.getMessage());
			return;
		}
		// deleting the one you're on drops you back to Default rather than leaving nothing loaded
		if (n.equals(active)) {
			active = DEFAULT;
			savePointer();
			Config.load();
		}
	}

	private static void copy(Path from, Path to) {
		try {
			if (Files.exists(from)) Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
			else Files.writeString(to, "{}");
		} catch (IOException e) {
			Shrike.LOG.warn("couldn't copy profile: {}", e.getMessage());
		}
	}
}
