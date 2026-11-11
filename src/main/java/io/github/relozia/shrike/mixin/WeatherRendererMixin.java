package io.github.relozia.shrike.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.relozia.shrike.visual.WorldVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// forced rain from Sky & Ambience: falls in biomes that never rain, and under barrier roofs
@Mixin(WeatherEffectRenderer.class)
public class WeatherRendererMixin {
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
	private Biome.Precipitation shrike$anywhere(Biome.Precipitation p) {
		return p == Biome.Precipitation.NONE && WorldVisuals.Sky.forcedRain() ? Biome.Precipitation.RAIN : p;
	}

	// the heightmap stops at the barrier, so the rain does too. look under it for the real ground, 96 down is
	// plenty for any lobby roof
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
	private int shrike$underBarriers(ClientLevel level, Heightmap.Types type, int x, int z, Operation<Integer> op) {
		int h = op.call(level, type, x, z);
		if (!WorldVisuals.Sky.throughBarriers()) return h;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, h - 1, z);
		if (!level.getBlockState(p).is(Blocks.BARRIER)) return h;
		for (int i = 0; i < 96 && p.getY() > level.getMinY(); i++) {
			p.move(0, -1, 0);
			BlockState s = level.getBlockState(p);
			if (!s.is(Blocks.BARRIER) && type.isOpaque().test(s)) return p.getY() + 1;
		}
		return p.getY();
	}
}
