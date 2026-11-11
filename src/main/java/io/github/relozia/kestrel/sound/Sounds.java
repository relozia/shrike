package io.github.relozia.kestrel.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.config.Config;
import io.github.relozia.kestrel.gui.Notice;
import io.github.relozia.kestrel.gui.SoundsScreen;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryUtil;

// key and click sounds, and game sound volume per group or per sound
public final class Sounds {
	private Sounds() {}

	// ---------- key sounds ----------

	// played straight through OpenAL instead of the game's sound system, so a pack is just a folder of files,
	// no resource pack or sounds.json. minecraft's context is current for the whole process, any thread can use it
	// issues:
	//   - a new pack folder only shows up in the list after a restart
	//   - changing the audio device in options drops our buffers, they reload on the next key press
	public static class KeySounds extends Module {
		private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("kestrel").resolve("soundpacks");
		static KeySounds INSTANCE;

		private final Setting.Toggle keys = add(new Setting.Toggle("Keyboard", true));
		private final Setting.Choice keyPack = add(new Setting.Choice("Key Pack", "Default", packs())).when(keys::on);
		private final Setting.Slider keyVolume = add(new Setting.Slider("Key Volume", 50, 0, 100, 5, "%")).when(keys::on);
		private final Setting.Choice when = add(new Setting.Choice("Key Sounds When", "Always", "Always", "Only When Typing")).when(keys::on);
		private final Setting.Toggle mouse = add(new Setting.Toggle("Mouse Clicks", false));
		private final Setting.Choice mousePack = add(new Setting.Choice("Mouse Pack", "Default", packs())).when(mouse::on);
		private final Setting.Slider mouseVolume = add(new Setting.Slider("Mouse Volume", 50, 0, 100, 5, "%")).when(mouse::on);
		private final Setting.Slider amplify = add(new Setting.Slider("Amplify", 1, 1, 3, 1, "x"));

		// pack name to its OpenAL buffers, both packs can be loaded at once
		private final Map<String, List<Integer>> buffers = new HashMap<>();
		private final int[] sources = new int[12];
		private long context;
		private int next;

		public KeySounds() {
			super("Key Sounds", Category.UTILITY, '\ue284', "Mechanical keyboard sounds when you press keys or click. Only you hear them.");
			INSTANCE = this;
		}

		private static String[] packs() {
			List<String> out = new ArrayList<>(List.of("Default"));
			if (Files.isDirectory(DIR)) {
				try (Stream<Path> s = Files.list(DIR)) {
					s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().forEach(out::add);
				} catch (IOException e) {
					Kestrel.LOG.warn("couldn't list sound packs: {}", e.getMessage());
				}
			}
			return out.toArray(String[]::new);
		}

		// typing only means while some screen is open, chat, a sign, a menu. walking around stays quiet
		public static void key() {
			KeySounds k = INSTANCE;
			if (k == null || !k.on() || !k.keys.on()) return;
			if (k.when.get().equals("Only When Typing") && Minecraft.getInstance().gui.screen() == null) return;
			k.play(k.keyPack.get(), k.keyVolume);
		}

		// "Default" means a different built in pack for the mouse, real mouse clicks instead of key presses
		public static void click() {
			KeySounds k = INSTANCE;
			if (k == null || !k.on() || !k.mouse.on()) return;
			k.play(k.mousePack.get().equals("Default") ? "Default Mouse" : k.mousePack.get(), k.mouseVolume);
		}

