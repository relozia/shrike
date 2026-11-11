package io.github.relozia.kestrel.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.module.Modules;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

// config/kestrel/config.json, or a server's own file while Server Profiles has one in use
// issues:
//   - renaming a module in an update loses its settings, there's no alias table yet
//   - no named profiles yet, only the per server ones
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static long dirtyAt = -1;
	private static boolean loading;
	private static @Nullable Path override;

	private Config() {}

	private static Path file() {
		return override != null ? override : FabricLoader.getInstance().getConfigDir().resolve("kestrel").resolve("config.json");
	}

	// switch which file the settings live in, null = back to the main one. what's loaded now is saved first,
	// and a file that doesn't exist yet starts out as a copy of it
	public static void use(@Nullable Path p) {
		save();
		override = p;
		if (p != null && !Files.exists(p)) save();
		else load();
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
					JsonElement e = mods.get(m.name);
					if (e != null) m.load(e.getAsJsonObject());
				}
			}
			if (root.has("kestrel")) Modules.SETTINGS.load(root.getAsJsonObject("kestrel"));
		} catch (IOException | RuntimeException e) {
			// gson throws runtime exceptions on broken json, same outcome either way
			Kestrel.LOG.warn("couldn't read config, using defaults ({})", e.getMessage());
		} finally {
			loading = false;
		}
	}

	public static void save() {
		dirtyAt = -1;
		JsonObject mods = new JsonObject();
		for (Module m : Modules.all()) mods.add(m.name, m.save());
		JsonObject root = new JsonObject();
		root.add("modules", mods);
		root.add("kestrel", Modules.SETTINGS.save());

		Path f = file();
		// write next to it then swap, so a crash mid write can't leave a half written config
		Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
		try {
			Files.createDirectories(f.getParent());
			Files.writeString(tmp, GSON.toJson(root));
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Kestrel.LOG.warn("config save failed: {}", e.getMessage());
		}
	}
}
