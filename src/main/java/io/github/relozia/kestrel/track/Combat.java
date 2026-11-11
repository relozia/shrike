package io.github.relozia.kestrel.track;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// your own hits only: combo (hits landed in a row without taking one) and reach (distance of your last hit)
// a swing "lands" if the target starts flashing red shortly after (one round trip), that is the moment the
// game itself accepted the hit
// issues:
//   - a target already flashing from someone else's hit can't flash again, so that swing doesn't count (correct, it did no damage)
//   - the flash comes back a round trip after the swing, so the wait grows with ping. a fixed 3 ticks
//     missed hits even in singleplayer when the integrated server was a tick behind
public final class Combat {
	private static final long RESET = 3000;

	private static LivingEntity pending;
	private static int pendingTicks;
	private static int startHurt;
	private static int combo;
	private static long lastLanded;
	private static float reach = -1;
	private static long reachAt;
	private static int lastHurt;
	// who we last landed a hit on and when, plus session counts for pvp info
	private static LivingEntity target;
	private static long targetAt;
	private static int landed, attempts;

	private Combat() {}

	public static void init() {
		AttackEntityCallback.EVENT.register((p, level, hand, e, hit) -> {
			if (level.isClientSide() && p == Minecraft.getInstance().player && e instanceof LivingEntity t) {
				pending = t;
				attempts++;
				pendingTicks = waitTicks();
				startHurt = t.hurtTime;
				reach = (float) dist(p, t);
				reachAt = Util.getMillis();
			}
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(Combat::tick);
	}

	private static void tick(Minecraft mc) {
		Player p = mc.player;
		if (p == null) {
			combo = 0;
			pending = null;
			return;
		}
		if (pending != null) {
			if (pending.hurtTime > startHurt) {
				combo++;
				landed++;
				lastLanded = Util.getMillis();
				target = pending;
				targetAt = lastLanded;
				pending = null;
			} else if (--pendingTicks <= 0) {
				pending = null;
			}
		}
		// hurtTime jumps up when we take a hit and counts down after, so any rise means we got hit
		if (p.hurtTime > lastHurt) combo = 0;
		lastHurt = p.hurtTime;
		if (combo > 0 && Util.getMillis() - lastLanded > RESET) combo = 0;
	}

	// 4 ticks of slack plus the round trip in ticks, capped at a second so a lagging server can't make
	// a whiff count as a hit much later
	private static int waitTicks() {
		Minecraft mc = Minecraft.getInstance();
		int ms = 0;
		if (mc.getConnection() != null && mc.player != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (info != null) ms = info.getLatency();
		}
		return Math.min(20, 4 + ms / 50);
	}

	// eye to the closest point of the target's hitbox, which is what reach actually measures
	private static double dist(Player p, LivingEntity t) {
		Vec3 eye = p.getEyePosition();
		AABB b = t.getBoundingBox();
		Vec3 near = new Vec3(Mth.clamp(eye.x, b.minX, b.maxX), Mth.clamp(eye.y, b.minY, b.maxY), Mth.clamp(eye.z, b.minZ, b.maxZ));
		return eye.distanceTo(near);
	}

	// the last thing we landed a hit on, if that was within the last ms milliseconds
	public static LivingEntity target(long ms) {
		return target != null && Util.getMillis() - targetAt <= ms ? target : null;
	}

	public static int landed() {
		return landed;
	}

	public static int attempts() {
		return attempts;
	}

	public static int combo() {
		return combo;
	}

	public static float reach() {
		return reach;
	}

	public static long reachAge() {
		return Util.getMillis() - reachAt;
	}
}
