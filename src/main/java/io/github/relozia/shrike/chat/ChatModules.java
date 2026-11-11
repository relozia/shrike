package io.github.relozia.shrike.chat;

import io.github.relozia.shrike.gui.Ui;
import io.github.relozia.shrike.mixin.ChatScreenAccessor;
import io.github.relozia.shrike.mixin.EditBoxAccessor;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.nav.Nav;
import io.github.relozia.shrike.pvp.KillEffects;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

// everything that hooks the chat box. ChatComponentMixin runs each incoming line through incoming() once,
// in this order: mentions (needs the untouched text), auto gg, stacking, timestamp
public final class ChatModules {
	private ChatModules() {}

	// raw text and sender per shown message. copy pastes the raw text, heads and search read the rest
	private static final Map<GuiMessage, String> RAW = new WeakHashMap<>();
	private static final Map<GuiMessage, Senders.Hit> HITS = new WeakHashMap<>();

	public static String raw(GuiMessage m) {
		return RAW.getOrDefault(m, m.content().getString());
	}

	public static void remember(GuiMessage m, String raw, Senders.@Nullable Hit hit, GuiMessageSource source) {
		RAW.put(m, raw);
		if (hit != null) HITS.put(m, hit);
		Chat.sort(m, raw, hit, source);
	}

	private static final Pattern STAMP = Pattern.compile("^\\[\\d{1,2}:\\d{2}(?::\\d{2})?(?: [AP]M)?\\] ");

	// the copied line without our own timestamp in front
	public static String unstamp(String s) {
		return STAMP.matcher(s).replaceFirst("");
	}

