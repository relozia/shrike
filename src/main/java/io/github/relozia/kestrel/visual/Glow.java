package io.github.relozia.kestrel.visual;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.relozia.kestrel.Kestrel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// soft additive glow in the world: vanilla's textured quad shader with lightning's blending, so overlapping
// glows add up to light instead of stacking up as grey. used by the fireflies
public final class Glow {
	private Glow() {}

	public static final Identifier TEXTURE = Kestrel.id("textures/world/glow.png");

	private static RenderPipeline pipeline(String name, boolean depth) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withLocation(Kestrel.id("pipeline/" + name))
			.withVertexShader("core/position_tex_color")
			.withFragmentShader("core/position_tex_color")
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(false)
			// tested against the world but never written, glows don't hide each other
			.withDepthStencilState(new DepthStencilState(depth ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS, false))
			.build());
	}

	private static final RenderPipeline DEPTH = pipeline("glow", true);
	private static final RenderType IN_WORLD = RenderType.create("kestrel_glow",
		RenderSetup.builder(DEPTH).withTexture("Sampler0", TEXTURE).sortOnUpload().createRenderSetup());
	private static final RenderType THROUGH_WALLS = RenderType.create("kestrel_glow_through_walls",
		RenderSetup.builder(pipeline("glow_through_walls", false)).withTexture("Sampler0", TEXTURE).sortOnUpload().createRenderSetup());

	public static RenderType type(boolean throughWalls) {
		return throughWalls ? THROUGH_WALLS : IN_WORLD;
	}

	// a square facing the camera: right and up are the camera's own axes
	public static void quad(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f right, Vector3f up, float size, int argb) {
		float h = size / 2;
		float rx = right.x * h, ry = right.y * h, rz = right.z * h, ux = up.x * h, uy = up.y * h, uz = up.z * h;
		vc.addVertex(m, c.x - rx + ux, c.y - ry + uy, c.z - rz + uz).setUv(0, 0).setColor(argb);
		vc.addVertex(m, c.x - rx - ux, c.y - ry - uy, c.z - rz - uz).setUv(0, 1).setColor(argb);
		vc.addVertex(m, c.x + rx - ux, c.y + ry - uy, c.z + rz - uz).setUv(1, 1).setColor(argb);
		vc.addVertex(m, c.x + rx + ux, c.y + ry + uy, c.z + rz + uz).setUv(1, 0).setColor(argb);
	}
}
