package io.github.relozia.kestrel.chat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

// what people say as a speech bubble over their head: rounded box with a tail, light,
// dark or custom look, pops in and fades out, a player's newer lines stack on top of the older ones
// issues:
//   - bubbles draw after the world, a wall between you and the speaker doesn't hide them
//   - a nick plugin name only matches when it's the name on their nametag
public class ChatBubbles extends Module {
	static ChatBubbles INSTANCE;
	private static final float PAD_X = 5, PAD_Y = 4, LINE = 10, RADIUS = 4, TAIL_H = 5, TAIL_W = 4, GAP = 3;
	private static final int SEGMENTS = 4;

	private final Setting.Toggle showName = add(new Setting.Toggle("Show Player Name", false));
	private final Setting.Slider height = add(new Setting.Slider("Bubble Height", 0.75, 0, 5, 0.05, " blocks"));
	private final Setting.Slider textScale = add(new Setting.Slider("Text Scale", 0.025, 0.01, 0.1, 0.001, ""));
	private final Setting.Slider shown = add(new Setting.Slider("Display Duration", 3000, 500, 30000, 100, " ms"));
	private final Setting.Slider fadeOut = add(new Setting.Slider("Fade Duration", 1000, 0, 5000, 50, " ms"));
	private final Setting.Slider fadeIn = add(new Setting.Slider("Fade In Duration", 200, 0, 2000, 25, " ms"));
	private final Setting.Color textColor = add(new Setting.Color("Text Color", 0xFFFFFFFF,
		0xFFFFFFFF, 0xFFE9EAEE, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6));
	private final Setting.Slider maxDistance = add(new Setting.Slider("Max Distance", 64, 8, 256, 1, " blocks"));
	private final Setting.Slider rise = add(new Setting.Slider("Upward Speed", 0.1, 0, 5, 0.01, " blocks/s"));
	private final Setting.Color bgColor = add(new Setting.Color("Background Color", 0xFF000000,
		0xFF000000, 0xFF101216, 0xFF1D2028, 0xFF2A1A12, 0xFF101A2E));
	private final Setting.Slider bgTransparency = add(new Setting.Slider("Background Transparency", 85, 0, 100, 1, "%"));
	private final Setting.Choice style = add(new Setting.Choice("Style", "Light", "Light", "Dark", "Custom"));
	private final Setting.Toggle showTail = add(new Setting.Toggle("Show Tail", true));
	private final Setting.Toggle popIn = add(new Setting.Toggle("Pop Animation", true));
	private final Setting.Slider maxWidth = add(new Setting.Slider("Max Width", 150, 60, 300, 10, " px"));
	private final Setting.Slider perPlayer = add(new Setting.Slider("Max Bubbles Per Player", 3, 1, 5, 1, ""));

	private record Bubble(UUID owner, @Nullable Component header, Component text, Vec3 start, long born) {}

	private final List<Bubble> bubbles = new ArrayList<>();
	private String lastKey = "";
	private long lastAt;

