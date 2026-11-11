package io.github.relozia.shrike.gui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.qol.Qol;
import io.github.relozia.shrike.visual.ScreenTweaks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

// Shrike's main menu: the buttons in a column on the left over the panorama, your last few servers on the right
// with live ping. it's a TitleScreen so anything checking for one (fabric's test runner, other mods) still finds it
// the buttons are vanilla's own: a plain TitleScreen gets built off screen and its buttons get pressed for you, so
// Realms, Friends and whatever other mods add (Mod Menu, Flashback, Replay Mod) keep working the way they always do
// issues:
//   - a mod that draws on the title screen instead of adding a button won't show up here
//   - no splash text, no realms news dot
public class ShrikeTitleScreen extends TitleScreen {
	private static final int SERVERS = 5;
	private static final char I_REFRESH = '\ue145';
	private static final float BTN_W = 300, ROW = 58, PANEL_W = 420;

	private record Entry(String label, AbstractButton b) {}
	private record Hit(float x, float y, float w, float h, Runnable r) {}

	private static final class Server {
		final ServerData d;
		final FaviconTexture icon;
		final long last;
		byte[] iconBytes;

		Server(ServerData d, FaviconTexture icon, long last) {
			this.d = d;
			this.icon = icon;
			this.last = last;
		}
	}

	private final List<Entry> main = new ArrayList<>(), extra = new ArrayList<>(), links = new ArrayList<>();
	private AbstractButton options, quit, credits, multiplayer;
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private final List<Server> servers = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private String version = "";

	// layout pixels: the window scaled by its height, anchored at the top left. the column's top is shared with the panel
	private float eff, lw, lh, mx, my, vis, top;
	private long last = Util.getMillis();

	public ShrikeTitleScreen() {
		super(false, null);
	}

	@Override
	protected void init() {
		main.clear();
		extra.clear();
		links.clear();
		TitleScreen v = new TitleScreen();
		v.init(width, height);
		for (GuiEventListener c : v.children()) {
			if (!(c instanceof AbstractButton b)) continue;
			String key = b.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : "";
			String label = b.getMessage().getString();
			switch (key) {
				case "menu.singleplayer", "menu.online", "menu.playdemo", "menu.resetdemo" -> main.add(new Entry(label, b));
				case "menu.multiplayer" -> {
					main.add(new Entry(label, b));
					multiplayer = b;
				}
				case "menu.options" -> options = b;
				case "menu.quit" -> quit = b;
				case "title.credits" -> credits = b;
				// vanilla's own labels trail off with "...", these sit in a row of short links
				case "options.language" -> links.add(new Entry("Language", b));
				case "options.accessibility" -> links.add(new Entry("Accessibility", b));
				case "gui.friends.open" -> links.add(new Entry("Friends", b));
				// the dev build's "TW" test world button
				default -> {
					if (!label.isBlank() && !label.equals("TW")) extra.add(new Entry(label, b));
				}
			}
		}
		version = "Minecraft " + SharedConstants.getCurrentVersion().name() + (Minecraft.checkModStatus().shouldReportAsModified() ? I18n.get("menu.modded") : "");
		if (servers.isEmpty() && ScreenTweaks.Title.quickJoin()) loadServers();
	}

