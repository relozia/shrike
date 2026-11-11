package io.github.relozia.kestrel.visual;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.relozia.kestrel.Kestrel;
import io.github.relozia.kestrel.chat.Players;
import io.github.relozia.kestrel.gui.Notice;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.PlayerSkin;

// Kestrel's cosmetics on your own player. there's no online part yet, so nobody else sees them
// one module, each cosmetic its own switch. wings, hats, halos, pets and auras slot in here later
// issues:
//   - a png dropped into config/kestrel/capes only shows in the list after a restart, same as sound packs
//   - vanilla's Cape toggle in skin customization still hides it, on purpose
public class Cosmetics extends Module {
	static Cosmetics INSTANCE;
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("kestrel").resolve("capes");
	private static final String NONE = "No pngs found";

	private final Setting.Toggle cape = add(new Setting.Toggle("Cape", true));
	private final Setting.Toggle badge = add(new Setting.Toggle("Badge", true));
	// order matches the glyphs in font/badge.png, left to right from U+E000
	private final Setting.Choice badgeIcon = add(new Setting.Choice("Badge Icon", "Stoop", "Dive", "Stoop", "Soar", "K-Wing")).when(badge::on);
	private final Setting.Choice design = add(new Setting.Choice("Cape Design", "Mark", "Mark", "Rust", "Dusk", "Feather", "K-Wing", "Stoop", "Dive", "Custom")).when(cape::on);
	private final Setting.Choice custom = add(new Setting.Choice("Custom Cape", files()[0], files())).when(() -> cape.on() && design.get().equals("Custom"));

	// file name to its texture, loaded the first time it's worn. null marks one that failed so it isn't read every frame
	private final Map<String, ClientAsset.Texture> loaded = new HashMap<>();

	public Cosmetics() {
		super("Cosmetics", Category.VISUAL, '\ue176', "A Kestrel cape or your own, and the Kestrel badge by your name. Only you can see them for now.");
		INSTANCE = this;
		add(new Setting.Action("Capes Folder", "Open", () -> {
			try {
				Files.createDirectories(DIR);
			} catch (IOException e) {
				Kestrel.LOG.warn("couldn't make the capes folder: {}", e.getMessage());
			}
			Util.getPlatform().openPath(DIR);
		})).when(() -> cape.on() && design.get().equals("Custom"));
	}

	private static String[] files() {
		List<String> out = new ArrayList<>();
		if (Files.isDirectory(DIR)) {
			try (Stream<Path> s = Files.list(DIR)) {
				s.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png")).sorted().forEach(out::add);
			} catch (IOException e) {
				Kestrel.LOG.warn("couldn't list capes: {}", e.getMessage());
			}
		}
		return out.isEmpty() ? new String[] {NONE} : out.toArray(String[]::new);
	}

	// each mark is one character in its own bitmap font (font/badge.json), so it goes anywhere text does
	private static final FontDescription BADGE_FONT = new FontDescription.Resource(Kestrel.id("badge"));

	// the Kestrel mark in front of your own name, tab list and name tag. only yours until there's a server that says
	// who else runs Kestrel. white so a team color on the name doesn't tint the orange glyph
	public static Component badge(Component name) {
		Component p = prefix();
		return p == null ? name : Component.empty().append(p).append(name);
	}

	// the glyph and a space, null with the badge off
	private static Component prefix() {
		Cosmetics c = INSTANCE;
		if (c == null || !c.on() || !c.badge.on()) return null;
		char ch = (char) (0xE000 + c.badgeIcon.options.indexOf(c.badgeIcon.get()));
		return Component.empty().append(Component.literal(String.valueOf(ch)).withStyle(s -> s.withFont(BADGE_FONT).withColor(0xFFFFFF))).append(" ");
	}

	// your name as a whole word, "kaiden" in "[VIP] kaiden" but not in "kaiden2"
	public static boolean hasName(String text) {
		return nameAt(text) >= 0;
	}

