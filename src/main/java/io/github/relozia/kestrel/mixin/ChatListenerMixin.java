package io.github.relozia.kestrel.mixin;

import com.mojang.authlib.GameProfile;
import io.github.relozia.kestrel.chat.Senders;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatListener.class)
public class ChatListenerMixin {
	// signed chat knows exactly who sent it, noted before the line reaches the chat box
	@Inject(method = "showMessageToPlayer", at = @At("HEAD"))
	private void kestrel$sender(ChatType.Bound type, PlayerChatMessage msg, Component shown, GameProfile sender, boolean onlySecure, java.time.Instant at,
		CallbackInfoReturnable<Boolean> cir) {
		Senders.signed(shown, sender, msg.decoratedContent());
	}
}
