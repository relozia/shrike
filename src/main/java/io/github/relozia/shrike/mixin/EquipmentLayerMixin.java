package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.relozia.shrike.visual.Animations;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerMixin {
	private static final String LAYERS = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";

	private static boolean hurt(Object state) {
		return Animations.redArmor() && state instanceof LivingEntityRenderState s && s.hasRedOverlay;
	}

	// the armor pipeline is built with NO_OVERLAY so it can't tint red at all. entity_cutout_z_offset is the
	// same pipeline (cutout, no cull, same z layering) minus that define, only swapped in while hurt
	@WrapOperation(method = LAYERS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	private RenderType shrike$type(Identifier tex, Operation<RenderType> og, @Local(argsOnly = true) Object state) {
		return hurt(state) ? RenderTypes.entityCutoutZOffset(tex) : og.call(tex);
	}

	// ordinal 0 is the armor layer itself, the glint and trims stay as they are
	@ModifyExpressionValue(method = LAYERS, at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I", ordinal = 0))
	private int shrike$red(int none, @Local(argsOnly = true) Object state) {
		return hurt(state) ? OverlayTexture.pack(0, OverlayTexture.v(true)) : none;
	}
}
