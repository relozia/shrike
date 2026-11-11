package io.github.relozia.kestrel.chat;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

// the chat tabs and Search, mouse only. the chat screen lets arrow key focus navigation look at a key before chat
// history does, so a normal button above the input took the up arrow: focus jumped to "All" and the next enter pressed it
public class ChatButton extends Button.Plain {
	public ChatButton(int x, int y, int w, int h, Component label, OnPress press) {
		super(x, y, w, h, label, press, DEFAULT_NARRATION);
	}

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent e) {
		return null;
	}
}
