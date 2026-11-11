package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.relozia.shrike.chat.ChatModules;
import io.github.relozia.shrike.hud.VanillaWidgets;
import io.github.relozia.shrike.visual.Animations;
import io.github.relozia.shrike.visual.Crosshair;
import io.github.relozia.shrike.visual.ScreenTweaks;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow private @Nullable Component title;
	@Shadow private @Nullable Component subtitle;
	@Shadow private @Nullable Component overlayMessageString;

	@Shadow
	public abstract Font getFont();

	// ---------- crosshair, hotbar ----------

	// ordinal 0 is the plain crosshair sprite, 1 and 2 are the attack indicator which stays vanilla
	@WrapWithCondition(
		method = "extractCrosshair",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0)
	)
	private boolean shrike$crosshair(GuiGraphicsExtractor g, RenderPipeline p, Identifier sprite, int x, int y, int w, int h) {
		return !Crosshair.draw(g);
	}

	// ordinal 0 is the hotbar itself, 1 is the white selection box
	@ModifyArg(
		method = "extractItemHotbar",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1),
		index = 2
	)
	private int shrike$selection(int x) {
		return ScreenTweaks.Hotbar.selX(x);
	}

	// ---------- scoreboard ----------

	@Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void shrike$sidebarF3(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
		if (VanillaWidgets.Scoreboard.INSTANCE.hidden() || WorldVisuals.NoRender.hides("Scoreboard")) ci.cancel();
	}

	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void shrike$noCrosshair(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
		if (WorldVisuals.NoRender.hides("Crosshair")) ci.cancel();
	}

	// first fill is the header, by then vanilla has worked out the whole box
	@Inject(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
	private void shrike$sidebarPush(GuiGraphicsExtractor g, Objective o, CallbackInfo ci,
		@Local(name = "left") int left, @Local(name = "right") int right, @Local(name = "headerY") int headerY, @Local(name = "bottom") int bottom) {
		VanillaWidgets.Scoreboard.INSTANCE.push(g, left - 2, headerY - 10, right, bottom);
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("TAIL"))
	private void shrike$sidebarPop(GuiGraphicsExtractor g, Objective o, CallbackInfo ci) {
		VanillaWidgets.Scoreboard.INSTANCE.pop(g);
	}

	@ModifyExpressionValue(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I", ordinal = 0))
	private int shrike$sidebarBg(int c) {
		return VanillaWidgets.Scoreboard.INSTANCE.bg(c, false);
	}

	@ModifyExpressionValue(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I", ordinal = 1))
	private int shrike$sidebarHeaderBg(int c) {
		return VanillaWidgets.Scoreboard.INSTANCE.bg(c, true);
	}

	// emptied where each entry is built, so vanilla's width math drops the score column too
	// instead of leaving a gap on the right
	@ModifyExpressionValue(
		method = "lambda$displayScoreboardSidebar$1",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerScoreEntry;formatValue(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/MutableComponent;")
	)
	private MutableComponent shrike$hideScore(MutableComponent c) {
		return VanillaWidgets.Scoreboard.INSTANCE.hideNumbers() ? Component.empty() : c;
	}

	// ---------- title, action bar ----------

	// title is drawn at 4x around the screen center, subtitle at 2x under it. box includes the 2px backdrop
	@Inject(method = "extractTitle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;nextStratum()V", shift = At.Shift.AFTER))
	private void shrike$titlePush(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
		int half = getFont().width(title) * 2 + 8, bottom = 4;
		if (subtitle != null) {
			half = Math.max(half, getFont().width(subtitle) + 4);
			bottom = 32;
		}
		VanillaWidgets.Title.INSTANCE.push(g, cx - half, cy - 48, cx + half, cy + bottom);
	}

	@Inject(method = "extractTitle", at = @At("TAIL"))
	private void shrike$titlePop(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
		VanillaWidgets.Title.INSTANCE.pop(g);
	}

	@Inject(method = "extractOverlayMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;nextStratum()V", shift = At.Shift.AFTER))
	private void shrike$actionPush(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
		int cx = g.guiWidth() / 2, cy = g.guiHeight() - 68, w = getFont().width(overlayMessageString);
		VanillaWidgets.ActionBar.INSTANCE.push(g, cx - w / 2 - 2, cy - 6, cx - w / 2 + w + 2, cy + 7);
	}

	@Inject(method = "extractOverlayMessage", at = @At("TAIL"))
	private void shrike$actionPop(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
		VanillaWidgets.ActionBar.INSTANCE.pop(g);
	}

	// ---------- hearts ----------

	// the second write is the "just healed" blink, the first one (damage) stays
	@WrapWithCondition(method = "extractPlayerHealth", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;healthBlinkTime:J", opcode = Opcodes.PUTFIELD, ordinal = 1))
	private boolean shrike$healFlash(Hud h, long t) {
		return !Animations.noHealFlash();
	}

	// ---------- chat ----------

	@WrapWithCondition(method = "onDisconnected", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V"))
	private boolean shrike$keepChat(ChatComponent chat, boolean history) {
		return !ChatModules.Chat.keeps();
	}

	// ---------- no render ----------

	// ordinal 0 is the worn item's overlay (carved pumpkin), 1 is powder snow
	@WrapWithCondition(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0))
	private boolean shrike$pumpkin(Hud h, GuiGraphicsExtractor g, Identifier tex, float a) {
		return !WorldVisuals.NoRender.hides("Pumpkin Overlay");
	}

	@WrapWithCondition(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 1))
	private boolean shrike$powderSnow(Hud h, GuiGraphicsExtractor g, Identifier tex, float a) {
		return !WorldVisuals.NoRender.hides("Powder Snow Overlay");
	}

	@Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
	private void shrike$vignette(GuiGraphicsExtractor g, @Nullable Entity camera, CallbackInfo ci) {
		if (WorldVisuals.NoRender.hides("Vignette")) ci.cancel();
	}

	@Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void shrike$portal(GuiGraphicsExtractor g, float a, CallbackInfo ci) {
		if (WorldVisuals.NoRender.hides("Portal Overlay")) ci.cancel();
	}

	@Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
	private void shrike$nausea(GuiGraphicsExtractor g, float strength, CallbackInfo ci) {
		if (WorldVisuals.NoRender.hides("Nausea Overlay")) ci.cancel();
	}
}