		private void play(String pack, Setting.Slider vol) {
			List<Integer> b = ready(pack);
			if (b.isEmpty()) return;
			Minecraft mc = Minecraft.getInstance();
			int buf = b.get(ThreadLocalRandom.current().nextInt(b.size()));
			// a little pitch wobble so fast typing doesn't sound like one sample on repeat
			float pitch = 0.94f + ThreadLocalRandom.current().nextFloat() * 0.12f;
			float gain = vol.get().floatValue() / 100 * mc.options.getSoundSourceVolume(SoundSource.MASTER);
			// amplify is the same sample started on several sources at once, OpenAL caps one source's gain at 1
			for (int i = 0; i < amplify.get().intValue(); i++) {
				int src = sources[next++ % sources.length];
				AL10.alSourceStop(src);
				AL10.alSourcei(src, AL10.AL_BUFFER, buf);
				AL10.alSourcef(src, AL10.AL_GAIN, gain);
				AL10.alSourcef(src, AL10.AL_PITCH, pitch);
				AL10.alSourcePlay(src);
			}
		}

		// loads a pack the first time it's used, everything again if the game made a new audio context
		// (device change, F3+T), the old buffers died with the old one
		private List<Integer> ready(String pack) {
			long ctx = ALC10.alcGetCurrentContext();
			if (ctx == 0) return List.of();
			if (ctx != context) {
				buffers.clear();
				context = ctx;
				for (int i = 0; i < sources.length; i++) {
					sources[i] = AL10.alGenSources();
					AL10.alSourcei(sources[i], AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
				}
			}
			List<Integer> got = buffers.get(pack);
			if (got != null) return got;
			got = new ArrayList<>();
			buffers.put(pack, got);
			try {
				if (pack.equals("Default") || pack.equals("Default Mouse")) {
					boolean m = pack.equals("Default Mouse");
					for (int i = 1; i <= (m ? 14 : 9); i++) {
						try (InputStream in = Minecraft.getInstance().getResourceManager().open(Kestrel.id(m ? "keysounds/mouse/click" + i + ".wav" : "keysounds/default/key" + i + ".wav"))) {
							got.add(load(in, false));
						}
					}
				} else {
					try (Stream<Path> s = Files.list(DIR.resolve(pack))) {
						for (Path p : s.sorted().toList()) {
							String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
							if (!n.endsWith(".wav") && !n.endsWith(".ogg")) continue;
							try (InputStream in = Files.newInputStream(p)) {
								got.add(load(in, n.endsWith(".ogg")));
							}
						}
					}
				}
			} catch (IOException | UnsupportedAudioFileException e) {
				Kestrel.LOG.warn("key sound pack {} didn't load: {}", pack, e.getMessage());
			}
			if (got.isEmpty()) Kestrel.LOG.warn("key sound pack {} has no .wav or .ogg files", pack);
			return got;
		}

		private static int load(InputStream in, boolean ogg) throws IOException, UnsupportedAudioFileException {
			ByteBuffer pcm;
			AudioFormat f;
			if (ogg) {
				JOrbisAudioStream s = new JOrbisAudioStream(in);
				f = s.getFormat();
				pcm = s.readAll();
			} else {
				AudioInputStream raw = AudioSystem.getAudioInputStream(new BufferedInputStream(in));
				// whatever the file is, OpenAL gets 16 bit signed little endian
				AudioFormat want = new AudioFormat(raw.getFormat().getSampleRate(), 16, raw.getFormat().getChannels(), true, false);
				byte[] b = AudioSystem.getAudioInputStream(want, raw).readAllBytes();
				f = want;
				pcm = MemoryUtil.memAlloc(b.length).put(b).flip();
			}
			int fmt = f.getChannels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
			int buf = AL10.alGenBuffers();
			AL10.alBufferData(buf, fmt, pcm, (int) f.getSampleRate());
			if (!ogg) MemoryUtil.memFree(pcm);
			return buf;
		}

		@Override
		protected void disable() {
			if (ALC10.alcGetCurrentContext() == context && context != 0) {
				for (int i = 0; i < sources.length; i++) {
					if (sources[i] != 0) AL10.alDeleteSources(sources[i]);
					sources[i] = 0;
				}
				for (List<Integer> l : buffers.values()) for (int b : l) AL10.alDeleteBuffers(b);
			}
			buffers.clear();
			context = 0;
		}
	}

	// ---------- better sounds ----------