	// a message as & codes: the 16 named colors as &0-&f, anything else as &#RRGGBB, then the styles, &r between runs
	public static String codes(Component c) {
		StringBuilder sb = new StringBuilder();
		c.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			if (!sb.isEmpty()) sb.append("&r");
			TextColor col = style.getColor();
			if (col != null) {
				String named = null;
				for (ChatFormatting f : ChatFormatting.values()) {
					TextColor tc = TextColor.fromLegacyFormat(f);
					// toString is the section sign and the code, "§c"
					if (tc != null && tc.getValue() == col.getValue()) named = "&" + f.toString().charAt(1);
				}
				sb.append(named != null ? named : String.format(Locale.ROOT, "&#%06X", col.getValue()));
			}
			if (style.isBold()) sb.append("&l");
			if (style.isItalic()) sb.append("&o");
			if (style.isUnderlined()) sb.append("&n");
			if (style.isStrikethrough()) sb.append("&m");
			if (style.isObfuscated()) sb.append("&k");
			sb.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	// ---------- chat ----------

	public static class Chat extends Module {
		static Chat INSTANCE;

		private final Setting.Choice stamps = add(new Setting.Choice("Timestamps", "Off", "Off", "24 Hour", "12 Hour"));
		private final Setting.Toggle seconds = add(new Setting.Toggle("Show Seconds", false));
		private final Setting.Toggle stack = add(new Setting.Toggle("Stack Repeats", true));
		private final Setting.Toggle smooth = add(new Setting.Toggle("Smooth Chat", true));
		private final Setting.Toggle keep = add(new Setting.Toggle("Keep Chat", true));
		private final Setting.Toggle antiClear = add(new Setting.Toggle("Anti Clear", false));
		private final Setting.Toggle blankSpam = add(new Setting.Toggle("Block Clear-Chat Spam", true));
		private final Setting.Slider history = add(new Setting.Slider("History", 500, 100, 1000, 50, " lines"));
		private final Setting.Toggle copy = add(new Setting.Toggle("Right Click Copy", true));
		private final Setting.Toggle copyStamp = add(new Setting.Toggle("Copy Includes Timestamp", false));
		private final Setting.Toggle search = add(new Setting.Toggle("Ctrl+F Search", true));
		private final Setting.Toggle tabs = add(new Setting.Toggle("Chat Tabs", true));
		private final Setting.Toggle backToAll = add(new Setting.Toggle("Tabs Back To All On Close", true));
		private final Setting.Words pmWords = add(new Setting.Words("Extra PM Patterns", "Part of a private message, like \"-> me\""));

		// what's being searched for right now, null when not searching
		static @Nullable String query;

		// a message's bits say which of these it belongs in, All shows everything
		public static final String[] TABS = {"All", "PMs", "Server", "Mentions"};
		public static int tab;
		public static final int[] UNREAD = new int[TABS.length];
		private static final Map<GuiMessage, Integer> TAB_BITS = new WeakHashMap<>();
		// the private message shapes servers use
		private static final List<Pattern> PM = List.of(
			Pattern.compile("whispers to you\\s*:", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^you whisper to ", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^[\\[(<{]?\\s*(from|to)\\s+[^\\s:)>\\]]{1,24}(\\s+[^\\s:)>\\]]{1,8}){0,3}\\s*[:)>\\]\u00bb]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("(->|\u2192|\u279c|\u00bb|>>)\\s*(me|you)\\s*[:\\]>)]", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^[\\[(<]?\\s*(me|you)\\s*(->|\u2192|\u279c|\u00bb|>>)", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^[\\[(<]?(msg|pm|whisper|dm)[\\])>]?\\s", Pattern.CASE_INSENSITIVE));

		private static long lastAt;
		private static long blankFrom;
		private static int blanks;

		public Chat() {
			super("chat", "Chat", Category.CHAT, '\ue40d', "Timestamps, tabs, stacked repeats, longer history, search and copy.");
			INSTANCE = this;
		}

		@Override
		protected void disable() {
			tab = 0;
			query = null;
		}

		private static boolean has(Setting.Toggle t) {
			return INSTANCE != null && INSTANCE.on() && t.on();
		}

		public static boolean stacks() { return has(INSTANCE.stack); }
		public static boolean keeps() { return has(INSTANCE.keep); }
		public static boolean copies() { return has(INSTANCE.copy); }
		public static boolean copyStamp() { return has(INSTANCE.copyStamp); }
		public static boolean searches() { return has(INSTANCE.search); }
		public static boolean antiClear() { return has(INSTANCE.antiClear); }
		public static boolean tabbed() { return has(INSTANCE.tabs); }
		public static boolean backToAll() { return has(INSTANCE.backToAll); }

		public static int history(int vanilla) {
			return INSTANCE != null && INSTANCE.on() ? INSTANCE.history.get().intValue() : vanilla;
		}

		public static Component stamp(Component c) {
			lastAt = Util.getMillis();
			if (INSTANCE == null || !INSTANCE.on() || INSTANCE.stamps.get().equals("Off")) return c;
			boolean h12 = INSTANCE.stamps.get().equals("12 Hour");
			String fmt = (h12 ? "h:mm" : "HH:mm") + (INSTANCE.seconds.on() ? ":ss" : "") + (h12 ? " a" : "");
			String t = LocalTime.now().format(DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH));
			// side by side under an empty root: appended to the grey stamp, the message inherited its gray and
			// anything without its own color (plain player chat) came out dark
			return Component.empty().append(Component.literal("[" + t + "] ").withStyle(ChatFormatting.DARK_GRAY)).append(c);
		}

		// new lines start 8px low and ease up over 180ms
		public static float smoothOffset() {
			if (!has(INSTANCE.smooth)) return 0;
			long t = Util.getMillis() - lastAt;
			if (t <= 0 || t >= 180) return 0;
			float e = 1 - t / 180f;
			return 8 * e * e * (3 - 2 * e);
		}

		// "clear chat" plugins spam empty lines. the first three in a second get through, the rest are dropped
		public static boolean dropBlank(String raw) {
			if (!has(INSTANCE.blankSpam)) return false;
			if (!raw.isBlank()) {
				blanks = 0;
				return false;
			}
			long now = Util.getMillis();
			if (now - blankFrom > 1000) {
				blankFrom = now;
				blanks = 0;
			}
			return ++blanks > 3;
		}

		public static void search(@Nullable String q) {
			query = q == null || q.isBlank() ? null : q.toLowerCase(Locale.ROOT);
		}

		private static boolean pm(String plain) {
			for (Pattern p : PM) if (p.matcher(plain).find()) return true;
			if (INSTANCE == null) return false;
			String low = plain.toLowerCase(Locale.ROOT);
			for (String w : INSTANCE.pmWords.get()) if (!w.isBlank() && low.contains(w.toLowerCase(Locale.ROOT))) return true;
			return false;
		}

		static void sort(GuiMessage m, String raw, Senders.@Nullable Hit hit, GuiMessageSource source) {
			Minecraft mc = Minecraft.getInstance();
			int bits = 0;
			boolean isPm = pm(raw);
			if (isPm) bits |= 2;
			else if (source != GuiMessageSource.PLAYER && hit == null) bits |= 4;
			if (mc.player != null && !Senders.self(hit)) {
				String me = Pattern.quote(mc.player.getGameProfile().name());
				if (Pattern.compile("(?<![A-Za-z0-9_])" + me + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE).matcher(raw).find()) bits |= 8;
			}
			TAB_BITS.put(m, bits);
			for (int i = 1; i < TABS.length; i++) if (i != tab && (bits & 1 << i) != 0) UNREAD[i] = Math.min(99, UNREAD[i] + 1);
		}

		public static void pick(int t) {
			tab = t;
			UNREAD[t] = 0;
		}

		public static boolean shown(GuiMessage m) {
			if (tab != 0 && tabbed() && (TAB_BITS.getOrDefault(m, 0) & 1 << tab) == 0) return false;
			return query == null || raw(m).toLowerCase(Locale.ROOT).contains(query);
		}
	}

