package io.github.relozia.shrike.hud;

import io.github.relozia.shrike.gui.Shape;
import io.github.relozia.shrike.setting.Setting;
import io.github.relozia.shrike.track.Clicks;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

// WASD + mouse + jump bar: dark rounded keys that fill with the accent while held
// labels come from your actual binds, so arrow key players see arrows
// issues:
//   - sprint in toggle mode shows the toggle state, not whether the key is physically held
public class KeystrokesWidget extends HudWidget {
	private static final float K = 26, GAP = 3, R = 6, W = K * 3 + GAP * 2, JUMP = 14.3f;
	private static final int IDLE = 0x9E0B0F15, LINE = 0x2EDCE6F5, TEXT = 0xEBF7FAFC, TEXT_ON = 0xFF101014;

	private final Setting.Slider size = add(new Setting.Slider("Size", 1, 0.6, 2, 0.05, "x"));
	private final Setting.Toggle mouse = add(new Setting.Toggle("Mouse Buttons", true));
	private final Setting.Toggle cps = add(new Setting.Toggle("Show CPS", true));
	private final Setting.Toggle jump = add(new Setting.Toggle("Jump Bar", true));
	private final Setting.Toggle extra = add(new Setting.Toggle("Sneak + Sprint", false));
	private final Setting.Slider fade = add(new Setting.Slider("Fade Speed", 14, 4, 30, 1, ""));
	private final Setting.Color pressed = add(new Setting.Color("Pressed Color", RUST, RUST, 0xFFFFFFFF, 0xFFF5A524, 0xFF5B8DEF, 0xFF2EC4B6, 0xFF8B5CF6));

	// how lit each key is, eased so a tap fades out instead of blinking
	private final Map<String, Float> glow = new HashMap<>();
	private long last = Util.getMillis();

	public KeystrokesWidget() {
		super("keystrokes", "Keystrokes", '\ue284', "Shows your movement keys and clicks as you press them.", 0.01f, 0.62f);
	}

	@Override
	protected Look look() {
		return Look.NONE;
	}

	private float s() {
		return size.get().floatValue();
	}

	private float rawH() {
		float h = K * 2 + GAP;
		if (mouse.on()) h += GAP + K;
		if (jump.on()) h += GAP + JUMP;
		if (extra.on()) h += GAP + 18;
		return h;
	}

	@Override
	protected int contentW() {
		return Math.round(W * s());
	}

	@Override
	protected int contentH() {
		return Math.round(rawH() * s());
	}

	@Override
	protected void content(GuiGraphicsExtractor g) {
		Options o = Minecraft.getInstance().options;
		long now = Util.getMillis();
		float step = Math.min(0.1f, (now - last) / 1000f) * fade.get().floatValue();
		last = now;
		g.pose().pushMatrix();
		g.pose().scale(s(), s());
		float y = 0;
		key(g, "w", o.keyUp, label(o.keyUp), null, K + GAP, y, K, K, step);
		y += K + GAP;
		key(g, "a", o.keyLeft, label(o.keyLeft), null, 0, y, K, K, step);
		key(g, "s", o.keyDown, label(o.keyDown), null, K + GAP, y, K, K, step);
		key(g, "d", o.keyRight, label(o.keyRight), null, (K + GAP) * 2, y, K, K, step);
		y += K + GAP;
		float half = (W - GAP) / 2;
		if (mouse.on()) {
			key(g, "lmb", o.keyAttack, "LMB", cps.on() ? Clicks.left() + " CPS" : null, 0, y, half, K, step);
			key(g, "rmb", o.keyUse, "RMB", cps.on() ? Clicks.right() + " CPS" : null, half + GAP, y, half, K, step);
			y += K + GAP;
		}
		if (jump.on()) {
			float t = key(g, "space", o.keyJump, null, null, 0, y, W, JUMP, step);
			// a bar instead of the word, "Space" in that box looks cramped
			Shape.rect(g, W * 0.3f, y + JUMP / 2 - 1, W * 0.4f, 2, 1, ARGB.srgbLerp(t, TEXT, TEXT_ON));
			y += JUMP + GAP;
		}
		if (extra.on()) {
			key(g, "sneak", o.keyShift, "Sneak", null, 0, y, half, 18, step);
			key(g, "sprint", o.keySprint, "Sprint", null, half + GAP, y, half, 18, step);
		}
		g.pose().popMatrix();
	}

	private static String label(KeyMapping k) {
		return Setting.Bind.label(KeyMappingHelper.getBoundKeyOf(k));
	}

	// rises twice as fast as it falls, so a tap still lights the key up properly
	private float key(GuiGraphicsExtractor g, String id, KeyMapping k, String label, String sub, float x, float y, float w, float h, float step) {
		float v = glow.getOrDefault(id, 0f);
		v = k.isDown() ? Math.min(1, v + step * 2) : Math.max(0, v - step);
		glow.put(id, v);
		float t = v * v * (3 - 2 * v);
		Shape.rect(g, x, y, w, h, R, ARGB.srgbLerp(t, IDLE, pressed.get()));
		Shape.outline(g, x, y, w, h, R, LINE, 1);
		if (label == null) return t;
		int tc = ARGB.srgbLerp(t, TEXT, TEXT_ON);
		float cx = x + w / 2, cy = y + h / 2;
		if (sub == null) {
			// long names (LShift on a rebound W) get smaller instead of spilling out of the key
			float sz = Math.min(12, 12 * (w - 4) / width(label, HUD, 12));
			draw(g, label, HUD, cx - width(label, HUD, sz) / 2, cy + sz * 0.375f, tc, sz);
		} else {
			draw(g, label, HUD, cx - width(label, HUD, 11) / 2, cy + 0.5f, tc, 11);
			draw(g, sub, LIGHT, cx - width(sub, LIGHT, 8.5f) / 2, cy + 10, ARGB.srgbLerp(t, 0x99F7FAFC, TEXT_ON), 8.5f);
		}
		return t;
	}
}
