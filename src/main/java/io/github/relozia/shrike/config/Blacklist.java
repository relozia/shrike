package io.github.relozia.shrike.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.gui.Notice;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.module.Modules;
import io.github.relozia.shrike.setting.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

// which modules a server doesn't allow, pulled from relozia/pubshrike. no setting, it can't be turned off.
// this is NOT anticheat, anyone can edit the jar. it's here so you don't get banned for forgetting to turn
// something off before joining
// issues:
//   - a server you reach by raw ip can't be matched, the list is keyed on hostnames
//   - rules change faster than the list does, which is why every entry carries a source url
public final class Blacklist {
	private static final String URL = "https://raw.githubusercontent.com/relozia/pubshrike/main/blocklist.json";
	private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("shrike").resolve("blocklist-cache.json");
	// the format this jar understands. a newer list is ignored outright rather than half read
	private static final int VERSION = 1;
	private static final long REFETCH_AFTER = Duration.ofHours(6).toMillis();
	// a single entry blocking more than this share of everything is a typo or a bad push, not a real rule
	private static final float SANITY = 0.6f;

	// own threads, same reason ShrikeTitleScreen.PINGS has its own: anything of minecraft's that resolves
	// through the thread's context class loader explodes on Util's pools, and the dev environment never shows it
	private static final ExecutorService NET = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Shrike Blacklist");
		t.setDaemon(true);
		t.setContextClassLoader(Blacklist.class.getClassLoader());
		return t;
	});

	private record Entry(String name, List<String> match, List<String> block, List<String> allow, Map<String, JsonObject> settings) {}

	private static List<Entry> entries = List.of();
	private static List<String> globalBlock = List.of();
	private static long fetchedAt;
	private static @Nullable String server;

	private Blacklist() {}

	// the server you're on, as the list sees it: lowercase, no port
	private static String host(ServerData d) {
		String ip = d.ip.toLowerCase(Locale.ROOT).trim();
		int colon = ip.lastIndexOf(':');
		if (colon > 0 && ip.indexOf(':') == colon) ip = ip.substring(0, colon);
		return ip.endsWith(".") ? ip.substring(0, ip.length() - 1) : ip;
	}

	// suffix match, so one "funtime.su" line covers mc. and play. without listing every subdomain
	private static boolean covers(String pattern, String h) {
		return h.equals(pattern) || h.endsWith("." + pattern);
	}

	public static @Nullable String server() {
		return server;
	}

	// ---------- loading ----------

	public static void init() {
		readCache();
		refresh();
	}

	private static void readCache() {
		if (!Files.exists(CACHE)) return;
		try {
			JsonObject o = JsonParser.parseString(Files.readString(CACHE)).getAsJsonObject();
			fetchedAt = o.has("shrikeFetchedAt") ? o.get("shrikeFetchedAt").getAsLong() : 0;
			parse(o);
		} catch (IOException | RuntimeException e) {
			Shrike.LOG.warn("blacklist cache unreadable, ignoring it: {}", e.getMessage());
		}
	}

	private static void refresh() {
		if (System.currentTimeMillis() - fetchedAt < REFETCH_AFTER) return;
		NET.execute(() -> {
			try (HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()) {
				HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(10))
					.header("User-Agent", "Shrike").GET().build();
				HttpResponse<InputStream> res = c.send(req, HttpResponse.BodyHandlers.ofInputStream());
				if (res.statusCode() != 200) {
					Shrike.LOG.warn("blacklist fetch got http {}, keeping the cached one", res.statusCode());
					return;
				}
				String body = new String(res.body().readAllBytes());
				JsonObject o = JsonParser.parseString(body).getAsJsonObject();
				// parse before writing the cache, a list this jar can't read isn't worth keeping
				if (!parse(o)) return;
				fetchedAt = System.currentTimeMillis();
				o.addProperty("shrikeFetchedAt", fetchedAt);
				Files.createDirectories(CACHE.getParent());
				Files.writeString(CACHE, o.toString());
			} catch (IOException | InterruptedException | RuntimeException e) {
				// offline, github down, dns. the cached list carries on, and with no cache nothing is blocked
				Shrike.LOG.warn("blacklist fetch failed, using what's cached: {}", e.getMessage());
			}
		});
	}

	private static boolean parse(JsonObject root) {
		int v = root.has("version") ? root.get("version").getAsInt() : 0;
		if (v != VERSION) {
			Shrike.LOG.warn("blacklist is version {}, this build reads {}. ignoring it", v, VERSION);
			return false;
		}
		int total = Math.max(1, Modules.all().size());
		List<Entry> out = new ArrayList<>();
		for (JsonElement e : root.has("servers") ? root.getAsJsonArray("servers") : new JsonArray()) {
			JsonObject s = e.getAsJsonObject();
			List<String> match = strings(s, "match");
			List<String> block = strings(s, "block");
			Map<String, JsonObject> settings = new HashMap<>();
			if (s.has("block_settings")) {
				for (Map.Entry<String, JsonElement> m : s.getAsJsonObject("block_settings").entrySet()) {
					settings.put(m.getKey(), m.getValue().getAsJsonObject());
				}
			}
			String name = s.has("name") ? s.get("name").getAsString() : match.isEmpty() ? "this server" : match.getFirst();
			if (block.size() > total * SANITY) {
				Shrike.LOG.error("blacklist entry {} blocks {} of {} modules, treating it as broken", name, block.size(), total);
				continue;
			}
			if (!match.isEmpty()) out.add(new Entry(name, match, block, strings(s, "allow"), settings));
		}
		entries = List.copyOf(out);
		globalBlock = root.has("default") ? strings(root.getAsJsonObject("default"), "block") : List.of();
		return true;
	}

	private static List<String> strings(JsonObject o, String key) {
		if (!o.has(key)) return List.of();
		List<String> out = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString().toLowerCase(Locale.ROOT));
		return out;
	}

	// ---------- applying ----------

	// only ever on join and on leaving, never mid session. a widget vanishing in the middle of a fight because
	// a refresh landed would be worse than being a few hours out of date
	public static void joined() {
		refresh();
		ServerData d = Minecraft.getInstance().getCurrentServer();
		if (d == null) {
			clear();
			return;
		}
		applyFor(host(d));
	}

	// reads a list from text, false when it's a version this build doesn't understand
	public static boolean load(String json) {
		try {
			return parse(JsonParser.parseString(json).getAsJsonObject());
		} catch (RuntimeException e) {
			Shrike.LOG.warn("blacklist wouldn't parse: {}", e.getMessage());
			return false;
		}
	}

	// split off joined() so the matching can be driven with a hostname on its own, it's the part worth checking
	public static void applyFor(String h) {
		Set<String> ids = new LinkedHashSet<>(globalBlock);
		Map<String, JsonObject> settings = new HashMap<>();
		String name = null;
		for (Entry en : entries) {
			if (en.match.stream().noneMatch(p -> covers(p, h))) continue;
			name = en.name;
			ids.addAll(en.block);
			settings.putAll(en.settings);
			// an entry can hand back something the global list took away
			en.allow.forEach(ids::remove);
		}
		server = name;
		apply(ids, settings);
	}

	public static void left() {
		clear();
	}

	private static void clear() {
		server = null;
		apply(Set.of(), Map.of());
	}

	private static void apply(Set<String> ids, Map<String, JsonObject> settings) {
		List<String> hit = new ArrayList<>();
		for (Module m : Modules.all()) {
			boolean block = ids.contains(m.id);
			if (block && m.enabled() && !m.blocked()) hit.add(m.name);
			m.block(block);
			JsonObject forced = settings.get(m.id);
			for (Setting<?> st : m.settings()) {
				boolean sb = forced != null && forced.has(st.name);
				if (sb && !st.blocked()) hit.add(m.name + ": " + st.name);
				st.block(sb ? forced.get(st.name) : null);
			}
		}
		if (hit.isEmpty()) return;
		// the long sentence goes in the body, not the title: Notice wraps the body but a title is one unwrapped
		// line that sets the whole toast's width, so it would stretch right across the screen
		StringBuilder sb = new StringBuilder("The following modules are blacklisted on this server and have been disabled:\n");
		for (int i = 0; i < hit.size() && i < 8; i++) sb.append('\n').append(hit.get(i));
		if (hit.size() > 8) sb.append("\n and ").append(hit.size() - 8).append(" more");
		Notice.show("blacklist", "Blacklisted Modules", Component.literal(sb.toString()), 7000);
	}
}
