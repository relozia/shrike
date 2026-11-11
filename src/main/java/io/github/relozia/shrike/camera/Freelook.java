package io.github.relozia.shrike.camera;

import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

// hold left alt to look around in third person without turning your character
// issues:
//   - while it's active the mouse only moves the camera, you can't aim. that's the point but worth knowing
public class Freelook extends Module {
	static Freelook INSTANCE;

	private final Setting.Bind key = add(new Setting.Bind("Freelook Key", 342));
	private final Setting.Toggle toggle = add(new Setting.Toggle("Toggle Mode", false));
	private final Setting.Toggle nametag = add(new Setting.Toggle("Show Nametag", false));
	private final Setting.Choice view = add(new Setting.Choice("Start Perspective", "Back", "Back", "Front"));

	private boolean active, warned;
	private float yaw, pitch;
	private CameraType before;

	public Freelook() {
		super("freelook", "Freelook", Category.UTILITY, '\ue0ba', "Hold Left Alt to look around without turning.");
		INSTANCE = this;
	}

	@Override
	public void tick(Minecraft mc) {
		if (!warned && mc.player != null) {
			warned = true;
			mc.player.sendSystemMessage(Component.literal("[Shrike] Freelook isn't allowed on some servers, check the rules before using it."));
		}
		boolean want = mc.player != null && (toggle.on() ? active ^ key.pressed(mc) : key.down(mc));
		if (want && !active) start(mc);
		else if (!want && active) stop(mc);
	}

	private void start(Minecraft mc) {
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		before = mc.options.getCameraType();
		mc.options.setCameraType(view.get().equals("Front") ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
		active = true;
	}

	private void stop(Minecraft mc) {
		if (before != null) mc.options.setCameraType(before);
		active = false;
	}

	@Override
	protected void disable() {
		if (active) stop(Minecraft.getInstance());
	}

	// mouse movement goes to the camera instead of the player while active. 0.15 is the same factor Entity.turn uses
	public static boolean turn(double dx, double dy) {
		Freelook f = INSTANCE;
		if (f == null || !f.active) return false;
		f.yaw += (float) dx * 0.15f;
		f.pitch = Mth.clamp(f.pitch + (float) dy * 0.15f, -90, 90);
		return true;
	}

	public static boolean active() {
		return INSTANCE != null && INSTANCE.active;
	}

	public static float yaw() {
		return INSTANCE.yaw;
	}

	public static float pitch() {
		return INSTANCE.pitch;
	}

	public static boolean showOwnName(Entity e) {
		Freelook f = INSTANCE;
		return f != null && f.active && f.nametag.on() && e == Minecraft.getInstance().player;
	}
}
