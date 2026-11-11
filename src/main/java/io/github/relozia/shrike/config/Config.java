package io.github.relozia.shrike.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.module.Modules;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.util.Util;

// the settings file for whichever profile is live, see Profiles for where that lands
// modules are keyed by Module.id, not by their display name, so renaming one in the menu keeps its settings
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static long dirtyAt = -1;
	private static boolean loading;

	private Config() {}

	private static Path file() {
		return Profiles.file(Profiles.active());
	}

	public static void dirty() {
		if (!loading) dirtyAt = Util.getMillis();
	}

	// saving on every change would write the file every frame while dragging a slider,
	// so it waits until nothing has changed for a second
	public static void tick() {
		if (dirtyAt >= 0 && Util.getMillis() - dirtyAt > 1000) save();
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) return;
		loading = true;
		try (Reader r = Files.newBufferedReader(f)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			JsonObject mods = root.getAsJsonObject("modules");
			if (mods != null) {
				for (Module m : Modules.all()) {
					// configs written before modules had ids keyed on the display name, so fall back to it once.
					// saving puts it back under the id, so the old key disappears on the next write
					JsonElement e = mods.has(m.id) ? mods.get(m.id) : mods.get(m.name);
					if (e != null) m.load(e.getAsJsonObject());
				}
			}
			if (root.has("shrike")) Modules.SETTINGS.load(root.getAsJsonObject("shrike"));
		} catch (IOException | RuntimeException e) {
			// gson throws runtime exceptions on broken json, same outcome either way
			Shrike.LOG.warn("couldn't read config, using defaults ({})", e.getMessage());
		} finally {
			loading = false;
		}
	}

	public static void save() {
		dirtyAt = -1;
		JsonObject mods = new JsonObject();
		for (Module m : Modules.all()) mods.add(m.id, m.save());
		JsonObject root = new JsonObject();
		root.add("modules", mods);
		root.add("shrike", Modules.SETTINGS.save());

		Path f = file();
		// write next to it then swap, so a crash mid write can't leave a half written config
		Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
		try {
			Files.createDirectories(f.getParent());
			Files.writeString(tmp, GSON.toJson(root));
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Shrike.LOG.warn("config save failed: {}", e.getMessage());
		}
	}
}
