package io.github.relozia.kestrel.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.jspecify.annotations.Nullable;

// bits of ChatComponent the chat screen needs, ChatComponentMixin fills them in
public interface ChatAccess {
	// the message whose line is under this gui position, null if none
	@Nullable GuiMessage kestrel$at(double x, double y, int screenH);

	// rebuild the shown lines, after the search changed
	void kestrel$refresh();
}
