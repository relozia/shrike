package io.github.relozia.kestrel.chat;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

// who wrote a chat message. signed player chat says so itself. most servers send chat as plain system text
// though ("[VIP] Steve: hi", "<Steve> hi", "Steve » hi"), for those it's the first online player's name in
// the first few words, right before a : > or »
// issues:
//   - nick plugins (shown name isn't the account name) aren't found
//   - a message starting with someone else's name and a colon gets pinned on them, rare enough
public final class Senders {
	private Senders() {}

	public record Hit(UUID id, String name, String body) {}

	// the decorated line chat gets -> who sent it, filled from ChatListener right before it's added
	private static final Map<Component, Hit> SIGNED = new WeakHashMap<>();
	private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]{3,16}");

	public static void signed(Component shown, GameProfile sender, Component body) {
		SIGNED.put(shown, new Hit(sender.id(), sender.name(), body.getString()));
	}

	public static @Nullable Hit find(Component c) {
		Hit h = SIGNED.get(c);
		if (h != null) return h;
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn == null) return null;
		String s = c.getString();
		Matcher m = WORD.matcher(s.length() > 48 ? s.substring(0, 48) : s);
		while (m.find()) {
			PlayerInfo p = conn.getPlayerInfo(m.group());
			if (p == null) continue;
			int i = m.end();
			while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == ']' || s.charAt(i) == ')')) i++;
			if (i < s.length() && ":>»".indexOf(s.charAt(i)) >= 0) return new Hit(p.getProfile().id(), p.getProfile().name(), s.substring(i + 1).trim());
		}
		return null;
	}

	public static boolean self(@Nullable Hit h) {
		Minecraft mc = Minecraft.getInstance();
		return h != null && mc.player != null && h.id().equals(mc.player.getUUID());
	}
}