	// the text the way it's drawn: a head or sprite object is one placeholder character here, getString() spells
	// out its fallback instead ("[kaiden head]"), which shifted every position after it and has your name in it too
	public static String shown(Component c) {
		StringBuilder sb = new StringBuilder();
		c.visit((st, str) -> {
			sb.append(str);
			return Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	// where it starts, counted in code points like the styled text is walked, -1 when it isn't there
	private static int nameAt(String text) {
		var me = Minecraft.getInstance().player;
		if (me == null) return -1;
		var m = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(me.getGameProfile().name()) + "(?![A-Za-z0-9_])").matcher(text);
		return m.find() ? text.codePointCount(0, m.start()) : -1;
	}

	// the badge right in front of your name inside a server's tag, "HT3 #9830 | ! name | flag", ranks and the rest
	// stay where the server put them
	public static Component badgeAtName(Component tag) {
		Component p = prefix();
		if (p == null) return tag;
		int at = nameAt(shown(tag));
		// a tab entry that's yours by account but shows a nickname: up front then
		if (at < 0) return badge(tag);
		// piece by piece, each with its style already merged in. only the piece your name starts in gets split, the rest
		// (heads, sprites, translations) go back exactly as the server sent them
		List<Component> pieces = new ArrayList<>();
		pieces(tag, Style.EMPTY, pieces);
		MutableComponent out = Component.empty();
		int pos = 0;
		boolean put = false;
		for (Component piece : pieces) {
			String str = shown(piece);
			int len = str.codePointCount(0, str.length());
			if (!put && pos <= at && at < pos + len) {
				int cut = str.offsetByCodePoints(0, at - pos);
				out.append(Component.literal(str.substring(0, cut)).withStyle(piece.getStyle())).append(p).append(Component.literal(str.substring(cut)).withStyle(piece.getStyle()));
				put = true;
			} else {
				out.append(piece);
			}
			pos += len;
		}
		// the walk and the search use the same text so this shouldn't miss, but a tag without the badge is worse than one up front
		return put ? out : badge(tag);
	}

	// vanilla's toFlatList rebuilds every piece as plain text, a head object came back as a bare placeholder.
	// this keeps each piece's own contents and only merges the styles down
	private static void pieces(Component c, Style parent, List<Component> out) {
		Style st = c.getStyle().applyTo(parent);
		out.add(MutableComponent.create(c.getContents()).withStyle(st));
		for (Component sib : c.getSiblings()) pieces(sib, st, out);
	}

	// the server's own name tag for you as a text display: the badge in front of your name on the line that has it.
	// done on every read of the split lines, not inside vanilla's cache, a display set up before it was attached to
	// you would keep its unbadged lines until the server changed the text
	public static Display.TextDisplay.CachedInfo badgeLines(Display.TextDisplay d, Display.TextDisplay.CachedInfo info) {
		Component p = prefix();
		if (p == null || !Players.NameTags.serverTag(d)) return info;
		List<Display.TextDisplay.CachedLine> lines = new ArrayList<>(info.lines());
		for (int i = 0; i < lines.size(); i++) {
			Display.TextDisplay.CachedLine l = lines.get(i);
			StringBuilder sb = new StringBuilder();
			l.contents().accept((at, st, cp) -> {
				sb.appendCodePoint(cp);
				return true;
			});
			int at = nameAt(sb.toString());
			if (at < 0) continue;
			FormattedCharSequence pre = p.getVisualOrderText(), old = l.contents();
			// the line as it was, with the badge slipped in when the walk reaches your name
			FormattedCharSequence badged = sink -> {
				int[] n = {0};
				return old.accept((x, st, cp) -> n[0]++ == at && !pre.accept(sink) ? false : sink.accept(x, st, cp));
			};
			int w = l.width() + Minecraft.getInstance().font.width(pre);
			lines.set(i, new Display.TextDisplay.CachedLine(badged, w));
			return new Display.TextDisplay.CachedInfo(lines, Math.max(info.width(), w));
		}
		return info;
	}

	// your tab entry. custom tab plugins hide the real entries and fill the list with fake ones that only carry text,
	// so an entry showing your name as a whole word is yours too
	public static boolean mine(PlayerInfo info) {
		var me = Minecraft.getInstance().player;
		if (me == null) return false;
		if (info.getProfile().id().equals(me.getUUID())) return true;
		Component d = info.getTabListDisplayName();
		return d != null && hasName(shown(d));
	}

	// your skin with the cape swapped in. AbstractClientPlayerMixin calls it for the local player only, the cape layer
	// and the elytra both read the cape from here (no elytra texture means the elytra wears the cape, like vanilla)
	public static PlayerSkin skin(PlayerSkin s) {
		Cosmetics c = INSTANCE;
		if (c == null || !c.on() || !c.cape.on()) return s;
		ClientAsset.Texture t = c.capeTexture();
		return t == null ? s : new PlayerSkin(s.body(), t, null, s.model(), s.secure());
	}

	private ClientAsset.Texture capeTexture() {
		String d = design.get();
		// the shipped ones are textures/cape/<name>.png, drawn by tools/capes.py ("K-Wing" is kwing.png)
		if (!d.equals("Custom")) return new ClientAsset.ResourceTexture(Kestrel.id("cape/" + d.toLowerCase(Locale.ROOT).replace("-", "")));
		String f = custom.get();
		if (f.equals(NONE)) return null;
		if (loaded.containsKey(f)) return loaded.get(f);
		ClientAsset.Texture t = null;
		try (InputStream in = Files.newInputStream(DIR.resolve(f))) {
			NativeImage img = NativeImage.read(in);
			// vanilla's cape layout is 2:1 (64x32 and up), anything else maps onto the model wrong
			if (img.getWidth() != img.getHeight() * 2 || img.getWidth() < 64) {
				Notice.show("Cosmetics", f + " isn't a cape image, it has to be 2:1 like 64x32 or 512x256", 4000);
				img.close();
			} else {
				Identifier id = Kestrel.id("cape/custom_" + f.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "kestrel cape " + f, img));
				t = new ClientAsset.ResourceTexture(id, id);
			}
		} catch (IOException e) {
			Notice.show("Cosmetics", "Couldn't read " + f, 4000);
			Kestrel.LOG.warn("cape {} didn't load: {}", f, e.getMessage());
		}
		loaded.put(f, t);
		return t;
	}
}
