package io.github.relozia.kestrel.mixin;

import io.github.relozia.kestrel.gui.Ui;
import io.github.relozia.kestrel.qol.Qol;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// last played and the start of your note, small and right aligned under the player count
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class ServerEntryMixin extends ServerSelectionList.Entry {
	@Shadow @Final private ServerData serverData;

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void kestrel$notes(GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a, CallbackInfo ci) {
		if (!Qol.ServerNotes.active()) return;
		long last = Qol.ServerNotes.showLast() ? Qol.lastPlayed(serverData.ip) : 0;
		String note = Qol.ServerNotes.showNotes() ? Qol.note(serverData.ip).lines().findFirst().orElse("") : "";
		if (note.length() > 28) note = note.substring(0, 27) + "...";
		String s = (last > 0 ? "played " + Qol.ServerNotes.ago(last) : "") + (last > 0 && !note.isEmpty() ? "  |  " : "") + note;
		if (s.isEmpty()) return;
		Component c = Ui.small(s);
		Ui.text(g, c, getContentRight() - Ui.width(c) - 2, getContentBottom() - 8, 0xFF9AA0AA);
	}
}
