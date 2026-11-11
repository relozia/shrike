package io.github.relozia.kestrel.module;

// icon chars are lucide codepoints, see font/lucide_icons.ttf (subset, only what the menu uses)
public enum Category {
	HUD("HUD", '\ue11d'),
	VISUAL("Visual", '\ue412'),
	COMBAT("Combat", '\ue2b4'),
	CHAT("Chat", '\ue117'),
	UTILITY("Utility", '\ue1b1');

	public final String label;
	public final char icon;

	Category(String label, char icon) {
		this.label = label;
		this.icon = icon;
	}
}
