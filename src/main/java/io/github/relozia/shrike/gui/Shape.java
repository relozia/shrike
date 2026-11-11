package io.github.relozia.shrike.gui;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.relozia.shrike.Shrike;
import io.github.relozia.shrike.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

// smooth rounded shapes for the menu and the hud: fill, 4 corner gradient, outline, soft shadow and frosted glass, any
// radius, anti aliased by a distance field in the shader (shaders/core/shape.*). the menu's 9 slice sprites can't
// do sub pixel corners or soft shadows
// glass samples a blurred copy of the world, made once a frame right before the gui draws (GuiRendererMixin)
// issues:
//   - glass shows the world only, a hud panel under a glass panel isn't in the blur
//   - the blur radius is in screen pixels, so it's a bit stronger on small windows than on big ones
public final class Shape {
	private Shape() {}

	// the per shape numbers ride on vanilla's attribute names, BufferBuilder only knows how to fill those
	private static final VertexFormat FORMAT = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT)
		.addAttribute("UV1", GpuFormat.RG16_SINT)
		.addAttribute("UV2", GpuFormat.RG16_SINT)
		.addAttribute("LineWidth", GpuFormat.R32_FLOAT)
		.build();

	private static final RenderPipeline FLAT = pipeline("shape", null);
	private static final RenderPipeline GLASS = pipeline("glass", "GLASS");
	private static final RenderPipeline IMAGE = pipeline("shape_image", "IMAGE");

	private static RenderPipeline pipeline(String name, @Nullable String define) {
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withLocation(Shrike.id("pipeline/" + name))
			.withVertexShader(Shrike.id("core/shape"))
			.withFragmentShader(Shrike.id("core/shape"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexBinding(0, FORMAT)
			.withPrimitiveTopology(PrimitiveTopology.QUADS);
		if (define != null) b.withShaderDefine(define).withBindGroupLayout(BindGroupLayouts.SAMPLER0);
		return RenderPipelines.register(b.build());
	}

	// ---------- shapes ----------

	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int c) {
		add(g, FLAT, TextureSetup.noTexture(), x, y, w, h, r, 0, 0, 1, c, c, c, c);
	}

	public static void gradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int tl, int tr, int br, int bl) {
		add(g, FLAT, TextureSetup.noTexture(), x, y, w, h, r, 0, 0, 1, tl, tr, br, bl);
	}

	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int c, float t) {
		add(g, FLAT, TextureSetup.noTexture(), x, y, w, h, r, t, 0, 1, c, c, c, c);
	}

	// blur is how far the edge fades out, spread grows the shape first
	public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float blur, float spread, int c) {
		add(g, FLAT, TextureSetup.noTexture(), x - spread, y - spread, w + spread * 2, h + spread * 2, r + spread, 0, blur, blur * 2 + 1, c, c, c, c);
	}

	// what's behind, blurred, at this alpha. strength is a blur radius in pixels at 2560x1440 that shrinks with the
	// window, so the frost reads the same on a laptop as on a 1440p monitor. 5 for pills and cards, 10.5 for panels.
	// nothing when HUD Blur is off
	public static void glass(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float alpha, float strength) {
		if (!Modules.SETTINGS.hudBlur.on() || alpha <= 0) return;
		TextureSetup tex = Blur.texture(strength);
		if (tex == null) return;
		int c = Math.round(Math.min(alpha, 1) * 255) << 24 | 0xFFFFFF;
		add(g, GLASS, tex, x, y, w, h, r, 0, 0, 1, c, c, c, c);
	}

	// glass at a fixed blur radius in screen pixels, the menu's panels. the menu style "Solid" turns it off
	public static void glassPx(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float alpha, int px) {
		if (alpha <= 0) return;
		TextureSetup tex = Blur.texturePx(px);
		if (tex == null) return;
		int c = Math.round(Math.min(alpha, 1) * 255) << 24 | 0xFFFFFF;
		add(g, GLASS, tex, x, y, w, h, r, 0, 0, 1, c, c, c, c);
	}

	// a texture (album art) in a rounded box
	public static void image(GuiGraphicsExtractor g, Identifier tex, float x, float y, float w, float h, float r, float alpha) {
		var t = Minecraft.getInstance().getTextureManager().getTexture(tex);
		int c = Math.round(Math.min(alpha, 1) * 255) << 24 | 0xFFFFFF;
		add(g, IMAGE, TextureSetup.singleTexture(t.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)), x, y, w, h, r, 0, 0, 1, c, c, c, c);
	}

	private static void add(GuiGraphicsExtractor g, RenderPipeline p, TextureSetup tex, float x, float y, float w, float h, float r,
		float thick, float soft, float pad, int tl, int tr, int br, int bl) {
		if (w <= 0 || h <= 0) return;
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		ScreenRectangle scissor = g.scissorStack.peek();
		float x0 = x - pad, y0 = y - pad, x1 = x + w + pad, y1 = y + h + pad;
		ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1)
			.transformMaxBounds(pose);
		if (scissor != null) bounds = scissor.intersection(bounds);
		g.guiRenderState.addGuiElement(new El(p, tex, pose, x0, y0, x1, y1, x + w / 2, y + h / 2, w / 2, h / 2, r, thick, soft, tl, tr, br, bl, scissor, bounds));
	}

	private record El(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float x0, float y0, float x1, float y1,
		float cx, float cy, float hw, float hh, float r, float thick, float soft, int tl, int tr, int br, int bl,
		@Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer vc) {
			corner(vc, x0, y0, tl);
			corner(vc, x0, y1, bl);
			corner(vc, x1, y1, br);
			corner(vc, x1, y0, tr);
		}

		private void corner(VertexConsumer vc, float x, float y, int c) {
			vc.addVertexWith2DPose(pose, x, y).setColor(c).setUv(x - cx, y - cy)
				.setUv1(Math.round(hw * 8), Math.round(hh * 8)).setUv2(Math.round(r * 8), Math.round(thick * 8)).setLineWidth(soft);
		}
	}

	// ---------- the blurred copies glass reads ----------

	// one copy per blur radius in use this frame (a hud with pills and panels uses two)
	public static final class Blur {
		private static final RenderTarget[] COPIES = new RenderTarget[17];
		private static final boolean[] WANTED = new boolean[17];
		private static final CrossFrameResourcePool POOL = new CrossFrameResourcePool(3);

		// the texture glass samples, made (or resized) to match the window. the copy itself happens later, in capture()
		static @Nullable TextureSetup texture(float strength) {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			GpuTexture mc = main.getColorTexture();
			if (mc == null) return null;
			float px = strength * (float) Math.sqrt((double) main.width * main.height / (2560.0 * 1440.0));
			return texturePx(Math.round(px));
		}

		static @Nullable TextureSetup texturePx(int px) {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			GpuTexture mc = main.getColorTexture();
			if (mc == null) return null;
			int r = Math.clamp(px, 1, 16);
			RenderTarget copy = COPIES[r];
			if (copy == null || copy.width != main.width || copy.height != main.height) {
				if (copy != null) copy.destroyBuffers();
				copy = COPIES[r] = new TextureTarget("shrike hud blur " + r, main.width, main.height, false, mc.getFormat());
			}
			WANTED[r] = true;
			return TextureSetup.singleTexture(copy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		}

		// right before the gui's draws run: the main target holds just the world then
		public static void capture() {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			boolean any = false;
			for (int r = 1; r < COPIES.length; r++) {
				if (!WANTED[r]) continue;
				WANTED[r] = false;
				RenderTarget copy = COPIES[r];
				if (copy.width != main.width || copy.height != main.height) continue;
				RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
				PostChain chain = Minecraft.getInstance().getShaderManager().getPostChain(Shrike.id("hud_blur_" + r), LevelTargetBundle.MAIN_TARGETS);
				if (chain != null) chain.process(copy, POOL);
				any = true;
			}
			if (any) POOL.endFrame();
		}
	}
}
