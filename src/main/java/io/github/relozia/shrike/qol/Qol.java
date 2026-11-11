package io.github.relozia.shrike.qol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.gui.NotesScreen;
import io.github.relozia.shrike.gui.Notice;
import io.github.relozia.shrike.gui.Ui;
import io.github.relozia.shrike.mixin.JoinMultiplayerAccessor;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

// small things that make the game nicer to use
public final class Qol {
	private Qol() {}

	// ---------- modern keybinds ----------

	// vanilla puts keyboard keys back to held after a menu closes, mouse buttons it forgets. sprint on a side
	// button stops sprinting every time you close your inventory
	public static class ModernKeybinds extends Module {
		static ModernKeybinds INSTANCE;

		// off by default: a held attack coming back after closing a chest swings at whatever you're looking at
		private final Setting.Toggle attackUse = add(new Setting.Toggle("Include Attack/Use", false));

		public ModernKeybinds() {
			super("modern_keybinds", "Modern Keybinds", Category.UTILITY, '\ue120', "Mouse button keybinds you're holding stay held after closing a menu.");
			INSTANCE = this;
		}

		public static void regrabbed(Minecraft mc) {
			if (INSTANCE == null || !INSTANCE.on()) return;
			for (KeyMapping k : mc.options.keyMappings) {
				if (!INSTANCE.attackUse.on() && (k == mc.options.keyAttack || k == mc.options.keyUse)) continue;
				InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(k);
				if (key.getType() == InputConstants.Type.MOUSE && GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS) {
					KeyMapping.set(key, true);
				}
			}
		}
	}

	// ---------- shulker preview ----------

	public record ShulkerItems(List<ItemStack> items) implements TooltipComponent {}

	public static class ShulkerPreview extends Module {
		static ShulkerPreview INSTANCE;

		// unbound shows it on every hover, bound only while that key is held so normal tooltips stay short
		private final Setting.Bind key = add(new Setting.Bind("Preview Key", -1));

		public ShulkerPreview() {
			super("shulker_preview", "Shulker Preview", Category.UTILITY, '\ue129', "Shows what's inside a shulker box when you hover it.");
			INSTANCE = this;
			ClientTooltipComponentCallback.EVENT.register(c -> c instanceof ShulkerItems s ? new Grid(s.items()) : null);
		}

