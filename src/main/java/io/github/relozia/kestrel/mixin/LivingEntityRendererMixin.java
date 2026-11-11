package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.relozia.kestrel.camera.Freelook;
import io.github.relozia.kestrel.chat.Players;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	// vanilla never shows your own name (you're the camera entity). freelook's "Show Nametag" and NameTags put it back
	@ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"))
	private boolean kestrel$ownName(boolean show, LivingEntity entity, double dist) {
		return show || (Freelook.showOwnName(entity) || Players.NameTags.showOwn(entity)) && !Players.NameTags.serverShows(entity);
	}
}
