package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.shrike.chat.Players;
import io.github.relozia.shrike.visual.Cosmetics;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
	private Component shrike$friend(Component name, Entity e) {
		// a server's name tag for you made of armor stands: the stand whose name has yours gets the badge, in front of your name
		if (name != null && e instanceof ArmorStand && Players.NameTags.serverTag(e) && Cosmetics.hasName(Cosmetics.shown(name))) return Cosmetics.badgeAtName(name);
		if (name == null || !(e instanceof Player p)) return name;
		Component n = Players.Friends.is(p.getGameProfile().name()) ? Players.Friends.paint(p.getGameProfile().name()) : name;
		return p == Minecraft.getInstance().player ? Cosmetics.badge(n) : n;
	}

	// the helper every renderer ends up in, players included, so one cancel covers all of them
	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At("HEAD"), cancellable = true)
	private void shrike$noNames(EntityRenderState s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam, int offset, CallbackInfo ci) {
		if (WorldVisuals.NoRender.hides("Name Tags")) ci.cancel();
	}
}