	public static class BetterSounds extends Module {
		static BetterSounds INSTANCE;
		// sound id to {volume, pitch}, set per sound in the list screen. presets only ever touch volume
		private static final Map<String, Map<String, Float>> PRESETS = Map.of(
			"PvP", Map.ofEntries(Map.entry("entity.generic.explode", 0.4f), Map.entry("entity.wind_charge.wind_burst", 0.5f),
				Map.entry("entity.wind_charge.throw", 0.6f), Map.entry("item.mace.smash_air", 0.6f), Map.entry("item.mace.smash_ground", 0.6f),
				Map.entry("item.mace.smash_ground_heavy", 0.5f), Map.entry("entity.arrow.hit_player", 1.8f), Map.entry("entity.player.attack.crit", 1.2f),
				Map.entry("item.shield.break", 1.4f), Map.entry("item.totem.use", 0.6f), Map.entry("ambient.cave", 0f),
				Map.entry("weather.rain", 0.3f), Map.entry("weather.rain.above", 0.3f)),
			"Quiet Ambience", Map.of("ambient.cave", 0f, "weather.rain", 0.25f, "weather.rain.above", 0.25f, "entity.lightning_bolt.thunder", 0.3f,
				"block.water.ambient", 0.3f, "block.lava.ambient", 0.3f, "block.lava.pop", 0.3f, "block.portal.ambient", 0.2f),
			"Mute Annoying Mobs", Map.of("entity.villager.ambient", 0f, "entity.villager.trade", 0f, "entity.bee.loop", 0f,
				"entity.bee.loop_aggressive", 0f, "entity.bat.ambient", 0f, "entity.enderman.ambient", 0.2f, "entity.ghast.ambient", 0.2f,
				"entity.ghast.warn", 0.3f));

		private final Setting.Slider hits = add(new Setting.Slider("Hits", 100, 0, 100, 5, "%"));
		private final Setting.Slider hurt = add(new Setting.Slider("Hurt", 100, 0, 100, 5, "%"));
		private final Setting.Slider steps = add(new Setting.Slider("Footsteps", 100, 0, 100, 5, "%"));
		private final Setting.Slider blocks = add(new Setting.Slider("Breaking And Placing", 100, 0, 100, 5, "%"));
		private final Setting.Slider boom = add(new Setting.Slider("Explosions", 100, 0, 100, 5, "%"));
		private final Setting.Slider bows = add(new Setting.Slider("Bows", 100, 0, 100, 5, "%"));
		private final Setting.Slider eat = add(new Setting.Slider("Eating And Drinking", 100, 0, 100, 5, "%"));
		private final Setting.Slider ambience = add(new Setting.Slider("Ambience And Weather", 100, 0, 100, 5, "%"));
		private final Setting.Toggle pvp = add(new Setting.Toggle("PvP Mix", false));
		private final PerSound tweaks = add(new PerSound()).when(() -> false);
		private final Setting.Choice preset;

		public BetterSounds() {
			super("Better Sounds", Category.UTILITY, '\ue1ab', "Turn groups of game sounds down, or any single sound up or down and higher or lower. Only affects what you hear.");
			INSTANCE = this;
			add(new Setting.Action("Every Sound", "Open List", () -> Minecraft.getInstance().gui.setScreen(new SoundsScreen(Minecraft.getInstance().gui.screen()))));
			preset = add(new Setting.Choice("Preset", "PvP", "PvP", "Quiet Ambience", "Mute Annoying Mobs", "Reset Everything"));
			add(new Setting.Action("Apply Preset", "Apply", this::applyPreset));
		}

		public static PerSound tweaks() {
			return INSTANCE.tweaks;
		}

		// presets add on top of what you already set, only Reset Everything starts over
		private void applyPreset() {
			String p = preset.get();
			if (p.equals("Reset Everything")) tweaks.clear();
			else PRESETS.get(p).forEach((id, v) -> tweaks.volume("minecraft:" + id, v));
			Notice.show("Better Sounds", p.equals("Reset Everything") ? "All sounds reset" : "Applied preset: " + p, 1800);
		}