	// ---------- heads ----------

	public static class Heads extends Module {
		static Heads INSTANCE;

		public record Head(UUID id, FormattedCharSequence first) {}

		private static final Map<GuiMessage, Head> FIRST = new WeakHashMap<>();
		private final Setting.Toggle hat = add(new Setting.Toggle("Hat Layer", true));

		public Heads() {
			super("chat_heads", "Chat Heads", Category.CHAT, '\ue468', "The sender's face next to their chat messages.");
			INSTANCE = this;
		}

		public static boolean wants(GuiMessage m) {
			return INSTANCE != null && INSTANCE.on() && HITS.containsKey(m);
		}

		// lines get rebuilt on resize and search, so the first line is noted every time they're split
		public static void split(GuiMessage m, List<FormattedCharSequence> lines) {
			Senders.Hit h = HITS.get(m);
			if (h != null && !lines.isEmpty()) FIRST.put(m, new Head(h.id(), lines.getFirst()));
		}

		public static @Nullable Head of(GuiMessage m) {
			return INSTANCE != null && INSTANCE.on() ? FIRST.get(m) : null;
		}

		// someone who left still gets a face, just the default one for their uuid
		public static void draw(GuiGraphicsExtractor g, Head h, int top, float alpha) {
			var conn = Minecraft.getInstance().getConnection();
			PlayerInfo p = conn == null ? null : conn.getPlayerInfo(h.id());
			var skin = p != null ? p.getSkin() : DefaultPlayerSkin.get(h.id());
			boolean hat = INSTANCE == null || INSTANCE.hat.on();
			PlayerFaceExtractor.extractRenderState(g, skin.body().texturePath(), 0, top, 8, hat, false, ARGB.white(alpha));
		}
	}

	// ---------- mentions ----------

	public static class Mentions extends Module {
		static Mentions INSTANCE;

		private final Setting.Toggle name = add(new Setting.Toggle("Your Name", true));
		private final Setting.Words words = add(new Setting.Words("Also Watch For", "A word or nickname"));
		private final Setting.Toggle sound = add(new Setting.Toggle("Sound", true));
		private final Setting.Toggle highlight = add(new Setting.Toggle("Highlight Mention", true));
		private final Setting.Toggle flash = add(new Setting.Toggle("Flash Taskbar", true));
		private final Setting.Toggle toast = add(new Setting.Toggle("Windows Notification When Tabbed Out", false));
		private long toastAt;

		private String cachedKey;
		private Pattern cached;

		public Mentions() {
			super("mentions", "Mentions", Category.CHAT, '\ue04e', "Highlights your name in chat and dings when someone says it.");
			INSTANCE = this;
		}

		private @Nullable Pattern pattern() {
			Minecraft mc = Minecraft.getInstance();
			List<String> all = new ArrayList<>(words.get());
			if (name.on() && mc.player != null) all.add(mc.player.getGameProfile().name());
			String key = String.join("\n", all);
			if (!key.equals(cachedKey)) {
				cachedKey = key;
				List<String> q = all.stream().filter(s -> !s.isBlank()).map(Pattern::quote).toList();
				// whole words only, "Al" shouldn't light up inside "Alright"
				cached = q.isEmpty() ? null : Pattern.compile("(?<![A-Za-z0-9_])(" + String.join("|", q) + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
			}
			return cached;
		}

		static Component process(Component c, GuiMessageSource source, Senders.@Nullable Hit hit) {
			Mentions m = INSTANCE;
			if (m == null || !m.on() || source == GuiMessageSource.SYSTEM_CLIENT || Senders.self(hit)) return c;
			Pattern p = m.pattern();
			if (p == null || !p.matcher(c.getString()).find()) return c;
			Minecraft mc = Minecraft.getInstance();
			if (m.sound.on()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6f));
			if (m.flash.on() && !mc.isWindowActive()) GLFW.glfwRequestWindowAttention(mc.getWindow().handle());
			if (m.toast.on() && !mc.isWindowActive() && Util.getMillis() - m.toastAt > 2000) {
				m.toastAt = Util.getMillis();
				Toast.show("Mentioned in chat", c.getString());
			}
			return m.highlight.on() ? highlight(c, p, Ui.accent()) : c;
		}

