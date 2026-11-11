package io.github.relozia.shrike.camera;

import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import net.minecraft.client.player.AbstractClientPlayer;

// the small camera modules, each one is a couple of settings and one number fed into a mixin
public final class CameraTweaks {
	private CameraTweaks() {}

	// vanilla's fov change comes from the movement speed attribute (sprint is +30%, speed/slowness change it too),
	// so this rebuilds that number with each part scaled on its own and lets vanilla do the rest of the math
	public static class Fov extends Module {
		static Fov INSTANCE;

		private final Setting.Choice mode = add(new Setting.Choice("Mode", "Custom", "Custom", "Locked"));
		private final Setting.Slider sprint = add(new Setting.Slider("Sprint Effect", 100, 0, 200, 5, "%"));
		private final Setting.Slider speed = add(new Setting.Slider("Speed Effect", 100, 0, 200, 5, "%"));
		private final Setting.Slider slow = add(new Setting.Slider("Slowness Effect", 100, 0, 200, 5, "%"));

		public Fov() {
			super("fov_changer", "FOV Changer", Category.VISUAL, '\ue29e', "Control how much sprinting and effects change your FOV, or lock it.");
			INSTANCE = this;
		}

		public static double speedAttr(AbstractClientPlayer p, double vanilla) {
			Fov f = INSTANCE;
			if (f == null || !f.on() || f.mode.get().equals("Locked")) return vanilla;
			double walk = p.getAbilities().getWalkingSpeed();
			if (walk == 0) return vanilla;
			double sprintMul = p.isSprinting() ? 1.3 : 1;
			double effect = vanilla / walk / sprintMul;
			double s = 1 + (sprintMul - 1) * f.sprint.get() / 100;
			double e = 1 + (effect - 1) * (effect >= 1 ? f.speed.get() : f.slow.get()) / 100;
			return walk * s * e;
		}

		// locked keeps the spyglass zoom, that one isn't an "effect"
		public static float lock(AbstractClientPlayer p, float vanilla) {
			Fov f = INSTANCE;
			return f != null && f.on() && f.mode.get().equals("Locked") && !p.isScoping() ? 1 : vanilla;
		}
	}

	public static class Bobbing extends Module {
		static Bobbing INSTANCE;

		private final Setting.Choice keep = add(new Setting.Choice("Keep", "Hand Bob", "Hand Bob", "Camera Bob", "Neither"));

		public Bobbing() {
			super("minimal_bobbing", "Minimal Bobbing", Category.VISUAL, '\ue283', "Keeps the hand bob but stops the screen from bobbing.");
			INSTANCE = this;
		}

		public static boolean camera() {
			return INSTANCE == null || !INSTANCE.on() || INSTANCE.keep.get().equals("Camera Bob");
		}

		public static boolean hand() {
			return INSTANCE == null || !INSTANCE.on() || INSTANCE.keep.get().equals("Hand Bob");
		}
	}

	public static class HurtCamera extends Module {
		static HurtCamera INSTANCE;

		private final Setting.Slider shake = add(new Setting.Slider("Shake", 30, 0, 100, 5, "%"));

		public HurtCamera() {
			super("hurt_camera", "Hurt Camera", Category.VISUAL, '\ue2d6', "How much the screen shakes when you get hit, 0% turns it off.");
			INSTANCE = this;
		}

		// replaces vanilla's damage tilt option while on (that one is the same 0-100% scale)
		public static double tilt(double vanilla) {
			return INSTANCE != null && INSTANCE.on() ? INSTANCE.shake.get() / 100 : vanilla;
		}
	}

	public static class AspectRatio extends Module {
		static AspectRatio INSTANCE;

		private final Setting.Choice ratio = add(new Setting.Choice("Ratio", "4:3", "4:3", "5:4", "16:10", "1:1", "Custom"));
		private final Setting.Slider custom = add(new Setting.Slider("Custom Ratio", 1.33, 0.5, 3, 0.01, ""));

		public AspectRatio() {
			super("aspect_ratio", "Aspect Ratio", Category.VISUAL, '\ue4e8', "Stretched resolution for the world, the HUD stays normal.");
			INSTANCE = this;
		}

		private float value() {
			return switch (ratio.get()) {
				case "5:4" -> 1.25f;
				case "16:10" -> 1.6f;
				case "1:1" -> 1f;
				case "Custom" -> custom.get().floatValue();
				default -> 4 / 3f;
			};
		}

		public static float width(float w, float h) {
			return INSTANCE != null && INSTANCE.on() ? h * INSTANCE.value() : w;
		}

		// culling has to cover at least what's on screen, a wider custom ratio would otherwise cut chunks at the edges
		public static float cullAspect(float vanilla) {
			return INSTANCE != null && INSTANCE.on() ? Math.max(vanilla, INSTANCE.value()) : vanilla;
		}
	}
}
