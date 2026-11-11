package io.github.relozia.shrike.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// boxes, lines and floating text in the world. block overlay, hitboxes, damage numbers
// and tnt timers all draw through here
public final class WorldDraw {
	private WorldDraw() {}

	// 12 edges as line pairs. the lines render type wants a normal (the line's direction) and a width per vertex
	public static void box(PoseStack.Pose p, VertexConsumer vc, AABB b, int c, float w) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		line(p, vc, c, w, x0, y0, z0, x1, y0, z0); line(p, vc, c, w, x0, y0, z1, x1, y0, z1);
		line(p, vc, c, w, x0, y1, z0, x1, y1, z0); line(p, vc, c, w, x0, y1, z1, x1, y1, z1);
		line(p, vc, c, w, x0, y0, z0, x0, y1, z0); line(p, vc, c, w, x1, y0, z0, x1, y1, z0);
		line(p, vc, c, w, x0, y0, z1, x0, y1, z1); line(p, vc, c, w, x1, y0, z1, x1, y1, z1);
		line(p, vc, c, w, x0, y0, z0, x0, y0, z1); line(p, vc, c, w, x1, y0, z0, x1, y0, z1);
		line(p, vc, c, w, x0, y1, z0, x0, y1, z1); line(p, vc, c, w, x1, y1, z0, x1, y1, z1);
	}

	public static void line(PoseStack.Pose p, VertexConsumer vc, int c, float w, float ax, float ay, float az, float bx, float by, float bz) {
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-6f) return;
		dx /= len;
		dy /= len;
		dz /= len;
		vc.addVertex(p, ax, ay, az).setColor(c).setNormal(p, dx, dy, dz).setLineWidth(w);
		vc.addVertex(p, bx, by, bz).setColor(c).setNormal(p, dx, dy, dz).setLineWidth(w);
	}

	// six faces for RenderTypes.debugQuads (translucent, no cull, so the inside shows too)
	public static void fill(Matrix4f m, VertexConsumer vc, AABB b, int c) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		quad(m, vc, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(m, vc, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(m, vc, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(m, vc, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(m, vc, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(m, vc, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(Matrix4f m, VertexConsumer vc, int c, float... p) {
		for (int i = 0; i < 12; i += 3) vc.addVertex(m, p[i], p[i + 1], p[i + 2]).setColor(c);
	}

	// text facing the camera at a world position (already camera relative), nametag sized at scale 1.
	// full bright and drawn through walls, these are things you want to see
	public static void text(SubmitNodeCollector out, PoseStack ps, CameraRenderState cam, Vec3 at, Component c, int color, float scale) {
		text(out, ps, cam, at, c, color, scale, 0, true);
	}

	// with a background behind the text, and optionally hidden behind blocks like a normal nametag
	public static void text(SubmitNodeCollector out, PoseStack ps, CameraRenderState cam, Vec3 at, Component c, int color, float scale, int bg, boolean throughWalls) {
		ps.pushPose();
		ps.translate(at.x, at.y, at.z);
		ps.mulPose(cam.orientation);
		float s = 0.025f * scale;
		ps.scale(s, -s, s);
		Font f = Minecraft.getInstance().font;
		out.submitText(ps, -f.width(c) / 2f, 0, c.getVisualOrderText(), true, throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, color, bg, 0);
		ps.popPose();
	}
}
