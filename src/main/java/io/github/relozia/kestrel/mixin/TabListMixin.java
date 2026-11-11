package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.kestrel.chat.Players;
import io.github.relozia.kestrel.visual.Cosmetics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class TabListMixin {
	@ModifyConstant(method = "getPlayerInfos", constant = @Constant(longValue = 80L))
	private long kestrel$max(long vanilla) {
		return Players.BetterTab.max(vanilla);
	}

	// 13 is the ping bars plus their gap in each slot's width
	@ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = 13))
	private int kestrel$slot(int vanilla) {
		return vanilla + Players.BetterTab.extra();
	}

	// vanilla only draws heads on online mode servers, offline ones get bare names
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"))
	private boolean kestrel$heads(boolean online) {
		return online || Players.BetterTab.heads();
	}

	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void kestrel$ping(GuiGraphicsExtractor g, int slotWidth, int xo, int yo, PlayerInfo info, CallbackInfo ci) {
		if (!Players.BetterTab.numbers()) return;
		Players.BetterTab.ping(g, slotWidth, xo, yo, info.getLatency());
		ci.cancel();
	}

	@ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
	private Component kestrel$friend(Component name, PlayerInfo info) {
		Component n = Players.Friends.is(info.getProfile().name()) ? Players.Friends.paint(info.getProfile().name()) : name;
		if (!Cosmetics.mine(info)) return n;
		// right before your name like on server name tags, "[VIP] ! name". a nickname without your name gets it up front
		// (badgeAtName puts it there when your name isn't in the text)
		return Cosmetics.badgeAtName(n);
	}
}
