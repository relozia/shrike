package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
	private void shrike$scale(ItemEntity e, ItemEntityRenderState s, float pt, CallbackInfo ci) {
		s.setData(WorldVisuals.DroppedItems.SCALE, WorldVisuals.DroppedItems.active() ? WorldVisuals.DroppedItems.scale(e.getItem()) : null);
		s.setData(WorldVisuals.DroppedItems.GROUND, e.onGround());
	}

	// facing you never bobs. flat only stops once it's on the ground, in the air it tumbles like vanilla. a sprite
	// laid down sits right on the ground, everything else on its model bottom
	@ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), index = 1)
	private float shrike$noBob(float y, @Local(argsOnly = true) ItemEntityRenderState s, @Local(name = "minOffsetY") float minY, @Local(name = "boundingBox") AABB box) {
		if (!WorldVisuals.DroppedItems.active()) return y;
		return switch (WorldVisuals.DroppedItems.style()) {
			case "Facing You" -> minY;
			case "Flat" -> {
				if (!ground(s)) yield y;
				if (WorldVisuals.DroppedItems.layFlat() && sprite(box)) yield WorldVisuals.DroppedItems.noBounce() ? 0.02f : y - minY + 0.02f;
				yield WorldVisuals.DroppedItems.noBounce() ? minY : y;
			}
			default -> y;
		};
	}

	@Unique
	private static boolean ground(ItemEntityRenderState s) {
		return Boolean.TRUE.equals(s.getData(WorldVisuals.DroppedItems.GROUND));
	}

	// only flat sprites get laid down, a block or a 3d model on its side just looks knocked over
	@Unique
	private static boolean sprite(AABB box) {
		return box.getZsize() <= 0.0625;
	}

	// the spin. flat: a fixed turn per item (its bob offset is random per item), sprites laid on their back.
	// facing you: turned to the camera like a nametag
	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 0))
	private void shrike$spin(PoseStack ps, Quaternionfc q, Operation<Void> og, @Local(argsOnly = true) ItemEntityRenderState s,
		@Local(argsOnly = true) CameraRenderState cam, @Local(name = "boundingBox") AABB box) {
		String style = WorldVisuals.DroppedItems.active() ? WorldVisuals.DroppedItems.style() : "Vanilla";
		switch (style) {
			case "Flat" -> {
				// a fixed turn per item instead of no turn, the bob offset is random per item so a pile doesn't line up
				if (ground(s) && WorldVisuals.DroppedItems.noSpin()) ps.mulPose(Axis.YP.rotation(s.bobOffset));
				else og.call(ps, q);
				if (ground(s) && WorldVisuals.DroppedItems.layFlat() && sprite(box)) ps.mulPose(Axis.XP.rotationDegrees(90));
			}
			case "Facing You" -> ps.mulPose(cam.orientation);
			default -> og.call(ps, q);
		}
		Float k = s.getData(WorldVisuals.DroppedItems.SCALE);
		if (k != null && k != 1) ps.scale(k, k, k);
	}
}