	// played ones newest first, the never played ones after them in your list's order (the sort is stable)
	private void loadServers() {
		ServerList list = new ServerList(minecraft);
		list.load();
		List<ServerData> all = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) all.add(list.get(i));
		all.sort(Comparator.comparingLong(d -> -Qol.ServerNotes.lastPlayed(d.ip)));
		for (ServerData d : all.subList(0, Math.min(SERVERS, all.size()))) {
			servers.add(new Server(d, FaviconTexture.forServer(minecraft.getTextureManager(), d.ip), Qol.ServerNotes.lastPlayed(d.ip)));
			ping(d);
		}
	}

	// own threads, not Util's download pool. the first ping sets up ServerNameResolver, which finds mojang's server
	// blocklist through the thread's context class loader. the download threads carry the plain app loader, so it
	// found a second copy of the blocklist class, "MojangBlockListSupplier not a subtype", and that crashed the game
	// at the title screen in Prism. these get the loader the game itself runs on, like vanilla's server list threads
	static final ThreadPoolExecutor PINGS = new ThreadPoolExecutor(3, 3, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
		Thread t = new Thread(r, "Shrike Pinger");
		t.setDaemon(true);
		t.setContextClassLoader(ShrikeTitleScreen.class.getClassLoader());
		return t;
	});

	static {
		PINGS.allowCoreThreadTimeOut(true);
	}

	// the same steps the server list takes, on a background thread since the address lookup blocks
	private void ping(ServerData d) {
		d.setState(ServerData.State.PINGING);
		d.motd = CommonComponents.EMPTY;
		d.status = CommonComponents.EMPTY;
		PINGS.execute(() -> {
			try {
				pinger.pingServer(d, () -> {}, () -> d.setState(d.protocol == SharedConstants.getCurrentVersion().protocolVersion()
					? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE), EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
			} catch (Exception | LinkageError e) {
				// unknown host lands here, and a resolver some other mod already broke the same way (a LinkageError
				// on every use after that). either way it's one offline row, never a crash
				Shrike.LOG.warn("ping {} failed: {}", d.ip, e.toString());
				d.setState(ServerData.State.UNREACHABLE);
			}
		});
	}

	@Override
	public void tick() {
		pinger.tick();
	}

	@Override
	public void removed() {
		pinger.removeAll();
		for (Server s : servers) s.icon.close();
		servers.clear();
		super.removed();
	}

	// ---------- drawing ----------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int x, int y, float a) {
		extractPanorama(g, a);
		// darker behind the column so the buttons read on a bright panorama
		Shape.gradient(g, 0, 0, width * 0.55f, height, 0, 0xA6080A0E, 0x00080A0E, 0x00080A0E, 0xA6080A0E);
		Shape.gradient(g, 0, height * 0.7f, width, height * 0.3f, 0, 0, 0, 0x80080A0E, 0x80080A0E);
	}

	private boolean in(float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private void hit(float x, float y, float w, float h, Runnable r) {
		hits.add(new Hit(x, y, w, h, r));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int gmx, int gmy, float a) {
		long now = Util.getMillis();
		float dt = Math.min(0.1f, (now - last) / 1000f);
		last = now;
		vis = Math.min(1, vis + dt / 0.5f);
		var win = minecraft.getWindow();
		float gs = (float) win.getGuiScale();
		// sized off the window's height, not the menu's scale: there's a whole empty screen to fill here
		eff = Mth.clamp(win.getHeight() / 900f, 0.5f, 2.4f);
		lw = win.getWidth() / eff;
		lh = win.getHeight() / eff;
		mx = gmx * gs / eff;
		my = gmy * gs / eff;
		hits.clear();
		Ui.alpha = vis * vis * (3 - 2 * vis);

		g.pose().pushMatrix();
		g.pose().scale(eff / gs, eff / gs);
		column(g);
		if (ScreenTweaks.Title.quickJoin()) quickJoin(g);
		footer(g);
		g.pose().popMatrix();
		Ui.alpha = 1;
	}

	private void column(GuiGraphicsExtractor g) {
		float x = Math.max(56, lw * 0.07f);
		float total = 96 + main.size() * 58 + extra.size() * 48 + 14 + 46;
		float y = Math.max(40, (lh - total) / 2 - 20);
		// slides in a little from the left on open
		x -= 24 * (1 - Ui.alpha);

		top = y + 96;
		Ui.logo(g, x - 4, y - 6, 60, Ui.accent());
		Ui.label(g, "Shrike", true, 44, x + 66, y + 42, 0xFFFFFFFF, 'l');
		y += 96;

		for (Entry e : main) {
			big(g, e, x, y, 50, 18);
			y += 58;
		}
		for (Entry e : extra) {
			big(g, e, x, y, 40, 15.5f);
			y += 48;
		}
		y += 14 - 8;
		float hw = (BTN_W - 8) / 2;
		if (options != null) small(g, "Options", options, x, y, hw);
		if (quit != null) small(g, "Quit", quit, x + hw + 8, y, hw);
	}

	// the main ones: glass, the label on the left, an accent bar and a small nudge when you're on it
	private void big(GuiGraphicsExtractor g, Entry e, float x, float y, float h, float size) {
		boolean live = e.b.active, over = live && in(x, y, BTN_W, h);
		float hv = over ? 1 : 0;
		Shape.glassPx(g, x, y, BTN_W, h, 11, Ui.alpha, 14);
		Shape.rect(g, x, y, BTN_W, h, 12, Ui.c(over ? 0x8C161C24 : 0x7010141A));
		Shape.outline(g, x, y, BTN_W, h, 12, Ui.c(over ? 0x40DCE6F5 : 0x24DCE6F5), 1);
		if (over) Shape.rect(g, x + 1.5f, y + h / 2 - 9, 3, 18, 1.5f, Ui.c(Ui.accent()));
		Ui.label(g, e.label, true, size, x + 20 + 4 * hv, y + h / 2 + size * 0.3f, live ? over ? ShrikeScreen.PRIMARY : ShrikeScreen.SECONDARY : ShrikeScreen.FAINT, 'l');
		if (live) hit(x, y, BTN_W, h, () -> press(e.b));
	}

	private void small(GuiGraphicsExtractor g, String label, AbstractButton b, float x, float y, float w) {
		boolean over = in(x, y, w, 40);
		Shape.glassPx(g, x, y, w, 40, 10, Ui.alpha, 14);
		Shape.rect(g, x, y, w, 40, 11, Ui.c(over ? 0x8C161C24 : 0x5C10141A));
		Shape.outline(g, x, y, w, 40, 11, Ui.c(over ? 0x40DCE6F5 : 0x1FDCE6F5), 1);
		Ui.label(g, label, true, 15, x + w / 2, y + 25, over ? ShrikeScreen.PRIMARY : ShrikeScreen.MUTED, 'c');
		hit(x, y, w, 40, () -> press(b));
	}

	private void press(AbstractButton b) {
		Ui.sound("click");
		b.onPress(new MouseButtonInfo(0, 0));
	}

	private void quickJoin(GuiGraphicsExtractor g) {
		float x = lw - Math.max(56, lw * 0.07f) - PANEL_W + 24 * (1 - Ui.alpha);
		int n = servers.size();
		float h = 52 + Math.max(1, n) * ROW + 10;
		// lines up with the first button, unless that would push it off the bottom
		float y = Math.min(top - 52 + 6, lh - 70 - h);
		Shape.glassPx(g, x, y, PANEL_W, h, 15, Ui.alpha, 16);
		Shape.shadow(g, x, y + 6, PANEL_W, h, 16, 14, 0, Ui.c(0x40000000));
		Shape.rect(g, x, y, PANEL_W, h, 16, Ui.c(0x9E10141A));
		Shape.outline(g, x, y, PANEL_W, h, 16, Ui.c(0x26DCE6F5), 1);
		Ui.label(g, "Jump back in", true, 19, x + 20, y + 33, ShrikeScreen.PRIMARY, 'l');
		boolean canJoin = multiplayer != null && multiplayer.active;
		if (multiplayer != null) {
			boolean ov = in(x + PANEL_W - 130, y + 14, 112, 28);
			Ui.label(g, "All servers", false, 13.5f, x + PANEL_W - 20, y + 33, ov ? ShrikeScreen.PRIMARY : ShrikeScreen.MUTED, 'r');
			if (canJoin) hit(x + PANEL_W - 130, y + 14, 112, 28, () -> press(multiplayer));
		}
		// pings only go out when the screen opens, this sends them again without leaving and coming back
		float rx = x + PANEL_W - 130 - 34;
		boolean ro = in(rx, y + 15, 26, 26);
		if (ro) Shape.rect(g, rx, y + 15, 26, 26, 8, Ui.c(0x1CFFFFFF));
		Ui.glyph(g, I_REFRESH, rx + 13, y + 28, 12, ro ? ShrikeScreen.PRIMARY : ShrikeScreen.MUTED);
		hit(rx, y + 15, 26, 26, () -> {
			Ui.sound("click");
			for (Server s : servers) ping(s.d);
		});
		if (n == 0) {
			Ui.label(g, "No servers yet, add some under Multiplayer", false, 14, x + 20, y + 52 + ROW / 2 + 5, ShrikeScreen.FAINT, 'l');
			return;
		}
		float ry = y + 52;
		for (Server s : servers) {
			row(g, s, x + 8, ry, PANEL_W - 16, canJoin);
			ry += ROW;
		}
	}

	private void row(GuiGraphicsExtractor g, Server s, float x, float y, float w, boolean canJoin) {
		ServerData d = s.d;
		boolean over = canJoin && in(x, y, w, ROW - 4);
		if (over) Shape.rect(g, x, y, w, ROW - 4, 11, Ui.c(0x1CFFFFFF));
		byte[] bytes = d.getIconBytes();
		if (!Arrays.equals(bytes, s.iconBytes)) {
			s.iconBytes = bytes;
			try {
				if (bytes == null) s.icon.clear();
				else s.icon.upload(NativeImage.read(bytes));
			} catch (Exception e) {
				Shrike.LOG.warn("bad server icon for {}: {}", d.ip, e.getMessage());
			}
		}
		Shape.image(g, s.icon.textureLocation(), x + 10, y + 8, 38, 38, 8, Ui.alpha);
		String name = d.name.isBlank() ? d.ip : d.name;
		float textW = w - 60 - 96;
		Ui.label(g, fit(name, textW), true, 15.5f, x + 60, y + 25, ShrikeScreen.PRIMARY, 'l');
		Ui.label(g, s.last > 0 ? "Played " + Qol.ServerNotes.ago(s.last) : "Not played yet", false, 12.5f, x + 60, y + 42, ShrikeScreen.MUTED, 'l');

		String top, bottom = "";
		int c;
		// a failed ping only swaps the motd for "can't connect", the state stays on pinging
		boolean failed = d.motd.getContents() instanceof TranslatableContents t && t.getKey().equals("multiplayer.status.cannot_connect");
		switch (failed ? ServerData.State.UNREACHABLE : d.state()) {
			case SUCCESSFUL -> {
				top = d.ping + " ms";
				c = d.ping < 80 ? 0xFF57D163 : d.ping < 180 ? 0xFFF5C542 : 0xFFE5484D;
				if (d.players != null) bottom = d.players.online() + " / " + d.players.max();
			}
			case INCOMPATIBLE -> {
				top = "Other version";
				c = 0xFFE5484D;
				bottom = d.version.getString();
			}
			case UNREACHABLE -> {
				top = "Offline";
				c = ShrikeScreen.FAINT;
			}
			default -> {
				// three dots that fill in while it waits
				top = ".".repeat(1 + (int) (Util.getMillis() / 300 % 3));
				c = ShrikeScreen.MUTED;
			}
		}
		Ui.label(g, top, true, 13.5f, x + w - 12, y + 25, c, 'r');
		if (!bottom.isEmpty()) Ui.label(g, fit(bottom, 90), false, 12, x + w - 12, y + 42, ShrikeScreen.FAINT, 'r');
		if (over) hit(x, y, w, ROW - 4, () -> {
			Ui.sound("click");
			ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(d.ip), d, false, null);
		});
	}

	private static String fit(String s, float max) {
		if (Ui.tw(s, true, 15.5f) <= max) return s;
		while (s.length() > 1 && Ui.tw(s + "...", true, 15.5f) > max) s = s.substring(0, s.length() - 1);
		return s + "...";
	}

	private void footer(GuiGraphicsExtractor g) {
		float x = Math.max(56, lw * 0.07f), y = lh - 26;
		Ui.label(g, version, false, 12.5f, x, y, ShrikeScreen.FAINT, 'l');
		float lx = x;
		float ly = y - 24;
		for (Entry e : links) lx = link(g, e.label, lx, ly, () -> press(e.b));
		link(g, "Shrike", lx, ly, () -> minecraft.gui.setScreen(new ShrikeScreen()));
		if (credits != null) {
			String t = credits.getMessage().getString();
			float w = Ui.tw(t, false, 12.5f), cx = lw - 20 - w;
			boolean ov = in(cx, y - 14, w, 20);
			Ui.label(g, t, false, 12.5f, lw - 20, y, ov ? ShrikeScreen.PRIMARY : ShrikeScreen.FAINT, 'r');
			hit(cx, y - 14, w, 20, () -> press(credits));
		}
	}

	private float link(GuiGraphicsExtractor g, String label, float x, float y, Runnable r) {
		float w = Ui.tw(label, false, 13.5f);
		boolean ov = in(x - 4, y - 15, w + 8, 22);
		Ui.label(g, label, false, 13.5f, x, y, ov ? ShrikeScreen.PRIMARY : ShrikeScreen.MUTED, 'l');
		hit(x - 4, y - 15, w + 8, 22, r);
		return x + w + 20;
	}

	// ---------- input ----------

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.button() != 0) return false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (in(h.x, h.y, h.w, h.h)) {
				h.r.run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		// the menu works from here too, it's where you'd turn this screen off
		if (KeyMappingHelper.getBoundKeyOf(Shrike.menuKey).getValue() == e.key()) {
			minecraft.gui.setScreen(new ShrikeScreen());
			return true;
		}
		return super.keyPressed(e);
	}
}
