package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.relozia.shrike.nav.Nav;
import io.github.relozia.shrike.pvp.Coaches;
import io.github.relozia.shrike.pvp.KillEffects;
import io.github.relozia.shrike.pvp.PvpTools;
import io.github.relozia.shrike.qol.Qol;
import io.github.relozia.shrike.track.ServerTps;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	// TAIL on purpose: the handler first runs on the netty thread, bails out with an exception and
	// gets re-run on the main thread. HEAD would count every packet twice, TAIL is only reached once
	@Inject(method = "handleSetTime(Lnet/minecraft/network/protocol/game/ClientboundSetTimePacket;)V", at = @At("TAIL"))
	private void shrike$time(ClientboundSetTimePacket packet, CallbackInfo ci) {
		ServerTps.onTime(packet.gameTime());
	}

	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void shrike$totem(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Entity e = packet.getEntity(((ClientPacketListener) (Object) this).getLevel());
		if (packet.getEventId() == 35 && e instanceof Player p) PvpTools.Totems.popped(p);
		KillEffects.entityEvent(e, packet.getEventId());
	}

	// sent to whoever shot the arrow when it hits a player
	@Inject(method = "handleGameEvent", at = @At("TAIL"))
	private void shrike$arrowHit(ClientboundGameEventPacket packet, CallbackInfo ci) {
		if (packet.getEvent() == ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND) PvpTools.PvpInfo.arrowHit();
	}

	// the only two emitters in here are the crit and the magic crit
	@WrapOperation(method = "handleAnimate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V"))
	private void shrike$crits(ParticleEngine pe, Entity e, ParticleOptions type, Operation<Void> og) {
		if (!WorldVisuals.Particles.hitEffect(e, type)) og.call(pe, e, type);
	}

	// after the thread hop, before vanilla removes the item entity from the world
	@Inject(method = "handleTakeItemEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void shrike$pickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null || packet.getPlayerId() != mc.player.getId()) return;
		if (((ClientPacketListener) (Object) this).getLevel().getEntity(packet.getItemId()) instanceof ItemEntity item) Qol.PickupTracker.picked(item.getItem(), packet.getAmount());
	}

	@Inject(method = "handleSoundEvent", at = @At("TAIL"))
	private void shrike$trapSound(ClientboundSoundPacket packet, CallbackInfo ci) {
		Nav.TrapTimer.sound(packet);
		KillEffects.positionalSound(packet.getSound().value().location(), packet.getX(), packet.getY(), packet.getZ());
		Coaches.SwapHelper.sound(packet.getSound().value().location());
	}

	@Inject(method = "handleSoundEntityEvent", at = @At("TAIL"))
	private void shrike$entitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
		KillEffects.entitySound(packet.getSound().value().location(), packet.getId());
	}
}