	public ChatBubbles() {
		super("Chat Bubbles", Category.CHAT, '\ue116', "Speech bubbles over players' heads when they chat.");
		INSTANCE = this;
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			if (on() && !bubbles.isEmpty()) draw(ctx);
		});
	}

	// ---------- who said it ----------

	public static void said(Component c, Senders.@Nullable Hit hit) {
		ChatBubbles b = INSTANCE;
		Minecraft mc = Minecraft.getInstance();
		if (b == null || !b.on() || mc.level == null) return;
		Player p = hit == null ? null : mc.level.getPlayerByUUID(hit.id());
		String text = hit == null ? "" : hit.body().trim();
		if (p == null || text.isEmpty()) {
			// signed chat already says who, server formatted chat gets matched against the players around you
			Match m = match(mc.level, c.getString().trim());
			if (m == null) return;
			p = m.p;
			text = m.text;
		}
		// some chat mods add each line twice (once with their timestamp), that shouldn't make two bubbles
		String key = p.getUUID() + "|" + text;
		long now = Util.getMillis();
		boolean dupe = key.equals(b.lastKey) && now - b.lastAt < 300;
		b.lastKey = key;
		b.lastAt = now;
		if (dupe) return;
		Component header = b.showName.on() && !p.isInvisible() ? p.getDisplayName() : null;
		b.bubbles.add(new Bubble(p.getUUID(), header, Component.literal(text), p.position().add(0, p.getBbHeight() + b.height.get(), 0), now));
	}

	private record Match(Player p, String text, int at) {}

	// earliest "name <separator> text" in the line for any loaded player, by account name, name or nametag name
	private static @Nullable Match match(ClientLevel level, String msg) {
		if (msg.isEmpty()) return null;
		Match best = null;
		for (AbstractClientPlayer p : level.players()) {
			Set<String> names = new LinkedHashSet<>();
			names.add(p.getGameProfile().name().trim());
			names.add(p.getName().getString().trim());
			names.add(p.getDisplayName().getString().trim());
			for (String n : names) {
				Match m = matchName(msg, p, n);
				if (m != null && (best == null || m.at < best.at)) best = m;
			}
		}
		return best;
	}

	private static @Nullable Match matchName(String msg, Player p, String name) {
		if (name.isBlank()) return null;
		int from = 0;
		while (from < msg.length()) {
			int i = msg.indexOf(name, from);
			if (i < 0) return null;
			int end = i + name.length();
			from = i + 1;
			// "Steve" inside "Steve123" or "xSteve" isn't them
			if (!edge(msg, i - 1) || !edge(msg, end)) continue;
			int start = afterSeparator(msg, end);
			if (start < 0) continue;
			String text = msg.substring(start).trim();
			if (!text.isEmpty()) return new Match(p, text, i);
		}
		return null;
	}

	private static boolean edge(String s, int i) {
		if (i < 0 || i >= s.length()) return true;
		char c = s.charAt(i);
		return !Character.isLetterOrDigit(c) && c != '_';
	}

	// "<Steve> hi", "Steve: hi", "Steve » hi", "[Steve] hi", "Steve | hi", "Steve - hi"
	private static int afterSeparator(String s, int i) {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
		boolean any = false;
		while (i < s.length() && ">:»|-]".indexOf(s.charAt(i)) >= 0) {
			any = true;
			i++;
			while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
		}
		return any ? i : -1;
	}

	// ---------- timing ----------

	private float alpha(Bubble b, long now) {
		long age = Math.max(0, now - b.born);
		double in = fadeIn.get(), stay = shown.get(), out = fadeOut.get();
		if (age > stay + out) return 0;
		if (in > 0 && age < in) return (float) (age / in);
		if (out > 0 && age > stay) return (float) Math.clamp(1 - (age - stay) / out, 0, 1);
		return 1;
	}

	// pops in with a little overshoot and shrinks a bit while it fades out
	private float pop(Bubble b, long now, float alpha) {
		if (!popIn.on()) return 1;
		long age = now - b.born;
		float s = 1;
		if (age < 180) {
			float u = age / 180f - 1;
			s = 0.5f + 0.5f * (1 + 2.70158f * u * u * u + 1.70158f * u * u);
		}
		if (age > shown.get()) s *= 0.85f + 0.15f * alpha;
		return Math.max(0.05f, s);
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.level == null) {
			bubbles.clear();
			return;
		}
		long now = Util.getMillis();
		bubbles.removeIf(b -> now - b.born > shown.get() + fadeOut.get());
	}

	@Override
	protected void disable() {
		bubbles.clear();
	}

	// ---------- drawing ----------

	private record Look(int fill, int outline, int text, int header) {}

	private Look look(float a) {
		return switch (style.get()) {
			case "Dark" -> new Look(ARGB.multiplyAlpha(0xB4101014, a), ARGB.multiplyAlpha(0x55FFFFFF, a), ARGB.multiplyAlpha(0xFFFFFFFF, a), ARGB.multiplyAlpha(0xFFB8B8B8, a));
			case "Custom" -> new Look(ARGB.color((float) (1 - bgTransparency.get() / 100) * a, bgColor.get()),
				ARGB.multiplyAlpha(0x40FFFFFF, a), ARGB.multiplyAlpha(textColor.get(), a), ARGB.multiplyAlpha(0xFFB8B8B8, a));
			default -> new Look(ARGB.multiplyAlpha(0xF2FFFFFF, a), ARGB.multiplyAlpha(0x70303030, a), ARGB.multiplyAlpha(0xFF1E1E1E, a), ARGB.multiplyAlpha(0xFF505050, a));
		};
	}

	// wrapped lines (the name first when there is one) and the box size in text pixels
	private record Layout(List<FormattedCharSequence> lines, boolean named, float w, float h) {}

	private Layout layout(Font f, Bubble b) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		if (b.header != null) lines.add(b.header.getVisualOrderText());
		lines.addAll(f.split(b.text, maxWidth.get().intValue()));
		float tw = 0;
		for (FormattedCharSequence l : lines) tw = Math.max(tw, f.width(l));
		return new Layout(lines, b.header != null, tw + PAD_X * 2, lines.size() * LINE - 1 + PAD_Y * 2);
	}

	private void draw(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
		long now = Util.getMillis();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		CameraRenderState cam = ctx.levelState().cameraRenderState;
		float scale = textScale.get().floatValue();
		int most = perPlayer.get().intValue();
		double far = maxDistance.get() * maxDistance.get();

		// grouped by speaker, newest first, a speaker's bubbles stack upward
		Map<UUID, List<Bubble>> groups = new LinkedHashMap<>();
		for (Bubble b : bubbles) if (alpha(b, now) > 0) groups.computeIfAbsent(b.owner, k -> new ArrayList<>()).add(b);
		for (List<Bubble> g : groups.values()) {
			g.sort((a, b) -> Long.compare(b.born, a.born));
			Bubble newest = g.getFirst();
			Player p = mc.level.getPlayerByUUID(newest.owner);
			Vec3 at;
			if (p != null) {
				// chat never gives an invisible player away
				if (p.isInvisible() && p != mc.player) continue;
				at = p.getPosition(pt).add(0, p.getBbHeight() + height.get(), 0);
			} else {
				// they walked out of range after talking, the bubble stays where they were and drifts up
				at = newest.start.add(0, (now - newest.born) / 1000.0 * rise.get(), 0);
			}
			if (at.distanceToSqr(mc.player.position()) > far) continue;
			float lift = 0;
			for (int i = 0; i < Math.min(g.size(), most); i++) {
				Bubble b = g.get(i);
				float a = alpha(b, now), s = scale * pop(b, now, a);
				boolean tail = showTail.on() && i == 0;
				Layout l = layout(mc.font, b);
				bubble(ctx.poseStack(), ctx.submitNodeCollector(), cam, at, lift, s, l, look(a), tail);
				lift += (l.h + (tail ? TAIL_H : 0) + GAP) * s;
			}
		}
	}

	private static void bubble(PoseStack ps, SubmitNodeCollector out, CameraRenderState cam, Vec3 at, float lift, float s, Layout l, Look look, boolean tail) {
		ps.pushPose();
		ps.translate(at.x - cam.pos.x, at.y - cam.pos.y + lift, at.z - cam.pos.z);
		ps.mulPose(cam.orientation);
		ps.scale(s, -s, s);
		// y points down from here, the tail tip (or the box's bottom edge) sits on the anchor
		float bottom = tail ? -TAIL_H : 0, top = bottom - l.h, left = -l.w / 2, right = l.w / 2;
		out.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> {
			Matrix4f m = pose.pose();
			// outline a pixel bigger and a bit further back, then the fill, the text goes in front of both
			roundRect(vc, m, left - 1, top - 1, right + 1, bottom + 1, RADIUS + 1, -0.6f, look.outline);
			if (tail) tri(vc, m, 0, 1.6f, -TAIL_W - 1.4f, bottom + 0.5f, TAIL_W + 1.4f, bottom + 0.5f, -0.6f, look.outline);
			roundRect(vc, m, left, top, right, bottom, RADIUS, -0.3f, look.fill);
			if (tail) tri(vc, m, 0, 0, -TAIL_W, bottom - 0.5f, TAIL_W, bottom - 0.5f, -0.3f, look.fill);
		});
		Font f = Minecraft.getInstance().font;
		float y = top + PAD_Y;
		for (int i = 0; i < l.lines.size(); i++) {
			FormattedCharSequence line = l.lines.get(i);
			int c = i == 0 && l.named ? look.header : look.text;
			out.submitText(ps, -f.width(line) / 2f, y, line, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, c, 0, 0);
			y += LINE;
		}
		ps.popPose();
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float z, int c) {
		vc.addVertex(m, x0, y0, z).setColor(c);
		vc.addVertex(m, x1, y1, z).setColor(c);
		vc.addVertex(m, x2, y2, z).setColor(c);
		vc.addVertex(m, x3, y3, z).setColor(c);
	}

	private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int c) {
		if (x1 > x0 && y1 > y0) quad(vc, m, x0, y0, x0, y1, x1, y1, x1, y0, z, c);
	}

	// a quad with the tip twice, debugQuads has no triangles
	private static void tri(VertexConsumer vc, Matrix4f m, float tx, float ty, float ax, float ay, float bx, float by, float z, int c) {
		quad(vc, m, tx, ty, bx, by, ax, ay, tx, ty, z, c);
	}

	private static void roundRect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float radius, float z, int c) {
		float r = Math.min(radius, Math.min(x1 - x0, y1 - y0) / 2);
		rect(vc, m, x0 + r, y0, x1 - r, y1, z, c);
		rect(vc, m, x0, y0 + r, x0 + r, y1 - r, z, c);
		rect(vc, m, x1 - r, y0 + r, x1, y1 - r, z, c);
		corner(vc, m, x0 + r, y0 + r, r, 180, z, c);
		corner(vc, m, x1 - r, y0 + r, r, 270, z, c);
		corner(vc, m, x1 - r, y1 - r, r, 0, z, c);
		corner(vc, m, x0 + r, y1 - r, r, 90, z, c);
	}

	// quarter circle from start (0 = +x, 90 = +y which is down here) as thin fans
	private static void corner(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, float start, float z, int c) {
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = Math.toRadians(start + 90.0 * i / SEGMENTS), a1 = Math.toRadians(start + 90.0 * (i + 1) / SEGMENTS);
			float ax = cx + (float) Math.cos(a0) * r, ay = cy + (float) Math.sin(a0) * r;
			float bx = cx + (float) Math.cos(a1) * r, by = cy + (float) Math.sin(a1) * r;
			quad(vc, m, cx, cy, ax, ay, bx, by, bx, by, z, c);
		}
	}
}
