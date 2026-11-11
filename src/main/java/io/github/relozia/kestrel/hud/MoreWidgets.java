package io.github.relozia.kestrel.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.gui.Shape;
import io.github.relozia.kestrel.gui.Ui;
import io.github.relozia.kestrel.setting.Setting;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

// the last hud widgets: debug lines, what you're looking at, inventory, packs, music
public final class MoreWidgets {
	private MoreWidgets() {}

	// ---------- mini debug ----------

	public static class MiniDebug extends HudWidget {
		private final Setting.Color color = add(new Setting.Color("Color", 0xF5F7FAFC, 0xF5F7FAFC, RUST, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6).withAlpha());
		private final Setting.Multi lines = add(new Setting.Multi("Lines", List.of("Biome", "Facing", "Light", "Chunk", "Dimension", "Day", "Block", "Looking At"),
			"Biome", "Facing", "Light"));

		public MiniDebug() {
			super("Mini Debug", '\ue0f9', "Just the F3 lines you pick: biome, facing, light, chunk and more.", 0.01f, 0.4f);
		}

		private static String pretty(String path) {
			StringBuilder sb = new StringBuilder();
			for (String w : path.split("[_/]")) {
				if (w.isEmpty()) continue;
				if (!sb.isEmpty()) sb.append(' ');
				sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
			}
			return sb.toString();
		}

		private List<String[]> rows() {
			Minecraft mc = Minecraft.getInstance();
			List<String[]> out = new ArrayList<>();
			if (mc.player == null || mc.level == null) {
				for (String l : lines.get()) out.add(new String[] {l, "..."});
				return out;
			}
			BlockPos b = mc.player.blockPosition();
			for (String l : lines.options) {
				if (!lines.has(l)) continue;
				switch (l) {
					case "Biome" -> out.add(new String[] {l, mc.level.isInsideBuildHeight(b.getY()) ? mc.level.getBiome(b).unwrapKey().map(k -> pretty(k.identifier().getPath())).orElse("Unknown") : "Unknown"});
					case "Facing" -> {
						var d = mc.player.getDirection();
						String axis = switch (d) {
							case NORTH -> "-Z";
							case SOUTH -> "+Z";
							case WEST -> "-X";
							default -> "+X";
						};
						out.add(new String[] {l, String.format(Locale.ROOT, "%s %s (%.1f)", pretty(d.getName()), axis, Mth.wrapDegrees(mc.player.getYRot()))});
					}
					case "Light" -> out.add(new String[] {l, mc.level.getBrightness(LightLayer.SKY, b) + " sky, " + mc.level.getBrightness(LightLayer.BLOCK, b) + " block"});
					case "Chunk" -> {
						// servers that turn on reduced debug info don't want this shown, same as f3
						if (!mc.showOnlyReducedInfo()) out.add(new String[] {l, String.format(Locale.ROOT, "%d %d (in %d %d)", SectionPos.blockToSectionCoord(b.getX()), SectionPos.blockToSectionCoord(b.getZ()), b.getX() & 15, b.getZ() & 15)});
					}
					case "Dimension" -> out.add(new String[] {l, pretty(mc.level.dimension().identifier().getPath())});
					case "Day" -> out.add(new String[] {l, Long.toString(mc.level.getOverworldClockTime() / 24000)});
					case "Block" -> out.add(new String[] {l, b.getX() + " " + b.getY() + " " + b.getZ()});
					default -> {
						if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
							out.add(new String[] {l, pretty(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(bh.getBlockPos()).getBlock()).getPath())});
						}
					}
				}
			}
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

	// ---------- waila ----------

	public static class Waila extends HudWidget {
		private final Setting.Toggle blocks = add(new Setting.Toggle("Blocks", true));
		private final Setting.Toggle entities = add(new Setting.Toggle("Entities", true));
		private final Setting.Toggle modName = add(new Setting.Toggle("Show Mod Name", true));
		private final Setting.Toggle health = add(new Setting.Toggle("Show Health", false));

		public Waila() {
			super("WAILA", '\ue536', "The name and icon of the block or mob you're looking at.", 0.5f, 0.01f);
		}

