package io.github.relozia.kestrel.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// the two chat drawers that actually draw (the third one only collects clickable text)
public final class ChatDrawAccessor {
	private ChatDrawAccessor() {}

	@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
	public interface Background {
		@Accessor("graphics")
		GuiGraphicsExtractor kestrel$graphics();
	}

	@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
	public interface Focused {
		@Accessor("graphics")
		GuiGraphicsExtractor kestrel$graphics();
	}
}
