package io.github.relozia.shrike.camera;

import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

// hold C to zoom. scroll while zoomed changes how far, eases in and out
// issues:
//   - C is vanilla's "save hotbar" key in creative, rebind one of them if you use both
public class Zoom extends Module {
	static Zoom INSTANCE;

	private final Setting.Bind key = add(new Setting.Bind("Zoom Key", 67));
	private final Setting.Slider start = add(new Setting.Slider("Default Zoom", 3, 1, 10, 0.1, "x"));
	private final Setting.Slider max = add(new Setting.Slider("Max Zoom", 50, 2, 50, 1, "x"));
	private final Setting.Slider step = add(new Setting.Slider("Scroll Step", 10, 2, 25, 1, "%"));
	private final Setting.Slider smooth = add(new Setting.Slider("Smoothness", 60, 0, 95, 5, "%"));
	private final Setting.Toggle toggle = add(new Setting.Toggle("Toggle Mode", false));
	private final Setting.Toggle sens = add(new Setting.Toggle("Lower Sensitivity", true));

	private boolean zooming;
	private float level = 3;
	private float cur = 1;
	private long last;

	public Zoom() {
		super("zoom", "Zoom", Category.UTILITY, '\ue1b6', "Hold C to zoom in, scroll to zoom further.");
		INSTANCE = this;
	}

	@Override
	public void tick(Minecraft mc) {
		boolean want = toggle.on() ? zooming ^ key.pressed(mc) : key.down(mc);
		if (want && !zooming) level = start.get().floatValue();
		zooming = want;
	}

	@Override
	protected void disable() {
		zooming = false;
		cur = 1;
	}

	// called once per frame from Camera.calculateFov. the easing is time based so it feels the same at any fps
	public static float fov(float fov) {
		Zoom z = INSTANCE;
		if (z == null || !z.on()) return fov;
		long now = Util.getMillis();
		float dt = Math.min(0.1f, (now - z.last) / 1000f);
		z.last = now;
		float target = z.zooming ? z.level : 1;
		float keep = (float) Math.pow(z.smooth.get() / 100.0, dt * 20);
		z.cur = target + (z.cur - target) * keep;
		if (Math.abs(z.cur - target) < 0.001f) z.cur = target;
		return fov / z.cur;
	}

	public static boolean scroll(double dy) {
		Zoom z = INSTANCE;
		if (z == null || !z.on() || !z.zooming || dy == 0) return false;
		float f = 1 + z.step.get().floatValue() / 100f;
		// by the amount, not the sign. a normal wheel sends 1 per notch, smooth wheels and trackpads send
		// lots of small ones that would each count as a full notch otherwise
		z.level = Mth.clamp(z.level * (float) Math.pow(f, dy), 1, z.max.get().floatValue());
		return true;
	}

	// mouse moves the same distance on screen whatever the zoom, otherwise 10x feels twitchy
	public static double sensitivity() {
		Zoom z = INSTANCE;
		return z != null && z.on() && z.sens.on() && z.cur > 1 ? 1 / z.cur : 1;
	}
}
