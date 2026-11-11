package io.github.relozia.shrike.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.shrike.module.Category;
import io.github.relozia.shrike.module.Module;
import io.github.relozia.shrike.setting.Setting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

// fullbright, block outline and the fire overlay. all three are "change one number vanilla already draws with"
public final class WorldTweaks {
	private WorldTweaks() {}

	public static class Fullbright extends Module {
		static Fullbright INSTANCE;

		private final Setting.Slider strength = add(new Setting.Slider("Strength", 100, 10, 100, 5, "%"));

		public Fullbright() {
			super("fullbright", "Fullbright", Category.VISUAL, '\ue178', "See in the dark without night vision.");
			INSTANCE = this;
		}

		// vanilla's brightness slider tops out at 1.0, the lightmap is happy with way more. 15 is "everything lit"
		public static float gamma(float vanilla) {
			return INSTANCE != null && INSTANCE.on() ? 1 + 14 * INSTANCE.strength.get().floatValue() / 100f : vanilla;
		}
	}

	public static class BlockOverlay extends Module {
		static BlockOverlay INSTANCE;

		private final Setting.Color color = add(new Setting.Color("Outline Color", 0xFF000000,
			0xFF000000, 0xFFFFFFFF, 0xFFE0703A, 0xFF5B8DEF, 0xFF2EC4B6, 0xFFE5484D));
		private final Setting.Slider alpha = add(new Setting.Slider("Outline Opacity", 40, 10, 100, 5, "%"));
		private final Setting.Slider width = add(new Setting.Slider("Thickness", 2, 1, 8, 0.5, "px"));
		private final Setting.Toggle fill = add(new Setting.Toggle("Fill", false));
		private final Setting.Color fillColor = add(new Setting.Color("Fill Color", 0xFFFFFFFF,
			0xFFFFFFFF, 0xFF000000, 0xFFE0703A, 0xFF5B8DEF, 0xFF2EC4B6, 0xFFE5484D));
		private final Setting.Slider fillAlpha = add(new Setting.Slider("Fill Opacity", 20, 5, 80, 5, "%"));

		public BlockOverlay() {
			super("block_overlay", "Block Overlay", Category.VISUAL, '\ue061', "Change the outline of the block you're looking at, or fill it.");
			INSTANCE = this;
		}

		public static boolean active() {
			return INSTANCE != null && INSTANCE.on();
		}

		public static int color() {
			return ARGB.color(Math.round(INSTANCE.alpha.get().floatValue() / 100 * 255), INSTANCE.color.get() & 0xFFFFFF);
		}

		public static float width() {
			return INSTANCE.width.get().floatValue();
		}

		// pose is already moved to the block, shape coords are 0..1 inside it. pushed out a hair so it doesn't z-fight the block
		public static void fill(PoseStack ps, SubmitNodeCollector out, VoxelShape shape) {
			if (!INSTANCE.fill.on()) return;
			int c = ARGB.color(Math.round(INSTANCE.fillAlpha.get().floatValue() / 100 * 255), INSTANCE.fillColor.get() & 0xFFFFFF);
			out.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> {
				for (AABB b : shape.toAabbs()) WorldDraw.fill(pose.pose(), vc, b.inflate(0.002), c);
			});
		}
	}

	public static class FireHeight extends Module {
		static FireHeight INSTANCE;

		private final Setting.Slider height = add(new Setting.Slider("Height", 60, 0, 100, 5, "%"));
		private final Setting.Slider opacity = add(new Setting.Slider("Opacity", 90, 10, 100, 5, "%"));

		public FireHeight() {
			super("fire_height", "Fire Height", Category.VISUAL, '\ue0d2', "Makes the fire on your screen lower and see through.");
			INSTANCE = this;
		}

		public static boolean active() {
			return INSTANCE != null && INSTANCE.on();
		}

		// same two tilted quads vanilla draws, the top just gets cut down. the texture is cropped with it so
		// the flames get shorter instead of squashed
		public static void submit(PoseStack ps, SubmitNodeCollector out, TextureAtlasSprite sprite) {
			float h = INSTANCE.height.get().floatValue() / 100f;
			if (h <= 0) return;
			int c = ARGB.white(INSTANCE.opacity.get().floatValue() / 100f);
			out.submitCustomGeometry(ps, RenderTypes.fireScreenEffect(sprite.atlasLocation()), (base, vc) -> {
				for (int side = -1; side <= 1; side += 2) {
					Matrix4f m = new Matrix4f(base.pose());
					m.translate(-side * 0.24f, -0.3f, 0);
					m.rotateY(side * (float) (Math.PI / 18));
					float top = -0.5f + h;
					float vTop = sprite.getV1() + (sprite.getV0() - sprite.getV1()) * h;
					vc.addVertex(m, -0.5f, -0.5f, -0.5f).setUv(sprite.getU1(), sprite.getV1()).setColor(c);
					vc.addVertex(m, 0.5f, -0.5f, -0.5f).setUv(sprite.getU0(), sprite.getV1()).setColor(c);
					vc.addVertex(m, 0.5f, top, -0.5f).setUv(sprite.getU0(), vTop).setColor(c);
					vc.addVertex(m, -0.5f, top, -0.5f).setUv(sprite.getU1(), vTop).setColor(c);
				}
			});
		}
	}
}
