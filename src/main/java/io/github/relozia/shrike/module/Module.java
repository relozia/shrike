package io.github.relozia.shrike.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.config.Config;
import io.github.relozia.shrike.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public abstract class Module {
	// never change this once it ships. the config keys on it and so does the blacklist in relozia/pubshrike,
	// so renaming one here silently stops a server's block from matching. the display name is free to change
	public final String id;
	public final String name;
	public final String desc;
	public final Category cat;
	public final char icon;

	private boolean on;
	private boolean blocked;
	// whether enable() has run (and disable() hasn't since)
	private boolean live;
	private boolean fav;
	private final List<Setting<?>> settings = new ArrayList<>();
	// the key from the settings header: toggles the module, or keeps it on only while held
	public final Setting.Bind key = new Setting.Bind("Key", -1);
	public boolean hold;

	protected Module(String id, String name, Category cat, char icon, String desc) {
		this.id = id;
		this.name = name;
		this.cat = cat;
		this.icon = icon;
		this.desc = desc;
	}

	protected <S extends Setting<?>> S add(S s) {
		settings.add(s);
		return s;
	}

	public List<Setting<?>> settings() {
		return settings;
	}

	public boolean on() {
		return on && !blocked;
	}

	// the switch as you set it, which is what gets saved. on() is this minus anything the server disallows
	public boolean enabled() {
		return on;
	}

	public boolean blocked() {
		return blocked;
	}

	// set while you're on a server whose rules don't allow this. deliberately never saved and never touches `on`,
	// so leaving brings the module back exactly as you had it
	public void block(boolean v) {
		if (blocked == v) return;
		blocked = v;
		sync();
	}

	public void toggle() {
		setOn(!on);
	}

	public void setOn(boolean v) {
		if (on == v) return;
		on = v;
		sync();
		Config.dirty();
	}

	// enable/disable follow what actually ran, not just the flag: switching profile reloads every module's state
	// mid game, and a module that starts something (the music helper process) must not start it twice or leave
	// it running
	public void sync() {
		// on(), not on, so blocking a module actually stops it running rather than only hiding the switch
		boolean want = on();
		if (want && !live) {
			live = true;
			enable();
		} else if (!want && live) {
			live = false;
			disable();
		}
	}

	protected void enable() {}

	protected void disable() {}

	// fresh install default, only matters when the config has never seen this module
	protected void startOn() {
		on = true;
	}

	// called every client tick while the module is on
	public void tick(Minecraft mc) {}

	// every tick for every module, on or off, so a bind can turn it on
	public void keys(Minecraft mc) {
		if (!key.bound()) return;
		if (hold) {
			// a screen opening doesn't count as letting go, otherwise opening chat turns zoom style holds off
			if (mc.gui.screen() == null) setOn(key.down(mc));
		} else if (key.pressed(mc)) {
			toggle();
		}
	}

	public boolean fav() {
		return fav;
	}

	public void setFav(boolean v) {
		fav = v;
		Config.dirty();
	}

	// ---------- config ----------

	public JsonObject save() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", on);
		if (fav) o.addProperty("favorite", true);
		if (key.bound()) {
			o.addProperty("key", key.get());
			if (hold) o.addProperty("hold", true);
		}
		JsonObject s = new JsonObject();
		for (Setting<?> st : settings()) if (!(st instanceof Setting.Action)) s.add(st.name, st.save());
		o.add("settings", s);
		return o;
	}

	public void load(JsonObject o) {
		on = o.has("enabled") && o.get("enabled").getAsBoolean();
		fav = o.has("favorite") && o.get("favorite").getAsBoolean();
		key.set(o.has("key") ? o.get("key").getAsInt() : -1);
		hold = o.has("hold") && o.get("hold").getAsBoolean();
		JsonObject s = o.getAsJsonObject("settings");
		if (s != null) {
			for (Setting<?> st : settings()) {
				JsonElement e = s.get(st.name);
				if (e == null) continue;
				// hand edited configs are the usual culprit, one bad value shouldn't reset the rest
				try {
					st.load(e);
				} catch (RuntimeException ex) {
					Shrike.LOG.warn("bad value for {} / {}: {}", name, st.name, e);
				}
			}
		}
		sync();
	}
}
