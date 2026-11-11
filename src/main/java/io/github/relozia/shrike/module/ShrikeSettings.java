package io.github.relozia.shrike.module;

import com.google.gson.JsonObject;
import io.github.relozia.shrike.setting.Setting;

public class ShrikeSettings extends Module {
	// rust, amber, slate, teal, violet, crimson. same palette we picked the default from
	public final Setting.Color accent = add(new Setting.Color("Accent Color", 0xFFE0703A,
		0xFFE0703A, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6, 0xFF8B5CF6, 0xFFE5484D));
	public final Setting.Choice menuScale = add(new Setting.Choice("Menu Scale", "120%", "80%", "Auto", "120%"));
	public final Setting.Choice menuStyle = add(new Setting.Choice("Menu Style", "Blur", "Blur", "Solid"));
	// the two halves of the hud look, frosted panels and the soft shadow under them, each can go off on its own
	public final Setting.Toggle hudBlur = add(new Setting.Toggle("HUD Blur", true));
	public final Setting.Toggle hudShadow = add(new Setting.Toggle("HUD Shadow", true));
	public final Setting.Color shadowColor = add(new Setting.Color("HUD Shadow Color", 0xFF000000, 0xFF000000, 0xFF101A2E, 0xFF2A1A12, 0xFFE0703A));
	public final Setting.Slider shadowSize = add(new Setting.Slider("HUD Shadow Size", 100, 25, 300, 5, "%"));
	public final Setting.Slider shadowSpread = add(new Setting.Slider("HUD Shadow Spread", 0, 0, 8, 0.25, "px"));
	public final Setting.Slider shadowOpacity = add(new Setting.Slider("HUD Shadow Opacity", 100, 0, 200, 5, "%"));
	// the hud editor's toolbar switches, kept here so they stick between opens
	public final Setting.Toggle editorSnap = add(new Setting.Toggle("Editor Snapping", true));
	public final Setting.Toggle editorGrid = add(new Setting.Toggle("Editor Grid", false));
	public final Setting.Toggle sounds = add(new Setting.Toggle("Menu Sounds", true));
	public final Setting.Slider volume = add(new Setting.Slider("Menu Volume", 40, 0, 100, 5, "%"));

	public ShrikeSettings() {
		super("shrike", "Shrike", Category.UTILITY, '\ue154', "Menu and HUD look, the HUD editor, menu sounds.");
	}

	// shares the module plumbing but can't be turned off, so "enabled" in the file would just be noise
	@Override
	public JsonObject save() {
		JsonObject o = super.save();
		o.remove("enabled");
		return o;
	}
}
