package io.github.relozia.kestrel.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.relozia.kestrel.chat.Players;
import io.github.relozia.kestrel.mixin.OverlayTextureAccessor;
import io.github.relozia.kestrel.module.Category;
import io.github.relozia.kestrel.module.Module;
import io.github.relozia.kestrel.setting.Setting;
import io.github.relozia.kestrel.visual.WorldDraw;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// pvp things drawn in the world. one COLLECT_SUBMITS hook draws all of them, each module only adds its part
public final class PvpVisuals {
	private PvpVisuals() {}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(PvpVisuals::draw);
	}

	private static void draw(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		CameraRenderState cam = ctx.levelState().cameraRenderState;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		PoseStack ps = ctx.poseStack();
		SubmitNodeCollector out = ctx.submitNodeCollector();
		if (Hitboxes.INSTANCE.on()) Hitboxes.INSTANCE.draw(mc, ps, out, cam.pos, pt);
		if (DamageNumbers.INSTANCE.on()) DamageNumbers.INSTANCE.draw(ps, out, cam);
		if (TntTimer.INSTANCE.on()) TntTimer.draw(mc, ps, out, cam, pt);
	}

	// where the entity's box is this frame, relative to the camera
	static AABB box(Entity e, Vec3 cam, float pt) {
		double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
		return e.getBoundingBox().move(x - e.getX() - cam.x, y - e.getY() - cam.y, z - e.getZ() - cam.z);
	}

	// ---------- hitboxes ----------

	public static class Hitboxes extends Module {
		static Hitboxes INSTANCE;

		private final Setting.Toggle outline = add(new Setting.Toggle("Outline", true));
		private final Setting.Color color = add(new Setting.Color("Outline Color", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE0703A, 0xFF5B8DEF, 0xFF2EC4B6, 0xFFE5484D));
		private final Setting.Slider outlineAlpha = add(new Setting.Slider("Outline Opacity", 100, 10, 100, 5, "%"));
		private final Setting.Slider width = add(new Setting.Slider("Line Width", 2, 1, 6, 0.5, "px"));
		private final Setting.Toggle eye = add(new Setting.Toggle("Eye Line", true));
		private final Setting.Toggle fill = add(new Setting.Toggle("Fill", false));
		private final Setting.Color fillColor = add(new Setting.Color("Fill Color", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE0703A, 0xFF5B8DEF, 0xFF2EC4B6, 0xFFE5484D));
		private final Setting.Slider fillAlpha = add(new Setting.Slider("Fill Opacity", 15, 5, 60, 5, "%"));
		private final Setting.Toggle hitFlash = add(new Setting.Toggle("Hit Flash", true));
		private final Setting.Toggle mobs = add(new Setting.Toggle("Mobs Too", false));
		private final Setting.Toggle self = add(new Setting.Toggle("Yourself In Third Person", false));

		public Hitboxes() {
			super("Hitboxes", Category.COMBAT, '\ue257', "Shows player hitboxes, friends in their friend color.");
			INSTANCE = this;
		}

		void draw(Minecraft mc, PoseStack ps, SubmitNodeCollector out, Vec3 cam, float pt) {
			float w = width.get().floatValue();
			for (Entity e : mc.level.entitiesForRendering()) {
				boolean player = e instanceof Player;
				if (!player && !(mobs.on() && e instanceof LivingEntity)) continue;
				if (e == mc.player && (!self.on() || mc.options.getCameraType().isFirstPerson())) continue;
				if (e instanceof Player fp && Players.Friends.ignored(fp)) continue;
				// invisible stays invisible, a hitbox mod isn't a wallhack
				if (e.isInvisibleTo(mc.player) || e.distanceToSqr(mc.player) > 64 * 64) continue;
				AABB b = box(e, cam, pt);
				boolean friend = player && Players.Friends.is(((Player) e).getGameProfile().name());
				// the box goes red for the half second a hit makes it flash, so you see your hits land
				boolean hurt = hitFlash.on() && e instanceof LivingEntity l && l.hurtTime > 0;
				int base = friend ? Players.Friends.color() : color.get();
				int c = ARGB.color(Math.round(outlineAlpha.get().floatValue() / 100 * 255), (hurt ? 0xFFE5484D : base) & 0xFFFFFF);
				float eyeY = (float) b.minY + e.getEyeHeight();
				if (outline.on() || eye.on()) out.submitCustomGeometry(ps, RenderTypes.lines(), (pose, vc) -> {
					if (outline.on()) WorldDraw.box(pose, vc, b, c, w);
					// red ring around the box at eye height, like F3+B
					if (eye.on()) WorldDraw.box(pose, vc, new AABB(b.minX, eyeY, b.minZ, b.maxX, eyeY, b.maxZ), 0xFFE5484D, w);
				});
				if (fill.on()) {
					int fb = hurt ? 0xFFE5484D : friend ? base : fillColor.get();
					int fc = ARGB.color(Math.round(fillAlpha.get().floatValue() / 100 * 255), fb & 0xFFFFFF);
					out.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> WorldDraw.fill(pose.pose(), vc, b, fc));
				}
			}
		}
	}

	// ---------- hit color ----------

	// the red flash is a 16x16 texture (top half hurt red, bottom half the white flash), so it gets repainted
	public static class HitColor extends Module {
		private static final int VANILLA = 0xB2FF0000;

		private final Setting.Color color = add(new Setting.Color("Color", 0xFFFF0000, 0xFFFF0000, 0xFFE0703A, 0xFF5B8DEF, 0xFFC879E8, 0xFF2EC4B6, 0xFFFFFFFF));
		// how much of the color shows. the texture's alpha is the opposite (how much skin shows through the
		// tint), vanilla's red is 0xB2 alpha = 30% strength
		private final Setting.Slider strength = add(new Setting.Slider("Strength", 30, 10, 90, 5, "%"));
		private int painted = VANILLA;

		public HitColor() {
			super("Hit Color", Category.COMBAT, '\ue2e7', "Changes the red flash when something takes damage.");
		}

		@Override
		public void tick(Minecraft mc) {
			int want = ARGB.color(Math.round((1 - strength.get().floatValue() / 100) * 255), color.get() & 0xFFFFFF);
			if (want != painted) paint(mc, want);
		}

		@Override
		protected void disable() {
			paint(Minecraft.getInstance(), VANILLA);
		}

		private void paint(Minecraft mc, int c) {
			var tex = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).kestrel$texture();
			NativeImage px = tex.getPixels();
			for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) px.setPixel(x, y, c);
			tex.upload();
			painted = c;
		}
	}

	// ---------- damage numbers ----------

	public static class DamageNumbers extends Module {
		static DamageNumbers INSTANCE;

		// moves on its own once spawned: pops toward you, slows down and drops a little
		private static final class Num {
			Vec3 at, prev, vel;
			final String text;
			final int color;
			int age;

			Num(Vec3 at, Vec3 vel, String text, int color) {
				this.at = prev = at;
				this.vel = vel;
				this.text = text;
				this.color = color;
			}
		}

		private final Setting.Choice unit = add(new Setting.Choice("Show As", "Hearts", "Hearts", "Health"));
		private final Setting.Toggle heals = add(new Setting.Toggle("Show Healing", false));
		private final Setting.Color small = add(new Setting.Color("Small Damage Color", 0xFFFFAA00, 0xFFFFAA00, 0xFFF5A524, 0xFFFFFFFF));
		private final Setting.Color medium = add(new Setting.Color("Medium Damage Color", 0xFFFF0000, 0xFFFF0000, 0xFFE0703A, 0xFFE5484D));
		private final Setting.Color large = add(new Setting.Color("Large Damage Color", 0xFFAA0000, 0xFFAA0000, 0xFF8B5CF6, 0xFF7A0000));
		private final Setting.Toggle background = add(new Setting.Toggle("Background", false));
		private final Setting.Color bgColor = add(new Setting.Color("Background Color", 0x90000000, 0xFF000000, 0xFF101216, 0xFF2A1A12).withAlpha());
		private final Setting.Slider size = add(new Setting.Slider("Text Scale", 100, 40, 400, 10, "%"));
		private final Setting.Slider lifetime = add(new Setting.Slider("Lifetime", 1.6, 0.4, 3, 0.05, "s"));
		private final Setting.Slider distance = add(new Setting.Slider("Max Distance", 48, 8, 128, 1, " blocks"));
		private final Map<Integer, Float> health = new HashMap<>();
		private final ArrayDeque<Num> nums = new ArrayDeque<>();

		public DamageNumbers() {
			super("Damage Numbers", Category.COMBAT, '\ue0ef', "Floating numbers when you or a mob takes damage.");
			INSTANCE = this;
		}

		// under 2 small, up to 8 toward medium, up to 16 toward large. health, not hearts, so a 4 is a two heart hit
		private int color(float d) {
			if (d < 2) return small.get();
			if (d < 8) return ARGB.srgbLerp((d - 2) / 6, small.get(), medium.get());
			if (d < 16) return ARGB.srgbLerp((d - 8) / 8, medium.get(), large.get());
			return large.get();
		}

		private int life() {
			return Math.max(8, Math.round(lifetime.get().floatValue() * 20));
		}

		// other players are left out on purpose: plenty of servers fake or hide their health, a number there lies
		@Override
		public void tick(Minecraft mc) {
			if (mc.level == null || mc.player == null) return;
			for (Iterator<Num> it = nums.iterator(); it.hasNext(); ) {
				Num n = it.next();
				if (++n.age >= life()) {
					it.remove();
					continue;
				}
				n.prev = n.at;
				n.at = n.at.add(n.vel);
				n.vel = n.vel.scale(0.982).add(0, -0.0012, 0);
			}
			double far = distance.get() * distance.get();
			Vec3 cam = mc.gameRenderer.mainCamera().position();
			Map<Integer, Float> seen = new HashMap<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof LivingEntity l) || e instanceof Player && e != mc.player || e instanceof ArmorStand || e.distanceToSqr(mc.player) > far) continue;
				float h = l.getHealth();
				seen.put(e.getId(), h);
				Float before = health.get(e.getId());
				if (before == null || Math.abs(h - before) < 0.01f || l.isDeadOrDying() && h >= before) continue;
				float d = h - before;
				if (d > 0 && !heals.on()) continue;
				var rnd = ThreadLocalRandom.current();
				// somewhere in a flattened shell around the upper body
				Vec3 dir = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize().scale(rnd.nextDouble(0.2, 0.5));
				Vec3 at = e.position().add(dir.x * 0.45, e.getBbHeight() * 0.62 + dir.y * 0.3, dir.z * 0.45);
				Vec3 away = new Vec3(at.x - cam.x, 0, at.z - cam.z);
				away = away.lengthSqr() < 1e-6 ? Vec3.ZERO : away.normalize();
				Vec3 vel = e.getDeltaMovement().scale(0.35).add(-away.x * 0.028 + rnd.nextDouble(-0.006, 0.006), 0.018 + rnd.nextDouble(0, 0.006), -away.z * 0.028 + rnd.nextDouble(-0.006, 0.006));
				float shown = unit.get().equals("Hearts") ? Math.abs(d) / 2 : Math.abs(d);
				shown = Math.round(shown * 10) / 10f;
				String num = shown == Math.floor(shown) ? Integer.toString((int) shown) : String.format(java.util.Locale.ROOT, "%.1f", shown);
				String text = (d > 0 ? "+" : "-") + num + (unit.get().equals("Hearts") ? "\u2764" : "");
				nums.addLast(new Num(at, vel, text, d > 0 ? 0xFF57D163 : color(-d)));
				// the particle setting caps how many can float at once, someone on Minimal asked for less on screen
				int cap = switch (mc.options.particles().get()) {
					case ALL -> 256;
					case DECREASED -> 64;
					default -> 16;
				};
				while (nums.size() > cap) nums.removeFirst();
			}
			health.clear();
			health.putAll(seen);
		}

		@Override
		protected void disable() {
			health.clear();
			nums.clear();
		}

		void draw(PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
			float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			for (Num n : nums) {
				// fades to a third over its life, never all the way, the last number still has to be readable
				float a = Mth.clamp(1 - 0.65f * (n.age + pt) / life(), 0.35f, 1);
				Vec3 at = n.prev.lerp(n.at, pt).subtract(cam.pos);
				if (at.lengthSqr() > distance.get() * distance.get()) continue;
				int bg = background.on() ? ARGB.multiplyAlpha(bgColor.get(), a) : 0;
				WorldDraw.text(out, ps, cam, at, Component.literal(n.text), ARGB.multiplyAlpha(n.color, a), size.get().floatValue() / 100, bg, false);
			}
		}
	}

	// ---------- tnt ----------

	public static class TntTimer extends Module {
		static TntTimer INSTANCE;

		private final Setting.Slider size = add(new Setting.Slider("Size", 1.4, 0.5, 3, 0.1, "x"));
		private final Setting.Slider distance = add(new Setting.Slider("Max Distance", 48, 8, 128, 4, " blocks"));
		private final Setting.Toggle decimals = add(new Setting.Toggle("Decimals", true));
		private final Setting.Choice colors = add(new Setting.Choice("Colors", "By Time Left", "By Time Left", "Single Color"));
		private final Setting.Color color = add(new Setting.Color("Color", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE5484D, 0xFFF5A524, 0xFF57D163, 0xFFE0703A));

		public TntTimer() {
			super("TNT Timer", Category.COMBAT, '\ue2ff', "Seconds left on lit TNT, floating over it.");
			INSTANCE = this;
		}

		// green with plenty of time, red when it's about to go
		static void draw(Minecraft mc, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam, float pt) {
			TntTimer t = INSTANCE;
			double far = t.distance.get() * t.distance.get();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof PrimedTnt tnt) || e.distanceToSqr(mc.player) > far) continue;
				float sec = Math.max(0, (tnt.getFuse() - pt) / 20f);
				int c = t.colors.get().equals("Single Color") ? t.color.get() : ARGB.srgbLerp(Mth.clamp(sec / 4f, 0, 1), 0xFFE5484D, 0xFF57D163);
				AABB b = box(e, cam.pos, pt);
				Vec3 at = new Vec3((b.minX + b.maxX) / 2, b.maxY + 0.35, (b.minZ + b.maxZ) / 2);
				String txt = t.decimals.on() ? String.format(java.util.Locale.ROOT, "%.1f", sec) : Integer.toString((int) Math.ceil(sec));
				WorldDraw.text(out, ps, cam, at, Component.literal(txt), c, t.size.get().floatValue());
			}
		}
	}
}
