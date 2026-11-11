package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.shrike.camera.CameraTweaks;
import io.github.relozia.shrike.visual.ScreenTweaks;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	private static final String BOB = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V";

	@ModifyExpressionValue(method = "extractOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I"))
	private int shrike$blur(int vanilla) {
		return ScreenTweaks.blurRadius(vanilla);
	}

	// the same bobView runs twice a frame: once for the world (camera bob), once for the hand
	@WrapOperation(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE", target = BOB))
	private void shrike$cameraBob(GameRenderer r, CameraRenderState cam, PoseStack ps, Operation<Void> og) {
		if (CameraTweaks.Bobbing.camera()) og.call(r, cam, ps);
	}

	@WrapOperation(
		method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
		at = @At(value = "INVOKE", target = BOB)
	)
	private void shrike$handBob(GameRenderer r, CameraRenderState cam, PoseStack ps, Operation<Void> og) {
		if (CameraTweaks.Bobbing.hand()) og.call(r, cam, ps);
	}

	@ModifyExpressionValue(
		method = "bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D")
	)
	private double shrike$hurtTilt(double vanilla) {
		return CameraTweaks.HurtCamera.tilt(vanilla);
	}

	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private RenderTarget mainRenderTarget;
	@Shadow @Final private CrossFrameResourcePool resourcePool;

	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void shrike$noTotem(ItemStack stack, CallbackInfo ci) {
		if (WorldVisuals.NoRender.hides("Totem Animation")) ci.cancel();
	}

	// right after the world is drawn and before the hud, same spot vanilla runs the creeper/spider view effects
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void shrike$motionBlur(DeltaTracker dt, boolean advance, CallbackInfo ci) {
		Identifier id = WorldVisuals.MotionBlur.effect();
		if (id == null) return;
		PostChain chain = minecraft.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
		if (chain != null) chain.process(mainRenderTarget, resourcePool);
	}
}
