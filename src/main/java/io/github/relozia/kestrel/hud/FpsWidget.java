package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

public class FpsWidget extends TextWidget {
	private final Setting.Toggle smooth = add(new Setting.Toggle("Smooth Counter", true));
	private float shown = -1;
	private long last;

	public FpsWidget() {
		super("FPS", '\ue1bf', "Shows your frames per second.", 0.01f, 0.02f, '\ue13b', "FPS:", "Icon");
		startOn();
	}

	// eased toward the real fps instead of snapping, a counter that jumps every second is hard to read mid fight
	@Override
	protected String value() {
		int fps = Minecraft.getInstance().getFps();
		if (!smooth.on()) return Integer.toString(fps);
		long now = Util.getMillis();
		float k = Math.min(1, (now - last) / 1000f * 6);
		last = now;
		shown = shown < 0 ? fps : shown + (fps - shown) * k;
		return Integer.toString(Math.round(shown));
	}

	@Override
	protected String suffix() {
		return "fps";
	}
}