		// gui pixels and vanilla's font here, it sits under the crosshair and should match the rest of the hud
		@Override protected boolean units() { return false; }
		@Override protected Look look() { return Look.NONE; }

		private record Target(ItemStack icon, String name, String source) {}

		private static String mod(String ns) {
			if (ns.equals("minecraft")) return "Minecraft";
			return FabricLoader.getInstance().getModContainer(ns).map(c -> c.getMetadata().getName()).orElse(ns);
		}

		private Target target() {
			Minecraft mc = Minecraft.getInstance();
			if (blocks.on() && mc.level != null && mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
				BlockState s = mc.level.getBlockState(bh.getBlockPos());
				return new Target(s.getCloneItemStack(mc.level, bh.getBlockPos(), false), s.getBlock().getName().getString(),
					mod(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace()));
			}
			if (entities.on() && mc.hitResult instanceof EntityHitResult eh) {
				Entity e = eh.getEntity();
				ItemStack pick = e.getPickResult();
				String src = mod(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
				// health replaces the mod line when you ask for it, mobs are almost always from minecraft anyway
				if (health.on() && e instanceof LivingEntity l) src = String.format(Locale.ROOT, "%.1f / %.0f hearts", l.getHealth() / 2, l.getMaxHealth() / 2);
				return new Target(pick == null ? ItemStack.EMPTY : pick, e.getName().getString(), src);
			}
			return editing ? new Target(ItemStack.EMPTY, "WAILA", "Minecraft") : null;
		}

		@Override
		public boolean visible() {
			return target() != null;
		}

		private boolean two() {
			return modName.on() || health.on();
		}

		@Override
		protected int contentW() {
			Target t = target();
			if (t == null) return 40;
			return 8 + (t.icon().isEmpty() ? 0 : 20) + Math.max(font().width(t.name()), two() ? font().width(t.source()) : 0);
		}

		@Override
		protected int contentH() {
			return two() ? 26 : 18;
		}

		@Override
		protected void content(GuiGraphicsExtractor g) {
			Target t = target();
			if (t == null) return;
			int w = contentW(), h = contentH();
			if (bg.on()) box(g, w, h, 6);
			if (!t.icon().isEmpty()) g.item(t.icon(), 4, (h - 16) / 2);
			int tx = 4 + (t.icon().isEmpty() ? 0 : 20);
			if (two()) {
				g.text(font(), t.name(), tx, 4, 0xFFF7F7F7, true);
				g.text(font(), t.source(), tx, 15, 0xFF6F8FD8, true);
			} else {
				g.text(font(), t.name(), tx, 5, 0xFFF7F7F7, true);
			}
		}
	}

	// ---------- inventory ----------

	public static class InventoryHud extends HudWidget {
		private static List<ItemStack> sample;
		private final Setting.Toggle hotbar = add(new Setting.Toggle("Show Hotbar", false));
		private final Setting.Slider size = add(new Setting.Slider("Scale", 1, 0.75, 2, 0.05, "x"));

		public InventoryHud() {
			super("Inventory HUD", '\ue0e9', "Your inventory in a little grid, the hotbar too if you want.", 0.99f, 0.6f);
		}

		@Override protected boolean units() { return false; }
		@Override protected Look look() { return Look.NONE; }

		private int rows() {
			return hotbar.on() ? 4 : 3;
		}

		@Override protected int contentW() { return Math.round(8 + 162 * size.get().floatValue()); }
		@Override protected int contentH() { return Math.round(8 + 18 * rows() * size.get().floatValue()); }

