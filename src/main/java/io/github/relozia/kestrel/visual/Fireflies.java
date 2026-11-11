package io.github.relozia.kestrel.visual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import io.github.relozia.kestrel.track.Combat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// glowing fireflies drifting around you, each with a fading trail. drawn as soft additive glows, not particles,
// so they stay visible and don't count against the particle limit
// issues:
//   - they don't collide with blocks, one can drift through a wall (through walls mode hides that anyway)
public class Fireflies extends Module {
	// gold, yellow, green, cyan, pink, orange, sky blue. saturated so they read as color through the white core
	private static final int[] PALETTE = {0xFFFFD700, 0xFFFFFF00, 0xFF62FF6A, 0xFF00FFFF, 0xFFFF69B4, 0xFFFFA500, 0xFF00BFFF};

	private final Setting.Slider count = add(new Setting.Slider("Count", 24, 5, 120, 1, ""));
	private final Setting.Slider range = add(new Setting.Slider("Range", 35, 10, 60, 5, " blocks"));
	private final Setting.Slider speed = add(new Setting.Slider("Speed", 100, 25, 300, 25, "%"));
	private final Setting.Slider size = add(new Setting.Slider("Glow Size", 1, 0.5, 2, 0.05, "x"));
	private final Setting.Slider bright = add(new Setting.Slider("Brightness", 100, 20, 100, 5, "%"));
	private final Setting.Slider trail = add(new Setting.Slider("Trail Length", 25, 0, 32, 1, ""));
	private final Setting.Toggle pulse = add(new Setting.Toggle("Pulse", true));
	private final Setting.Toggle custom = add(new Setting.Toggle("Custom Color", false));
	private final Setting.Color color = add(new Setting.Color("Color", 0xFFFFD45A, 0xFFFFD45A, 0xFFE0703A, 0xFF7CF0E0, 0xFFC879E8, 0xFF57D163, 0xFFFFFFFF));
	private final Setting.Toggle follow = add(new Setting.Toggle("Follow Target", false));
	private final Setting.Toggle walls = add(new Setting.Toggle("Through Walls", true));
	private final Setting.Toggle night = add(new Setting.Toggle("Only At Night", false));

	private final List<Fly> flies = new ArrayList<>();
	// eases between wandering around you and circling a target, so switching doesn't snap
	private float targetBlend;

	private static final class Fly {
		double x, y, z, px, py, pz, vx, vy, vz, tx, ty, tz, phase;
		final double orbitR, orbitH, orbitSpeed;
		final int color;
		final long born = Util.getMillis();
		long turnAt;
		final ArrayDeque<double[]> trail = new ArrayDeque<>();

		Fly(double x, double y, double z, double vx, double vy, double vz, int color) {
			this.x = px = x;
			this.y = py = y;
			this.z = pz = z;
			this.vx = tx = vx;
			this.vy = ty = vy;
			this.vz = tz = vz;
			this.color = color;
			var r = ThreadLocalRandom.current();
			orbitR = 0.7 + r.nextDouble() * 0.7;
			orbitH = (r.nextDouble() - 0.35) * 0.7;
			phase = r.nextDouble() * Mth.TWO_PI;
			orbitSpeed = 0.03 + r.nextDouble() * 0.035;
		}
	}