		// rebuilt piece by piece so clicks and hovers on the rest of the message keep working.
		// a match split across two differently styled pieces is missed, rare in practice
		static Component highlight(Component c, Pattern p, int color) {
			MutableComponent out = Component.empty();
			c.visit((style, text) -> {
				Matcher mt = p.matcher(text);
				int at = 0;
				while (mt.find()) {
					if (mt.start() > at) out.append(Component.literal(text.substring(at, mt.start())).setStyle(style));
					out.append(Component.literal(mt.group()).setStyle(style.withColor(color & 0xFFFFFF).withBold(true)));
					at = mt.end();
				}
				if (at < text.length()) out.append(Component.literal(text.substring(at)).setStyle(style));
				return Optional.empty();
			}, Style.EMPTY);
			return out;
		}
	}

	// ---------- auto gg ----------

	public static class AutoGG extends Module {
		static AutoGG INSTANCE;

		private final Setting.Words triggers = add(new Setting.Words("Triggers", "Part of the game over message",
			"WINNER!", "Winner:", "won the game", "1st Killer", "Victory!", "has won"));
		private final Setting.Words lines = add(new Setting.Words("Messages", "What to send", "gg"));
		private final Setting.Slider delay = add(new Setting.Slider("Delay", 1, 0, 5, 0.5, "s"));

		// Util.getMillis counts from launch, a 0 here blocked it for the game's first 10 seconds
		private long lastSent = -10_000, sendAt;

		public AutoGG() {
			super("auto_gg", "Auto GG", Category.CHAT, '\ue373', "Says gg when a game ends. Sends a real chat message.");
			INSTANCE = this;
		}

		// only server text, never player chat, otherwise anyone typing "winner" sets it off
		static void check(String raw, GuiMessageSource source, Senders.@Nullable Hit hit) {
			AutoGG a = INSTANCE;
			if (a == null || !a.on() || source != GuiMessageSource.SYSTEM_SERVER || hit != null || a.lines.get().isEmpty()) return;
			long now = Util.getMillis();
			if (now - a.lastSent < 10_000 || a.sendAt != 0) return;
			String low = raw.toLowerCase(Locale.ROOT);
			if (a.triggers.get().stream().noneMatch(t -> low.contains(t.toLowerCase(Locale.ROOT)))) return;
			a.sendAt = now + Math.round(a.delay.get() * 1000);
		}

		@Override
		public void tick(Minecraft mc) {
			if (sendAt == 0 || Util.getMillis() < sendAt) return;
			sendAt = 0;
			if (mc.player == null) return;
			List<String> l = lines.get();
			mc.player.connection.sendChat(l.get(ThreadLocalRandom.current().nextInt(l.size())));
			lastSent = Util.getMillis();
		}
	}

	// ---------- hide password ----------

	public static class HidePassword extends Module {
		static HidePassword INSTANCE;

		// servers name these differently, so the list is yours to add to
		private final Setting.Words commands = add(new Setting.Words("Commands", "A command without the /",
			"login", "l", "log", "register", "reg", "changepassword", "changepass", "unregister"));

		public HidePassword() {
			super("hide_password", "Hide Password", Category.CHAT, '\ue10b', "Stars out your password while you type /login or /register.");
			INSTANCE = this;
			// goes first in the input's formatters, the command highlighter after it would show the real text
			ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (!(screen instanceof ChatScreen cs)) return;
				EditBox in = ((ChatScreenAccessor) cs).shrike$input();
				((EditBoxAccessor) in).shrike$formatters().add(0, (text, offset) -> {
					int from = from(in.getValue());
					if (from < 0) return null;
					StringBuilder sb = new StringBuilder(text);
					for (int i = 0; i < sb.length(); i++) if (offset + i >= from) sb.setCharAt(i, '*');
					return FormattedCharSequence.forward(sb.toString(), Style.EMPTY);
				});
			});
		}

		// index where the hidden part starts, -1 if this isn't a password command
		public static int from(String value) {
			if (INSTANCE == null || !INSTANCE.on() || !value.startsWith("/")) return -1;
			int sp = value.indexOf(' ');
			if (sp < 0) return -1;
			return INSTANCE.commands.has(value.substring(1, sp).toLowerCase(Locale.ROOT)) ? sp + 1 : -1;
		}
	}

	// ---------- the pipeline ----------

	// returns what chat should show. sender is worked out once here from the untouched text
	public static Component incoming(Component c, GuiMessageSource source, Senders.@Nullable Hit hit) {
		c = Mentions.process(c, source, hit);
		AutoGG.check(c.getString(), source, hit);
		if (source == GuiMessageSource.SYSTEM_SERVER && hit == null) Nav.AutoWaypoint.message(c.getString());
		if (source != GuiMessageSource.SYSTEM_CLIENT) KillEffects.chat(c.getString());
		return c;
	}
}
