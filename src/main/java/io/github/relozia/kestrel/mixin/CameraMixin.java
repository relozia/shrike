package io.github.relozia.kestrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.relozia.kestrel.camera.CameraTweaks;
import io.github.relozia.kestrel.camera.Freelook;
import io.github.relozia.kestrel.camera.Zoom;
import io.github.relozia.kestrel.visual.Animations;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class CameraMixin {
	@ModifyReturnValue(method = "calculateFov(F)F", at = @At("RETURN"))
	private float kestrel$zoom(float fov) {
		return Zoom.fov(fov);
	}

	// only the first two setRotation calls (minecart + normal), the third one mirrors for front view and
	// already builds on whatever the first ones set
	@WrapOperation(
		method = "alignWithEntity(F)V",
		at = {
			@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0),
			@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1)
		}
	)
	private void kestrel$freelook(Camera cam, float yRot, float xRot, Operation<Void> og) {
		if (Freelook.active()) og.call(cam, Freelook.yaw(), Freelook.pitch());
		else og.call(cam, yRot, xRot);
	}

	@ModifyArgs(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"))
	private void kestrel$aspect(Args args) {
		float w = args.get(3), h = args.get(4);
		args.set(3, CameraTweaks.AspectRatio.width(w, h));
	}

	@ModifyArg(
		method = "createProjectionMatrixForCulling()Lorg/joml/Matrix4f;",
		at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;"),
		index = 1
	)
	private float kestrel$cullAspect(float aspect) {
		return CameraTweaks.AspectRatio.cullAspect(aspect);
	}

	@Shadow private float eyeHeight;
	@Shadow private float eyeHeightOld;
	@Shadow private @Nullable Entity entity;

	// vanilla eases the eye toward the target every tick, 1.7 just set it
	@Inject(method = "tick()V", at = @At("TAIL"))
	private void kestrel$oldSneak(CallbackInfo ci) {
		if (entity instanceof Player p && Animations.sneakApplies(p)) eyeHeight = eyeHeightOld = Animations.sneakEye(p);
	}
}
