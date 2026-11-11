package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.relozia.shrike.chat.ChatModules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// the anonymous per line drawer inside ChatComponent's render. draw, hover and click passes all come through
// here, so shifting the text here shifts its click area too
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class ChatLineMixin {
	@WrapOperation(
		method = "accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z")
	)
	private boolean shrike$head(ChatComponent.ChatGraphicsAccess g, int top, float alpha, FormattedCharSequence text, Operation<Boolean> og,
		@Local(argsOnly = true) GuiMessage.Line line) {
		ChatModules.Heads.Head h = ChatModules.Heads.of(line.parent());
		if (h == null) return og.call(g, top, alpha, text);
		GuiGraphicsExtractor gg = g instanceof ChatDrawAccessor.Background b ? b.shrike$graphics()
			: g instanceof ChatDrawAccessor.Focused f ? f.shrike$graphics() : null;
		if (gg != null && line.content() == h.first()) ChatModules.Heads.draw(gg, h, top, alpha);
		g.updatePose(p -> p.translate(10, 0));
		boolean hovered = og.call(g, top, alpha, text);
		g.updatePose(p -> p.translate(-10, 0));
		return hovered;
	}
}