		public static @Nullable ShulkerItems preview(ItemStack s) {
			if (INSTANCE == null || !INSTANCE.on() || !(s.getItem() instanceof BlockItem b) || !(b.getBlock() instanceof ShulkerBoxBlock)) return null;
			// Bind.down() ignores keys while a screen is open, and a tooltip only ever shows with one open
			Setting.Bind k = INSTANCE.key;
			if (k.bound() && !InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), k.get())) return null;
			ItemContainerContents c = s.get(DataComponents.CONTAINER);
			if (c == null) return null;
			List<ItemStack> items = new ArrayList<>(c.allItemsCopyStream().toList());
			return items.stream().allMatch(ItemStack::isEmpty) ? null : new ShulkerItems(items);
		}

		// 9 x 3 slots in the vanilla inventory's colors, empty slots stay empty
		record Grid(List<ItemStack> items) implements ClientTooltipComponent {
			@Override public int getHeight(Font f) { return 3 * 18 + 4; }
			@Override public int getWidth(Font f) { return 9 * 18; }

			@Override
			public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
				for (int i = 0; i < 27; i++) {
					int sx = x + i % 9 * 18, sy = y + i / 9 * 18;
					g.fill(sx, sy, sx + 17, sy + 17, 0x40000000);
					if (i >= items.size() || items.get(i).isEmpty()) continue;
					g.item(items.get(i), sx, sy);
					g.itemDecorations(font, items.get(i), sx, sy);
				}
			}
		}
	}

	// ---------- pickups ----------

	public static class PickupTracker extends Module {
		// funtime's gear is renamed with these in it, "x1" after a named sword is just noise
		private static final List<String> GEAR = List.of("талисман", "понож", "шлем", "ботин", "нагруд", "меч", "арбалет", "трезуб", "лук", "сфера");
		static PickupTracker INSTANCE;

		private final Setting.Toggle donate = add(new Setting.Toggle("Only Donate Items", false));
		private final Setting.Choice output = add(new Setting.Choice("Output", "Chat", "Chat", "Notifications"));
		// the same pickup event can come in twice (merged stacks), 200ms is plenty to tell them apart
		private String lastKey = "";
		private long lastAt;

		public PickupTracker() {
			super("pickup_tracker", "Pickup Tracker", Category.UTILITY, '\ue2cc', "A line in chat or a notification for every item you pick up.");
			INSTANCE = this;
		}

		// donate items on servers are renamed with color somewhere in the name, a plain anvil rename has none
		private static boolean colored(Component c) {
			if (c.getStyle().getColor() != null) return true;
			for (Component k : c.getSiblings()) if (colored(k)) return true;
			return c.getString().contains("\u00a7");
		}

		// the same item a few times in a row collapses into one line with the Chat module's stacking
		public static void picked(ItemStack s, int amount) {
			PickupTracker t = INSTANCE;
			if (t == null || !t.on() || s.isEmpty()) return;
			Component custom = s.get(DataComponents.CUSTOM_NAME);
			boolean special = custom != null && colored(custom);
			if (t.donate.on() && !special) return;
			String key = s.getItem() + "|" + s.getHoverName().getString() + "|" + amount;
			long now = Util.getMillis();
			if (key.equals(t.lastKey) && now - t.lastAt < 200) return;
			t.lastKey = key;
			t.lastAt = now;
			String low = s.getHoverName().getString().toLowerCase(Locale.ROOT);
			boolean count = GEAR.stream().noneMatch(low::contains);
			Minecraft mc = Minecraft.getInstance();
			if (t.output.get().equals("Notifications")) {
				Notice.show("pickups", special ? "Picked up donate item" : "Picked up item", s.getHoverName().copy().append(count && amount > 1 ? " x" + amount : ""), 2200);
				return;
			}
			Component line = Component.literal("+" + (count ? amount + " " : "")).withStyle(ChatFormatting.GREEN).append(s.getHoverName().copy().withStyle(special ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			mc.gui.hud.getChat().addClientSystemMessage(line);
		}
	}

	// ---------- screenshots ----------

	public static class ScreenshotHelper extends Module {
		private static final Identifier PREVIEW = Shrike.id("screenshot_preview");
		static ScreenshotHelper INSTANCE;

		private final Setting.Toggle copy = add(new Setting.Toggle("Copy To Clipboard", true));
		private final Setting.Toggle preview = add(new Setting.Toggle("Show Preview", true));
		private final Setting.Slider seconds = add(new Setting.Slider("Preview Seconds", 4, 1, 10, 0.5, "s")).when(preview::on);
		private final Setting.Choice corner = add(new Setting.Choice("Position", "Bottom Right", "Bottom Right", "Top Right", "Bottom Left", "Top Left")).when(preview::on);
		private long shownAt;
		private int imgW, imgH;
		private boolean copied;

		public ScreenshotHelper() {
			super("screenshot_helper", "Screenshot Helper", Category.UTILITY, '\ue064', "A preview after you take a screenshot, and the image copied to your clipboard.");
			INSTANCE = this;
			HudElementRegistry.addLast(Shrike.id("screenshot_card"), (g, dt) -> card(g));
		}

		// runs on the io thread when vanilla's "saved screenshot as ..." message is ready. the file is in its click event
		public static void saved(Component msg) {
			ScreenshotHelper s = INSTANCE;
			if (s == null || !s.on() || !(msg.getContents() instanceof TranslatableContents t) || t.getArgs().length == 0) return;
			if (!(t.getArgs()[0] instanceof Component name) || !(name.getStyle().getClickEvent() instanceof ClickEvent.OpenFile open)) return;
			File file = open.file();
			boolean copied = s.copy.on() && Util.getPlatform() == Util.OS.WINDOWS && clipboard(file);
			Minecraft.getInstance().execute(() -> s.show(file, copied));
		}

		// .NET's clipboard wants an STA thread, powershell -sta gives it one. the path goes in as an argument,
		// not pasted into the script, so a weird file name can't turn into code
		private static boolean clipboard(File f) {
			String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile($args[0]))";
			try {
				Process p = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", "& { " + script + " }", f.getAbsolutePath()).redirectErrorStream(true).start();
				p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
				return p.waitFor() == 0;
			} catch (IOException e) {
				Shrike.LOG.warn("screenshot copy failed: {}", e.getMessage());
				return false;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		private void show(File f, boolean copied) {
			try (InputStream in = Files.newInputStream(f.toPath())) {
				NativeImage img = NativeImage.read(in);
				imgW = img.getWidth();
				imgH = img.getHeight();
				Minecraft.getInstance().getTextureManager().register(PREVIEW, new DynamicTexture(() -> "shrike screenshot preview", img));
				shownAt = Util.getMillis();
				this.copied = copied;
			} catch (IOException e) {
				Shrike.LOG.warn("couldn't load screenshot preview: {}", e.getMessage());
			}
		}

		// slides in from its side in 180ms, stays, then fades out over 300ms
		private void card(GuiGraphicsExtractor g) {
			long age = Util.getMillis() - shownAt, stay = Math.round(seconds.get() * 1000);
			if (!on() || !preview.on() || shownAt == 0 || age > stay + 300) return;
			int w = 120, h = Math.round(w * imgH / (float) Math.max(1, imgW)), pad = 5, cw = w + pad * 2, ch = h + pad * 2 + 12;
			float in = Math.min(1, age / 180f), a = age > stay ? 1 - (age - stay) / 300f : in;
			float slide = (1 - in) * (1 - in) * (1 - in) * 24;
			boolean right = corner.get().endsWith("Right"), top = corner.get().startsWith("Top");
			int x = right ? g.guiWidth() - cw - 6 + Math.round(slide) : 6 - Math.round(slide);
			int y = top ? 6 : g.guiHeight() - ch - 6;
			Ui.round(g, x, y, cw, ch, ARGB.multiplyAlpha(0xE0101216, a));
			g.blit(RenderPipelines.GUI_TEXTURED, PREVIEW, x + pad, y + pad, 0, 0, w, h, w, h, ARGB.white(a));
			Ui.text(g, Ui.small(copied ? "Saved and copied" : "Saved"), x + pad, y + pad + h + 3, ARGB.multiplyAlpha(Ui.MUTED, a));
		}
	}

	// ---------- per server: profiles, notes, last played ----------

	// one file for everything server related: config/shrike/servers.json, address -> note + last played
	static final class ServerInfo {
		private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
		private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("shrike").resolve("servers.json");
		private static Map<String, JsonObject> data;

		private ServerInfo() {}

		private static Map<String, JsonObject> data() {
			if (data == null) {
				data = new TreeMap<>();
				try {
					if (Files.exists(FILE)) JsonParser.parseString(Files.readString(FILE)).getAsJsonObject().entrySet().forEach(e -> data.put(e.getKey(), e.getValue().getAsJsonObject()));
				} catch (IOException | RuntimeException e) {
					Shrike.LOG.warn("couldn't read servers.json: {}", e.getMessage());
				}
			}
			return data;
		}

		static JsonObject get(String ip) {
			return data().computeIfAbsent(ip.toLowerCase(Locale.ROOT), k -> new JsonObject());
		}

		// read only, the title screen asks about every server in the list and shouldn't add an empty entry for each
		static long last(String ip) {
			JsonObject o = data().get(ip.toLowerCase(Locale.ROOT));
			return o != null && o.has("last") ? o.get("last").getAsLong() : 0;
		}

		static void save() {
			JsonObject root = new JsonObject();
			data.forEach(root::add);
			try {
				Files.createDirectories(FILE.getParent());
				Files.writeString(FILE, GSON.toJson(root));
			} catch (IOException e) {
				Shrike.LOG.warn("servers.json save failed: {}", e.getMessage());
			}
		}
	}

	public static String note(String ip) {
		JsonObject o = ServerInfo.get(ip);
		return o.has("note") ? o.get("note").getAsString() : "";
	}

	public static void note(String ip, String text) {
		ServerInfo.get(ip).addProperty("note", text);
		ServerInfo.save();
	}

	public static long lastPlayed(String ip) {
		JsonObject o = ServerInfo.get(ip);
		return o.has("last") ? o.get("last").getAsLong() : 0;
	}

	public static class ServerNotes extends Module {
		static ServerNotes INSTANCE;

		private final Setting.Toggle last = add(new Setting.Toggle("Show Last Played", true));
		private final Setting.Toggle notes = add(new Setting.Toggle("Show Notes", true));
		private long savedAt;

		public ServerNotes() {
			super("server_notes", "Server Notes", Category.UTILITY, '\ue303', "Your own notes and when you last played, right in the server list.");
			INSTANCE = this;
			startOn();
			ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> {
				ServerData sd = mc.getCurrentServer();
				if (sd == null) return;
				ServerInfo.get(sd.ip).addProperty("last", System.currentTimeMillis());
				ServerInfo.save();
			});			// top left of the server list, works on whichever server is selected
			ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (!(screen instanceof JoinMultiplayerScreen jm) || !active()) return;
				Screens.getWidgets(screen).add(Button.builder(Component.literal("Notes"), b -> {
					if (((JoinMultiplayerAccessor) jm).shrike$list().getSelected() instanceof ServerSelectionList.OnlineServerEntry e) {
						mc.gui.setScreen(new NotesScreen(jm, e.getServerData().ip, e.getServerData().name));
					}
				}).bounds(6, 6, 50, 20).build());
			});
		}

		public static boolean active() {
			return INSTANCE != null && INSTANCE.on();
		}

		public static boolean showLast() {
			return active() && INSTANCE.last.on();
		}

		public static boolean showNotes() {
			return active() && INSTANCE.notes.on();
		}

		// last played keeps counting while you're on, a crash or alt f4 would otherwise leave the join time
		@Override
		public void tick(Minecraft mc) {
			ServerData sd = mc.getCurrentServer();
			if (sd == null || Util.getMillis() - savedAt < 60_000) return;
			savedAt = Util.getMillis();
			ServerInfo.get(sd.ip).addProperty("last", System.currentTimeMillis());
			ServerInfo.save();
		}

		// joins are stamped even with this module off, so the title screen's quick join can sort by them
		public static long lastPlayed(String ip) {
			return ServerInfo.last(ip);
		}

		public static String ago(long t) {
			long m = (System.currentTimeMillis() - t) / 60_000, d = m / 60 / 24;
			if (m < 1) return "just now";
			if (m < 60) return m + " min ago";
			if (m < 60 * 24) return m / 60 + "h ago";
			if (d < 2) return "yesterday";
			if (d < 60) return d + " days ago";
			return d / 30 + " months ago";
		}
	}
}