		// the editor gets a sample row when your inventory is empty, otherwise there's nothing to look at
		private static List<ItemStack> sample() {
			if (sample == null) {
				sample = List.of(new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.GOLDEN_APPLE, 12), new ItemStack(Items.EXPERIENCE_BOTTLE, 32),
					new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.OBSIDIAN, 64), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2),
					new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.SHIELD), new ItemStack(Items.COBWEB, 16));
			}
			return sample;
		}

		@Override
		protected void content(GuiGraphicsExtractor g) {
			Minecraft mc = Minecraft.getInstance();
			if (bg.on()) box(g, contentW(), contentH(), 6);
			boolean empty = mc.player == null || mc.player.getInventory().isEmpty();
			float s = size.get().floatValue();
			g.pose().pushMatrix();
			g.pose().translate(4, 4);
			g.pose().scale(s, s);
			for (int i = 0; i < rows() * 9; i++) {
				int x = i % 9 * 18, y = i / 9 * 18;
				g.fill(x + 1, y + 1, x + 17, y + 17, 0x704F4F4F);
				ItemStack st = editing && empty ? i < 9 ? sample().get(i) : ItemStack.EMPTY
					: mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(i < 27 ? 9 + i : i - 27);
				if (st.isEmpty()) continue;
				g.item(st, x + 1, y + 1);
				g.itemDecorations(font(), st, x + 1, y + 1);
			}
			g.pose().popMatrix();
			if (!bg.on() && editing) Shape.outline(g, 0, 0, contentW(), contentH(), 6, 0x40DCE6F5, 1);
		}
	}

	// ---------- packs ----------

	// your own resource packs and the server's, highest first, each with its icon
	public static class PackDisplay extends HudWidget {
		private static final float ROW = 20, PAD = 7, TEXT = 12;
		private final Setting.Slider max = add(new Setting.Slider("Max Packs Shown", 5, 1, 10, 1, ""));
		private final Setting.Toggle showIcons = add(new Setting.Toggle("Show Icons", true));
		private final Map<String, Identifier> icons = new HashMap<>();

		public PackDisplay() {
			super("Pack Display", '\ue0ff', "The resource packs you're using, with their icons.", 0.99f, 0.02f);
		}

		@Override
		protected Look look() {
			return Look.NONE;
		}

		private List<Pack> packs() {
			List<Pack> out = new ArrayList<>();
			for (Pack p : Minecraft.getInstance().getResourcePackRepository().getSelectedPacks()) {
				if (p.getId().startsWith("file/") || p.getId().startsWith("server/")) out.add(p);
			}
			return out.reversed();
		}

		private static String title(Pack p) {
			return p.getId().startsWith("server/") ? "Server Pack" : p.getTitle().getString();
		}

		// "No resource packs", or "+N more" past the limit
		private List<String> extra(List<Pack> l) {
			if (l.isEmpty()) return List.of("No resource packs");
			int n = max.get().intValue();
			return l.size() > n ? List.of("+" + (l.size() - n) + " more") : List.of();
		}

		private List<Pack> shown(List<Pack> l) {
			return l.subList(0, Math.min(l.size(), max.get().intValue()));
		}

		// loaded once per pack and kept, same thing the pack screen does
		private Identifier icon(Pack p) {
			return icons.computeIfAbsent(p.getId(), id -> {
				Identifier out = Kestrel.id("pack_icon/" + Integer.toHexString(id.hashCode()));
				try (var res = p.open()) {
					var sup = res.getRootResource("pack.png");
					if (sup == null) return null;
					try (InputStream in = sup.get()) {
						Minecraft.getInstance().getTextureManager().register(out, new DynamicTexture(() -> "kestrel pack icon", NativeImage.read(in)));
					}
				} catch (IOException e) {
					Kestrel.LOG.warn("no icon for pack {}: {}", id, e.getMessage());
					return null;
				}
				return out;
			});
		}

		@Override
		protected int contentW() {
			List<Pack> l = packs();
			float t = 0;
			for (Pack p : shown(l)) t = Math.max(t, width(title(p), LABEL_FONT, TEXT));
			for (String s : extra(l)) t = Math.max(t, width(s, LABEL_FONT, TEXT));
			return Math.round(14 + (showIcons.on() ? 22 : 0) + t);
		}

		@Override
		protected int contentH() {
			List<Pack> l = packs();
			return Math.round(14 + ROW * (shown(l).size() + extra(l).size()) - 4);
		}

		@Override
		protected void content(GuiGraphicsExtractor g) {
			List<Pack> l = packs();
			int w = contentW(), h = contentH();
			if (bg.on()) box(g, w, h, 6);
			float y = PAD, tx = PAD + (showIcons.on() ? 22 : 0);
			for (Pack p : shown(l)) {
				if (showIcons.on()) {
					Identifier ic = icon(p);
					if (ic != null) g.blit(RenderPipelines.GUI_TEXTURED, ic, Math.round(PAD), Math.round(y), 0, 0, 16, 16, 16, 16);
					else Shape.rect(g, PAD, y, 16, 16, 3, 0x40DCE6F5);
				}
				draw(g, title(p), LABEL_FONT, tx, y + 12, 0xF2F7FAFC, TEXT);
				y += ROW;
			}
			for (String s : extra(l)) {
				draw(g, s, LABEL_FONT, PAD, y + 12, 0x99F7FAFC, TEXT);
				y += ROW;
			}
		}
	}

	// ---------- music ----------

	// what's playing on the pc, a 250x70 card: cover, title, artist, progress, little equalizer
	// a small powershell helper reads windows' media session (the same thing the volume flyout shows) and prints a line
	// every half second. windows only
	// issues:
	//   - apps that don't report a timeline get no progress bar
	//   - the helper costs a powershell process (~40mb) while the widget is on
	public static class MusicPlayer extends HudWidget {
		private static final Identifier ART = Kestrel.id("music_art");
		private static final float W = 250, H = 70, PAD = 9, COVER = 52, R = 8;

		private final Setting.Slider size = add(new Setting.Slider("Size", 1, 0.7, 1.6, 0.05, "x"));
		private final Setting.Toggle albumArt = add(new Setting.Toggle("Album Art", true));
		private final Setting.Toggle source = add(new Setting.Toggle("Show Source App", false));
		private final Setting.Toggle hidePaused = add(new Setting.Toggle("Hide When Paused", false));
		private final Setting.Bind toggle = add(new Setting.Bind("Play/Pause Key", -1));
		private final Setting.Bind next = add(new Setting.Bind("Next Track Key", -1));
		private final Setting.Bind prev = add(new Setting.Bind("Previous Track Key", -1));

		private Process proc;
		private Path dir;
		private volatile String title = "", artist = "", app = "", error = "";
		private volatile boolean playing, artNew;
		private volatile double pos, dur;
		private volatile long at;
		private boolean hasArt;
		private float vis;
		private double shownPos;
		private long last = Util.getMillis();

		public MusicPlayer() {
			super("Music Player", '\ue122', "What's playing on your PC with cover, artist and progress, plus optional media keys. Windows only.", 0.01f, 0.64f);
		}

		@Override
		protected Look look() {
			return Look.NONE;
		}

		@Override
		protected void enable() {
			if (proc != null) return;
			if (Util.getPlatform() != Util.OS.WINDOWS) return;
			try {
				dir = Files.createTempDirectory("kestrel-media");
				Path script = dir.resolve("media.ps1");
				try (InputStream in = Minecraft.getInstance().getResourceManager().open(Kestrel.id("media/media.ps1"))) {
					Files.copy(in, script);
				}
				proc = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString(),
					dir.resolve("cmd.txt").toString(), dir.resolve("art.png").toString(), Long.toString(ProcessHandle.current().pid())).start();
				Thread t = new Thread(this::read, "kestrel media reader");
				t.setDaemon(true);
				t.start();
			} catch (IOException e) {
				Kestrel.LOG.warn("music player helper didn't start: {}", e.getMessage());
			}
		}

		@Override
		protected void disable() {
			if (proc != null) proc.destroy();
			proc = null;
			title = "";
		}

		private void read() {
			Process p = proc;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					JsonObject o;
					try {
						o = JsonParser.parseString(line).getAsJsonObject();
					} catch (RuntimeException e) {
						// one bad line shouldn't end the reader, the next one comes half a second later
						Kestrel.LOG.warn("music helper sent a bad line: {}", e.getMessage());
						continue;
					}
					if (o.has("error")) {
						// logged once per new message, the helper repeats it every half second
						String e = o.get("error").getAsString();
						if (!e.equals(error)) Kestrel.LOG.warn("music helper: {}", e);
						error = e;
						continue;
					}
					error = "";
					title = o.get("title").getAsString();
					if (title.isEmpty()) continue;
					artist = o.get("artist").getAsString();
					app = o.has("app") && !o.get("app").isJsonNull() ? o.get("app").getAsString() : "";
					playing = o.get("playing").getAsBoolean();
					dur = o.get("dur").getAsDouble();
					// the timeline is only as fresh as the app made it, "age" says how old it is
					pos = o.get("pos").getAsDouble() + (playing ? o.get("age").getAsDouble() : 0);
					at = Util.getMillis();
					String art = o.get("art").getAsString();
					if (art.equals("new")) artNew = true;
					else if (art.equals("none")) hasArt = false;
				}
			} catch (IOException e) {
				Kestrel.LOG.warn("music player helper stopped: {}", e.getMessage());
			}
		}

		private void send(String cmd) {
			if (dir == null) return;
			try {
				Files.writeString(dir.resolve("cmd.txt"), cmd);
			} catch (IOException e) {
				Kestrel.LOG.warn("music command failed: {}", e.getMessage());
			}
		}

		@Override
		public void tick(Minecraft mc) {
			if (toggle.pressed(mc)) send("toggle");
			if (next.pressed(mc)) send("next");
			if (prev.pressed(mc)) send("prev");
			if (artNew) {
				artNew = false;
				try (InputStream in = Files.newInputStream(dir.resolve("art.png"))) {
					mc.getTextureManager().register(ART, new DynamicTexture(() -> "kestrel music art", NativeImage.read(in)));
					hasArt = true;
				} catch (IOException e) {
					hasArt = false;
				}
			}
		}

		private boolean preview() {
			return editing && title.isEmpty();
		}

		private boolean wanted() {
			return preview() || !title.isEmpty() && (playing || !hidePaused.on());
		}

		@Override
		public boolean visible() {
			return wanted() || vis > 0.002f;
		}

		@Override protected int contentW() { return Math.round(W * size.get().floatValue()); }
		@Override protected int contentH() { return Math.round(H * size.get().floatValue()); }

		private static String time(double s) {
			int t = (int) Math.max(0, s);
			return t >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", t / 3600, t / 60 % 60, t % 60) : String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60);
		}

		// "SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify" and "chrome.exe" both read as a name
		private static String appName(String id) {
			String s = id;
			int bang = s.lastIndexOf('!');
			if (bang >= 0) s = s.substring(bang + 1);
			if (s.toLowerCase(Locale.ROOT).endsWith(".exe")) s = s.substring(0, s.length() - 4);
			int dot = s.lastIndexOf('.');
			if (dot >= 0 && dot < s.length() - 1) s = s.substring(dot + 1);
			return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
		}

		// the font has no curly quotes or fancy dashes, swap them for plain ones instead of showing boxes
		private static String clean(String s) {
			return s.replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"')
				.replace('–', '-').replace('—', '-').replace("…", "...").replace(' ', ' ');
		}

		@Override
		protected void content(GuiGraphicsExtractor g) {
			long now = Util.getMillis();
			float dt = Math.min(0.1f, (now - last) / 1000f);
			last = now;
			vis = Mth.clamp(vis + (wanted() ? 1 : -1) * dt * 6, 0, 1);
			float a = vis * vis * (3 - 2 * vis);
			if (a <= 0.002f) return;
			boolean pv = preview();
			String t = clean(pv ? "Song Title" : title), ar = clean(pv ? "Artist" : artist.isBlank() ? "Unknown artist" : artist);
			if (source.on() && !pv && !app.isEmpty()) ar += "  ·  " + appName(app);
			boolean play = pv || playing;
			double d = pv ? 225 : dur;
			// never steps back a little when the helper's reading lags, only on a real seek or a pause
			double p = pv ? 83 : Math.min(dur, pos + (playing ? (now - at) / 1000.0 : 0));
			if (!pv && (p > shownPos || shownPos - p > 1.5 || !playing)) shownPos = p;
			if (!pv) p = shownPos;

			g.pose().pushMatrix();
			g.pose().scale(size.get().floatValue(), size.get().floatValue());
			if (bg.on()) box(g, W, H, R, a, false);
			int accent = Ui.accent();
			float tx = PAD;
			if (albumArt.on()) {
				if (hasArt && !pv) {
					Shape.image(g, ART, PAD, PAD, COVER, COVER, 7, a);
					Shape.outline(g, PAD, PAD, COVER, COVER, 7, ARGB.multiplyAlpha(0x1AFFFFFF, a), 1);
				} else {
					int top = ARGB.srgbLerp(0.25f, accent, 0xFF000000), bot = ARGB.srgbLerp(0.65f, accent, 0xFF000000);
					Shape.gradient(g, PAD, PAD, COVER, COVER, 7, ARGB.multiplyAlpha(top, a), ARGB.multiplyAlpha(top, a), ARGB.multiplyAlpha(bot, a), ARGB.multiplyAlpha(bot, a));
					float sec = now / 1000f, x0 = PAD + (COVER - 32) / 2;
					for (int i = 0; i < 4; i++) {
						float lvl = play ? 0.35f + 0.65f * Math.abs(Mth.sin(sec * (2.2f + 0.7f * i) + 1.3f * i)) : 0.25f;
						float bh = 6 + 20 * lvl;
						Shape.rect(g, x0 + i * 9, PAD + 38 - bh, 5, bh, 2, ARGB.multiplyAlpha(0xD9FFFFFF, a));
					}
				}
				tx = 71;
			}
			float right = W - 11, tw = right - tx;
			marquee(g, t, HUD, 13, tx, 21, tw, ARGB.multiplyAlpha(0xF5F7FAFC, a), now);
			marquee(g, ar, LIGHT, 11, tx, 36, tw, ARGB.multiplyAlpha(0x99F7FAFC, a), now + 1500);
			Shape.rect(g, tx, 48, tw, 4, 2, ARGB.multiplyAlpha(0x1FFFFFFF, a));
			if (d > 0) {
				float fill = Math.max(4, (float) (tw * Mth.clamp(p / d, 0, 1)));
				int l = ARGB.multiplyAlpha(ARGB.srgbLerp(0.25f, accent, 0xFFFFFFFF), a), rr = ARGB.multiplyAlpha(accent, a);
				Shape.gradient(g, tx, 48, fill, 4, 2, l, rr, rr, l);
				Shape.rect(g, tx + fill - 3.2f, 50 - 3.2f, 6.4f, 6.4f, 3.2f, ARGB.multiplyAlpha(0xFFFFFFFF, a));
				String ts = time(p) + " | " + time(d);
				draw(g, ts, LIGHT, right - width(ts, LIGHT, 10), 63, ARGB.multiplyAlpha(0x6BF7FAFC, a), 10);
			}
			// three bouncing bars while it plays, a pause sign when it doesn't
			float sx = tx, sy = 55.5f;
			if (play) {
				float sec = now / 1000f;
				for (int i = 0; i < 3; i++) {
					float bh = 3 + 6 * (0.35f + 0.65f * Math.abs(Mth.sin(sec * (3.1f + 0.9f * i) + i)));
					Shape.rect(g, sx + i * 3.5f, sy + 9 - bh, 2.2f, bh, 1, ARGB.multiplyAlpha(accent, a));
				}
			} else {
				Shape.rect(g, sx, sy + 1, 2.6f, 8, 1, ARGB.multiplyAlpha(0x99F7FAFC, a));
				Shape.rect(g, sx + 4.6f, sy + 1, 2.6f, 8, 1, ARGB.multiplyAlpha(0x99F7FAFC, a));
			}
			g.pose().popMatrix();
		}

		// too long to fit: hold, slide to the end, hold, slide back
		private static void marquee(GuiGraphicsExtractor g, String s, net.minecraft.network.chat.Style st, float size, float x, float base, float max, int color, long now) {
			float full = width(s, st, size), over = full - max + 6;
			if (over <= 6) {
				draw(g, s, st, x, base, color, size);
				return;
			}
			float cycle = 3 + over / 22, period = 2 * cycle + 3, tt = (now / 1000f) % period, off;
			if (tt < 1.5f) off = 0;
			else if (tt < 1.5f + cycle) off = (tt - 1.5f) / cycle * over;
			else if (tt < 3 + cycle) off = over;
			else off = over * (1 - (tt - 3 - cycle) / cycle);
			g.enableScissor(Math.round(x), Math.round(base - size - 2), Math.round(x + max), Math.round(base + 4));
			draw(g, s, st, x - off, base, color, size);
			g.disableScissor();
		}
	}
}