		// the PvP mix leaves what you need to hear (hits, steps, hurt, bows) alone and pushes the rest back
		public static float volume(SoundInstance s, float vanilla) {
			BetterSounds b = INSTANCE;
			if (b == null || !b.on()) return vanilla;
			Identifier id = s.getIdentifier();
			String p = id.getPath();
			float k;
			if (p.startsWith("entity.player.attack")) k = b.pvp.on() ? 1 : pct(b.hits);
			else if (p.startsWith("entity.player.hurt") || p.equals("entity.generic.hurt")) k = b.pvp.on() ? 1 : pct(b.hurt);
			else if (p.endsWith(".step")) k = b.pvp.on() ? 1 : pct(b.steps);
			else if (p.endsWith(".break") || p.endsWith(".place") || p.endsWith(".hit")) k = b.pvp.on() ? 0.6f : pct(b.blocks);
			else if (p.contains("explode") || p.startsWith("entity.tnt")) k = b.pvp.on() ? 0.4f : pct(b.boom);
			else if (p.startsWith("entity.arrow") || p.startsWith("item.bow") || p.startsWith("item.crossbow")) k = b.pvp.on() ? 1 : pct(b.bows);
			else if (p.endsWith(".eat") || p.endsWith(".drink") || p.equals("entity.player.burp")) k = b.pvp.on() ? 0.7f : pct(b.eat);
			else if (p.startsWith("ambient") || p.startsWith("weather") || p.startsWith("block.water.ambient")) k = b.pvp.on() ? 0.3f : pct(b.ambience);
			else k = 1;
			return Math.max(0, vanilla * k * b.tweaks.volume(id.toString()));
		}

		public static float pitch(SoundInstance s, float vanilla) {
			BetterSounds b = INSTANCE;
			if (b == null || !b.on()) return vanilla;
			return Mth.clamp(vanilla * b.tweaks.pitch(s.getIdentifier().toString()), 0.5f, 2);
		}

		private static float pct(Setting.Slider s) {
			return s.get().floatValue() / 100;
		}
	}

	// every changed sound as id -> [volume, pitch]. anything back at 100% drops out so the file only lists what
	// you actually touched
	public static class PerSound extends Setting<Map<String, float[]>> {
		PerSound() {
			super("Per Sound", Map.of());
			value = new HashMap<>();
		}

		public float volume(String id) {
			float[] v = value.get(id);
			return v == null ? 1 : v[0];
		}

		public float pitch(String id) {
			float[] v = value.get(id);
			return v == null ? 1 : v[1];
		}

		public void volume(String id, float f) {
			put(id, f, pitch(id));
		}

		public void pitch(String id, float f) {
			put(id, volume(id), f);
		}

		public boolean touched(String id) {
			return value.containsKey(id);
		}

		private void put(String id, float vol, float pitch) {
			if (Math.abs(vol - 1) < 0.005f && Math.abs(pitch - 1) < 0.005f) value.remove(id);
			else value.put(id, new float[] {vol, pitch});
			Config.dirty();
		}

		void clear() {
			value.clear();
			Config.dirty();
		}

		@Override
		public boolean changed() {
			return !value.isEmpty();
		}

		@Override
		public void reset() {
			clear();
		}

		@Override
		public JsonElement save() {
			JsonObject o = new JsonObject();
			value.forEach((id, v) -> {
				JsonArray a = new JsonArray();
				a.add(v[0]);
				a.add(v[1]);
				o.add(id, a);
			});
			return o;
		}

		@Override
		public void load(JsonElement e) {
			value = new HashMap<>();
			for (var en : e.getAsJsonObject().entrySet()) {
				JsonArray a = en.getValue().getAsJsonArray();
				value.put(en.getKey(), new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()});
			}
		}
	}
}