	public Fireflies() {
		super("Fireflies", Category.VISUAL, '\ue20c', "Glowing fireflies drift around you, or circle whoever you're fighting.");
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			if (on() && !flies.isEmpty()) draw(ctx);
		});
	}

	private boolean nightOk(Minecraft mc) {
		return !night.on() || mc.level.getSkyDarken() >= 4;
	}

	private LivingEntity target() {
		LivingEntity t = follow.on() ? Combat.target(6000) : null;
		return t != null && t.isAlive() ? t : null;
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null || !nightOk(mc)) {
			flies.clear();
			return;
		}
		LivingEntity t = target();
		boolean orbit = t != null;
		Vec3 me = mc.player.position();
		Vec3 anchor = orbit ? new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.58, t.getZ()) : me;
		targetBlend = Mth.lerp(orbit ? 0.12f : 0.08f, targetBlend, orbit ? 1 : 0);
		double sp = 0.35 * speed.get() / 100 * (orbit ? 0.5 : 1);
		int keep = trail.get().intValue() + 2;
		for (Fly f : flies) step(f, anchor, orbit, sp, keep);
		double far = (range.get() + 45) * (range.get() + 45);
		flies.removeIf(f -> (f.x - me.x) * (f.x - me.x) + (f.y - me.y) * (f.y - me.y) + (f.z - me.z) * (f.z - me.z) > far);
		int want = count.get().intValue();
		while (flies.size() > want) flies.removeLast();
		while (flies.size() < want) spawn(anchor, orbit, sp);
	}

	@Override
	protected void disable() {
		flies.clear();
		targetBlend = 0;
	}

	private void spawn(Vec3 a, boolean orbit, double sp) {
		var r = ThreadLocalRandom.current();
		double max = orbit ? 2.45 : range.get(), min = orbit ? 0.6 : 5;
		double d = min + r.nextDouble() * (max - min), yaw = r.nextDouble() * Mth.TWO_PI, pitch = Math.toRadians((r.nextDouble() - 0.5) * 60);
		double y = a.y + (orbit ? (r.nextDouble() - 0.38) * 1.25 : (r.nextDouble() - 0.3) * 8 + 1);
		flies.add(new Fly(a.x - Math.sin(yaw) * d, y, a.z + Math.cos(yaw) * d,
			-Math.sin(yaw) * Math.cos(pitch) * sp, Math.sin(pitch) * sp * 0.5, Math.cos(yaw) * Math.cos(pitch) * sp, PALETTE[r.nextInt(PALETTE.length)]));
	}

	// wandering: a new heading every 2 to 4 seconds, eased into, plus a little jitter. pulled back if it drifts out
	// of range. orbiting: each fly keeps its own ring around the target and steers onto it
	private void step(Fly f, Vec3 a, boolean orbit, double sp, int keep) {
		var r = ThreadLocalRandom.current();
		f.px = f.x;
		f.py = f.y;
		f.pz = f.z;
		long now = Util.getMillis();
		if (!orbit && now >= f.turnAt) {
			double yaw = r.nextDouble() * Mth.TWO_PI, pitch = Math.toRadians((r.nextDouble() - 0.5) * 40);
			f.tx = -Math.sin(yaw) * Math.cos(pitch) * sp;
			f.ty = Math.sin(pitch) * sp * 0.3;
			f.tz = Math.cos(yaw) * Math.cos(pitch) * sp;
			f.turnAt = now + 2000 + r.nextInt(2000);
		}
		if (orbit) {
			f.phase += f.orbitSpeed;
			double dx = a.x + Math.cos(f.phase) * f.orbitR - f.x, dy = a.y + f.orbitH + Math.sin(f.phase * 1.35) * 0.14 - f.y, dz = a.z + Math.sin(f.phase) * f.orbitR - f.z;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz), pull = d > 4 ? 0.34 : 0.16;
			f.tx *= 0.74;
			f.ty *= 0.74;
			f.tz *= 0.74;
			if (d > 0.001) {
				f.tx += dx / d * sp * pull;
				f.ty += dy / d * sp * pull;
				f.tz += dz / d * sp * pull;
			}
		} else {
			double dx = a.x - f.x, dy = a.y + 1 - f.y, dz = a.z - f.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d > range.get()) {
				f.tx += dx / d * sp * 0.15;
				f.ty += dy / d * sp * 0.15;
				f.tz += dz / d * sp * 0.15;
			}
		}
		double ease = orbit ? 0.06 : 0.02, jitter = orbit ? 0.002 : 0.03, max = sp * 1.5;
		f.vx = Mth.clamp(f.vx + (f.tx - f.vx) * ease + (r.nextDouble() - 0.5) * jitter, -max, max);
		f.vy = Mth.clamp(f.vy + (f.ty - f.vy) * ease + (r.nextDouble() - 0.5) * jitter, -max, max);
		f.vz = Mth.clamp(f.vz + (f.tz - f.vz) * ease + (r.nextDouble() - 0.5) * jitter, -max, max);
		f.x += f.vx;
		f.y += f.vy;
		f.z += f.vz;
		f.trail.addFirst(new double[] {f.x, f.y, f.z});
		while (f.trail.size() > keep) f.trail.removeLast();
	}

	// trails first, then the glows on top. each glow is four squares: a wide faint halo down to a white core
	private void draw(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		CameraRenderState cam = ctx.levelState().cameraRenderState;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vector3f right = cam.orientation.transform(new Vector3f(1, 0, 0)), up = cam.orientation.transform(new Vector3f(0, 1, 0));
		float glow = size.get().floatValue() * Mth.lerp(targetBlend, 1, 0.48f);
		float alphaK = bright.get().floatValue() / 100 * Mth.lerp(targetBlend, 1, 0.56f);
		int trailLen = Math.max(0, Math.round(Mth.lerp(targetBlend, trail.get().floatValue(), Math.min(10, trail.get().floatValue()))));
		long now = Util.getMillis();
		Vec3 c = cam.pos;
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), Glow.type(walls.on()), (pose, vc) -> {
			for (Fly f : flies) {
				float life = Math.min(1, (now - f.born) / 1000f) * alphaK;
				if (life <= 0.01f) continue;
				int col = custom.on() ? color.get() : f.color;
				Vector3f at = new Vector3f((float) (Mth.lerp(pt, f.px, f.x) - c.x), (float) (Mth.lerp(pt, f.py, f.y) - c.y), (float) (Mth.lerp(pt, f.pz, f.z) - c.z));
				ribbon(vc, pose.pose(), f, at, c, col, life, glow, trailLen);
				float p = pulse.on() ? 0.8f + 0.2f * Mth.sin((now - f.born) / 200f) : 1;
				float a = p * life;
				Glow.quad(vc, pose.pose(), at, right, up, 1.7f * glow, ARGB.color(Math.round(a * 0.15f * 255), col & 0xFFFFFF));
				Glow.quad(vc, pose.pose(), at, right, up, 0.95f * glow, ARGB.color(Math.round(a * 0.4f * 255), col & 0xFFFFFF));
				Glow.quad(vc, pose.pose(), at, right, up, 0.4f * glow, ARGB.color(Math.round(a * 0.85f * 255), col & 0xFFFFFF));
				Glow.quad(vc, pose.pose(), at, right, up, 0.15f * glow, ARGB.color(Math.round(a * 255), 0xFFFFFF));
			}
		});
	}

	// a strip that narrows and fades toward the tail, turned to face the camera along its length. it samples the
	// middle column of the glow texture, so its edges are soft too
	private static void ribbon(VertexConsumer vc, Matrix4f m, Fly f, Vector3f head, Vec3 cam, int col, float life, float glow, int len) {
		int n = Math.min(f.trail.size(), len);
		if (n < 2) return;
		List<Vector3f> pts = new ArrayList<>(n + 1);
		pts.add(head);
		int i = 0;
		for (double[] p : f.trail) {
			if (i++ == 0) continue;
			if (pts.size() > n) break;
			pts.add(new Vector3f((float) (p[0] - cam.x), (float) (p[1] - cam.y), (float) (p[2] - cam.z)));
		}
		for (int k = 0; k < pts.size() - 1; k++) {
			Vector3f a = pts.get(k), b = pts.get(k + 1);
			Vector3f dir = new Vector3f(b).sub(a);
			if (dir.lengthSquared() < 1e-6f || a.lengthSquared() < 1e-6f) continue;
			Vector3f side = dir.normalize().cross(new Vector3f(a).normalize());
			if (side.lengthSquared() < 1e-6f) continue;
			side.normalize();
			float t1 = k / (float) (pts.size() - 1), t2 = (k + 1) / (float) (pts.size() - 1);
			float w1 = 0.055f * glow * (1 - t1), w2 = 0.055f * glow * (1 - t2);
			int c1 = ARGB.color(Math.round((1 - t1) * (1 - t1) * 0.55f * life * 255), col & 0xFFFFFF);
			int c2 = ARGB.color(Math.round((1 - t2) * (1 - t2) * 0.55f * life * 255), col & 0xFFFFFF);
			vc.addVertex(m, a.x + side.x * w1, a.y + side.y * w1, a.z + side.z * w1).setUv(0.5f, 0).setColor(c1);
			vc.addVertex(m, a.x - side.x * w1, a.y - side.y * w1, a.z - side.z * w1).setUv(0.5f, 1).setColor(c1);
			vc.addVertex(m, b.x - side.x * w2, b.y - side.y * w2, b.z - side.z * w2).setUv(0.5f, 1).setColor(c2);
			vc.addVertex(m, b.x + side.x * w2, b.y + side.y * w2, b.z + side.z * w2).setUv(0.5f, 0).setColor(c2);
		}
	}
}
