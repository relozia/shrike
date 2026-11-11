package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.relozia.kestrel.chat.ChatAccess;
import io.github.relozia.kestrel.chat.ChatBubbles;
import io.github.relozia.kestrel.chat.ChatModules;
import io.github.relozia.kestrel.chat.Senders;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatAccess {
	@Shadow private int chatScrollbarPos;
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;
	@Shadow @Final private List<GuiMessage> allMessages;

	@Shadow protected abstract double getScale();
	@Shadow protected abstract int getWidth();
	@Shadow protected abstract int getLineHeight();
	@Shadow public abstract int getLinesPerPage();
	@Shadow protected abstract void refreshTrimmedMessages();

	// the message on top before the current one came in, and how many times in a row its text showed up
	@Unique private @Nullable GuiMessage kestrel$last;
	@Unique private String kestrel$lastRaw = "";
	@Unique private int kestrel$repeats;
	@Unique private String kestrel$raw = "";
	@Unique private Senders.@Nullable Hit kestrel$hit;
	@Unique private GuiMessageSource kestrel$source = GuiMessageSource.SYSTEM_SERVER;

	// a wall of empty lines from a "clear chat" plugin, past the first three in a second they never get in
	@Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
	private void kestrel$blankSpam(Component c, MessageSignature sig, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		if (ChatModules.Chat.dropBlank(c.getString())) ci.cancel();
	}

	@Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
	private void kestrel$antiClear(boolean history, CallbackInfo ci) {
		if (ChatModules.Chat.antiClear()) ci.cancel();
	}

	// new lines slide up from a few pixels lower
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
	private void kestrel$smoothIn(GuiGraphicsExtractor g, Font font, int ticks, int mx, int my, ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo ci) {
		g.pose().pushMatrix();
		g.pose().translate(0, ChatModules.Chat.smoothOffset());
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("TAIL"))
	private void kestrel$smoothOut(GuiGraphicsExtractor g, Font font, int ticks, int mx, int my, ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo ci) {
		g.pose().popMatrix();
	}

	// ---------- incoming ----------

	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component kestrel$incoming(Component c, @Local(argsOnly = true) GuiMessageSource source) {
		String raw = c.getString();
		Senders.Hit hit = source == GuiMessageSource.SYSTEM_CLIENT ? null : Senders.find(c);
		if (source != GuiMessageSource.SYSTEM_CLIENT) ChatBubbles.said(c, hit);
		c = ChatModules.incoming(c, source, hit);
		// the same text again right away replaces the old line with one "(x2)" line instead of piling up
		if (ChatModules.Chat.stacks() && !raw.isBlank() && raw.equals(kestrel$lastRaw) && kestrel$last != null && !allMessages.isEmpty() && allMessages.getFirst() == kestrel$last) {
			kestrel$repeats++;
			GuiMessage old = kestrel$last;
			allMessages.removeFirst();
			trimmedMessages.removeIf(l -> l.parent() == old);
			c = c.copy().append(Component.literal(" (x" + kestrel$repeats + ")").withStyle(ChatFormatting.GRAY));
		} else {
			kestrel$repeats = 1;
		}
		kestrel$lastRaw = raw;
		kestrel$raw = raw;
		kestrel$hit = hit;
		kestrel$source = source;
		return ChatModules.Chat.stamp(c);
	}

	// the filter can reject it, then it never makes it into the lists and there's nothing to remember
	@Inject(method = "addMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;logChatMessage(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"))
	private void kestrel$added(Component c, MessageSignature sig, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci, @Local GuiMessage message) {
		ChatModules.remember(message, kestrel$raw, kestrel$hit, kestrel$source);
		kestrel$last = message;
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int kestrel$history(int vanilla) {
		return ChatModules.Chat.history(vanilla);
	}

	// ---------- search, heads ----------

	// a search hides lines, it doesn't drop messages. vanilla's own filter would throw away anything that
	// arrives while searching, that's why this isn't done with setVisibleMessageFilter
	@WrapOperation(method = "refreshTrimmedMessages", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
	private boolean kestrel$searchRefresh(Predicate<GuiMessage> p, Object m, Operation<Boolean> og) {
		return og.call(p, m) && ChatModules.Chat.shown((GuiMessage) m);
	}

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
	private void kestrel$searchNew(GuiMessage message, CallbackInfo ci) {
		if (!ChatModules.Chat.shown(message)) ci.cancel();
	}

	// lines of a message with a face get wrapped 10px narrower, the face goes in that gap
	@ModifyArg(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"), index = 1)
	private int kestrel$headWidth(int w, @Local(argsOnly = true) GuiMessage message) {
		return ChatModules.Heads.wants(message) ? w - 10 : w;
	}

	@ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
	private List<FormattedCharSequence> kestrel$headLines(List<FormattedCharSequence> lines, @Local(argsOnly = true) GuiMessage message) {
		ChatModules.Heads.split(message, lines);
		return lines;
	}

	// ---------- ChatAccess ----------

	// same layout math as vanilla's chat drawing: bottom up from 40px over the screen edge, scaled
	@Override
	public @Nullable GuiMessage kestrel$at(double x, double y, int screenH) {
		float s = (float) getScale();
		int bottom = Mth.floor((screenH - 40) / s);
		double lx = x / s - 4, ly = y / s;
		if (lx < -4 || lx > Mth.ceil(getWidth() / s) + 4 || ly > bottom) return null;
		int line = (int) ((bottom - ly) / getLineHeight());
		int i = line + chatScrollbarPos;
		return line < getLinesPerPage() && i < trimmedMessages.size() ? trimmedMessages.get(i).parent() : null;
	}

	@Override
	public void kestrel$refresh() {
		refreshTrimmedMessages();
	}
}
