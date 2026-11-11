package io.github.relozia.kestrel.hud;

import io.github.relozia.kestrel.setting.Setting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ClockWidget extends TextWidget {
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH), H12S = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH);
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm"), H24S = DateTimeFormatter.ofPattern("HH:mm:ss");

	private final Setting.Toggle mil = add(new Setting.Toggle("24 Hour Time", false));
	private final Setting.Toggle secs = add(new Setting.Toggle("Seconds", false));
	private final Setting.Toggle showLabel = add(new Setting.Toggle("Show Label", false));

	public ClockWidget() {
		super("Clock", '\ue087', "Shows the real world time.", 0.99f, 0.97f, '\ue855', "Time:", "Label");
	}

	@Override
	protected String label() {
		return showLabel.on() ? "Time:" : "";
	}

	@Override
	protected String value() {
		return LocalTime.now().format(mil.on() ? secs.on() ? H24S : H24 : secs.on() ? H12S : H12);
	}
}
